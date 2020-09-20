package ai.cellbots.common.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;

import java.util.UUID;

/**
 * Stores a log message for a robot.
 */
@IgnoreExtraProperties
public class LogMessage {
    @Exclude
    private final String mUuid;
    @Exclude
    private final String mUserUuid;
    @Exclude
    private final String mRobotUuid;
    @PropertyName("text")
    private final String mText;
    @PropertyName("timestamp")
    private final long mTimestamp;

    /**
     * Get the uuid of the message.
     * @return The uuid.
     */
    @Exclude
    public String getUuid() {
        return mUuid;
    }
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
     * Get the text of the message.
     * @return The text of the robot message.
     */
    @PropertyName("text")
    public String getText() {
        return mText;
    }
    /**
     * Get the robotside timestamp of the log message.
     * @return The timestamp in robot time.
     */
    @PropertyName("timestamp")
    public long getTimestamp() {
        return mTimestamp;
    }

    /**
     * Create an empty log message.
     */
    public LogMessage() {
        mUuid = null;
        mUserUuid = null;
        mRobotUuid = null;
        mText = null;
        mTimestamp = 0;
    }

    /**
     * Create a new logged message.
     * @param userUuid The message of the uuid.
     * @param robotUuid The message of the robot.
     * @param text The message to write.
     * @param timestamp The timestamp to save.
     */
    public LogMessage(String userUuid, String robotUuid, String text, long timestamp) {
        mUuid = UUID.randomUUID().toString();
        mUserUuid = userUuid;
        mRobotUuid = robotUuid;
        mText = text;
        mTimestamp = timestamp;
    }

    /**
     * Create message from another message.
     * @param uuid The message uuid.
     * @param userUuid The message of the uuid.
     * @param robotUuid The message of the robot.
     * @param copy The copy of the message.
     */
    private LogMessage(String uuid, String userUuid, String robotUuid, LogMessage copy) {
        mUuid = uuid;
        mUserUuid = userUuid;
        mRobotUuid = robotUuid;
        mText = copy.getText();
        mTimestamp = copy.getTimestamp();
    }

    /**
     * Return the object from firebase.
     *
     * @param userUuid     The user uuid.
     * @param robotUuid    The robot uuid.
     * @param dataSnapshot The firebase DataSnapshot.
     * @return The metadata object or null.
     */
    @SuppressWarnings("unchecked")
    public static LogMessage fromFirebase(String userUuid,
            String robotUuid, DataSnapshot dataSnapshot) {
        return new LogMessage(dataSnapshot.getKey(), userUuid, robotUuid,
                dataSnapshot.getValue(LogMessage.class));
    }

}
