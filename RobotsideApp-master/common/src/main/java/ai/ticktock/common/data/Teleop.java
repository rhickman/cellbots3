package ai.cellbots.common.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.PropertyName;

import ai.cellbots.common.cloud.DataFactory;
import ai.cellbots.common.cloud.JsonSerializer;
import ai.cellbots.common.cloud.TimestampManager;

/**
 * Contains the teleop data for a robot.
 */
// TODO (playerfive): Rename this class - It's not used for Teleop only
public class Teleop implements DataFactory.UserUuidData, DataFactory.RobotUuidData,
        TimestampManager.Timestamped {
    public static final DataFactory<Teleop> FACTORY = new DataFactory<>(
            new DataFactory.Listener<Teleop>() {
                /**
                 * Creates the object.
                 * @param userUuid The user uuid. May be null unless the object implements UserUuidData.
                 * @param robotUuid The robot uuid. May be null unless the object implements RobotUuidData.
                 * @param dataSnapshot The DataSnapshot to be converted.
                 * @return The Teleop object.
                 */
                @Override
                public Teleop create(String userUuid, String robotUuid,
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
                public Teleop create(String userUuid, String robotUuid, String string) {
                    Teleop r = JsonSerializer.fromJson(string, Teleop.class);
                    return r == null ? null : new Teleop(r, userUuid, robotUuid);
                }
            }, Teleop.class, true, true);

    // Ignore messages older than this many milliseconds
    public final static long TIMEOUT = 1000;

    @PropertyName("vx")
    private final double mVx;
    @PropertyName("vy")
    private final double mVy;
    @PropertyName("vz")
    private final double mVz;
    @PropertyName("rx")
    private final double mRx;
    @PropertyName("ry")
    private final double mRy;
    @PropertyName("rz")
    private final double mRz;
    @PropertyName("action1")
    private final boolean mAction1;
    @PropertyName("action2")
    private final boolean mAction2;
    @Exclude
    private final String mUserUuid;
    @Exclude
    private final String mRobotUuid;

    @PropertyName("timestamp")
    private final Object mTimestamp;

    @Exclude
    private final TimestampManager mTimestampManager;

    /**
     * Get the name of the user.
     * @return The user.
     */
    @Exclude
    public String getUserUuid() {
        return mUserUuid;
    }
    /**
     * Get the name of the robot.
     * @return The robot.
     */
    @Exclude
    public String getRobotUuid() {
        return mRobotUuid;
    }
    /**
     * Get X velocity goal.
     * @return The X velocity.
     */
    @PropertyName("vx")
    public double getVx() {
        return mVx;
    }
    /**
     * Get Y velocity goal.
     * @return The Y velocity.
     */
    @PropertyName("vy")
    public double getVy() {
        return mVy;
    }
    /**
     * Get Z velocity goal.
     * @return The Z velocity.
     */
    @PropertyName("vz")
    public double getVz() {
        return mVz;
    }
    /**
     * Get X rotation goal.
     * @return The X rotation.
     */
    @PropertyName("rx")
    public double getRx() {
        return mRx;
    }
    /**
     * Get Y rotation goal.
     * @return The Y rotation.
     */
    @PropertyName("ry")
    public double getRy() {
        return mRy;
    }
    /**
     * Get Z rotation goal.
     * @return The Z rotation.
     */
    @PropertyName("rz")
    public double getRz() {
        return mRz;
    }

    /**
     * Get the value of the raw value of the timestamp. This could be an object for ServerValue,
     * or another strange object. Generally you should use getTimestamp();
     * @return The timestamp value.
     */
    @PropertyName("timestamp")
    public Object getRawTimestamp() {
        return mTimestamp;
    }

    /**
     * Get action1 setting.
     * @return True if we should turn on the action1.
     */
    @PropertyName("action1")
    public boolean getAction1() {
        return mAction1;
    }
    /**
     * Get action2 setting.
     * @return True if we should turn on the action2.
     */
    @PropertyName("action2")
    public boolean getAction2() {
        return mAction2;
    }

    /**
     * Get the value of the timestamp.
     * @return The timestamp value.
     */
    @Exclude
    public long getTimestamp() {
        return mTimestampManager.getTimestamp();
    }

    /**
     * Create teleop object.
     */
    public Teleop() {
        this(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Create teleop object.
     * @param vx The X velocity.
     * @param vy The Y velocity.
     * @param vz The Z velocity.
     * @param rx The X rotation.
     * @param ry The Y rotation.
     * @param rz The Z rotation.
     */
    public Teleop(double vx, double vy, double vz, double rx, double ry, double rz) {
        mVx = vx;
        mVy = vy;
        mVz = vz;
        mRx = rx;
        mRy = ry;
        mRz = rz;
        mTimestamp = TimestampManager.getCurrentTimestamp();
        mRobotUuid = null;
        mUserUuid = null;
        mAction1 = false;
        mAction2 = false;
        mTimestampManager = new TimestampManager(this);
    }

    /**
     * Create teleop object.
     * @param root The teleop object to copy
     * @param scaleVelocity The velocity scaling factor.
     * @param scaleAngular The angular velocity scaling factor.
     */
    public Teleop(Teleop root, double scaleVelocity, double scaleAngular) {
        this(root, root.getVx() * scaleVelocity, root.getVy() * scaleVelocity,
                root.getVz() * scaleVelocity, root.getRx() * scaleAngular,
                root.getRy() * scaleAngular, root.getRz() * scaleAngular);
    }

    /**
     * Create teleop object.
     * @param root The teleop object to copy
     * @param vx The X velocity.
     * @param vy The Y velocity.
     * @param vz The Z velocity.
     * @param rx The X rotation.
     * @param ry The Y rotation.
     * @param rz The Z rotation.
     */
    public Teleop(Teleop root, double vx, double vy, double vz, double rx, double ry, double rz) {
        mVx = vx;
        mVy = vy;
        mVz = vz;
        mRx = rx;
        mRy = ry;
        mRz = rz;
        mTimestamp = root.getRawTimestamp();
        mRobotUuid = root.getRobotUuid();
        mUserUuid = root.getUserUuid();
        mAction1 = root.getAction1();
        mAction2 = root.getAction2();
        mTimestampManager = new TimestampManager(this, root);
    }

    /**
     * Create teleop object copy with uuids
     * @param root The teleop object to copy
     * @param userUuid The user uuid
     * @param robotUuid The robot uuid
     */
    public Teleop(Teleop root, String userUuid, String robotUuid) {
        mVx = root.getVx();
        mVy = root.getVy();
        mVz = root.getVz();
        mRx = root.getRx();
        mRy = root.getRy();
        mRz = root.getRz();
        mTimestamp = root.getRawTimestamp();
        mRobotUuid = robotUuid;
        mUserUuid = userUuid;
        mAction1 = root.getAction1();
        mAction2 = root.getAction2();
        mTimestampManager = new TimestampManager(this, root);
    }

    /**
     * Create teleop resetting the path
     * @param root The teleop object to copy
     * @param timestamp The new timestamp
     */
    public Teleop(Teleop root, long timestamp) {
        mVx = root.getVx();
        mVy = root.getVy();
        mVz = root.getVz();
        mRx = root.getRx();
        mRy = root.getRy();
        mRz = root.getRz();
        mTimestamp = timestamp;
        mRobotUuid = root.getRobotUuid();
        mUserUuid = root.getUserUuid();
        mAction1 = root.getAction1();
        mAction2 = root.getAction2();
        mTimestampManager = new TimestampManager(this, root);
    }

    /**
     * Create teleop object copy with uuids
     * @param root The teleop object to copy
     * @param action1 The action 1 setting
     * @param action2 The action 2 setting
     */
    public Teleop(Teleop root, boolean action1, boolean action2) {
        mVx = root.getVx();
        mVy = root.getVy();
        mVz = root.getVz();
        mRx = root.getRx();
        mRy = root.getRy();
        mRz = root.getRz();
        mTimestamp = root.getRawTimestamp();
        mRobotUuid = root.getRobotUuid();
        mUserUuid = root.getUserUuid();
        mAction1 = action1;
        mAction2 = action2;
        mTimestampManager = new TimestampManager(this, root);
    }

    /**
     * Create a teleop object from firebase.
     * @param value The firebase object to copy
     * @param userUuid The user uuid
     * @param robotUuid The robot uuid
     */
    public static Teleop fromFirebase(String userUuid, String robotUuid, DataSnapshot value) {
        return new Teleop(value.getValue(Teleop.class), userUuid, robotUuid);
    }

    /**
     * Returns a string value.
     * @return The the string converted.
     */
    public String toString() {
        return "Teleop(" + getTimestamp() + ", " +
                getUserUuid() + ", " +
                getRobotUuid() + ", " +
                getVx() + ", " +
                getVy() + ", " +
                getVz() + ", " +
                getRx() + ", " +
                getRy() + ", " +
                getRz() + ", " +
                getAction1() + ", " +
                getAction2() + ")";
    }
}
