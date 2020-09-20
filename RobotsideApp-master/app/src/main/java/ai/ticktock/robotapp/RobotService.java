package ai.cellbots.robotapp;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import ai.cellbots.common.CloudHelper;
import ai.cellbots.common.Transform;
import ai.cellbots.common.cloud.TimestampManager;
import ai.cellbots.common.data.RobotPreferences;
import ai.cellbots.common.settings.RobotPreferencesFragment;
import ai.cellbots.kobuki.KobukiDriver;
import ai.cellbots.neato.NeatoDriver;
import ai.cellbots.parallax.ParallaxArloDriver;
import ai.cellbots.pathfollower.GoalPathFollower;
import ai.cellbots.robot.cloud.CloudRobotStateManager;
import ai.cellbots.robot.manager.RobotManagerConfiguration;
import ai.cellbots.robot.manager.RobotManagerWrapper;
import ai.cellbots.robotlib.CrashReporter;
import ai.cellbots.robotlib.RobotDriver;
import ai.cellbots.robotlib.RobotDriverMultiplex;
import ai.cellbots.robotlib.RobotRunner;
import ai.cellbots.tangocommon.CloudWorldManager;

/**
 * The RobotService runs in the background on Android, holding the RobotRunner.
 */
@SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
public class RobotService extends Service {
    private static final String TAG = "RobotService";
    private static final String PING_TIME_TAG = TAG + "PingTime";

    private static final String START_ACTION = "ROBOT_SERVICE_START";
    private static final String STOP_ACTION = "ROBOT_SERVICE_STOP";
    private static final int NOTIFICATION_ID = 101;

    private static RobotService sInstance = null;
    private static final Object sInstanceLock = new Object();
    private static boolean sLockout = false;

    private final static Set<RobotRunner.Monitor> sMonitors = new HashSet<>();
    private final static Set<RobotManagerWrapper.Listener> sListeners = new HashSet<>();

    // Frequency of sending heartbeat updates to the cloud. In milliseconds.
    private static final long HEARTBEAT_LOOP_DURATION = 1000;

    private RobotRunner mRobotRunner = null;
    private RobotManagerWrapper mRobotManagerWrapper = null;

    private static CloudWorldManager.PromptLink sPromptLink = null;

    // For accessing cloud database and storage.
    private final CloudHelper mCloudHelper = CloudHelper.getInstance();

    // Handler for pushing "ping_time" updates to the cloud.
    private Handler mHeartBeatHandler;
    private Runnable mHeartBeatRunnable;

    /**
     * If the lockout is set, then startService and stopService will do nothing.
     *
     * @return True if there is a lockout.
     */
    public static boolean getLockout() {
        return sLockout;
    }

    /**
     * Set the prompt link.
     *
     * @param promptLink The new link
     */
    public static void setPromptLink(CloudWorldManager.PromptLink promptLink) {
        synchronized (sInstanceLock) {
            sPromptLink = promptLink;
            if (sInstance != null) {
                RobotRunner r = sInstance.mRobotRunner;
                if (r != null) {
                    r.setPromptLink(sPromptLink);
                }
                RobotManagerWrapper w = sInstance.mRobotManagerWrapper;
                if (w != null) {
                    w.setPromptLink(sPromptLink);
                }
            }
        }
    }

    /**
     * Clear the prompt link.
     *
     * @param promptLink The new link
     */
    public static void clearPromptLink(CloudWorldManager.PromptLink promptLink) {
        synchronized (sInstanceLock) {
            if (sPromptLink == promptLink) {
                sPromptLink = null;
            }
        }
    }


    /**
     * Start the robot runner service. Does nothing in event of a lockout.
     * @param context The Android context within to start the service.
     */
    public static void startService(Context context) {
        Log.i(TAG, "Start service from startService()");
        synchronized (sInstanceLock) {
            if (getLockout()) {
                return;
            }
            if (sInstance != null) {
                return;
            }
            sLockout = true;
        }
        Intent startIntent = new Intent(context, RobotService.class);
        startIntent.setAction(RobotService.START_ACTION);
        context.startService(startIntent);
    }

