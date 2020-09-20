package ai.cellbots.common;

import android.content.Context;

import ai.cellbots.common.cloud.CloudPath;
import ai.cellbots.common.cloud.CloudTypedSingletonMonitor;
import ai.cellbots.common.data.ExecutiveStateCommand;
import ai.cellbots.common.data.NavigationStateCommand;
import ai.cellbots.common.data.Robot;
import ai.cellbots.common.data.SpeedStateCommand;

/**
 * Cloud connector for the companion app.
 */
public class CloudConnector {
    private final CloudTypedSingletonMonitor<ExecutiveStateCommand> mExecutiveCommandMonitor;
    private final CloudTypedSingletonMonitor<SpeedStateCommand> mSpeedCommandMonitor;
    private final Context mParent;
    private boolean mShutdown = false;
    private final Listener mListener;
    private String mUserUuid;

    /**
     * The listener for the cloud connector.
     */
    public interface Listener {
        /**
         * Called when a new executive command is generated.
         * @param command The command state, potentially null.
         * @param sync True if the command state is a recent update from the cloud.
         */
        void onExecutiveCommand(ExecutiveStateCommand command, boolean sync);
        /**
         * Called when a new speed command is generated.
         * @param command The command state, potentially null.
         * @param sync True if the command state is a recent update from the cloud.
         */
        void onSpeedCommand(SpeedStateCommand command, boolean sync);
    }

    /**
     * Create the cloud connector.
     * @param parent Parent for synchronization and logging.
     * @param listener The listener object for state feedback.
     */
    public CloudConnector(Context parent, Listener listener) {
        mParent = parent;
        mListener = listener;
        mListener.onExecutiveCommand(null, false);
        mExecutiveCommandMonitor
                = new CloudTypedSingletonMonitor<>(mParent, mParent,
                CloudPath.ROBOT_EXECUTIVE_STATE_PATH,
                new CloudTypedSingletonMonitor.Listener<ExecutiveStateCommand>() {

                    @Override
                    public void onData(ExecutiveStateCommand data) {
                        mListener.onExecutiveCommand(data, true);
                    }

                    @Override
                    public void afterListenerTerminated() {
                        mListener.onExecutiveCommand(null, false);
                    }

                    @Override
                    public void beforeListenerStarted() {
                        mListener.onExecutiveCommand(null, false);
                    }
                },  ExecutiveStateCommand.FACTORY);
        mListener.onSpeedCommand(null, false);
        mSpeedCommandMonitor
                = new CloudTypedSingletonMonitor<>(mParent, mParent,
                CloudPath.ROBOT_SPEED_STATE_PATH,
                new CloudTypedSingletonMonitor.Listener<SpeedStateCommand>() {

                    @Override
                    public void onData(SpeedStateCommand data) {
                        mListener.onSpeedCommand(data, true);
                    }

                    @Override
                    public void afterListenerTerminated() {
                        mListener.onExecutiveCommand(null, false);
                    }

                    @Override
                    public void beforeListenerStarted() {
                        mListener.onExecutiveCommand(null, false);
                    }
                },  SpeedStateCommand.FACTORY);
    }

    /**
     * Called to update the cloud connector.
     * @param user The user uuid.
     * @param robot The robot uuid.
     */
    public void update(String user, String robot) {
        synchronized (mParent) {
            mUserUuid = user;
            mExecutiveCommandMonitor.update(user, robot);
            mSpeedCommandMonitor.update(user, robot);
        }
    }

    /**
     * Called to update the cloud connector.
     * @param user The user uuid.
     * @param robot The Robot object.
     */
    public void updateWithRobot(String user, Robot robot) {
        if (robot != null) {
            update(user, robot.uuid);
        } else {
            update(user, null);
        }
    }

    /**
     * Shutdown the CloudConnector.
     */
    public void shutdown() {
        synchronized (mParent) {
            if (mShutdown) {
                return;
            }
            mShutdown = true;
            mExecutiveCommandMonitor.shutdown();
            mSpeedCommandMonitor.shutdown();
        }
    }

    /**
     * Set the robot's executive command.
     * @param robot The robot
     * @param state The command
     */
    public void setRobotExecutiveCommand(Robot robot, ExecutiveStateCommand state) {
        synchronized (mParent) {
            if (robot != null && robot.uuid != null && mUserUuid != null) {
                CloudPath.ROBOT_EXECUTIVE_STATE_PATH.getDatabaseReference(mUserUuid, robot.uuid)
                        .setValue(state);
            }
        }
    }

    /**
     * Set the robot's speed command.
     * @param robot The robot
     * @param state The command
     */
    public void setRobotSpeedCommand(Robot robot, SpeedStateCommand state) {
        synchronized (mParent) {
            if (robot != null && robot.uuid != null && mUserUuid != null) {
                CloudPath.ROBOT_SPEED_STATE_PATH.getDatabaseReference(mUserUuid, robot.uuid)
                        .setValue(state);
            }
        }
    }

    /**
     * Set the robot's navigation command.
     * @param robot The robot
     * @param state The command
     */
    public void setRobotNavigationCommand(Robot robot, NavigationStateCommand state) {
        synchronized (mParent) {
            if (robot != null && robot.uuid != null && mUserUuid != null) {
                CloudPath.ROBOT_NAVIGATION_STATE_PATH.getDatabaseReference(mUserUuid, robot.uuid)
                        .setValue(state);
            }
        }
    }

    /**
     * Save a map to the robot.
     * @param robot The robot
     * @param name The name of the map to save.
     */
    public void saveRobotMap(Robot robot, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        synchronized (mParent) {
            if (robot != null && robot.uuid != null && mUserUuid != null && robot.mMappingRunId != null) {
                CloudPath.ROBOT_SAVE_MAP_PATH.getDatabaseReference(mUserUuid, robot.uuid)
                        .child(robot.mMappingRunId).setValue(name);
            }
        }
    }
}
