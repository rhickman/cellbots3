package ai.cellbots.robot.control;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.PropertyName;
import com.google.firebase.database.ServerValue;

import ai.cellbots.common.cloud.CloudSingletonQueueMonitor;
import ai.cellbots.common.cloud.DataFactory;
import ai.cellbots.common.cloud.JsonSerializer;

/**
 * Animation to be executed by a robot.
 *
 * TODO(playerone) remove common/data/AnimationGoal.java
 */
public class RobotAnimation implements CloudSingletonQueueMonitor.QueueData,
        DataFactory.RobotUuidData, DataFactory.UserUuidData {
    public static final DataFactory<RobotAnimation> FACTORY = new DataFactory<> (
            new DataFactory.Listener<RobotAnimation>() {
                /**
                 * Creates a robot animation object.
                 *
                 * @param userUuid The user uuid. May be null unless the object implements UserUuidData.
                 * @param robotUuid The robot uuid. May be null unless the object implements RobotUuidData.
                 * @param dataSnapshot The DataSnapshot to be converted.
                 * @return The RobotAnimation object.
                 */
                @Override
                public RobotAnimation create(String userUuid, String robotUuid,
                                             DataSnapshot dataSnapshot) {
                    return getRobotAnimationFromFirebase(userUuid, robotUuid, dataSnapshot);
                }

                @Override
                public RobotAnimation create(String userUuid, String robotUuid, String json) {
                    RobotAnimation animation = JsonSerializer.fromJson(json, RobotAnimation.class);
                    return animation == null ? null : new RobotAnimation(userUuid, robotUuid, animation);
                }
            }, RobotAnimation.class, true, true);

    // User UUID.
    @Exclude
    private final String mUserUuid;

    // Robot UUID.
    @Exclude
    private final String mRobotUuid;

    // Animation ID.
    @PropertyName("id")
    private final String mId;

    // Is finished goal or not.
    @PropertyName("isfinished")
    private final boolean mIsFinished;

    // Timestamp of animation goal creation.
    @PropertyName("timestamp")
    private final Object mTimestamp;

    // True if this animation play is done.
    @PropertyName("isfinished")
    @Override
    public boolean isFinished() { return mIsFinished; }

    /**
     * Gets the key of the field for finished animation.
     *
     * @return String name of the key.
     */
    @Override
    public String getIsFinishedKey() { return "isfinished"; }

    /**
     * Gets the UUID of the user.
     *
     * @return The user's UUID.
     */
    @Exclude
    @Override
    public String getUserUuid() {
        return mUserUuid;
    }

    /**
     * Gets the UUID of the robot.
     *
     * @return The robot's UUID.
     */
    @Exclude
    @Override
    public String getRobotUuid() {
        return mRobotUuid;
    }

    /**
     * Gets the ID of the animation.
     *
     * @return The Animation id.
     */
    @PropertyName("id")
    public String getId() {
        return mId;
    }

    /**
     * Gets the value of the raw value of the timestamp. This could be an object for ServerValue,
     * or another strange object. Generally you should use getTimestamp().
     *
     * @return The raw timestamp value.
     */
    @PropertyName("timestamp")
    public Object getRawTimestamp() {
        return mTimestamp;
    }

    /**
     * Gets the value of the timestamp.
     *
     * @return The timestamp value.
     */
    @Exclude
    public long getTimestamp() {
        if (mTimestamp == null) {
            return 0;
        }
        if (mTimestamp instanceof Long || mTimestamp instanceof Integer) {
            return (long) mTimestamp;
        }
        if (mTimestamp instanceof String) {
            try {
                return Long.valueOf(mTimestamp.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Constructs RobotAnimation. We need this for serialization/deserialization from/to cloud.
     */
    public RobotAnimation() {
        mUserUuid = null;
        mRobotUuid = null;
        mId = null;
        mIsFinished = false;
        mTimestamp = ServerValue.TIMESTAMP;
    }

    /**
     * Constructs RobotAnimation.
     *
     * @param id Animation ID.
     */
    public RobotAnimation(String id) {
        mUserUuid = null;
        mRobotUuid = null;
        mId = id;
        mIsFinished = false;
        mTimestamp = ServerValue.TIMESTAMP;
    }

    /**
     * Constructs RobotAnimation from an existing animation.
     *
     * @param userUuid  The user uuid.
     * @param robotUuid  The robot uuid.
     * @param animation  Animation object.
     */
    public RobotAnimation(String userUuid, String robotUuid, RobotAnimation animation) {
        mUserUuid = userUuid;
        mRobotUuid = robotUuid;
        mId = animation.getId();
        mIsFinished = animation.isFinished();
        mTimestamp = animation.getRawTimestamp();
    }

    /**
     * Constructs RobotAnimation from an existing animation.
     *
     * @param animation  Animation object.
     * @param isFinished  True if the animation is finished.
     */
    public RobotAnimation(RobotAnimation animation, boolean isFinished) {
        mUserUuid = animation.getUserUuid();
        mRobotUuid = animation.getRobotUuid();
        mId = animation.getId();
        mIsFinished = isFinished;
        mTimestamp = animation.getRawTimestamp();
    }

    /**
     * Returns the animation object from firebase.
     *
     * @param userUuid  The user uuid.
     * @param robotUuid  The robot uuid.
     * @param dataSnapshot The firebase DataSnapshot.
     * @return The metadata object or null.
     */
    public static RobotAnimation getRobotAnimationFromFirebase(
            String userUuid, String robotUuid, DataSnapshot dataSnapshot) {
        return new RobotAnimation(userUuid, robotUuid, dataSnapshot.getValue(RobotAnimation.class));
    }
}
