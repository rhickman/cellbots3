package ai.cellbots.common.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;
import com.google.firebase.database.ServerValue;

import ai.cellbots.common.cloud.CloudSingletonQueueMonitor;
import ai.cellbots.common.cloud.DataFactory;
import ai.cellbots.common.cloud.JsonSerializer;

/**
 * The sound to be played by a robot directly from the companion app.
 */
@IgnoreExtraProperties
public class RobotSound implements CloudSingletonQueueMonitor.QueueData, DataFactory.RobotUuidData,
        DataFactory.UserUuidData {
    public static final DataFactory<RobotSound> FACTORY = new DataFactory<>(
            new DataFactory.Listener<RobotSound>() {
                /**
                 * Creates the object.
                 * @param userUuid The user uuid. May be null unless the object implements UserUuidData.
                 * @param robotUuid The robot uuid. May be null unless the object implements RobotUuidData.
                 * @param dataSnapshot The DataSnapshot to be converted.
                 * @return The RobotSound object.
                 */
                @Override
                public RobotSound create(String userUuid, String robotUuid,
                        DataSnapshot dataSnapshot) {
                    return fromFirebase(userUuid, robotUuid, dataSnapshot);
                }

                /**
                 * Creates the object.
                 * @param userUuid The user uuid. May be null unless the object implements UserUuidData.
                 * @param robotUuid The robot uuid. May be null unless the object implements RobotUuidData.
                 * @param string The JSON string.
                 * @return The RobotSound object.
                 */
                @Override
                public RobotSound create(String userUuid, String robotUuid, String string) {
                    RobotSound r = JsonSerializer.fromJson(string, RobotSound.class);
                    return r == null ? null : new RobotSound(userUuid, robotUuid, r);
                }
            }, RobotSound.class, true, true);

    @PropertyName("id")
    private final String mId;
    @PropertyName("timestamp")
    private final Object mTimestamp;

    @PropertyName("isfinished")
    private final boolean mIsFinished;

    @Exclude
    private final String mUserUuid;
    @Exclude
    private final String mRobotUuid;

    /**
     * Gets the finished key.
     *
     * @return The key set to true when finished.
     */
    @Override
    public String getIsFinishedKey() {
        return "isfinished";
    }

    /**
     * Gets if the sound has finished playing.
     *
     * @return True if this sound play is done.
     */
    @PropertyName("isfinished")
    @Override
    public boolean isFinished() {
        return mIsFinished;
    }

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
     * Gets the ID of the sound.
     *
     * @return The sound id.
     */
    @PropertyName("id")
    public String getId() {
        return mId;
    }

    /**
     * Gets the value of the raw value of the timestamp. This could be an object for ServerValue,
     * or another strange object. Generally you should use getTimestamp();
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

    public RobotSound() {
        mIsFinished = false;
        mUserUuid = null;
        mRobotUuid = null;
        mId = null;
        mTimestamp = ServerValue.TIMESTAMP;
    }

    public RobotSound(String id) {
        mIsFinished = false;
        mId = id;
        mUserUuid = null;
        mRobotUuid = null;
        mTimestamp = ServerValue.TIMESTAMP;
    }

    public RobotSound(String userUuid, String robotUuid, RobotSound sound) {
        mIsFinished = sound.isFinished();
        mId = sound.getId();
        mUserUuid = userUuid;
        mRobotUuid = robotUuid;
        mTimestamp = sound.getRawTimestamp();
    }

    public RobotSound(RobotSound sound, boolean finished) {
        mIsFinished = finished;
        mId = sound.getId();
        mUserUuid = sound.getUserUuid();
        mRobotUuid = sound.getRobotUuid();
        mTimestamp = sound.getRawTimestamp();
    }

    /**
     * Returns the object from firebase.
     *
     * @param userUuid     The user uuid.
     * @param robotUuid    The robot uuid.
     * @param dataSnapshot The firebase DataSnapshot.
     * @return The metadata object or null.
     */
    @SuppressWarnings("unchecked")
    public static RobotSound fromFirebase(String userUuid,
            String robotUuid, DataSnapshot dataSnapshot) {
        return new RobotSound(userUuid, robotUuid, dataSnapshot.getValue(RobotSound.class));
    }

}
