package ai.cellbots.robotlib;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.google.atap.tangoservice.experimental.TangoImageBuffer;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;
import com.projecttango.tangosupport.ux.TangoUx;
import com.projecttango.tangosupport.ux.UxExceptionEvent;
import com.projecttango.tangosupport.ux.UxExceptionEventListener;

import org.ros.RosCore;
import org.ros.exception.RosRuntimeException;
import org.ros.node.DefaultNodeMainExecutor;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.NetworkUtil;
import ai.cellbots.common.Strings;
import ai.cellbots.common.Transform;
import ai.cellbots.common.World;
import ai.cellbots.common.cloud.CloudPath;
import ai.cellbots.common.cloud.DataFactory;
import ai.cellbots.common.cloud.RTCDatabaseChannel;
import ai.cellbots.common.data.NavigationStateCommand;
import ai.cellbots.common.data.RobotMetadata;
import ai.cellbots.common.data.RobotPreferences;
import ai.cellbots.common.data.SmootherParams;
import ai.cellbots.common.data.Teleop;
import ai.cellbots.executive.ExecutivePlannerManager;
import ai.cellbots.robot.tango.TangoFloorplanner;
import ai.cellbots.robotapp.R;
import ai.cellbots.robotlib.cv.CVManager;
import ai.cellbots.robotlib.cv.FloorObjectDetector;
import ai.cellbots.tangocommon.CloudWorldManager;
import ai.cellbots.tangocommon.TangoTransformUtil;

/*
 * State Notes:
 * mLocalized = if true, system is localized, and if no world we can save
 * mShutdown = if true, system is shutdown, and any loops depending on it should terminate
 * mTangoConnected = if true, then the system has connected to the tango
 * mTangoStarted = if true, then the robot system is currently running
 * mWorld = if not null, then we are operating in this world
 */

/**
 * This class manages the Tango, RobotDriver, and Controller for a given robot.
 * Threads:
 */
