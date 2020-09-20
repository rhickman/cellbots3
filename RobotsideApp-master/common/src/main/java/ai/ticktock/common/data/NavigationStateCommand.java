package ai.cellbots.common.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.PropertyName;
import com.google.firebase.database.ServerValue;

import ai.cellbots.common.cloud.DataFactory;
import ai.cellbots.common.cloud.JsonSerializer;
import ai.cellbots.common.cloud.TimestampManager;

/**
 * The robot state command, containing the map and mapping state.
 */
public class NavigationStateCommand implements TimestampManager.Timestamped,
        DataFactory.RobotUuidData, DataFactory.UserUuidData {

    public static final DataFactory<NavigationStateCommand> FACTORY = new DataFactory<>(
            new DataFactory.Listener<NavigationStateCommand>() {
                /**
                 * Creates the object.
                 * @param userUuid The user uuid. May be null unless the object implements UserUuidData.
                 * @param robotUuid The robot uuid. May be null unless the object implements RobotUuidData.
                 * @param dataSnapshot The DataSnapshot to be converted.
                 * @return The RobotSound object.
                 */
                @Override
                public NavigationStateCommand create(String userUuid, String robotUuid,
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
                public NavigationStateCommand create(String userUuid, String robotUuid, String string) {
                    NavigationStateCommand r = JsonSerializer.fromJson(string, NavigationStateCommand.class);
                    return r == null ? null : new NavigationStateCommand(r, userUuid, robotUuid);
                }
            }, NavigationStateCommand.class, true, true);

    @Exclude
    private final String mRobotUuid;
    @Exclude
    private final String mUserUuid;
    @Exclude
    private final TimestampManager mTimestampManager;
    @PropertyName("timestamp")
    private final Object mTimestamp;
    @PropertyName("map")
    private final String mMap;
    @PropertyName("is_mapping")
    private final boolean mIsMapping;

    /**
     * Create an empty robot state command.
     */
    public NavigationStateCommand() {
        mRobotUuid = null;
        mUserUuid = null;
        mTimestamp = ServerValue.TIMESTAMP;
        mMap = null;
        mIsMapping = false;
        mTimestampManager = new TimestampManager(this);
    }

    /**
     * Create a mapping command.
     * @param map The map to navigate upon.
     * @param isMapping True if we are mapping.
     */
    public NavigationStateCommand(String map, boolean isMapping) {
        mRobotUuid = null;
        mUserUuid = null;
        mTimestamp = ServerValue.TIMESTAMP;
        mMap = map;
        mIsMapping = isMapping;
        mTimestampManager = new TimestampManager(this);
    }

    /**
     * Merge a local command.
     * @param copy The command to copy from remote.
     * @param local The local command to merge in.
     */
    public NavigationStateCommand(NavigationStateCommand copy, NavigationStateCommand local) {
        mRobotUuid = copy.getRobotUuid();
        mUserUuid = copy.getUserUuid();
        mTimestamp = local.getRawTimestamp();
        mMap = local.getMap();
        mIsMapping = local.getIsMapping();
        mTimestampManager = new TimestampManager(this, local);
    }

    /**
     * Create a command, merging in user and robot.
     * @param copy The command to copy.
     * @param userUuid The user uuid
     * @param robotUuid The robot uuid
     */
    public NavigationStateCommand(NavigationStateCommand copy, String userUuid, String robotUuid) {
        mRobotUuid = robotUuid;
        mUserUuid = userUuid;
        mTimestamp = copy.getRawTimestamp();
        mMap = copy.getMap();
        mIsMapping = copy.getIsMapping();
        mTimestampManager = new TimestampManager(this, copy);
    }

    /**
     * Gets the target map uuid.
     * @return The map uuid, or null if we want to stop.
     */
    @PropertyName("map")
    public String getMap() {
        return mMap;
    }

    /**
     * Gets if we should be mapping.
     * @return True if we should be mapping.
     */
    @PropertyName("is_mapping")
    public boolean getIsMapping() {
        return mIsMapping;
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
     * Create a state command object from firebase.
     * @param value The firebase object to copy
     * @param userUuid The user uuid
     * @param robotUuid The robot uuid
     */
    public static NavigationStateCommand fromFirebase(String userUuid, String robotUuid, DataSnapshot value) {
        return new NavigationStateCommand(value.getValue(NavigationStateCommand.class), userUuid, robotUuid);
    }

    /**
     * Convert to string.
     * @return String.
     */
    public String toString() {
        return "NavigationStateCommand('" + mUserUuid + "', '" + mRobotUuid + "', '" + mMap + "', "
                + mIsMapping + ")";
    }
}