    /**
     * Stop the robot runner service. Does nothing in event of a lockout.
     * @param context The Android context within to stop the service.
     */
    public static void stopService(final Context context) {
        RobotService instance;
        Log.i(TAG, "Shutdown service from stopService()");
        synchronized (sInstanceLock) {
            if (getLockout()) {
                return;
            }
            sLockout = true;
            instance = sInstance;
        }

        Runnable exit = new Runnable() {
            @Override
            public void run() {
                Intent stopIntent = new Intent(context, RobotService.class);
                stopIntent.setAction(RobotService.STOP_ACTION);
                context.stopService(stopIntent);
            }
        };

        if (instance != null) {
            instance.runDestroyThread(exit);
        } else {
            exit.run();
        }
    }

    /**
     * Get the RobotRunner associated with the service.
     * @return The RobotRunner.
     */
    public static RobotRunner getRobotRunner() {
        synchronized (sInstanceLock) {
            if (sInstance != null) {
                if (sInstance.mRobotRunner != null) {
                    return sInstance.mRobotRunner;
                }
            }
        }
        return null;
    }

    /**
     * Get the RobotManagerWrapper associated with the service.
     * @return The RobotManagerWrapper.
     */
    public static RobotManagerWrapper getRobotManagerWrapper() {
        synchronized (sInstanceLock) {
            if (sInstance != null) {
                if (sInstance.mRobotManagerWrapper != null) {
                    return sInstance.mRobotManagerWrapper;
                }
            }
        }
        return null;
    }

    /**
     * Add a monitor to the service.
     * @param monitor The monitor to add.
     */
    public static void addMonitor(RobotRunner.Monitor monitor) {
        synchronized (sInstanceLock) {
            if (!sMonitors.contains(monitor)) {
                sMonitors.add(monitor);
            }
            if (sInstance != null) {
                if (sInstance.mRobotRunner != null) {
                    sInstance.mRobotRunner.addMonitor(monitor);
                }
            }
        }
    }

    /**
     * Remove a monitor from the service.
     * @param monitor The monitor to add.
     */
    public static void removeMonitor(RobotRunner.Monitor monitor) {
        synchronized (sInstanceLock) {
            sMonitors.remove(monitor);
            if (sInstance != null) {
                if (sInstance.mRobotRunner != null) {
                    sInstance.mRobotRunner.removeMonitor(monitor);
                }
            }
        }
    }

    /**
     * Add a listener to the service.
     * @param listener The listener to add.
     */
    public static void addListener(RobotManagerWrapper.Listener listener) {
        synchronized (sInstanceLock) {
            if (!sListeners.contains(listener)) {
                sListeners.add(listener);
            }
            if (sInstance != null) {
                if (sInstance.mRobotManagerWrapper != null) {
                    sInstance.mRobotManagerWrapper.addListener(listener);
                }
            }
        }
    }

    /**
     * Remove a listener from the service.
     * @param listener The listener to add.
     */
    public static void removeListener(RobotManagerWrapper.Listener listener) {
        synchronized (sInstanceLock) {
            if (sListeners.contains(listener)) {
                sListeners.remove(listener);
            }
            if (sInstance != null) {
                if (sInstance.mRobotManagerWrapper != null) {
                    sInstance.mRobotManagerWrapper.removeListener(listener);
                }
            }
        }
    }

