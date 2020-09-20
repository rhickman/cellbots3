package ai.cellbots.robot.manager;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.EventProcessor;
import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.TimedLoop;
import ai.cellbots.common.Transform;
import ai.cellbots.common.cloud.CloudPath;
import ai.cellbots.common.cloud.DataFactory;
import ai.cellbots.common.cloud.RTCDatabaseChannel;
import ai.cellbots.common.concurrent.AtomicEnum;
import ai.cellbots.common.data.BatteryStatus;
import ai.cellbots.common.data.RobotMetadata;
import ai.cellbots.common.data.Teleop;
import ai.cellbots.robot.cloud.CloudRobotStateManager;
import ai.cellbots.robot.control.ActionMediator;
import ai.cellbots.robot.control.AnimationManager;
import ai.cellbots.robot.control.CleaningManager;
import ai.cellbots.robot.control.CloudWebRTCManager;
import ai.cellbots.robot.control.Controller;
import ai.cellbots.robot.control.VelocityMultiplexer;
import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.costmap.CostMapFuser;
import ai.cellbots.robot.costmap.CostMapInflator;
import ai.cellbots.robot.costmap.CostMapManager;
import ai.cellbots.robot.costmap.CostMapPose;
import ai.cellbots.robot.costmap.SimpleCostMapInflator;
import ai.cellbots.robot.costmap.TrivialCostMapFuser;
import ai.cellbots.robot.driver.RobotDriver;
import ai.cellbots.robot.executive.Executive;
import ai.cellbots.robot.executive.RandomDriverExecutive;
import ai.cellbots.robot.executive.SimpleExecutive;
import ai.cellbots.robot.navigation.AStarPathFinder;
import ai.cellbots.robot.navigation.DijkstraPathFinder;
import ai.cellbots.robot.navigation.GlobalPlanner;
import ai.cellbots.robot.navigation.LocalPlanner;
import ai.cellbots.robot.navigation.NavigationManager;
import ai.cellbots.robot.navigation.Path;
import ai.cellbots.robot.navigation.PointCloudSafetyController;
import ai.cellbots.robot.navigation.PurePursuitVelocityGenerator;
import ai.cellbots.robot.navigation.SimpleVelocityGenerator;
import ai.cellbots.robot.navigation.TrajectoryRolloutVelocityGenerator;
import ai.cellbots.robot.ros.ROSNodeManager;
import ai.cellbots.robot.slam.SLAMSystem;
import ai.cellbots.robot.state.RobotSessionGlobals;
import ai.cellbots.robot.tango.TangoSLAMSystem;
import ai.cellbots.robot.vision.CameraImage;
import ai.cellbots.robot.vision.CameraInfo;
import ai.cellbots.robot.vision.DepthImage;
import ai.cellbots.robot.vision.PointCloud;
import ai.cellbots.robot.vision.VisionSystemManager;

import static ai.cellbots.common.SystemUtil.GenerateMemoryUsageString;

