package ai.cellbots.robot.manager;

import android.content.Context;
import android.util.Log;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.Strings;
import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.TimedLoop;
import ai.cellbots.common.World;
import ai.cellbots.common.data.NavigationStateCommand;
import ai.cellbots.robot.cloud.CloudRobotStateManager;
import ai.cellbots.robot.driver.kobuki.KobukiDriver;
import ai.cellbots.robot.driver.TestRobotDriver;
import ai.cellbots.robot.driver.RobotDriver;
import ai.cellbots.robot.driver.RobotModel;
import ai.cellbots.robot.driver.parallax.ParallaxArloDriver;
import ai.cellbots.robot.driver.parallax.RubbermaidCartDriver;
import ai.cellbots.robot.slam.WorldManager;
import ai.cellbots.robot.state.RobotSessionGlobals;
import ai.cellbots.robot.tango.TangoWorldManager;
import ai.cellbots.tangocommon.CloudWorldManager;

/**
 * Initializes a robot by connecting to it.
 */
public class RobotManagerWrapper implements ThreadedShutdown, WorldManager.Listener,
        RobotManager.Listener {
    private static final String TAG = RobotManagerWrapper.class.getSimpleName();
    private static final long LOOP_MILLISECONDS = 200;  // In milliseconds.
    private final TimedLoop mTimedLoop;
    private final List<RobotDriver> mRobotDrivers;
    private final String mUserUuid;
    private final NavigationStateManager mNavigationStateManager;
    private final Context mParent;
    private final RobotManagerConfiguration mConfiguration;
    private final Set<Listener> mListeners = new HashSet<>();
    private static final Object sListenerLock = new Object();
    private RobotManager mManager = null;
    private WorldManager mWorldManager = null;
    private CloudWorldManager.PromptLink mPromptLink = null;
    private String mLastRobotUuid = null;

    /**
     * Listen for updates from the RobotManagerWrapper
     */
    public interface Listener {
        /**
         * Called when the state is updated.
         */
        void onStateUpdate();
    }

    /**
     * Add a new listener.
     * @param listener The listener to add.
     */
    public void addListener(RobotManagerWrapper.Listener listener) {
        synchronized (sListenerLock) {
            mListeners.add(listener);
            listener.onStateUpdate();
        }
    }

    /**
     * Remove a listener.
     * @param listener The listener to remove.
     */
    public void removeListener(RobotManagerWrapper.Listener listener) {
        synchronized (sListenerLock) {
            if (mListeners.contains(listener)) {
                mListeners.remove(listener);
            }
        }
    }

    /**
     * Set the promptLink for cloud prompts.
     * @param promptLink The promptLink.
     */
    public synchronized void setPromptLink(CloudWorldManager.PromptLink promptLink) {
        mPromptLink = promptLink;
        if (mWorldManager != null) {
            mWorldManager.setPromptLink(promptLink);
        }
    }

    /**
     * Gets the CloudRobotStateManager from the RobotManager.
     *
     * @return The CloudRobotStateManager, or null if RobotManager is null.
     */
    public CloudRobotStateManager getCloudRobotStateManager() {
        final RobotManager manager = mManager;
        if (manager != null) {
            return manager.getCloudRobotStateManager();
        }
        return null;
    }

    /**
     * Get the state of the manager.
     */
    public RobotManager.State getRobotState() {
        RobotManager manager = mManager;
        if (manager != null) {
            return manager.getState();
        }
        return null;
    }

    /**
     * Get the state of the world manager.
     */
    public WorldManager.State getWorldManagerState() {
        WorldManager manager = mWorldManager;
        if (manager != null) {
            return manager.getState();
        }
        return null;
    }

    /**
     * Get the session state of the manager.
     */
    public RobotSessionGlobals getSession() {
        RobotManager manager = mManager;
        if (manager != null) {
            return manager.getSession();
        }
        return null;
    }

    /**
     * Initialize the first connected robot.
     * @param parent The parent context.
     * @param userUuid The user uuid.
     * @param configuration The robot manager's configuration
     */
    public RobotManagerWrapper(final Context parent, final String userUuid,
                               RobotManagerConfiguration configuration) {
        mUserUuid = userUuid;
        mParent = parent;
        mConfiguration = configuration;

        mNavigationStateManager = new NavigationStateManager(parent, userUuid);

        LinkedList<RobotDriver> drivers = new LinkedList<>();
        if (configuration.getRobotDriver() == RobotManagerConfiguration.RobotDriver.MOCK) {
            drivers.add(new TestRobotDriver(parent));
        }
        if (configuration.getRobotDriver() == RobotManagerConfiguration.RobotDriver.REAL) {
            drivers.add(new KobukiDriver(parent));
            drivers.add(new ParallaxArloDriver(parent));
            drivers.add(new RubbermaidCartDriver(parent));
        }
        mRobotDrivers = Collections.unmodifiableList(drivers);

        startWorldManager();

        mTimedLoop = new TimedLoop(TAG, new TimedLoop.Looped() {
            @Override
            public boolean update() {
                doUpdate();
                return true;
            }

            @Override
            public void shutdown() {
                synchronized (RobotManagerWrapper.this) {
                    LinkedList<ThreadedShutdown> shutdown = new LinkedList<>();
                    shutdown.addAll(mRobotDrivers);
                    shutdown.add(mNavigationStateManager);
                    if (mManager != null) {
                        shutdown.add(mManager);
                    }
                    if (mWorldManager != null) {
                        shutdown.add(mWorldManager);
                    }
                    TimedLoop.efficientShutdown(shutdown);
                }
            }
        }, LOOP_MILLISECONDS);
    }

    /**
     * Starts the world manager. Warning: does not stop a pre-existing world manager.
     */
    private synchronized void startWorldManager() {
        if (mConfiguration.getSLAMSystem() == RobotManagerConfiguration.SLAMSystem.TANGO) {
            mWorldManager = new TangoWorldManager(mParent, mUserUuid, this);
            mWorldManager.setPromptLink(mPromptLink);
        } else {
            throw new IllegalArgumentException("Invalid SLAM type: "
                    + mConfiguration.getSLAMSystem());
        }
        onStateUpdate();
    }

    /**
     * Update the listeners' state.
     */
    @Override
    public void onStateUpdate() {
        synchronized (sListenerLock) {
            for (Listener listener : mListeners) {
                listener.onStateUpdate();
            }
        }
    }

    /**
     * Called to update the system.
     */
    private synchronized void doUpdate() {
        if (mManager == null) {
            RobotDriver robotDriver = null;
            String robotUuid = null;
            for (RobotDriver driver : mRobotDrivers) {
                robotUuid = driver.getRobotUuid();
                if (robotUuid != null) {
                    robotDriver = driver;
                    break;
                }
            }
            if (robotDriver != null) {
                if (!Strings.compare(mLastRobotUuid, robotUuid)) {
                    mLastRobotUuid = robotUuid;
                    onStateUpdate();
                }
                RobotModel m = robotDriver.getRobotModel();
                synchronized (mNavigationStateManager) {
                    mNavigationStateManager.setRobotUuid(robotUuid);
                    NavigationStateCommand latestState = mNavigationStateManager.getLatestState();
                    if (mNavigationStateManager.isSynchronized() && latestState != null
                            && (latestState.getIsMapping() || latestState.getMap() != null)) {
                        if (latestState.getIsMapping()) {
                            mWorldManager.waitShutdown();
                            mWorldManager = null;
                            String mappingRunUuid = UUID.randomUUID().toString();
                            mNavigationStateManager.setMappingRunUuid(mappingRunUuid);
                            mManager = new RobotManager(mParent, this, robotDriver, mappingRunUuid,
                                    new RobotSessionGlobals(mUserUuid, robotUuid, null, m), mConfiguration);
                        } else if (latestState.getMap() != null) {
                            DetailedWorld w = mWorldManager.getDetailedWorld(latestState.getMap());
                            if (w != null) {
                                mWorldManager.waitShutdown();
                                mWorldManager = null;
                                mManager = new RobotManager(mParent, this, robotDriver, null,
                                        new RobotSessionGlobals(mUserUuid, robotUuid, w, m), mConfiguration);
                            }
                        }
                        onStateUpdate();
                    }
                }
            }
        } else {
            NavigationStateCommand latestState = mNavigationStateManager.getLatestState();
            boolean terminate = mManager.shouldShutdown();
            if (latestState == null || (!latestState.getIsMapping() && latestState.getMap() == null)) {
                // Our latestState is gone, or it is a stop state
                Log.i(TAG, "Terminate for stop state");
                terminate = true;
            } else if (latestState.getIsMapping() && mManager.getMappingRunUuid() == null) {
                // We want to map and we are not
                Log.i(TAG, "Terminate because we are supposed to be mapping but are not");
                terminate = true;
            } else if (!latestState.getIsMapping() && latestState.getMap() != null
                    && mManager.getWorld() == null) {
                // We want to navigation and we are not
                Log.i(TAG, "Terminate because we want to be navigating and are not");
                terminate = true;
            } else if (!latestState.getIsMapping() && latestState.getMap() != null
                    && !latestState.getMap().equals(mManager.getWorld().getUuid())) {
                // We want to navigation, but have the wrong world
                Log.i(TAG, "Terminate because we are navigating on the wrong map");
                terminate = true;
            }

            // If we are mapping, and we do not want to terminate, we may want to save the map
            if (mManager.getMappingRunUuid() != null && !terminate) {
                String save = mNavigationStateManager.getMappingRunMapName(
                        mManager.getMappingRunUuid());
                if (save != null) {
                    mManager.saveMap(save);
                }
            }
            // If we are not terminated, then update the manager
            if (!terminate) {
                mManager.update();
                terminate = mManager.shouldShutdown();
            }

            if (terminate) {
                mManager.waitShutdown();
                mManager = null;
                mNavigationStateManager.setMappingRunUuid(null);
                startWorldManager();
            }
        }
    }

    /**
     * Gets an array of the worlds.
     *
     * @return A list of worlds, or null if the world could not be obtained.
     */
    public World[] getWorlds() {
        WorldManager worldManager = mWorldManager;
        if (worldManager != null) {
            return worldManager.getWorlds();
        }
        return null;
    }

    /**
     * Removes a world locally. This behavior will cause re-download of the world.
     *
     * @param world The world to remove.
     */
    public void removeWorld(World world) {
        WorldManager worldManager = mWorldManager;
        if (worldManager != null) {
            worldManager.removeWorld(world);
        }
    }

    /**
     * Gets if we can save the current world.
     *
     * @return True if the world can be saved.
     */
    public boolean canSaveWorld() {
        RobotManager manager = mManager;
        return manager != null && manager.canSaveWorld();
    }

    /**
     * Gets if the camera is localized.
     *
     * @return True if the camera is localized.
     */
    public boolean isLocalized() {
        RobotManager manager = mManager;
        if (manager != null) {
            Log.i(TAG, "isLocalized: " + manager.isLocalized());
            return manager.isLocalized();
        }
        return false;
    }

    /**
     * Shuts down the system.
     */
    @Override
    public void shutdown() {
        mTimedLoop.shutdown();
    }

    /**
     * Waits for the system to shutdown.
     */
    @Override
    public void waitShutdown() {
        mTimedLoop.waitShutdown();
    }

    /**
     * Starts the operation of the system.
     *
     * @param world The world.
     */
    public void startOperation(World world) {
        mNavigationStateManager.startOperation(world);
    }

    /**
     * Stops the operation of the system.
     */
    public void stopOperation() {
        mNavigationStateManager.stopOperation();
    }

    /**
     * Starts the system mapping.
     */
    public void startMapping() {
        mNavigationStateManager.startMapping();
    }

    /**
     * Saves the map.
     *
     * @param mapName The name of the map to save
     */
    public void saveMap(String mapName) {
        mNavigationStateManager.saveMap(mapName);
    }

    /**
     * Gets the uuid.
     *
     * @return The robot uuid.
     */
    public String getRobotUuid() {
        return mLastRobotUuid;
    }

    /**
     *  Nudges the robot if connected to the robot base.
     */
    public void nudgeRobot() {
        if (mManager != null) {
            mManager.nudgeRobot();
        }
    }
}
