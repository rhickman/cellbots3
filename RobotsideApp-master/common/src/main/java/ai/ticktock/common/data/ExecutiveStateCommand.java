package ai.cellbots.common.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.PropertyName;
import com.google.firebase.database.ServerValue;

import ai.cellbots.common.cloud.DataFactory;
import ai.cellbots.common.cloud.JsonSerializer;
import ai.cellbots.common.cloud.TimestampManager;

/**
 * The executive state, containing the state of the executive planner (e.g. random vs. stop).
 */
public class ExecutiveStateCommand implements TimestampManager.Timestamped,
        DataFactory.RobotUuidData, DataFactory.UserUuidData {
    public static final ExecutiveState DEFAULT_EXECUTIVE_MODE = ExecutiveState.STOP;

    /**
     * The executive system mode.
     */
    public enum ExecutiveState {
        RANDOM_DRIVER,
        STOP
    }

    public static final DataFactory<ExecutiveStateCommand> FACTORY = new DataFactory<>(
            new DataFactory.Listener<ExecutiveStateCommand>() {
                /**
                 * Creates the object.
                 * @param userUuid The user uuid. May be null unless the object implements UserUuidData.
                 * @param robotUuid The robot uuid. May be null unless the object implements RobotUuidData.
                 * @param dataSnapshot The DataSnapshot to be converted.
                 * @return The RobotSound object.
                 */
                @Override
                public ExecutiveStateCommand create(String userUuid, String robotUuid,
                        DataSnapshot dataSnapshot) {
                    return fromFirebase(userUuid, robotUuid, dataSnapshot);
                }

                /**
                 * Creates the object.
                 * @param userUuid The user uuid. May be null unless the object implements UserUuidData.
                 * @param robotUuid The robot uuid. May be null unless the object implements RobotUuidData.
                 * @param string The JSON string.
                 * @return The Teleop object.
                 */
                @Override
                public ExecutiveStateCommand create(String userUuid, String robotUuid, String string) {
                    ExecutiveStateCommand r = JsonSerializer.fromJson(string, ExecutiveStateCommand.class);
                    return r == null ? null : new ExecutiveStateCommand(r, userUuid, robotUuid);
                }
            }, ExecutiveStateCommand.class, true, true);

    @Exclude
    private final String mRobotUuid;
    @Exclude
    private final String mUserUuid;
    @Exclude
    private final TimestampManager mTimestampManager;
    @PropertyName("timestamp")
    private final Object mTimestamp;
    @PropertyName("executive_mode")
    private final ExecutiveState mExecutiveMode;

    /**
     * Create an empty executive state command.
     */
    public ExecutiveStateCommand() {
        mRobotUuid = null;
        mUserUuid = null;
        mTimestamp = ServerValue.TIMESTAMP;
        mExecutiveMode = null;
        mTimestampManager = new TimestampManager(this);
    }

    /**
     * Create an executive state object.
     * @param mode The executive mode.
     */
    public ExecutiveStateCommand(ExecutiveState mode) {
        mRobotUuid = null;
        mUserUuid = null;
        mTimestamp = ServerValue.TIMESTAMP;
        mExecutiveMode = mode;
        mTimestampManager = new TimestampManager(this);
    }

    /**
     * Create a command, merging in user and robot.
     * @param copy The command to copy.
     * @param userUuid The user uuid
     * @param robotUuid The robot uuid
     */
    public ExecutiveStateCommand(ExecutiveStateCommand copy, String userUuid, String robotUuid) {
        mRobotUuid = robotUuid;
        mUserUuid = userUuid;
        mTimestamp = copy.getRawTimestamp();
        mExecutiveMode = copy.getExecutiveMode();
        mTimestampManager = new TimestampManager(this, copy);
    }

    /**
     * Gets the name of the robot.
     * @return The robot.
     */
    @Exclude
    @Override
    public String getRobotUuid() {
        return mRobotUuid;
    }

    /**
     * Gets the name of the user.
     * @return The user.
     */
    @Exclude
    @Override
    public String getUserUuid() {
        return mUserUuid;
    }

    /**
     * Gets the mode for the executive planner.
     * @return The mode for the executive planner.
     */
    @PropertyName("executive_mode")
    public ExecutiveState getExecutiveMode() {
        return mExecutiveMode;
    }

    /**
     * Gets the value of the timestamp.
     * @return The timestamp value.
     */
    @Exclude
    @Override
    public long getTimestamp() {
        return mTimestampManager.getTimestamp();
    }

    /**
     * Gets the value of the raw value of the timestamp. This could be an object for ServerValue,
     * or another strange object. Generally you should use getTimestamp();
     * @return The timestamp value.
     */
    @PropertyName("timestamp")
    @Override
    public Object getRawTimestamp() {
        return mTimestamp;
    }

    /**
     * Create a executive state command object from firebase.
     * @param value The firebase object to copy
     * @param userUuid The user uuid
     * @param robotUuid The robot uuid
     */
    public static ExecutiveStateCommand fromFirebase(String userUuid, String robotUuid, DataSnapshot value) {
        return new ExecutiveStateCommand(value.getValue(ExecutiveStateCommand.class), userUuid, robotUuid);
    }

    /**
     * Convert to string.
     * @return String.
     */
    public String toString() {
        return "ExecutiveStateCommand('" + mUserUuid + "', '" + mRobotUuid + "', " + mExecutiveMode + ")";
    }
}
