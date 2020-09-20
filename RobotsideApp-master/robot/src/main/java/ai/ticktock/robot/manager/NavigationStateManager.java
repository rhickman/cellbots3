package ai.cellbots.robot.manager;

import android.content.Context;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;

import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.World;
import ai.cellbots.common.cloud.CloudPath;
import ai.cellbots.common.cloud.CloudSingletonMonitor;
import ai.cellbots.common.cloud.CloudStoredSingletonMonitor;
import ai.cellbots.common.cloud.Synchronizer;
import ai.cellbots.common.data.NavigationStateCommand;

/**
 * Downloads the current navigation state from the cloud.
 */
public class NavigationStateManager implements ThreadedShutdown {
    private static final String TAG = NavigationStateManager.class.getSimpleName();
    private final Context mParent; // The parent context.
    private final String mUserUuid; // The uuid of the user.
    private String mRobotUuid; // The uuid of the robot.
    private String mMappingRunUuid; // The current mapping run uuid.
    private String mSaveMapName; // The current name for the map to be saved to.

    private static final String COMMAND_SOURCE_LOCAL = "LOCAL";
    private static final String COMMAND_SOURCE_FIREBASE = "FIREBASE";
    private final Synchronizer<NavigationStateCommand> mSynchronizer
            = new Synchronizer<>(new String[]{COMMAND_SOURCE_LOCAL, COMMAND_SOURCE_FIREBASE});
    private final CloudStoredSingletonMonitor<NavigationStateCommand> mMonitor;
    private final CloudSingletonMonitor mSaveMapMonitor;

    /**
     * Manages the current navigation
     * @param parent The parent class.
     * @param userUuid The user uuid.
     */
    public NavigationStateManager(Context parent, String userUuid) {
        Log.i(TAG, "Create with user uuid: " + userUuid);
        mMonitor = new CloudStoredSingletonMonitor<>(parent, this,
                CloudPath.ROBOT_NAVIGATION_STATE_PATH, NavigationStateCommand.FACTORY);
        mUserUuid = userUuid;
        mParent = parent;
        mRobotUuid = null;
        mMappingRunUuid = null;
        mMonitor.update(userUuid, null);
        mSaveMapMonitor = new CloudSingletonMonitor(this, CloudPath.ROBOT_SAVE_MAP_PATH,
                new CloudSingletonMonitor.Listener() {
                    /**
                     * Called when a new save_map dictionary is available. Updates all the
                     * current mapName if available, and deletes all old data from the dictionary.
                     * @param dataSnapshot The new data element.
                     */
                    @Override
                    public void onDataSnapshot(DataSnapshot dataSnapshot) {
                        if (dataSnapshot == null) {
                            return;
                        }
                        if (mMappingRunUuid != null
                                && dataSnapshot.hasChild(mMappingRunUuid)
                                && dataSnapshot.child(mMappingRunUuid) != null) {
                            Object r = dataSnapshot.child(mMappingRunUuid).getValue();
                            if (r != null) {
                                mSaveMapName = r.toString();
                            }
                        }
                        for (DataSnapshot child : dataSnapshot.getChildren()) {
                            if (mMappingRunUuid == null || !child.getKey().equals(mMappingRunUuid)) {
                                child.getRef().removeValue();
                            }
                        }
                    }

                    /**
                     * Called when the save map listener is terminated. Does nothing.
                     */
                    @Override
                    public void afterListenerTerminated() {
                    }

                    /**
                     * Called when the save map listener is started. Does nothing.
                     */
                    @Override
                    public void beforeListenerStarted() {
                    }
                });
        mSaveMapMonitor.update(mUserUuid, mRobotUuid);
    }

    /**
     * Sets the robot uuid.
     * @param robotUuid The robot uuid.
     */
    public synchronized void setRobotUuid(String robotUuid) {
        Log.d(TAG, "Set robot uuid: " + robotUuid);
        mRobotUuid = robotUuid;
        mSaveMapMonitor.update(mUserUuid, mRobotUuid);
        mMonitor.update(mUserUuid, mRobotUuid);
    }

    /**
     * Shutdown the navigation state manager.
     */
    @Override
    public synchronized void shutdown() {
        Log.i(TAG, "Shutdown");
        mMonitor.shutdown();
        mSaveMapMonitor.shutdown();
    }

    /**
     * Wait for the navigation state manager to finish shutting down.
     */
    @Override
    public synchronized void waitShutdown() {
        shutdown();
    }

    /**
     * Get if the navigation manager has synchronized (gotten its first cloud update)
     */
    public synchronized boolean isSynchronized() {
        return mMonitor.isSynchronized();
    }

    /**
     * Get the latest navigation state of the system.
     * @return The navigation state of the system.
     */
    public synchronized NavigationStateCommand getLatestState() {
        mMonitor.update(mUserUuid, mRobotUuid);
        mSynchronizer.setValue(COMMAND_SOURCE_FIREBASE, mMonitor.getCurrent());
        return mSynchronizer.getNewestValue();
    }

    /**
     * Set the mapping run uuid.
     * @param uuid The mapping run uuid.
     */
    public synchronized void setMappingRunUuid(String uuid) {
        Log.i(TAG, "Set mapping run uuid: " + uuid);
        mMappingRunUuid = uuid;
        mSaveMapName = null;
    }

    /**
     * Get the name of the map for a given mapping run uuid.
     * @param uuid The mapping run uuid.
     * @return The name of the map.
     */
    public synchronized String getMappingRunMapName(String uuid) {
        if (uuid != null && uuid.equals(mMappingRunUuid)) {
            return mSaveMapName;
        }
        return null;
    }

    /**
     * Starts the operation of the system
     * @param world The world.
     */
    public void startOperation(World world) {
        if (world != null && world.getUuid() != null) {
            Log.i(TAG, "Local startOperation() on " + world.getUuid() + ": " + world.getName());
            mSynchronizer.setValue(COMMAND_SOURCE_LOCAL,
                    new NavigationStateCommand(world.getUuid(), false));
        } else {
            Log.w(TAG, "Local startOperation() on invalid world");
        }
    }

    /**
     * Starts the operation of the system
     */
    public void stopOperation() {
        Log.i(TAG, "Local stopOperation()");
        mSynchronizer.setValue(COMMAND_SOURCE_LOCAL, new NavigationStateCommand(null, false));
    }

    /**
     * Starts the system mapping
     */
    public void startMapping() {
        Log.i(TAG, "Local startMapping()");
        mSynchronizer.setValue(COMMAND_SOURCE_LOCAL, new NavigationStateCommand(null, true));
    }

    /**
     * Saves the map
     * @param mapName The name of the map to save
     */
    public void saveMap(String mapName) {
        Log.i(TAG, "Local saveMap() with name: " + mapName);
        String runUuid = mMappingRunUuid;
        String robot = mRobotUuid;
        if (runUuid != null && robot != null) {
            CloudPath.ROBOT_SAVE_MAP_PATH.getDatabaseReference(mUserUuid, robot)
                    .child(runUuid).setValue(mapName);
        }
    }
}