public class RobotRunner implements CloudWorldManager.Listener,
        UxExceptionEventListener, Tango.OnFrameAvailableListener {
    final static private String TAG = RobotRunner.class.getSimpleName();

    private static final int THREAD_RATE_HZ = 10;
    private static final double CALIBRATION_ANGULAR_SPEED = 60 * Math.PI;
    private static final float CALIBRATION_DURATION_MILLISECONDS = 6000;

    private RobotPreferences mPreferences;

    public enum State {
        INIT, // System is initializing, tango may be null
        WAIT_FOR_COMMAND, // Tango is running and connected, may query listWorlds, call
        // startMapping() or startOperation()
        CALIBRATION, // System is calculating device height
        COMMAND_EXEC, // Tango is executing startMapping() or startOperation()
        IMPORT_EXPORT, // System is importing or exporting a world
        RUNNING_MAPPING, // Running the SLAM mapping to create a new world, call saveADF() to save
        SAVING_WORLD, // Saving the world to disk
        SAVED_WORLD, // Map has been saved to disk and we are ready to shutdown
        RUNNING_NAVIGATION, // Running navigation on an existing map
        STOPPING, // Stopping navigation or mapping and going back to WAIT_FOR_COMMAND
        SHUTDOWN, // Shutting down the system
    }

    private State mState;

    private final CommandStateMachine mCommandStateMachine;
    private final CloudWorldManager mCloudWorldManager;
    private final RobotDriverMultiplex mDriver;
    private final SafetyController mSafetyController;
    private Tango mTango = null;
    private TangoUx mTangoUx = null;
    private final Context mParent;
    private List<Monitor> mMonitors = null;
    private boolean mLocalized = false;

    private DetailedWorld mWorld = null;

    private Thread mMainLoop = null;

    private RosCore mRosCore = null;
    private ROSNode mRosNode = null;
    private NodeMainExecutor mNodeMainExecutor = null;

    private Transform mLocation = null;
    private Transform mCentroid = null;
    private final Controller mController;
    private boolean mSoundsSync = false;
    private final TangoPointCloudManager mPointCloudManager = new TangoPointCloudManager();

    private final CloudConnector mCloudConnector;
    private FirebaseAnalyticsEvents mFirebaseEventLogger;
    private final TeleopMultiplexer mTeleopMultiplexer;

    // Store if the robot is blocked
    private boolean mRobotIsBlocked = false;

    // Variables needed to make floor plan
    private TangoFloorplanner mTangoFloorplanner = null;

    // Variables needed to perform calibration
    private final AtomicBoolean mCalibrate = new AtomicBoolean(false);
    private final ArrayList<Float> mDeviceToFloorMeasurements = new ArrayList<>();

    // The sound manager
    private final CloudSoundManager mSoundManager;

    // The executive planner manager
    private final ExecutivePlannerManager mExecutivePlannerManager;

    // The WebRTCManager
    private final WebRTCManager mWebRTCManager;

    // The data channel
    private final RTCDatabaseChannel mDatabaseChannel;

    // Camera manager
    private CVManager mCVManager;

    // The uuid of the current mapping run
    private String mMappingRunId = null;

    private int mWorldCoordinateFrame = TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION;
    private String mWorldCoordinateFrameName = Tango.COORDINATE_FRAME_ID_AREA_DESCRIPTION;

    /**
     * Gets if there is a world that needs upload or download to the system.
     *
     * @return True if we are syncing worlds with the cloud.
     */
    public boolean isInCloudWorldTransaction() {
        return mState == State.IMPORT_EXPORT;
    }

    /**
     * Gets if we are saving the world.
     *
     * @return True if we are currently in the process of saving the map.
     */
    public boolean isSavingWorld() {
        return mState == State.SAVING_WORLD;
    }

    // ASUS fisheye support is broken, so we have to disable it.
    private final boolean mHaveFisheye;

    // Variables used to get YCbCr frame from Tango and create RGB bitmap
    private byte[] mYCbCrPlane = null;

    private String mDriverState;

    /**
     * Listener interface from TangoUx, which reports Tango events such as localization, blocked
     * camera, etc.
     */
    @Override
    public void onUxExceptionEvent(UxExceptionEvent uxExceptionEvent) {
        if (mCloudConnector != null) {
            // Translate event into tabulated human readable form and log to Firebase.
            String status;
            switch (uxExceptionEvent.getType()) {
                case UxExceptionEvent.TYPE_COLOR_CAMERA_OVER_EXPOSED:
                    status = mParent.getString(R.string.tango_event_camera_over_exposed);
                    break;
                case UxExceptionEvent.TYPE_COLOR_CAMERA_UNDER_EXPOSED:
                    status = mParent.getString(R.string.tango_event_camera_under_exposed);
                    break;
                case UxExceptionEvent.TYPE_FEW_FEATURES:
                    status = mParent.getString(R.string.tango_event_few_features);
                    break;
                case UxExceptionEvent.TYPE_FEW_DEPTH_POINTS:
                    status = mParent.getString(R.string.tango_event_few_depth_points);
                    break;
                case UxExceptionEvent.TYPE_FISHEYE_CAMERA_OVER_EXPOSED:
                    status = mParent.getString(R.string.tango_event_fisheye_camera_over_exposed);
                    break;
                case UxExceptionEvent.TYPE_LYING_ON_SURFACE:
                    status = mParent.getString(R.string.tango_event_device_covered);
                    break;
                case UxExceptionEvent.TYPE_MOTION_TRACK_INVALID:
                    status = mParent.getString(R.string.tango_event_motion_track_invalid);
                    break;
                case UxExceptionEvent.TYPE_MOVING_TOO_FAST:
                    status = mParent.getString(R.string.tango_event_moving_too_fast);
                    break;
                default:
                    status = mParent.getString(R.string.tango_event_unknown);
                    break;
            }
            if (uxExceptionEvent.getStatus() == UxExceptionEvent.STATUS_DETECTED) {
                status += mParent.getString(R.string.tango_event_detected);

            } else {
                status += mParent.getString(R.string.tango_event_resolved);
            }
            mCloudConnector.logStatus(null, mRobotUuid, mRobotVersion, mWorld, status);
            // Report "Tango Error" events to Firebase Analytics.
            mFirebaseEventLogger.reportTangoErrorEvent(mParent, status);
        }
    }

    /**
     * The Transmitter is passed to objects that are allowed to update monitor states and
     * report errors and exceptions to the root class.
     */
    class Transmitter {
        void reportError(RobotRunner.Error error) {
            RobotRunner.this.reportError(error);
        }

        void reportException(RobotRunner.Error error, Exception e) {
            RobotRunner.this.reportException(error, e);
        }

        void updateMonitorsState() {
            RobotRunner.this.updateMonitorsState();
        }
    }

    /**
     * Classes of errors that can be reported.
     */
    public enum Error {
        exception_out_of_date,
        exception_tango_error,
        exception_tango_invalid,
        robot_driver_invalid,
        robot_driver_error,
        ros_startup_error,
    }

    /**
     * Monitor interface receives.
     */
    public interface Monitor {
        void reportRobotRunnerError(Error error);

        void onRobotRunnerState();
    }

    /**
     * Returns whether the robot is in running navigation state
     *
     * @return True if the robot is navigating
     */
    private boolean isRunning() {
        return mState == State.RUNNING_NAVIGATION;
    }

    /**
     * Returns whether the RobotRunner is in mapping (vs. running) mode.
     */
    private boolean isMapping() {
        return mState == State.RUNNING_MAPPING;
    }

    /**
     * If true, we can save the mapped world state.
     *
     * @return True if we can save.
     */
    public boolean canSaveWorld() {
        return mLocalized && isMapping();
    }

    /**
     * List the worlds that the robot could be in.
     *
     * @return List of worlds.
     */
    public World[] listWorldInfo() {
        return mCloudWorldManager.getWorlds();
    }

    /**
     * Get the robot's UUID.
     *
     * @return The robot's UUID.
     */
    public String getRobotUuid() {
        return mRobotUuid;
    }

    /**
     * Set the export lock. This lock is used to ensure that only one import or export operation
     * can occur at a given time per RobotRunner, as setting of multiple events could lead to
     * race conditions and the inability to determine which file to use.
     *
     * @return True if the lock is captured and import or export can proceed.
     */
    @Override
    public boolean lockExportState() {
        synchronized (mParent) {
            // If we are shutting down, or we are starting up, then do not export.
            if (mState != State.WAIT_FOR_COMMAND) {
                return false;
            }
            setState(State.IMPORT_EXPORT);
            updateMonitorsState();
            return true;
        }
    }

    /**
     * Clean an export state.
     */
    @Override
    public void clearExportState() {
        synchronized (mParent) {
            if (mState == State.IMPORT_EXPORT) {
                setState(State.WAIT_FOR_COMMAND);
            }
        }
    }

    @Override
    public boolean isExportState() {
        synchronized (mParent) {
            return mState == State.IMPORT_EXPORT;
        }
    }

    /**
     * Update the state of our monitors, since a new world has been updated.
     */
    @Override
    public void onStateUpdate() {
        synchronized (mParent) {
            for (Monitor m : mMonitors) {
                m.onRobotRunnerState();
            }
        }
    }

    private void reportError(Error error) {
        Log.e(TAG, error.toString());
        synchronized (mParent) {
            for (Monitor monitor : mMonitors) {
                monitor.reportRobotRunnerError(error);
            }
        }
    }

    private void reportException(Error error, Exception e) {
        Log.e(TAG, error + ": " + e, e);
        synchronized (mParent) {
            for (Monitor monitor : mMonitors) {
                monitor.reportRobotRunnerError(error);
            }
        }
    }

    private void setLocalized(boolean localized) {
        mLocalized = localized;
        updateMonitorsState();
    }

    private void updateMonitorsState() {
        synchronized (mParent) {
            for (Monitor monitor : mMonitors) {
                updateMonitorState(monitor);
            }
        }
    }

    private void updateMonitorState(Monitor monitor) {
        synchronized (mParent) {
            monitor.onRobotRunnerState();
        }
    }

    /**
     * On frame available from Tango.
     *
     * @param tangoImageBuffer The tango image buffer callback.
     * @param cameraId         The camera id callback.
     */
    @Override
    public void onFrameAvailable(TangoImageBuffer tangoImageBuffer, int cameraId) {
        TangoPoseData pose;
        TangoCameraIntrinsics intrinsics;
        boolean haveMonitor; // Used to check if we have monitors in a synchronized context
        synchronized (mParent) {
            if (mState != State.RUNNING_MAPPING
                    && mState != State.RUNNING_NAVIGATION) {
                return;
            }
            if (mTango == null) {
                return;
            }
            try {
                pose = mTango.getPoseAtTime(
                        tangoImageBuffer.timestamp, mWorldCoordinateFrameName,
                        cameraId == TangoCameraIntrinsics.TANGO_CAMERA_FISHEYE
                                ? Tango.COORDINATE_FRAME_CAMERA_FISHEYE
                                : Tango.COORDINATE_FRAME_ID_CAMERA_COLOR);
            } catch (TangoErrorException e) {
                Log.v(TAG, "Ignore tango exception for invalid image pose:", e);
                return;
            }
            try {
                intrinsics = mTango.getCameraIntrinsics(cameraId);
            } catch (TangoErrorException e) {
                Log.v(TAG, "Ignore tango exception for invalid intrinsics:", e);
                return;
            }
            haveMonitor = !mMonitors.isEmpty();
        }
        ROSNode node = mRosNode;
        if (node != null) {
            node.sendImage((cameraId == TangoCameraIntrinsics.TANGO_CAMERA_FISHEYE)
                            ? ROSNode.Camera.FISHEYE : ROSNode.Camera.COLOR,
                    TangoTransformUtil.poseToTransform(pose), tangoImageBuffer);
        }

        if (tangoImageBuffer.format == TangoImageBuffer.YCRCB_420_SP
                && TangoCameraIntrinsics.TANGO_CAMERA_COLOR == cameraId) {
            CVManager cvManager = mCVManager;
            if (mWebRTCManager.hasCall() || haveMonitor
                    || (cvManager != null && cvManager.requireImage())) {
                int yCbCrBufferSize =
                        (3 * tangoImageBuffer.width * tangoImageBuffer.height) / 2;
                if (mYCbCrPlane == null || mYCbCrPlane.length != yCbCrBufferSize) {
                    mYCbCrPlane = new byte[yCbCrBufferSize];
                }
                tangoImageBuffer.data.get(mYCbCrPlane);
                mWebRTCManager.sendImage(mYCbCrPlane, tangoImageBuffer.width,
                        tangoImageBuffer.height);
                if (cvManager != null && intrinsics != null && cvManager.requireImage()) {
                    cvManager.sendImage(tangoImageBuffer.timestamp, intrinsics,
                            tangoImageBuffer.width, tangoImageBuffer.height, mYCbCrPlane);
                }
            }
        } else if (TangoCameraIntrinsics.TANGO_CAMERA_COLOR == cameraId) {
            Log.w(TAG, "Frame ignored for wrong format: " + tangoImageBuffer.format);
        }
    }

    public State getState() {
        return mState;
    }

    public void setState(State state) {
        mState = state;
    }

    public boolean getLocalized() {
        return mLocalized;
    }

    public boolean getRobotConnected() {
        return (mDriver != null) && mDriver.isConnected();
    }

    public World getWorld() {
        return mWorld;
    }

    @SuppressWarnings("ThisEscapedInObjectConstruction")
    public RobotRunner(Context parent,
            @SuppressWarnings("SameParameterValue") Iterable<Monitor> monitors,
            RobotDriverMultiplex driver, Controller controller, RobotPreferences robotPreferences) {
        mPreferences = robotPreferences;
        mParent = parent;
        HashMap<CloudPath, DataFactory> paths = new HashMap<>();
        paths.put(CloudPath.ROBOT_TELEOP_PATH, Teleop.FACTORY);
        mDatabaseChannel = new RTCDatabaseChannel(paths);
        mWebRTCManager = new WebRTCManager(parent, mDatabaseChannel);
        mSoundManager = new CloudSoundManager(parent);
        mExecutivePlannerManager = new ExecutivePlannerManager(mSoundManager);
        mDriver = driver;
        mSafetyController = new SafetyController();
        mController = controller;
        mCloudConnector = new CloudConnector(parent, mExecutivePlannerManager);
        mFirebaseEventLogger = FirebaseAnalyticsEvents.getInstance();
        // Report "Tango Connected" events to Firebase Analytics.
        mFirebaseEventLogger.reportStartServiceEventToFirebase(mParent);
        mTeleopMultiplexer = new TeleopMultiplexer(mDriver, mDatabaseChannel);
        mTangoUx = new TangoUx(parent);
        mTangoUx.setUxExceptionEventListener(this);
        setState(State.INIT);
        mCloudWorldManager = new CloudWorldManager(mParent, this);
        mCloudWorldManager.setShouldDownloadWorlds(mPreferences.mImportWorlds);

        Log.i(TAG, "Build manufacturer = " + Build.MANUFACTURER);
        mHaveFisheye = !"asus".equals(Build.MANUFACTURER);
        mCommandStateMachine = new CommandStateMachine(mParent, mSoundManager);
        initRobotRunner(monitors);
    }

    private void initRobotRunner(Iterable<Monitor> initMonitors) {
        mMonitors = new ArrayList<>();
        mTango = null;
        mCloudWorldManager.setTango(null);
        mLocalized = false;

        if (mDriver != null) {
            mDriver.initDriver(this, new Transmitter());
        }

        if (initMonitors != null) {
            for (Monitor monitor : initMonitors) {
                addMonitor(monitor);
            }
        }

        mMainLoop = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (mParent) {
                    if (mState == State.SHUTDOWN) {
                        return;
                    }

                    SharedPreferences preferences
                            = PreferenceManager.getDefaultSharedPreferences(mParent);
                    Boolean preferenceEnableRos = preferences.getBoolean(
                            mParent.getString(R.string.pref_enable_ros), false);
                    if (preferenceEnableRos) {
                        try {
                            mRosCore = RosCore.newPublic("0.0.0.0", 11311);
                            mRosCore.start();
                        } catch (RosRuntimeException ex) {
                            reportException(Error.ros_startup_error, ex);
                            return;
                        }

                        List<String> nodeIp = NetworkUtil.getIPv4List();
                        if ((nodeIp == null) || nodeIp.isEmpty()) {
                            return;
                        }
                        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(
                                nodeIp.get(0));
                        try {
                            nodeConfiguration.setMasterUri(new URI("http://127.0.0.1:11311/"));
                        } catch (URISyntaxException e) {
                            Log.e(TAG, "URL rejected: " + e);
                            return;
                        }
                        mNodeMainExecutor = DefaultNodeMainExecutor.newDefault();
                        mRosNode = new ROSNode(mDriver, mHaveFisheye);
                        mNodeMainExecutor.execute(mRosNode, nodeConfiguration);
                    }
                }

                RobotRunner.this.runRobot();
            }
        });
        mMainLoop.start();

        restart(false, null);
    }

    public void addMonitor(Monitor monitor) {
        synchronized (mParent) {
            mMonitors.add(monitor);
            updateMonitorState(monitor);
        }
    }

    public void removeMonitor(Monitor monitor) {
        synchronized (mParent) {
            mMonitors.remove(monitor);
        }
    }

    /**
     * Get if the sounds have been synchronized with the cloud.
     */
    public boolean getSoundsSync() {
        return mSoundsSync;
    }

    /**
     * Start mapping the world.
     */
    public void startMapping() {
        // Report "Start Mapping" events to Firebase Analytics.
        Log.i(TAG, "Start mapping");
        mFirebaseEventLogger.reportStartMappingEvent(mParent);
        mCommandStateMachine.startMapping();
    }

    /**
     * Start navigating over a world.
     *
     * @param world The world to navigate.
     */
    public void startOperation(final World world) {
        // Report "Start Operation" events to Firebase Analytics.
        mFirebaseEventLogger.reportStartOperationEvent(mParent);
        mCommandStateMachine.startOperation(world);
    }

    /**
     * Stop navigating or mapping operations.
     */
    public void stopOperations() {
        // Report "Stop Operations" events to Firebase Analytics
        mFirebaseEventLogger.reportStopOperationsEventToFirebase(mParent);
        mCommandStateMachine.stopOperations();
    }

    /**
     * Manages the local command synchronization logic.
     */
    private void manageCommandState() {
        NavigationStateCommand current = mCommandStateMachine.update(mUserUuid, mRobotUuid);
        if (current == null) {
            return;
        }
        synchronized (mParent) {
            if (mState == State.WAIT_FOR_COMMAND) {
                if (current.getIsMapping()) {
                    setState(State.COMMAND_EXEC);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Log.i(TAG, "Starting mapping...");
                            startSystem(null);
                        }
                    }).start();
                } else if (current.getMap() != null) {
                    World w = null;
                    World[] worlds = listWorldInfo();
                    if (worlds != null) {
                        for (World world : worlds) {
                            if (current.getMap().equals(world.getUuid())) {
                                w = world;
                            }
                        }
                    }
                    if (current.getMap().equals(World.VPS_WORLD.getUuid())) {
                        w = World.VPS_WORLD;
                    }
                    if (w != null) {
                        setState(State.COMMAND_EXEC);
                        final World startWorld = w;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Log.i(TAG, "Starting mapping...");
                                startSystem(startWorld);
                            }
                        }).start();
                    }
                }
            } else if ((mState == State.RUNNING_MAPPING || mState == State.RUNNING_NAVIGATION)
                    && current.getMap() == null && !current.getIsMapping()) {
                setState(State.STOPPING);
                restart(false, null);
            } else if (mState == State.RUNNING_NAVIGATION && current.getIsMapping()) {
                setState(State.STOPPING);
                restart(false, null);
            } else if (mState == State.RUNNING_MAPPING && !current.getIsMapping()) {
                setState(State.STOPPING);
                restart(false, null);
            } else if (mState == State.RUNNING_NAVIGATION && current.getMap() != null
                    && (mWorld == null || !current.getMap().equals(mWorld.getUuid()))) {
                setState(State.STOPPING);
                restart(false, null);
            } else if (mState == State.RUNNING_MAPPING) {
                String saveName = mCommandStateMachine.getMapNameForRunId(mMappingRunId);
                if (saveName != null && canSaveWorld()) {
                    saveADF(saveName);
                }
            }
        }
    }

    private float[] mPointCloudColor = null;

    private String mUserUuid = null;
    private String mRobotUuid = null;
    private String mRobotVersion = null;

    /**
     * Get the version of the robot involved.
     *
     * @return The robot version.
     */
    public String getRobotVersion() {
        return mRobotVersion;
    }

    private void runRobot() {
        long lastUpdate;
        while (mState != State.SHUTDOWN) {
            lastUpdate = new Date().getTime();
            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            mUserUuid = firebaseUser != null ? firebaseUser.getUid() : null;
            RobotMetadata metadata = mCloudConnector.getMetadata(mUserUuid, mRobotUuid);
            Controller controller = mController;
            Transform location = mLocation;
            Transform locationBase = location != null ? mDriver.tangoToBase(location) : null;
            Transform depthLocation = null;
            TangoPointCloudData pointCloud = mPointCloudManager.getLatestPointCloud();
            Tango tango = mTango;
            List<Transform> path = null;
            DetailedWorld world = mWorld;

            if (metadata != null && metadata.getTangoDeviceHeight() != null) {
                try {
                    double tangoDeviceHeight = Double.parseDouble(metadata.getTangoDeviceHeight());
                    mDriver.setTangoDeviceHeight(tangoDeviceHeight, true);
                } catch (NumberFormatException ex) {
                    Log.w(TAG, "Invalid robot height: " + metadata.getTangoDeviceHeight());
                    mDriver.setTangoDeviceHeight(0.0, false);
                }
            } else {
                mDriver.setTangoDeviceHeight(0.0, false);
            }

            if (pointCloud != null && tango != null && mLocalized) {
                try {
                    TangoPoseData pointCloudTangoPose = tango.getPoseAtTime(pointCloud.timestamp,
                            mWorldCoordinateFrameName, Tango.COORDINATE_FRAME_CAMERA_DEPTH);
                    depthLocation = TangoTransformUtil.poseToTransform(
                            pointCloudTangoPose, pointCloud.timestamp);
                } catch (TangoErrorException e) {
                    Log.v(TAG, "Ignore tango exception for invalid pointcloud pose:", e);
                    pointCloud = null;
                }
            } else {
                pointCloud = null;
            }

            boolean newSoundSync = mSoundManager.isSynchronized();
            if (mSoundsSync != newSoundSync) {
                mSoundsSync = newSoundSync;
                onStateUpdate();
            }

            // Get from robot_preferences the maximum allowed goals distances from the ADF points
            double goalMaxDistance = mPreferences.mMaxDistanceGoalFromADF;

            // Get from robot_preferences the time to wait before giving up on a goal
            double goalTimeout = mPreferences.mGoalTimeout;

            if ((location != null) && (world != null) && (depthLocation != null)
                    && (locationBase != null) && mCommandStateMachine.getSpeedState()) {
                location = new Transform(location);
                RobotDriver.RobotModel model = mDriver.getModel();
                if (controller != null && model != null && mSoundsSync) {
                    Log.d(TAG, "Update executive...");
                    mExecutivePlannerManager.update(world, locationBase, controller,
                            mDriver.getBatteryLowDebounced(),
                            mDriver.getBatteryCriticalDebounced(),
                            mCommandStateMachine.getExecutiveMode());
                    Log.i(TAG, "Start controller");
                    if ((mPointCloudColor == null) || (mPointCloudColor.length
                            < pointCloud.numPoints)) {
                        mPointCloudColor = new float[pointCloud.numPoints];
                    }
                    controller.update(world, locationBase, model,
                            location, depthLocation, pointCloud, mPointCloudColor,
                            goalMaxDistance, goalTimeout);
                    mTeleopMultiplexer.updateFromController(controller);
                    if (mDriver.getBumper() != 0) {
                        // Send status message
                        mCloudConnector.logStatus(null, mRobotUuid, mRobotVersion, mWorld,
                                mParent.getString(R.string.bumper_activated));
                        // Report bumper events to Firebase Analytics.
                        mFirebaseEventLogger.reportBumperFirebaseEvent(mParent);
                    }
                    // If the robot is blocked by the PointCloud, update the status.
                    boolean robotBlocked = controller.isBlockedDepth();
                    if (robotBlocked && !mRobotIsBlocked) {
                        // Report "Path Blocked" events to Firebase Analytics.
                        mFirebaseEventLogger.reportPathBlockedFirebaseEvent(mParent);
                    }
                    if (mRobotIsBlocked != robotBlocked) {
                        mRobotIsBlocked = robotBlocked;
                        onStateUpdate();
                    }
                    String[] logMessages = mController.getAndClearLogMessages();
                    for (String message : logMessages) {
                        mCloudConnector.logStatus(null, mRobotUuid, mRobotVersion, mWorld, message);
                    }
                    path = controller.getPath();
                    Log.i(TAG, "Done controller");
                } else {
                    mCloudConnector.logStatus(null, mRobotUuid, mRobotVersion, mWorld,
                            mParent.getString(R.string.controller_null));
                }
            } else {
                if (mLocalized) {
                    if (mRobotUuid != null && mRobotVersion != null && mWorld != null) {
                        mCloudConnector.logStatus(null, mRobotUuid, mRobotVersion, mWorld,
                                mParent.getString(R.string.unable_to_localize));
                    }
                }
            }

            if (mTeleopMultiplexer.updateFromSafetyController(mSafetyController)) {
                onStateUpdate();
            }

            synchronized (mParent) {
                if (mRosNode != null) {
                    mTeleopMultiplexer.updateFromROS(mRosNode);
                    if (location != null && mState == State.RUNNING_NAVIGATION) {
                        // Updates the ROS node only if the robot is running
                        mRosNode.sendRobotUpdate(location, locationBase, path, depthLocation,
                                pointCloud, mPointCloudColor,
                                controller != null && controller.getPointColorsValid());
                    }
                }

                mTeleopMultiplexer.updateFromCloud(mCloudConnector, mUserUuid, mRobotUuid);

                if (mCommandStateMachine.getSpeedState()) {
                    if (mTeleopMultiplexer.updateDriver()) {
                        onStateUpdate();
                    }
                } else {
                    mDriver.setMotion(0, 0);
                    mDriver.setAction1(false);
                    mDriver.setAction2(false);
                }
            }

            for (String sound : mController.getAndClearSounds()) {
                mSoundManager.playSound(sound, SoundManager.SoundLevel.ANIMATION);
            }

            mDriver.update();
            String newDriverState = mDriver.getStateString();
            if (!Strings.compare(newDriverState, mDriverState)) {
                mDriverState = newDriverState;
                onStateUpdate();
            }

            String oldUuid = mRobotUuid;
            mRobotUuid = mDriver.getRobotUuid();
            String oldVersion = mRobotVersion;
            mRobotVersion = mDriver.getVersionString();
            if (mRobotUuid != null && !mRobotUuid.equals(oldUuid)) {
                if (oldUuid != null) {
                    mCommandStateMachine.onRobotDisconnected();
                }
                mCommandStateMachine.onNewRobotConnected(mUserUuid, mRobotUuid);
                // Cancel all current goals
                // TODO: Note this is a race condition, in that we should not execute executive
                // planner until all the goals are gone. We may exec an old goal by accident.
                if (mUserUuid != null && mRobotUuid != null) {
                    mCloudConnector.cancelAllRobotGoals(mUserUuid, mRobotUuid);
                }
                // New robot should be logged
                mCloudConnector.logStatus(null, mRobotUuid, mRobotVersion, mWorld,
                        mParent.getString(R.string.new_robot_connected));
                // Report "New Robot Connected" events to Firebase Analytics.
                mFirebaseEventLogger.reportNewRobotConnectedEvent(mParent);
            } else if (mRobotUuid == null && oldUuid != null) {
                // Robot is removed, should be logged
                mCloudConnector.logStatus(null, oldUuid, oldVersion, mWorld,
                        mParent.getString(R.string.robot_disconnected));
                // Report "Robot Disconnected" events to Firebase Analytics.
                mFirebaseEventLogger.reportRobotDisconnectedEvent(mParent);
                mCommandStateMachine.onRobotDisconnected();
            }

            ROSNode node = mRosNode;
            if (node != null) {
                node.setRobotModel(mDriver.getModel());
            }

            // Update the sounds manager.
            mSoundManager.update(mUserUuid, mRobotUuid);

            // Update the state of the data channel
            mDatabaseChannel.update(mUserUuid, mRobotUuid);

            // Update the state of the WebRTCManager.
            mWebRTCManager.update(mRobotUuid);

            // New session on startup, switching worlds, or robots
            if (mCloudConnector.logUpdate(mRobotUuid, mRobotVersion, mLocalized, world,
                    Transform.unproject(location, mCentroid),
                    Transform.unproject(locationBase, mCentroid),
                    mDriver.getBatteryStatuses(), path, mMappingRunId)) {
                updateMonitorsState();
            }

            // Manage if we should so any command changes.
            manageCommandState();

            long goalTime = lastUpdate + (1000 / THREAD_RATE_HZ);
            long finishUpdate = new Date().getTime();
            if (goalTime > finishUpdate) {
                try {
                    Thread.sleep(goalTime - finishUpdate);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private void startSystem(final World world) {
        restart(false, new Runnable() {
            public void run() {
                // Load the world, assuming it is not null.
                if (world != null) {
                    mWorld = mCloudWorldManager.loadDetailedWorld(world,
                            mCloudConnector.getSmootherParams());
                    if (mWorld == null) {
                        Log.e(TAG, "Attempted to start on an invalid world");
                        mWorld = null;
                        restart(false, null);
                        return;
                    }
                } else {
                    mWorld = null;
                }
                try {
                    TangoConfig config = setupTangoConfig(mTango, mWorld);
                    TangoSupport.initialize(mTango);
                    mTangoUx.start();
                    mTango.connect(config);
                    startupTango(world == null);
                    // TODO: if the ROS node takes a while to start and operation starts really
                    // fast, the ROS node may be null in CVManager.
                    mCVManager = new CVManager(mRosNode, new FloorObjectDetector());
                    if (world != null) {
                        setState(State.RUNNING_NAVIGATION);
                    } else {
                        if (mCalibrate.get()) {
                            setState(State.CALIBRATION);
                        } else {
                            setState(State.RUNNING_MAPPING);
                            String mappingId = UUID.randomUUID().toString();
                            mCommandStateMachine.setMappingRunId(mappingId);
                            mMappingRunId = mappingId;
                        }
                    }
                    updateMonitorsState();
                } catch (TangoOutOfDateException e) {
                    reportException(Error.exception_out_of_date, e);
                } catch (TangoErrorException e) {
                    reportException(Error.exception_tango_error, e);
                } catch (TangoInvalidException e) {
                    reportException(Error.exception_tango_invalid, e);
                }
                updateMonitorsState();
            }
        });
    }

    /**
     * Get the current bumper maneuver.
     */
    public SafetyController.BumperManeuver getBumperManeuver() {
        return mSafetyController.getBumperManeuver();
    }

    /**
     * Get if the robot is blocked.
     * @return True if the robot is blocked.
     */
    public boolean isRobotBlocked() {
        return mRobotIsBlocked;
    }

    /**
     * Get the driver state string.
     * @return The state string. Could be null.
     */
    public String getDriverState() {
        return mDriverState;
    }

    public void shutdown(final Runnable command) {
        // Report "Robot Navigation Shutdown" events to Firebase Analytics.
        mFirebaseEventLogger.reportRobotNavigationShutdownEvent(mParent);
        setState(State.COMMAND_EXEC);
        restart(true, command);
    }

    // Should not be called from a synchronized (mParent) call
    private void restart(final boolean shutdown, final Runnable command) {
        Log.i(TAG, "Starting shutdown");

        Thread waitThread = null;
        RobotDriverMultiplex waitDriver = null;
        ROSNode waitNode = null;
        CVManager waitCVManager = null;

        synchronized (mParent) {
            if (mState != State.SHUTDOWN) {
                setState(shutdown ? State.SHUTDOWN : State.INIT);
            }
            // If we have a tango running, stop it in a new thread and call restart system
            // again with the same arguments after the tango is stopped.
            if (mTango != null) {
                final Tango stopTango = mTango;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mParent) {
                            mCloudWorldManager.setTango(null);
                        }
                        try {
                            Log.d(TAG, "Disconnecting Tango");
                            stopTango.disconnect();
                            Log.d(TAG, "Tango disconnected");
                        } catch (TangoErrorException e) {
                            reportException(Error.exception_tango_error, e);
                        } catch (IllegalArgumentException e) {
                            // Do nothing
                            Log.e(TAG, "Illegal Argument Exception: ", e);
                        }
                        synchronized (mParent) {
                            if (mTango == stopTango) {
                                if (mTangoUx != null) {
                                    Log.d(TAG, "Disconnecting TangoUX");
                                    mTangoUx.stop();
                                    Log.d(TAG, "TangoUX disconnected");
                                }
                                mTango = null;
                            }
                        }
                        restart(shutdown, command);
                    }
                }).start();
                return;
            }
            mWorld = null;
            mLocalized = false;
            mLocation = null;
            mCentroid = null;
            mDriver.setAction1(false);
            mDriver.setAction2(false);
            mSoundManager.stopAllSounds();
            mMappingRunId = null;

            // Get rid of the floor planner
            if (mTangoFloorplanner != null) {
                mTangoFloorplanner.stopFloorplanning();
                mTangoFloorplanner.release();
                Log.d(TAG, "Releasing Tango Floor Planner");
            }
            mTangoFloorplanner = null;

            if (mCVManager != null) {
                mCVManager.shutdown();
                waitCVManager = mCVManager;
                mCVManager = null;
            }

            // If we are not shutting down the robotRunner, then we need to start the tango service
            // again so that it will be ready for a start() command.
            if (!shutdown && mState != State.SHUTDOWN) {
                // This compareTango logic is really tricky and also unfortunate. We needed this
                // logic to alleviate all possible race conditions in rapid start and stop of the
                // Tango, as occurs during map save. Basically, the internal runnable needs to be
                // able to get Tango object it was associated with, but the Tango is not passed in.
                // Since the object needs to be created before the Tango, the object definition
                // cannot be created here. Instead, we use the AtomicReference as a container for
                // the Tango, so we can ignore the post-startup logic if the Tango is shutdown
                // before the logic in the runnable can run.
                final AtomicReference<Tango> compareTango = new AtomicReference<>();
                mTango = new Tango(mParent, new Runnable() {
                    // Pass in a Runnable to be called from UI thread when Tango is ready; this
                    // Runnable will be running on a new thread.
                    // When Tango is ready, we can call Tango functions safely here only when
                    // there are no UI thread changes involved.
                    @Override
                    public void run() {
                        synchronized (mParent) {
                            if (compareTango.get() != mTango) {
                                Log.i(TAG, "Tango ran really fast");
                                return;
                            }
                            mCloudWorldManager.setTango(mTango);
                            // Report "Tango Connected" events to Firebase Analytics.
                            mFirebaseEventLogger.reportTangoConnectedEventToFirebase(mParent);
                            if (command == null) {
                                setState(State.WAIT_FOR_COMMAND);
                                mCloudWorldManager.refreshAndGetWorlds();
                                updateMonitorsState();
                            } else {
                                Log.i(TAG, "Running command...");
                                command.run();
                            }
                        }
                    }
                });
                compareTango.set(mTango);
            } else {
                setState(State.SHUTDOWN);
                mCloudWorldManager.shutdown();
                mSoundManager.shutdown();
                if (mRosNode != null) {
                    mRosNode.shutdown();
                }
                mCloudConnector.shutdown();
                mDatabaseChannel.shutdown();
                waitNode = mRosNode;
                waitThread = mMainLoop;
                mDriver.shutdown();
                waitDriver = mDriver;

                if (mNodeMainExecutor != null) {
                    mNodeMainExecutor.shutdown();
                }
                if (mRosCore != null) {
                    mRosCore.shutdown();
                }
                mRosNode = null;
                mNodeMainExecutor = null;
                mRosCore = null;
                mCommandStateMachine.shutdown();
            }
            updateMonitorsState();
        }

        if (shutdown) {
            mWebRTCManager.shutdown();
        }
        if (waitThread != null) {
            try {
                waitThread.join();
            } catch (InterruptedException e) {
                Log.i(TAG, "Interrupted trying to join the main loop thread");
            }
        }
        if (waitDriver != null) {
            waitDriver.waitShutdown();
        }
        if (waitNode != null) {
            waitNode.waitShutdown();
        }

        if (waitCVManager != null) {
            waitCVManager.waitShutdown();
        }

        Log.i(TAG, "Finished shutdown");
        if (shutdown && command != null) {
            command.run();
        }
    }

    public void saveADF(
            @SuppressWarnings({"SameParameterValue", "UnusedParameters"}) final String mapName) {
        Log.i(TAG, "Saving ADF");
        final String userUuid = mUserUuid;
        if (userUuid == null) {
            Log.e(TAG, "User UUID is null. Doesn't save ADF");
            return;
        }

        // Stop floor planning
        mTangoFloorplanner.stopFloorplanning();

        synchronized (mParent) {
            if (mState != State.RUNNING_MAPPING) {
                Log.e(TAG, "The map could not be made");
                return;
            }
            setState(State.SAVING_WORLD);
            updateMonitorsState();
            // Report "Save Map" event to Firebase Analytics.
            mFirebaseEventLogger.reportSaveMapEvent(mParent);
        }
        Thread saver = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] fileData = mTangoFloorplanner.generateFileData(
                        mUserUuid, mPreferences.mBundleAdjustment);
                synchronized (mParent) {
                    if (mState != State.SAVING_WORLD) {
                        Log.e(TAG, "The map could not be saved");
                        return;
                    }
                    Log.i(TAG, "Saving map");
                }
                updateMonitorsState();
                mCloudWorldManager.saveFromTango(mTango, mapName, fileData);
                synchronized (mParent) {
                    if (mState != State.SAVING_WORLD) {
                        return;
                    }
                    Log.i(TAG, "Saved map");
                    setState(State.SAVED_WORLD);
                    mCommandStateMachine.stopOperations();
                }
                restart(false, null);
            }
        });
        saver.start();
    }

    /**
     * Sets up the tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango, DetailedWorld world) {
        // Create a new Tango Configuration and enable the HelloMotionTrackingActivity API.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);
        if (world == null) {
            config.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, true);
        } else {
            CloudWorldManager.loadToTango(world, config);
        }

        return config;
    }

    /**
     * Set up the callback listeners for the Tango Service and obtain other parameters required
     * after Tango connection.
     * Listen to new Pose data.
     *
     * @param isMapping True if we are mapping
     */
    private void startupTango(boolean isMapping) {
        // Lock configuration and connect to Tango.
        // Select coordinate frame pair.
        final List<TangoCoordinateFramePair> framePairs = new ArrayList<>();
        if (mWorld != null && mWorld.isVPS()) {
            mWorldCoordinateFrame = TangoPoseData.COORDINATE_FRAME_GLOBAL_WGS84;
            mWorldCoordinateFrameName = Tango.COORDINATE_FRAME_ID_GLOBAL_WGS84;
        } else {
            mWorldCoordinateFrame = TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION;
            mWorldCoordinateFrameName = Tango.COORDINATE_FRAME_ID_AREA_DESCRIPTION;
        }
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE));

        // Floor planner
        // Start new Floorplanner and visualize polygons.
        if (isMapping) {
            startFloorPlan();
        }

        if (mHaveFisheye) {
            mTango.experimentalConnectOnFrameListener(
                    TangoCameraIntrinsics.TANGO_CAMERA_FISHEYE, this);
        }
        mTango.experimentalConnectOnFrameListener(
                TangoCameraIntrinsics.TANGO_CAMERA_COLOR, this);

        // Listen for new Tango data.
        mTango.connectListener(framePairs, new Tango.TangoUpdateCallback() {
            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                synchronized (mParent) {
                    if (mState != State.CALIBRATION && mState != State.RUNNING_MAPPING &&
                            mState != State.RUNNING_NAVIGATION) {
                        return;
                    }
                    mTangoUx.updatePoseStatus(pose.statusCode);

                    // Taken from the tango example, this controls whether or not we are
                    // localized. We do not want to save the map until we are localized, because
                    // we do not want to save invalid maps.
                    if ((pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION)
                            && (pose.targetFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE)) {
                        boolean localized = (pose.statusCode == TangoPoseData.POSE_VALID);
                        if (mLocalized != localized) {
                            setLocalized(localized);
                            if (!mLocalized) {
                                mFirebaseEventLogger.reportTangoDelocalizedEvent(mParent);
                                mLocation = null;
                                mCentroid = null;
                            } else {
                                mFirebaseEventLogger.reportTangoLocalizedEvent(mParent);
                            }
                        }
                    }
                    if (mLocalized && (pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE)
                            && (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION)) {
                        if (isMapping()) {
                            TangoFloorplanner fp = mTangoFloorplanner;
                            if (fp != null) {
                                fp.logPose(pose);
                            }
                        }
                        if (mWorld != null && mWorld.isVPS()) {
                            try {
                                TangoPoseData poseT = TangoSupport.getPoseAtTime(0,
                                        TangoPoseData.COORDINATE_FRAME_GLOBAL_WGS84,
                                        TangoPoseData.COORDINATE_FRAME_DEVICE,
                                        TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                                        TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                                        TangoSupport.ROTATION_IGNORED);
                                Transform tf = TangoTransformUtil.poseToTransform(poseT);
                                if (mCentroid == null) {
                                    mCentroid = tf;
                                }
                                mLocation = tf.project(mCentroid);
                            } catch (Exception ex) {
                                mLocation = null;
                                mCentroid = null;
                            }
                        } else {
                            mLocation = TangoTransformUtil.poseToTransform(pose);
                            mCentroid = null;
                        }
                    }
                }
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                // We are not using onXyzIjAvailable for this app.
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData pointCloud) {
                State state = mState;
                if (state != State.CALIBRATION && state != State.RUNNING_MAPPING &&
                        state != State.RUNNING_NAVIGATION) {
                    Log.v(TAG, "Ignore point cloud for wrong state: " + state);
                    return;
                }

                mTangoUx.updatePointCloud(pointCloud);

                mPointCloudManager.updatePointCloud(pointCloud);

                // Send point cloud to tango floorplanner
                TangoFloorplanner floorplanner = mTangoFloorplanner;
                if (floorplanner != null) {
                    // This is OK to do here because it's very fast, it only adds the point cloud
                    // to the internal manager of the floor planner.
                    // TODO(adamantivm) Consider to re-use the same PointCloud manager.
                    floorplanner.onPointCloudAvailable(pointCloud);
                }

                CVManager manager = mCVManager;
                if (manager != null && manager.requirePointCloud()) {
                    manager.sendPointCloud(pointCloud);
                }
            }

            @Override
            public void onTangoEvent(final TangoEvent event) {
                mTangoUx.updateTangoEvent(event);
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // We are not using onFrameAvailable for this application.
            }
        });
    }

    /**
     * Sets up and initializes TangoFloorplanner. If calibration is enabled, then it calculates
     * the height of the device while making the floor plan.
     */
    private void startFloorPlan() {
        synchronized (mParent) {
            mTangoFloorplanner = new TangoFloorplanner(new TangoFloorplanner
                    .OnFloorplanAvailableListener() {
                @SuppressLint("LogConditional")
                @Override
                public void onFloorplanAvailable(TangoFloorplanner floorplanner,
                        List<World.FloorPlanLevel> levels) {
                    if (mCalibrate.get()) {
                        updateFloorAndCeiling(levels);
                    }
                    Log.d(TAG, "Levels: " + levels.size());
                }
            });

            // Set camera intrinsics to TangoFloorplanner.
            mTangoFloorplanner.setDepthCameraCalibration(mTango.getCameraIntrinsics
                    (TangoCameraIntrinsics.TANGO_CAMERA_DEPTH));

            mTangoFloorplanner.startFloorplanning();
        }
    }

    /**
     * Calibrates the robot. Calculates the height of the Tango device with respect to the floor
     * plane.
     */
    public void calibrate() {
        // Set calibration flag to true.
        mCalibrate.set(true);

        // First start the system and the floor planner.
        startSystem(null);
        startFloorPlan();

        // Perform calibration
        Log.v(TAG, "Perform robot calibration");

        final long startWaiting = System.currentTimeMillis();
        // We need to wait until wait until the calibration is performed. This is done continuing
        // on a different thread.
        Thread waitForWaitingState = new Thread(new Runnable() {
            @Override
            public void run() {
                // Turn around while performing calibration.
                while (System.currentTimeMillis() - startWaiting <
                        CALIBRATION_DURATION_MILLISECONDS) {
                    Log.v(TAG, "Turning around for calibration");
                    mDriver.setMotion(0, CALIBRATION_ANGULAR_SPEED);
                }

                synchronized (mParent) {
                    // Get the mean value of the distances calculated.
                    float deviceHeight = 0;
                    for (float height : mDeviceToFloorMeasurements) {
                        deviceHeight += height;
                    }
                    if (mDeviceToFloorMeasurements.size() > 0) {
                        deviceHeight /= mDeviceToFloorMeasurements.size();
                    }
                    // Clear the distances list.
                    mDeviceToFloorMeasurements.clear();

                    Log.v(TAG, "Mean device height: " + String.format("%.2f", deviceHeight));
                    // Send value to Firebase.
                    mCloudConnector.onNewTangoDeviceHeight(deviceHeight);

                    // Switch state to wait for command.
                    mCalibrate.set(false);
                    setState(State.WAIT_FOR_COMMAND);
                    updateMonitorsState();

                    // Stop floor planner.
                    mTangoFloorplanner.stopFloorplanning();
                    mTangoFloorplanner.release();
                    mTangoFloorplanner = null;
                }
            }
        });
        waitForWaitingState.start();
    }

    /**
     * Given the Floorplan levels, calculate the ceiling height and the current distance from the
     * device to the floor.
     */
    private void updateFloorAndCeiling(List<World.FloorPlanLevel> levels) {
        if (!levels.isEmpty()) {
            // Currently only one level is supported by the floorplanning API.
            World.FloorPlanLevel level = levels.get(0);
            // Query current device pose and calculate the distance from it to the floor.
            TangoPoseData devicePose;

            devicePose = TangoSupport.getPoseAtTime(0.0,
                    TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                    TangoPoseData.COORDINATE_FRAME_DEVICE,
                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                    0);

            float devicePoseY = devicePose.getTranslationAsFloats()[1];
            float floorY = (float) level.getMinZ();
            float deviceHeight = devicePoseY - floorY;
            Log.v(TAG, "Device height: " + String.format("%.2f", deviceHeight));
            synchronized (mParent) {
                mDeviceToFloorMeasurements.add(deviceHeight);
            }
        }
    }


    /**
     * Gets if the action1 is on
     */
    public boolean getAction1() {
        return mDriver.getAction1();
    }

    /**
     * Gets if the action2 is on
     */
    public boolean getAction2() {
        return mDriver.getAction2();
    }

    /**
     * Removes a world.
     */
    public void removeWorld(World w) {
        mCloudWorldManager.removeWorld(w);
    }


    public void setPromptLink(CloudWorldManager.PromptLink link) {
        mCloudWorldManager.setPromptLink(link);
    }

    public void updatePreferences() {
        updateMaxVelParam();
        updateSmootherParams();
        updateInflationParam();
    }

    public void setRobotPreferences(RobotPreferences preferences) {
        synchronized (mParent) {
            mPreferences = preferences;
        }
    }

    /**
     * Updates smoother params after user input.
     */
    public void updateSmootherParams() {
        synchronized (mParent) {
            mCloudConnector.onNewSmootherParams(new SmootherParams(mPreferences.mSmootherDeviation,
                    mPreferences.mSmootherSmoothness));
            Log.v(TAG, "Updated smoother deviation and smoothness to: " +
                    mPreferences.mSmootherDeviation + " and " + mPreferences.mSmootherSmoothness);
        }
    }

    /**
     * Updates inflation parameter in settings
     */
    public void updateInflationParam() {
        //TODO implement inflation parameters
    }

    /**
     * Updates maximum linear velocity parameter in settings.
     */
    public void updateMaxVelParam() {
        synchronized (mParent) {
            if (mDriver != null) {
                int velocity = mPreferences.mMaxLinearVelocity;
                double newVelocity = ((double) velocity) / 100.0f * getMaxAllowedSpeed();
                mDriver.getModel().changeMaxVelocity(newVelocity);
                Log.v(TAG, "Updated velocities to: " + newVelocity);
            }
        }
    }

    /**
     * Get maximum speed of the Robot Model
     *
     * @return Maximum speed
     */
    public double getMaxSpeed() {
        if (mDriver != null) {
            RobotDriver.RobotModel model = mDriver.getModel();
            if (model != null) {
                return model.getMaxSpeed();
            }
        }
        return 0.0f;
    }

    /**
     * Obtains the maximum linear speed of the robot from Robot Driver
     *
     * @return Speed
     */
    public double getMaxAllowedSpeed() {
        if (mDriver != null) {
            RobotDriver.RobotModel model = mDriver.getModel();
            if (model != null) {
                return model.getMaxAllowedLinearVelocity();
            }
        }
        return 0.65f;
    }
}
