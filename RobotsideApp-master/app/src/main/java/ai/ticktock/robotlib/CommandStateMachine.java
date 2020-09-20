package ai.cellbots.robotlib;

import android.content.Context;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;

import ai.cellbots.common.World;
import ai.cellbots.common.cloud.CloudPath;
import ai.cellbots.common.cloud.CloudSingletonMonitor;
import ai.cellbots.common.cloud.CloudStoredSingletonMonitor;
import ai.cellbots.common.cloud.Synchronizer;
import ai.cellbots.common.data.ExecutiveStateCommand;
import ai.cellbots.common.data.NavigationStateCommand;
import ai.cellbots.common.data.SpeedStateCommand;

/**
 * Manages the command state of the robot.
 * Commands: manage the navigation command of the robot
 * Save map: manage the mapping session id and saving of those maps
 * Executive state: manage the executive mode
 * Speed state: manage the start/stop logic
 */
public class CommandStateMachine {
    private static final String TAG = CommandStateMachine.class.getSimpleName();
    private static final String COMMAND_SOURCE_LOCAL = "LOCAL";
    private static final String COMMAND_SOURCE_FIREBASE = "FIREBASE";
    private final Synchronizer<NavigationStateCommand> mCommandSynchronizer
            = new Synchronizer<>(new String[]{COMMAND_SOURCE_LOCAL, COMMAND_SOURCE_FIREBASE});
    private final CloudStoredSingletonMonitor<NavigationStateCommand> mCommandMonitor;

    private final Synchronizer<ExecutiveStateCommand> mExecutiveStateSynchronizer
            = new Synchronizer<>(new String[]{COMMAND_SOURCE_LOCAL, COMMAND_SOURCE_FIREBASE});
    private final CloudStoredSingletonMonitor<ExecutiveStateCommand>  mExecutiveStateMonitor;

    private final Synchronizer<SpeedStateCommand> mSpeedStateSynchronizer
            = new Synchronizer<>(new String[]{COMMAND_SOURCE_LOCAL, COMMAND_SOURCE_FIREBASE});
    private final CloudStoredSingletonMonitor<SpeedStateCommand>  mSpeedStateMonitor;

    private final CloudSingletonMonitor mSaveMapMonitor;

    private final CloudSoundManager mSoundManager;

    private Context mParent;

    private boolean mStarted = false;
    private ExecutiveStateCommand.ExecutiveState mExecutiveMode = null;
    private String mMappingRunId;
    private String mMapName;

