package ai.cellbots.robot.cloud;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.TimedLoop;
import ai.cellbots.common.Transform;
import ai.cellbots.common.cloud.CloudPath;
import ai.cellbots.common.cloud.CloudTypedSingletonMonitor;
import ai.cellbots.common.data.BatteryStatus;
import ai.cellbots.common.data.RobotBaseConnectionStatus;
import ai.cellbots.common.data.RobotMetadata;
import ai.cellbots.common.data.Teleop;
import ai.cellbots.common.data.event.RobotBaseConnectionStatusEvent;
import ai.cellbots.robot.navigation.Path;
import ai.cellbots.robot.state.RobotSessionGlobals;

/**
 * Manages the robot state within the cloud.
 */

public class CloudRobotStateManager implements ThreadedShutdown {
    private static final String TAG = CloudRobotStateManager.class.getSimpleName();
    private static final long UPDATE_RATE = 300; // ms between updates without external source

    private final TimedLoop mContinuousUpdate;
    private final String mMappingRunId;
    private final RobotSessionGlobals mSession;
    private final Listener mListener;
    private final CloudTypedSingletonMonitor<RobotMetadata> mRobotMetadataMonitor;
    private final CloudTypedSingletonMonitor<Teleop> mTeleopMonitor;
    private final double mResolution;
    private long mPingTime;
    private boolean mIsLocalized;
    private Path mPath;
    private RobotMetadata mRobotMetadata = null;
    private Transform mTransform = null;
    private BatteryStatus[] mBatteryStatuses = null;
    private String mRobotVersion;

    /**
     * Connection Status of RobotApp and Robot base.
     */
    private RobotBaseConnectionStatus mRobotBaseConnectionStatus;

    /**
     * On updates from the cloud.
     */
    public interface Listener {
        /**
         * On update of metadata.
         *
         * @param metadata The new metadata.
         */
        void onRobotMetadata(RobotMetadata metadata);

        /**
         * On teleop update.
         *
         * @param teleop  The new teleop data.
         */
        void onTeleop(Teleop teleop);
    }

    /**
     * Create the CloudRobotStateManager.
     *
     * @param parent The context for the robot.
     * @param session The robot session.
     * @param listener The listener.
     * @param mappingRunId The mapping run id. Could be null.
     * @param resolution The resolution of the CostMap.
     */
    public CloudRobotStateManager(@NonNull Context parent,
                                  @NonNull final RobotSessionGlobals session, @NonNull Listener listener,
                                  String mappingRunId, double resolution) {
        Objects.requireNonNull(parent);
        Objects.requireNonNull(session);
        Objects.requireNonNull(listener);
        mResolution = resolution;
        mListener = listener;
        mSession = session;
        mMappingRunId = mappingRunId;
        mRobotMetadataMonitor = new CloudTypedSingletonMonitor<>(parent, new Object(),
                CloudPath.ROBOT_METADATA_PATH,
                new CloudTypedSingletonMonitor.Listener<RobotMetadata>() {
                    @Override
                    public void onData(RobotMetadata data) {
                        if (data != null
                                && session.getUserUuid().equals(data.getUserUuid())
                                && session.getRobotUuid().equals(data.getRobotUuid())) {
                            mRobotMetadata = data;
                            mListener.onRobotMetadata(data);
                            publishUpdate();
                        } else {
                            mRobotMetadata = null;
                            mListener.onRobotMetadata(data);
                        }
                    }

                    @Override
                    public void afterListenerTerminated() {
                    }

                    @Override
                    public void beforeListenerStarted() {
                    }
                }, RobotMetadata.FACTORY);
        mRobotMetadataMonitor.update(session.getUserUuid(), session.getRobotUuid());

        mTeleopMonitor = new CloudTypedSingletonMonitor<>(parent, new Object(),
                CloudPath.ROBOT_TELEOP_PATH,
                new CloudTypedSingletonMonitor.Listener<Teleop>() {
                    @Override
                    public void onData(Teleop data) {
                        Log.v(TAG, "Sending teleop: " + data);
                        if (data == null) {
                            mListener.onTeleop(null);
                        } else {
                            mListener.onTeleop(new Teleop(data,
                                    session.getRobotModel().getMaxLinearSpeed(),
                                    session.getRobotModel().getMaxAngularSpeed()));
                        }
                    }

                    @Override
                    public void afterListenerTerminated() {
                        Log.d(TAG, "Teleop terminated, sending null");
                        mListener.onTeleop(null);
                    }

                    @Override
                    public void beforeListenerStarted() {
                        Log.d(TAG, "Teleop starting, sending null");
                        mListener.onTeleop(null);
                    }
                }, Teleop.FACTORY);
        mTeleopMonitor.update(session.getUserUuid(), session.getRobotUuid());

        mContinuousUpdate = new TimedLoop(TAG, new TimedLoop.Looped() {
            @Override
            public boolean update() {
                publishUpdate();
                return true;
            }

            @Override
            public void shutdown() {
            }
        }, UPDATE_RATE);

        mRobotBaseConnectionStatus = new RobotBaseConnectionStatus();

        // Register this class as an EventBus subscriber.
        EventBus.getDefault().register(this);
    }

    /**
     * Subscription method for receiving RobotBaseConnectionStatusEvent events.
     *
     * @param event A RobotBaseConnectionStatusEvent event published by an EventBus publisher.
     */
    @Subscribe
    public void onRobotBaseConnectionStatusEvent(RobotBaseConnectionStatusEvent event) {
        // TODO (playerthree) figure out a better way to log this without spamming logcat.
        Log.d(TAG, "Received a RobotBaseConnectionStatusEvent event.");
        setRobotBaseConnectionStatus(event.mIsRobotBaseConnected);
    }