public class RobotManager implements ThreadedShutdown, SLAMSystem.TransformListener,
        SLAMSystem.ColorDepthListener, SLAMSystem.CameraImageListener,
        SLAMSystem.PointCloudListener, SLAMSystem.StatusUpdateListener,
        CostMapManager.Listener {
    private final static String TAG = RobotManager.class.getSimpleName();

    /**
     * On update of the color image.
     *
     * @param slamSystem The SLAM system.
     * @param colorImage The color image.
     */
    @Override
    public void onColorImage(SLAMSystem slamSystem, CameraImage colorImage) {
        if (mCloudWebRTCManager != null
                && colorImage.getCameraInfo().getCamera() == CameraInfo.SensorType.COLOR) {
            mCloudWebRTCManager.sendImage(colorImage);
        }
    }

    /**
     * Stores the state of the robot manager.
     */
    public enum State {
        INITIAL, // Robot is connecting
        NO_MAP, // Waiting for the user to select mapping or running
        MAPPING, // Creating a map
        NAVIGATING, // Navigating over an existing map
        SAVING_MAP, // Saving the map we have created
        SAVED_MAP, // Saved the map
        SHUTDOWN, // Shutting down the system
    }

    /**
     * Listen for updates from the RobotManagerWrapper
     */
    public interface Listener {
        /**
         * Called when the state is updated.
         */
        void onStateUpdate();
    }

    private final Context mParent; // Parent android context.

    private final RobotDriver mRobotDriver; // The robot driver.
    private final CostMapManager mCostMapManager;
    private final SoundManager mSoundManager; // The sound manager

    // TODO enable this
    //private final BumperCostMap mBumperCostMap;
    private final VisionSystemManager mVisionSystemManager;
    private final SLAMSystem mSLAMSystem;
    private final NavigationManager mNavigator;
    private final AnimationManager mAnimationManager;
    private final CleaningManager mCleaningManager;
    private final PointCloudSafetyController mPointCloudSafetyController;
    private final ActionMediator mActionMediator;
    private final VelocityMultiplexer mVelocityMultiplexer;
    private final Executive mExecutive;

    private final String mMappingRunUuid;
    private final RobotSessionGlobals mSession;
    private final Listener mListener;
    private final EventProcessor mListenerEventProcessor;
    private final CloudRobotStateManager mCloudRobotStateManager;

    private final Transform mMockLocation;

    // The state of the robot manager.
    private final AtomicEnum<State> mState;
    // True when the camera is localized.
    private final AtomicBoolean mIsLocalized;
    // True when the connected robot is not recognized.
    private final AtomicBoolean mRobotInvalid;
    // True when we can save the learned world.
    private final AtomicBoolean mCanSaveWorld;

    private final CloudWebRTCManager mCloudWebRTCManager;
    private final RTCDatabaseChannel mDatabaseChannel;

    private final ROSNodeManager mROSNodeManager;

    /**
     * Get the session.
     *
     * @return The session variables.
     */
    public RobotSessionGlobals getSession() {
        return mSession;
    }

    /**
     * Gets the CloudRobotStateManager.
     *
     * @return The CloudRobotStateManager, or null if not initialized.
     */
    public CloudRobotStateManager getCloudRobotStateManager() {
        return mCloudRobotStateManager;
    }

    /**
     * Gets the state of the system.
     *
     * @return Current state of RobotManager.
     */
    public State getState() {
        return mState.get();
    }

    /**
     * Create the robot manager.
     *
     * @param parent         The context for the parent.
     * @param listener       The listener for the robot manager.
     * @param robotDriver    The current robot driver.
     * @param mappingRunUuid The mapping session uuid if mapping.
     * @param session        The session global variables.
     * @param configuration  The robot manager configuration.
     */
    public RobotManager(Context parent, Listener listener, RobotDriver robotDriver,
                        String mappingRunUuid, final RobotSessionGlobals session,
                        final RobotManagerConfiguration configuration) {
        mParent = parent;
        mListener = listener;
        mRobotDriver = robotDriver;
        mMappingRunUuid = mappingRunUuid;
        mSession = session;
        mState = new AtomicEnum<>(State.INITIAL);
        mIsLocalized = new AtomicBoolean(false);
        mRobotInvalid = new AtomicBoolean(false);
        mCanSaveWorld = new AtomicBoolean(false);
        mMockLocation = configuration.getMockLocation();
        mROSNodeManager = new ROSNodeManager(configuration.isROSEnabled());

        if (robotDriver != null) {
            robotDriver.initDriver();
        }

        mListenerEventProcessor = new EventProcessor(TAG, new EventProcessor.Processor() {
            @Override
            public boolean update() {
                mListener.onStateUpdate();
                return true;
            }

            @Override
            public void shutdown() {
            }
        });

        if (mappingRunUuid != null && session.getWorld() != null) {
            throw new IllegalArgumentException("There must only be mapping run or world");
        }
        if (session.getWorld() == null && mappingRunUuid == null) {
            throw new IllegalArgumentException("Either world or mapping run must be started");
        }

        mSoundManager = new SoundManager(parent);
        mRobotDriver.setSelectedRobotUuid(session.getRobotUuid());
        // TODO enable this
        //mBumperCostMap = new BumperCostMap(configuration.getCostMapResolution());
        //mRobotDriver.setBumperCostMap(mBumperCostMap);
        mPointCloudSafetyController = new PointCloudSafetyController(session,
                configuration.isOperationSoundEnabled() ? mSoundManager : null);

        // Connect the sound manager with the robot driver. This is for making sound when
        // setting teleop.
        mRobotDriver.setSoundManager(
                configuration.isOperationSoundEnabled() ? mSoundManager : null);

        final LocalPlanner localPlanner;
        if (configuration.getLocalPlanner()
                == RobotManagerConfiguration.LocalPlanner.PURE_PURSUIT) {
            localPlanner = new LocalPlanner(session, mPointCloudSafetyController,
                    new PurePursuitVelocityGenerator(session,
                            configuration.getCostMapResolution()));
        } else if (configuration.getLocalPlanner()
                == RobotManagerConfiguration.LocalPlanner.TRAJECTORY_ROLLOUT) {
            localPlanner = new LocalPlanner(session, mPointCloudSafetyController,
                    new TrajectoryRolloutVelocityGenerator(session,
                            configuration.getCostMapResolution()));
        } else if (configuration.getLocalPlanner()
                == RobotManagerConfiguration.LocalPlanner.SIMPLE) {
            localPlanner = new LocalPlanner(session, mPointCloudSafetyController,
                    new SimpleVelocityGenerator(session,
                            configuration.getCostMapResolution()));
        } else {
            throw new IllegalArgumentException(
                    "Unsupported local planner type: " + configuration.getLocalPlanner());
        }

        GlobalPlanner globalPlanner;
        if (configuration.getGlobalPlanner() == RobotManagerConfiguration.GlobalPlanner.DIJKSTRA) {
            globalPlanner = new GlobalPlanner(session, new DijkstraPathFinder());
        } else if (configuration.getGlobalPlanner()
                == RobotManagerConfiguration.GlobalPlanner.ASTAR) {
            globalPlanner = new GlobalPlanner(session, new AStarPathFinder());
        } else {
            throw new IllegalArgumentException(
                    "Unsupported global planner type: " + configuration.getGlobalPlanner());
        }

        mVelocityMultiplexer = new VelocityMultiplexer(robotDriver);
        mAnimationManager = new AnimationManager(parent, session, mVelocityMultiplexer);
        mNavigator = new NavigationManager(session, mVelocityMultiplexer, globalPlanner,
                localPlanner,
                new NavigationManager.Listener() {
                    @Override
                    public void onPath(Path path) {
                        if (mCloudRobotStateManager != null) {
                            mCloudRobotStateManager.setPath(path);
                        }
                        mROSNodeManager.publishGlobalPlan(
                                path == null ? Collections.<CostMapPose>emptyList() : path.asList(),
                                configuration.getCostMapResolution());
                    }

                    @Override
                    public void onLocalPlan(List<Transform> path) {
                        mROSNodeManager.publishLocalPlan(path);
                    }
                });
        mCleaningManager = new CleaningManager(session, mVelocityMultiplexer);

        double robotHalfLength = mSession.getRobotModel().getLength() / 2;
        double robotHalfWidth = mSession.getRobotModel().getWidth() / 2;
        double robotRadius = Math.sqrt(robotHalfLength * robotHalfLength + robotHalfWidth * robotHalfWidth);
        if (configuration.isVisionSystemEnabled()) {
            mVisionSystemManager = new VisionSystemManager(parent,
                    configuration.getCostMapResolution(), robotRadius, mROSNodeManager);
        } else {
            Log.i(TAG, "Vision system is not enabled.");
            mVisionSystemManager = null;
        }

        ArrayList<Controller> controllers = new ArrayList<>(3);
        controllers.add(mNavigator);
        controllers.add(mCleaningManager);
        controllers.add(mAnimationManager);
        mActionMediator = new ActionMediator(controllers);

        // Executive startup must proceed the cloud robot state manager startup since the
        // cloud robot state manager will run callbacks on the executive.
        if (configuration.getExecutive() == RobotManagerConfiguration.Executive.RANDOM) {
            mExecutive = new RandomDriverExecutive(parent, session, mActionMediator,
                    mSoundManager, mAnimationManager, mROSNodeManager);
        } else if (configuration.getExecutive() == RobotManagerConfiguration.Executive.BASIC) {
            mExecutive = new SimpleExecutive(parent, session, mActionMediator, mSoundManager,
                    mAnimationManager, mROSNodeManager);
        } else {
            throw new IllegalArgumentException("Unsupported Executive type: "
                    + configuration.getExecutive());
        }

        mCloudRobotStateManager = new CloudRobotStateManager(parent, session,
                new CloudRobotStateManager.Listener() {
                    @Override
                    public void onRobotMetadata(RobotMetadata metadata) {
                        mExecutive.setMetadata(metadata);
                    }
                    @Override
                    public void onTeleop(Teleop teleop) {
                        mVelocityMultiplexer.enqueueVelocity(teleop,
                                VelocityMultiplexer.MuxPriority.TELEOP);
                    }
                }, mappingRunUuid, configuration.getCostMapResolution());

        if (configuration.getSLAMSystem() == RobotManagerConfiguration.SLAMSystem.TANGO) {
            mSLAMSystem = new TangoSLAMSystem(mParent, this, this, this, this, this, session,
                    configuration.getCostMapResolution());
        } else {
            throw new IllegalArgumentException("Unsupported SLAM system type: "
                    + configuration.getSLAMSystem());
        }

        LinkedList<CostMap> costMaps = new LinkedList<>();
        costMaps.addAll(mSLAMSystem.getCostMaps());
        if (mVisionSystemManager != null) {
            costMaps.add(mVisionSystemManager.getCostMap());
        }
        // TODO enable this
        //costMaps.add(mBumperCostMap);

        CostMapInflator inflator;
        if (configuration.getCostMapInflator()
                == RobotManagerConfiguration.CostMapInflator.SIMPLE) {
            inflator = new SimpleCostMapInflator(configuration.getCostMapResolution(), robotRadius,
                    configuration.getInflationFactor());
        } else {
            throw new IllegalArgumentException("Unsupported CostMapInflator type: "
                    + configuration.getCostMapInflator());
        }

        CostMapFuser fuser;
        if (configuration.getCostMapFuser() == RobotManagerConfiguration.CostMapFuser.TRIVIAL) {
            fuser = new TrivialCostMapFuser(session, configuration.getCostMapResolution());
        } else {
            throw new IllegalArgumentException("Unsupported CostMapInflator type: "
                    + configuration.getCostMapInflator());
        }

        mCostMapManager = new CostMapManager(mROSNodeManager, session, costMaps, this,
                inflator, fuser, configuration.getCostMapResolution());

        mRobotDriver.setListener(new RobotDriver.Listener() {
            @Override
            public void onBumperUpdate(String uuid, RobotDriver.BumperState bumperState) {
                if (session.getRobotUuid().equals(uuid)) {
                    localPlanner.setBumperState(bumperState);
                }
            }

            @Override
            public void onBatteryUpdate(String uuid, BatteryStatus[] batteryStatuses,
                                        boolean low, boolean critical) {
                if (session.getRobotUuid().equals(uuid)) {
                    mCloudRobotStateManager.setBatteryStatuses(batteryStatuses);
                    mExecutive.setBatteryState(low, critical);
                }
            }

            @Override
            public void onVersionUpdate(String uuid, String version) {
                if (session.getRobotUuid().equals(uuid)) {
                    mCloudRobotStateManager.setRobotVersion(version);
                }
            }
        });

        mSoundManager.update(mSession.getUserUuid(), mSession.getRobotUuid());
        mAnimationManager.update(mSession.getUserUuid(), mSession.getRobotUuid());
        mDatabaseChannel = new RTCDatabaseChannel(new HashMap<CloudPath, DataFactory>());
        mDatabaseChannel.update(mSession.getUserUuid(), mSession.getRobotUuid());
        mCloudWebRTCManager = new CloudWebRTCManager(parent, mSession, mDatabaseChannel,
                new CloudWebRTCManager.RTCDataListener() {
                    @Override
                    public void onDataUpdate(
                            CloudWebRTCManager webRTCManager,
                            RTCDatabaseChannel dataChannel) {
                        Object teleop = dataChannel.getObject(CloudPath.ROBOT_TELEOP_PATH,
                                mSession.getUserUuid(), mSession.getRobotUuid());
                        if (teleop != null && teleop instanceof Teleop) {
                            mVelocityMultiplexer.enqueueVelocity((Teleop) teleop,
                                    VelocityMultiplexer.MuxPriority.RTC_TELEOP);
                        } else {
                            mVelocityMultiplexer.enqueueVelocity(null,
                                    VelocityMultiplexer.MuxPriority.RTC_TELEOP);
                        }
                    }
                });

        if (mMockLocation != null) {
            onTransform(mSLAMSystem, mMockLocation);
        }
    }

    /**
     * Called to update the manager.
     */
    public synchronized void update() {
        boolean TriggerEventListener = false;
        String uuid = mRobotDriver.getRobotUuid();
        if (uuid != null && !mSession.getRobotUuid().equals(uuid)) {
            Log.i(TAG, "Robot is invalid: " + uuid + " != " + mSession.getRobotUuid());
            mRobotInvalid.set(true);
        }
        if (mState.get() == State.INITIAL) {
            if (mSLAMSystem.getState() == SLAMSystem.State.NAVIGATING) {
                mIsLocalized.set(false);
                setState(State.NAVIGATING);
            }
            if (mSLAMSystem.getState() == SLAMSystem.State.MAPPING) {
                setState(State.MAPPING);
            }
        }
        if (mState.get() == State.MAPPING) {
            boolean canSave = mSLAMSystem.canSaveWorld();
            if (canSave != mCanSaveWorld.get()) {
                mCanSaveWorld.set(canSave);
                TriggerEventListener = true;
            }
            if (mSLAMSystem.getState() == SLAMSystem.State.SAVING_MAP) {
                setState(State.SAVING_MAP);
                mCanSaveWorld.set(false);
                TriggerEventListener = true;
            }
            if (mSLAMSystem.getState() == SLAMSystem.State.SAVED_MAP) {
                setState(State.SAVED_MAP);
                mCanSaveWorld.set(false);
                TriggerEventListener = true;
            }
        }
        if (mState.get() == State.SAVING_MAP) {
            if (mSLAMSystem.getState() == SLAMSystem.State.SAVED_MAP) {
                setState(State.SAVED_MAP);
            }
        }
        if (mState.get() == State.NAVIGATING) {
            boolean isLocalized = mSLAMSystem.isLocalized();
            if (isLocalized != mIsLocalized.get()) {
                Log.i(TAG, "Localized: " + isLocalized);
                mIsLocalized.set(isLocalized);
                mCloudRobotStateManager.setLocalized(isLocalized);
                TriggerEventListener = true;
            }
            if (mActionMediator.isReady()) {
                mExecutive.enable();
            }
        }
        if (TriggerEventListener) {
            mListenerEventProcessor.onEvent();
        }
        Log.d(TAG, "Memory: " + GenerateMemoryUsageString());
    }

    /**
     * Set the state of the system.
     *
     * @param state The new state.
     */
    private synchronized void setState(State state) {
        mState.set(state);
        mListenerEventProcessor.onEvent();
    }

    /**
     *  Nudges the robot if connected to the robot base.
     */
    void nudgeRobot() {
        new Thread() {
            public void run() {
                String userUuid = mSession.getUserUuid();
                String robotUuid = mSession.getRobotUuid();
                Teleop teleopClockwise = new Teleop(3.7e-17, 0, 0, 0, 0, -5.0);
                Teleop teleopCounterClockwise = new Teleop(1.7e-16, 0, 0, 0, 0, 5.0);
                Teleop teleop1 = new Teleop(teleopClockwise, userUuid, robotUuid);
                Teleop teleop2 = new Teleop(teleopCounterClockwise, userUuid, robotUuid);
                for (int i = 0; i < 4; i++) {
                    mRobotDriver.setTeleop(teleop1);
                    SystemClock.sleep(40);
                }
                SystemClock.sleep(200);
                for (int i = 0; i < 4; i++) {
                    mRobotDriver.setTeleop(teleop2);
                    SystemClock.sleep(40);
                }
            }
        }.start();
    }

    /**
     * Gets if the camera is localized.
     *
     * @return True if the camera is localized.
     */
    public synchronized boolean isLocalized() {
        return mSLAMSystem.isLocalized();
    }

    /**
     * Gets if we can save the current world.
     *
     * @return True if the world can be saved.
     */
    public boolean canSaveWorld() {
        return mCanSaveWorld.get();
    }

    /**
     * Saves the map.
     *
     * @param mapName The map to save.
     */
    public void saveMap(String mapName) {
        mSLAMSystem.saveMap(mapName);
    }

    /**
     * Gets if the system should shutdown.
     *
     * @return True if it should terminate.
     */
    public boolean shouldShutdown() {
        if (mSLAMSystem.getError()) {
            Log.w(TAG, "SLAM system has error");
            return true;
        }
        return mRobotInvalid.get();
    }

    /**
     * Gets the mapping run uuid.
     *
     * @return The value of the mapping run uuid.
     */
    public String getMappingRunUuid() {
        return mMappingRunUuid;
    }

    /**
     * Gets the world.
     *
     * @return The world, or null if mapping.
     */
    public DetailedWorld getWorld() {
        return mSession.getWorld();
    }

    /**
     * Create a list of things to shutdown at shutdown time.
     *
     * @return The list to shutdown.
     */
    private List<ThreadedShutdown> getShutdownList() {
        LinkedList<ThreadedShutdown> shutdown = new LinkedList<>();
        shutdown.add(mActionMediator);
        shutdown.add(mAnimationManager);
        // TODO enable this
        //shutdown.add(mBumperCostMap);
        shutdown.add(mCloudRobotStateManager);
        shutdown.add(mCloudWebRTCManager);
        shutdown.add(mExecutive);
        shutdown.add(mCleaningManager);
        shutdown.add(mCostMapManager);
        shutdown.add(mNavigator);
        shutdown.add(mPointCloudSafetyController);
        shutdown.add(mSLAMSystem);
        shutdown.add(mSoundManager);
        shutdown.add(mVelocityMultiplexer);
        shutdown.add(mROSNodeManager);
        if (mVisionSystemManager != null) {
            shutdown.add(mVisionSystemManager);
        }
        return Collections.unmodifiableList(shutdown);
    }

    /**
     * Shutdown the robot manager.
     */
    @Override
    public void shutdown() {
        mState.set(State.SHUTDOWN);
        mDatabaseChannel.shutdown();
        for (ThreadedShutdown shutdown : getShutdownList()) {
            shutdown.shutdown();
        }
        mRobotDriver.setSelectedRobotUuid(null);
        mRobotDriver.setBumperCostMap(null);
        mRobotDriver.setListener(null);
    }

    /**
     * Wait for the robot manager to shutdown.
     */
    @Override
    public void waitShutdown() {
        shutdown();
        TimedLoop.efficientShutdown(getShutdownList());
    }

    /**
     * Called when a transform update is published by the SLAM system.
     *
     * @param slamSystem The SLAM system.
     * @param transform  The transform.
     */
    @Override
    public void onTransform(SLAMSystem slamSystem, Transform transform) {
        // If the transform is null, we will override with the mock location so we are localized.
        if (transform == null) {
            transform = mMockLocation;
        }
        Transform base = mRobotDriver.computeRobotBasePosition(transform);
        mActionMediator.setTransform(base);
        // TODO(playerone) enable this
        //mBumperCostMap.setTransform(base);
        mCloudRobotStateManager.setTransform(base);
        mExecutive.setTransform(base);
        mPointCloudSafetyController.setTransform(base);
        mROSNodeManager.publishRobotPose(transform, base);
    }

    /**
     * Called when a new depth image that is synchronized with a color image is produced.
     * The depth image is not upsampled, so only pixels projected from point cloud are set
     *
     * @param slamSystem The SLAM system.
     * @param colorImage The color image.
     * @param depthImage The depth image.
     * @param worldToCameraPose The pose from world origin (adf) to current frame
     *                          represented as [tx, ty, tz, qx, qy, qz, qw]
     */
    public void onColorDepth(SLAMSystem slamSystem, CameraImage colorImage, DepthImage depthImage,
                             double[] worldToCameraPose) {
        if (colorImage.getWidth() <= 0 || colorImage.getHeight() <= 0 ||
                colorImage.getBytes() == null) {
            Log.wtf(TAG, "Color image received by robot manager is invalid");
            return;
        }
        if (depthImage.getWidth() <= 0 || depthImage.getHeight() <= 0 ||
                depthImage.getFloats() == null) {
            Log.wtf(TAG, "Depth image received by robot manager is invalid");
            return;
        }
        if (worldToCameraPose == null) {
            Log.wtf(TAG, "World to camera pose received by robot manager is invalid");
            return;
        }
        if (mVisionSystemManager != null) {
            mVisionSystemManager.onColorDepth(colorImage, depthImage, worldToCameraPose);
        }
    }

    /**
     * Called when SLAM system status is updated.
     */
    public void onSLAMStatusUpdated() {
        update();
    }

    /**
     * Called when a new PointCloud is sent by the SLAMSystem.
     *
     * @param slamSystem    The SLAM system.
     * @param pointCloud    The PointCloud object.
     * @param depthLocation The depth location.
     */
    @Override
    public void onPointCloud(SLAMSystem slamSystem, PointCloud pointCloud, Transform depthLocation) {
        float[] pointColors = new float[pointCloud.getPointCount()];
        mPointCloudSafetyController.onPointCloud(pointCloud, pointColors, depthLocation);
        mROSNodeManager.publishPointCloudPose(depthLocation);
        mROSNodeManager.publishPointCloud(pointCloud, pointColors.length > 0, pointColors);
    }

    /**
     * Called when a new CostMap is sent by the CostMap manager.
     *
     * @param manager The CostMapManager that was updated.
     */
    @Override
    public void onNewCostMap(CostMapManager manager) {
        update();
        mActionMediator.setFullyInflatedCostMap(manager.getFullyInflatedOutputCostMap());
        mActionMediator.setProportionallyInflatedCostMap(manager.getProportionallyInflatedOutputCostMap());
    }
}