    /**
     * Create a command state manager.
     * @param parent The parent object for logging.
     * @param soundManager The sound manager, for playing the start and stop sounds.
     */
    public CommandStateMachine(Context parent, CloudSoundManager soundManager) {
        mParent = parent;
        mCommandMonitor = new CloudStoredSingletonMonitor<>(parent, this,
                CloudPath.ROBOT_NAVIGATION_STATE_PATH, NavigationStateCommand.FACTORY);
        mExecutiveStateMonitor = new CloudStoredSingletonMonitor<>(parent, this,
                CloudPath.ROBOT_EXECUTIVE_STATE_PATH, ExecutiveStateCommand.FACTORY);
        mSpeedStateMonitor = new CloudStoredSingletonMonitor<>(parent, this,
                CloudPath.ROBOT_SPEED_STATE_PATH, SpeedStateCommand.FACTORY);
        mSoundManager = soundManager;
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
                        if (mMappingRunId != null) {
                            if (!dataSnapshot.hasChild(mMappingRunId)) {
                                return;
                            }
                            if (dataSnapshot.child(mMappingRunId) == null) {
                                return;
                            }
                            Object r = dataSnapshot.child(mMappingRunId).getValue();
                            if (r != null) {
                                mMapName = r.toString();
                            }
                        }
                        for (DataSnapshot child : dataSnapshot.getChildren()) {
                            if (mMappingRunId == null || !child.getKey().equals(mMappingRunId)) {
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
        onNewRobotConnected(null, null);
    }

    /**
     * Sets the current mapping run ID of the robot. This must be complete before the mapping run
     * ID is written to the robot state dictionary to avoid race conditions.
     * @param mappingRunId The new mapping run ID
     */
    public synchronized void setMappingRunId(String mappingRunId) {
        mMappingRunId = mappingRunId;
        mMapName = null;
    }

    /**
     * Gets the map name for a given mapping run.
     * @param mappingRunId The mapping run ID to check
     * @return The map name set by the companion or null if not set yet
     */
    public synchronized String getMapNameForRunId(String mappingRunId) {
        if (mMappingRunId != null && mMappingRunId.equals(mappingRunId)) {
            return mMapName;
        }
        return null;
    }

    /**
     * Should be called when switching robots.
     * @param user The user uuid.
     * @param robot The new robot uuid.
     */
    public synchronized void onNewRobotConnected(String user, String robot) {
        Log.i(TAG, "onNewRobotConnected(" + user + ", " + robot + ")");
        mStarted = false;
        mExecutiveMode = ExecutiveStateCommand.DEFAULT_EXECUTIVE_MODE;

        ExecutiveStateCommand executiveCommand
                = new ExecutiveStateCommand(ExecutiveStateCommand.DEFAULT_EXECUTIVE_MODE);
        SpeedStateCommand speedCommand = new SpeedStateCommand(true);

        mExecutiveStateSynchronizer.setValue(COMMAND_SOURCE_LOCAL, executiveCommand);
        mSpeedStateSynchronizer.setValue(COMMAND_SOURCE_LOCAL, speedCommand);

        if (user != null && robot != null) {
            CloudPath.ROBOT_SPEED_STATE_PATH.getDatabaseReference(user, robot).setValue(speedCommand);
            CloudPath.ROBOT_EXECUTIVE_STATE_PATH.getDatabaseReference(user, robot).setValue(executiveCommand);
        }
    }

    /**
     * Start mapping the world.
     */
    public synchronized void startMapping() {
        mCommandSynchronizer.setValue(COMMAND_SOURCE_LOCAL, new NavigationStateCommand(null, true));
    }

    /**
     * Start navigating over a world.
     *
     * @param world The world to navigate.
     */
    public synchronized void startOperation(final World world) {
        if (world != null && world.getUuid() != null) {
            mCommandSynchronizer.setValue(COMMAND_SOURCE_LOCAL,
                    new NavigationStateCommand(world.getUuid(), false));
        }
    }

    /**
     * Stop navigating or mapping operations.
     */
    public synchronized void stopOperations() {
        mCommandSynchronizer.setValue(COMMAND_SOURCE_LOCAL,
                new NavigationStateCommand(null, false));
    }

    /**
     * Should be called when a robot is disconnected.
     */
    public synchronized void onRobotDisconnected() {
        Log.i(TAG, "onRobotDisconnected()");
        // Clear the local command, as it is no longer valid
        mCommandSynchronizer.setValue(COMMAND_SOURCE_LOCAL, null);
    }

    /**
     * Should be called every update.
     * @param user The user uuid
     * @param robot The robot uuid
     * @return The current navigation state command, or null if none is set
     */
    public synchronized NavigationStateCommand update(String user, String robot) {
        mCommandMonitor.update(user, robot);
        mCommandSynchronizer.setValue(COMMAND_SOURCE_FIREBASE, mCommandMonitor.getCurrent());
        mExecutiveStateMonitor.update(user, robot);
        mExecutiveStateSynchronizer.setValue(COMMAND_SOURCE_FIREBASE, mExecutiveStateMonitor.getCurrent());
        mSpeedStateMonitor.update(user, robot);
        mSpeedStateSynchronizer.setValue(COMMAND_SOURCE_FIREBASE, mSpeedStateMonitor.getCurrent());
        mSaveMapMonitor.update(user, robot);

        boolean lastStarted = mStarted;

        mStarted = mSpeedStateSynchronizer.getNewestValue() != null
                && mSpeedStateSynchronizer.getNewestValue().isStarted();

        if (lastStarted != mStarted) {
            if (mStarted) {
                FirebaseAnalyticsEvents.getInstance().reportRobotActiveEvent(mParent);
                mSoundManager.playCommandSound(SoundManager.CommandSound.START);
            } else {
                FirebaseAnalyticsEvents.getInstance().reportRobotPausedEvent(mParent);
                mSoundManager.playCommandSound(SoundManager.CommandSound.STOP);
            }
        }

        if (mExecutiveStateSynchronizer.getNewestValue() != null
                && mExecutiveStateSynchronizer.getNewestValue().getExecutiveMode() != null) {
            mExecutiveMode = mExecutiveStateSynchronizer.getNewestValue().getExecutiveMode();
        } else {
            mExecutiveMode = ExecutiveStateCommand.DEFAULT_EXECUTIVE_MODE;
        }

        if (!mCommandMonitor.isSynchronized() && user != null && robot != null) {
            return null;
        }

        // If the local commands are updated, and then we write them to firebase.
        // TODO there is a race condition if a local change and a firebase change happen at the same time
        if (mCommandSynchronizer.getValue(COMMAND_SOURCE_LOCAL) != null
                && (mCommandSynchronizer.getValue(COMMAND_SOURCE_FIREBASE) == null
                || mCommandSynchronizer.getValue(COMMAND_SOURCE_FIREBASE).getTimestamp()
                < mCommandSynchronizer.getValue(COMMAND_SOURCE_LOCAL).getTimestamp())
                && user != null && robot != null) {
            NavigationStateCommand result = mCommandSynchronizer.getValue(COMMAND_SOURCE_LOCAL);
            if (mCommandSynchronizer.getValue(COMMAND_SOURCE_FIREBASE) != null) {
                result = new NavigationStateCommand(
                        mCommandSynchronizer.getValue(COMMAND_SOURCE_FIREBASE),
                        mCommandSynchronizer.getValue(COMMAND_SOURCE_LOCAL));
            }
            CloudPath.ROBOT_NAVIGATION_STATE_PATH.getDatabaseReference(user, robot).setValue(result);
            mCommandSynchronizer.setValue(COMMAND_SOURCE_FIREBASE, result);
        }
        return mCommandSynchronizer.getNewestValue();
    }

    /**
     * Gets the executive planner mode.
     * @return The executive planner mode
     */
    public synchronized ExecutiveStateCommand.ExecutiveState getExecutiveMode() {
        return mExecutiveMode;
    }

    /**
     * Gets the speed state.
     * @return True if the robot should run, otherwise false.
     */
    public synchronized boolean getSpeedState() {
        return mStarted;
    }

    /**
     * Shuts down the system.
     */
    public synchronized void shutdown() {
        mCommandMonitor.shutdown();
        mExecutiveStateMonitor.shutdown();
        mSpeedStateMonitor.shutdown();
        mSaveMapMonitor.shutdown();
    }
}