    /**
     * Android onCreate.
     */
    @Override
    public void onCreate() {
        Log.i(TAG, "Creating robot service instance");
        super.onCreate();

        synchronized (sInstanceLock) {
            sInstance = this;
            Thread.setDefaultUncaughtExceptionHandler(new CrashReporter(this));
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences.getBoolean(getString(R.string.pref_enable_new_classes), true)) {
            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser != null) {
                // TODO set configuration from preferences settings
                mRobotManagerWrapper = new RobotManagerWrapper(this, firebaseUser.getUid(),
                        new RobotManagerConfiguration(
                                RobotManagerConfiguration.SLAMSystem.TANGO,
                                RobotManagerConfiguration.GlobalPlanner.ASTAR,
                                RobotManagerConfiguration.LocalPlanner.SIMPLE,
                                RobotManagerConfiguration.CostMapInflator.SIMPLE,
                                RobotManagerConfiguration.CostMapFuser.TRIVIAL,
                                RobotManagerConfiguration.Executive.BASIC,
                                preferences.getBoolean(getString(R.string.pref_enable_mock_robot), false)
                                        ? RobotManagerConfiguration.RobotDriver.MOCK
                                        : RobotManagerConfiguration.RobotDriver.REAL,
                                preferences.getBoolean(getString(R.string.pref_enable_mock_location), false)
                                        ? new Transform(0, 0, 0, 0) : null,
                                preferences.getBoolean(getString(R.string.pref_enable_ros), false),
                                preferences.getBoolean("pref_enable_operation_sound", false),
                                preferences.getBoolean("pref_enable_vision_system", false)
                                ));
                synchronized (sInstanceLock) {
                    mRobotManagerWrapper.setPromptLink(sPromptLink);
                    sLockout = false;
                    for (RobotManagerWrapper.Listener listener : sListeners) {
                        mRobotManagerWrapper.addListener(listener);
                    }
                }
            }
        } else {
            ArrayList<RobotDriver> drivers = new ArrayList<>();
            drivers.add(new ParallaxArloDriver(this));
            drivers.add(new NeatoDriver(this));
            drivers.add(new KobukiDriver(this));

            RobotPreferences robotPreferences = RobotPreferencesFragment.getRobotPreferencesData(this);
            mRobotRunner = new RobotRunner(this, sMonitors,
                    new RobotDriverMultiplex(drivers),
                    new GoalPathFollower(), robotPreferences);
            synchronized (sInstanceLock) {
                mRobotRunner.setPromptLink(sPromptLink);
                sLockout = false;
            }
        }
        Log.i(TAG, "RobotService instance created");

