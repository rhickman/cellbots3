package ai.cellbots.common.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.PropertyName;
import com.google.firebase.database.ServerValue;

import ai.cellbots.common.cloud.DataFactory;
import ai.cellbots.common.cloud.JsonSerializer;
import ai.cellbots.common.cloud.TimestampManager;

/**
 * Speed state command, containing whether the robot should be moving on its own.
 */
public class SpeedStateCommand implements TimestampManager.Timestamped,
        DataFactory.RobotUuidData, DataFactory.UserUuidData {

    public static final DataFactory<SpeedStateCommand> FACTORY = new DataFactory<>(
            new DataFactory.Listener<SpeedStateCommand>() {
                /**
                 * Creates the object.
                 * @param userUuid The user uuid. May be null unless the object implements UserUuidData.
                 * @param robotUuid The robot uuid. May be null unless the object implements RobotUuidData.
                 * @param dataSnapshot The DataSnapshot to be converted.
                 * @return The RobotSound object.
                 */
                @Override
                public SpeedStateCommand create(String userUuid, String robotUuid,
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
                public SpeedStateCommand create(String userUuid, String robotUuid, String string) {
                    SpeedStateCommand r = JsonSerializer.fromJson(string, SpeedStateCommand.class);
                    return r == null ? null : new SpeedStateCommand(r, userUuid, robotUuid);
                }
            }, SpeedStateCommand.class, true, true);

    @Exclude
    private final String mRobotUuid;
    @Exclude
    private final String mUserUuid;
    @Exclude
    private final TimestampManager mTimestampManager;
    @PropertyName("timestamp")
    private final Object mTimestamp;
    @PropertyName("started")
    private final boolean mStarted;


    /**
     * Create an empty speed state command.
     */
    public SpeedStateCommand() {
        mRobotUuid = null;
        mUserUuid = null;
        mTimestamp = ServerValue.TIMESTAMP;
        mStarted = false;
        mTimestampManager = new TimestampManager(this);
    }

    /**
     * Create a speed state command.
     * @param started If the robot is started or not.
     */
    public SpeedStateCommand(boolean started) {
        mRobotUuid = null;
        mUserUuid = null;
        mTimestamp = ServerValue.TIMESTAMP;
        mStarted = started;
        mTimestampManager = new TimestampManager(this);
    }

    /**
     * Create a command, merging in user and robot.
     * @param copy The command to copy.
     * @param userUuid The user uuid
     * @param robotUuid The robot uuid
     */
    public SpeedStateCommand(SpeedStateCommand copy, String userUuid, String robotUuid) {
        mRobotUuid = robotUuid;
        mUserUuid = userUuid;
        mTimestamp = copy.getRawTimestamp();
        mStarted = copy.isStarted();
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
     * Gets if the robot should be started.
     * @return True if the robot should be started.
     */
    @PropertyName("started")
    public boolean isStarted() {
        return mStarted;
    }

    /**
     * Convert to string.
     * @return String.
     */
    public String toString() {
        return "SpeedStateCommand('" + mUserUuid + "', '" + mRobotUuid + "', " + mStarted + ")";
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
    public static SpeedStateCommand fromFirebase(String userUuid, String robotUuid, DataSnapshot value) {
        return new SpeedStateCommand(value.getValue(SpeedStateCommand.class), userUuid, robotUuid);
    }

}