    /**
     * Sets robot base connection status.
     *
     * @param isRobotBaseConnected True if the robot base is still connected to the RobotApp.
     */
    public void setRobotBaseConnectionStatus(boolean isRobotBaseConnected) {
        if (isRobotBaseConnected) {
            mRobotBaseConnectionStatus.setStatus(RobotBaseConnectionStatus.Status.CONNECTED);
        } else {
            mRobotBaseConnectionStatus.setStatus(RobotBaseConnectionStatus.Status.DISCONNECTED);
        }
        publishUpdate();
    }

    /**
     * Sets ping time on cloud.
     *
     * @param pingTime Timestamp of ping event. In milliseconds.
     */
    public void setPingTime(long pingTime) {
        mPingTime = pingTime;
        publishUpdate();
    }

    /**
     * On transform update.
     *
     * @param transform The new transform.
     */
    public void setTransform(Transform transform) {
        mTransform = transform;
        publishUpdate();
    }

    /**
     * On battery update.
     *
     * @param batteryStatuses The new battery statuses.
     */
    @SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
    public void setBatteryStatuses(BatteryStatus[] batteryStatuses) {
        mBatteryStatuses = batteryStatuses;
        publishUpdate();
    }

    /**
     * Sets robot's localization state on cloud.
     *
     * @param localized True if robot is localized. Else, false.
     */
    public void setLocalized(boolean localized) {
        mIsLocalized = localized;
        publishUpdate();
    }

    /**
     * On path update.
     *
     * @param path The new path.
     */
    public void setPath(Path path) {
        mPath = path;
        publishUpdate();
    }


    /**
     * On version update.
     *
     * @param version The new robot version.
     */
    public void setRobotVersion(String version) {
        mRobotVersion = version;
        publishUpdate();
    }

    /**
     * On update of the system.
     */
    private void publishUpdate() {
        Transform tf = mTransform;
        RobotMetadata metadata = mRobotMetadata;
        BatteryStatus[] batteryStatuses = mBatteryStatuses;
        String version = mRobotVersion;

        final long updateTime = new Date().getTime();
        Map<String, Object> robotState = new HashMap<>();
        robotState.put("last_update_time", ServerValue.TIMESTAMP);
        robotState.put("local_time", updateTime);
        robotState.put("uuid", mSession.getRobotUuid());
        robotState.put("localized", mIsLocalized);
        robotState.put("ping_time", mPingTime);

        String robotBaseConnectionStatusText = mRobotBaseConnectionStatus.getStatus().toString();
        robotState.put("base_connection_status", robotBaseConnectionStatusText);

        if (mMappingRunId != null) {
            robotState.put("mapping_run_id", mMappingRunId);
        }
        if (tf != null) {
            robotState.put("tf", tf.toMap());
        }
        if (version != null) {
            robotState.put("version", mRobotVersion);
        }
        if (mSession.getWorld() != null) {
            robotState.put("map", mSession.getWorld().getUuid());
        }

        Map<String, Object> pathMap = new HashMap<>();
        robotState.put("path", pathMap);
        Path path = mPath;
        if (path != null) {
            for (int i = 0; i < path.size(); i++) {
                String goalName = "goal_" + i;
                pathMap.put(goalName, path.get(i).toWorldCoordinates(mResolution).toMap());
            }
        }

        Map<String, Object> batteryStateMap = new HashMap<>();
        robotState.put("batteries", batteryStateMap);
        if (batteryStatuses != null) {
            for (BatteryStatus status : batteryStatuses) {
                if (status != null) {
                    batteryStateMap.put(status.getName(), status.toFirebase());
                }
            }
        }

        // TODO(playerone): add back in log messages
        /*// Add all log messages to the robot that are for the correct robot, and remove
        // messages that have timed out from the log messages store.
        Map<String, Object> messageMap = new HashMap<>();
        synchronized (mLogMessages) {
            LinkedList<LogMessage> removeMessage = new LinkedList<>();
            for (LogMessage m : mLogMessages) {
                if (setRobot.equals(m.getRobotUuid()) && userId.equals(m.getUserUuid())) {
                    messageMap.put(m.getUuid(), m);
                }
                if (m.getTimestamp() < updateTime - LOG_MESSAGE_BUFFER_TIME) {
                    removeMessage.add(m);
                }
            }
            mLogMessages.removeAll(removeMessage);
        }
        robotState.put("log_messages", messageMap);*/

        if (metadata != null) {
            robotState.put("name", metadata.getRobotName());
        }
        // Set the key robots/<user>/<robot_uuid>
        FirebaseDatabase.getInstance().getReference("robots").child(mSession.getUserUuid())
                .child(mSession.getRobotUuid()).setValue(robotState);
    }

    /**
     * Shutdown the system.
     */
    @Override
    public void shutdown() {
        mContinuousUpdate.shutdown();
        mRobotMetadataMonitor.shutdown();
        mTeleopMonitor.shutdown();
    }

    /**
     * Wait for the system to shutdown.
     */
    @Override
    public void waitShutdown() {
        setRobotBaseConnectionStatus(false);  // Reset robot base connection status to DISCONNECTED.
        setLocalized(false);  // Reset localization state back to its default state (false)
        EventBus.getDefault().unregister(this);  // Unregister this class from EventBus.
        shutdown();
        mContinuousUpdate.waitShutdown();
    }
}