        // Start heartbeat function for pinging updates to the cloud.
        startHeartBeatFunction(HEARTBEAT_LOOP_DURATION);
    }

    /**
     * Starts the heartbeat function for sending periodic ping time updates to the cloud.
     * Useful for keeping track of this app's alive/dead status.
     *
     * If the app is alive and running, the ping time will be logged and sent to the cloud.
     * If the app is hanging/frozen, the logs will stop, cloud's ping_time field will not be
     * updated, and the app will run into an ANR if frozen for 5+ seconds.
     *
     * Firebase Field: robots/userUuid/robotUuid/ping_time
     *
     * @param heartBeatRate The rate of pushing ping times to the cloud.
     */
    private void startHeartBeatFunction(final long heartBeatRate) {
        Log.i(TAG, "Started heartbeat function.");
        mHeartBeatHandler = new Handler(Looper.getMainLooper());  // Run on main thread.
        mHeartBeatRunnable = new Runnable() {
            @Override
            public void run() {
                // Check if TimestampManager is synchronized.
                if (!TimestampManager.isSynchronized()) {
                    Log.d(PING_TIME_TAG, "TimestampManager is not synchronized. Synchronizing...");
                    TimestampManager.synchronize();
                } else {
                    Log.d(PING_TIME_TAG, "TimestampManager is synchronized.");
                    // Retrieve the current timestamp from the synchronized TimestampManager
                    // and send to the cloud as "ping time".
                    final long pingTime = TimestampManager.getCurrentTimestamp();
                    Log.v(PING_TIME_TAG, "Ping Time: " + pingTime);

                    RobotManagerWrapper robotManagerWrapper = getRobotManagerWrapper();
                    if (robotManagerWrapper != null) {
                        final String robotUuid = robotManagerWrapper.getRobotUuid();
                        CloudRobotStateManager cloudRobotStateManager =
                                robotManagerWrapper.getCloudRobotStateManager();
                        if (cloudRobotStateManager == null) {
                            // Robot is not currently running and we cannot use CloudRobotStateManager
                            // for automatic cloud updates.
                            // Thus, we'll need to send the ping time manually to the cloud.
                            Log.d(PING_TIME_TAG, "Sending ping time to CloudHelper.");
                            mCloudHelper.setPingTime(robotUuid, pingTime);
                        } else {
                            // Robot is currently running and CloudRobotStateManager is alive.
                            // Thus, send the ping time directly to CloudRobotStateManager for
                            // automatic cloud updates.
                            Log.d(PING_TIME_TAG, "Sending ping time to CloudRobotStateManager.");
                            cloudRobotStateManager.setPingTime(pingTime);
                        }
                    } else {
                        Log.d(PING_TIME_TAG, "RobotManagerWrapper is null. Cannot send ping time to cloud.");
                    }
                }
                // Recursively call this function at the given heartBeatRate indefinitely.
                mHeartBeatHandler.postDelayed(this, heartBeatRate);
            }
        };
        mHeartBeatHandler.postDelayed(mHeartBeatRunnable, heartBeatRate);
    }

    /**
     * Stops heartbeat function by removing its callbacks.
     */
    private void stopHeartBeatFunction() {
        Log.i(TAG, "Stopped heartbeat function.");
        mHeartBeatHandler.removeCallbacks(mHeartBeatRunnable);
    }

    /**
     * Get the global singleton instance.
     * @return The singleton instance.
     */
    public static RobotService getInstance() {
        synchronized (sInstanceLock) {
            return sInstance;
        }
    }

    /**
     * Android onStartCommand.
     * @param intent The Android intent.
     * @param flags The Android flags.
     * @param startId The Android start ID.
     * @return Android state.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }
        if (Objects.equals(intent.getAction(), START_ACTION)) {
            Log.i(TAG, "Received Start Foreground Intent ");
            Intent notificationIntent = new Intent(this, MainActivity.class);

            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    notificationIntent, 0);

            Notification notification = new NotificationCompat.Builder(this)
                    .setContentIntent(pendingIntent)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.service_notification_content_title))
                    .setTicker(getString(R.string.service_notification_ticker))
                    .setContentText(getString(R.string.service_notification_content_text))
                    .setOngoing(true).build();
            startForeground(NOTIFICATION_ID, notification);
        } else {
            Log.e(TAG, "Invalid action: " + intent.getAction());
        }
        return START_STICKY;
    }

    /**
     * Android on destroy function.
     */
    @Override
    public void onDestroy() {
        Log.i(TAG, "In onDestroy");
        stopHeartBeatFunction();
        super.onDestroy();
        runDestroyThread(null);
    }

    /**
     * Run a thread to shutdown the service and RobotRunner. The method will terminate immediately
     * but the afterDelete callback will be called after shutdown.
     * @param afterDelete Called after the RobotRunner is shutdown.
     */
    private void runDestroyThread(final Runnable afterDelete) {
        final Runnable terminator = new Runnable() {
            @Override
            public void run() {
                synchronized (sInstanceLock) {
                    if (sInstance == RobotService.this) {
                        sInstance = null;
                        sLockout = false;
                    }
                    // To report the service shutdown
                    for (RobotRunner.Monitor monitor : sMonitors) {
                        monitor.onRobotRunnerState();
                    }
                }
                if (afterDelete != null) {
                    afterDelete.run();
                }
            }
        };
        Thread destroyThread = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean callTerminator = true;
                if (mRobotManagerWrapper != null) {
                    mRobotManagerWrapper.waitShutdown();
                    mRobotManagerWrapper = null;
                }
                if (mRobotRunner != null) {
                    if (afterDelete != null) {
                        mRobotRunner.shutdown(terminator);
                        callTerminator = false; // Do not run terminator since it is run in shutdown
                    } else {
                        mRobotRunner.shutdown(null);
                    }
                    mRobotRunner = null;
                }
                if (callTerminator) {
                    terminator.run();
                }
            }
        });
        destroyThread.start();
    }

    /**
     * Unused Android function.
     * @param intent Android intent.
     * @return The IBinder interface.
     */
    @Override
    public IBinder onBind(Intent intent) {
        // Used only in case of bound services.
        return null;
    }
}
