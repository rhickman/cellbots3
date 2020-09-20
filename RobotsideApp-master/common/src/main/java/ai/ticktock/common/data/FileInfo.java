package ai.cellbots.common.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;
import com.google.firebase.database.ServerValue;

/**
 * File information used by the cloud file manager.
 */
@IgnoreExtraProperties
public class FileInfo {
    private static final String TIMESTAMP = "timestamp";
    private static final String NAME = "name";

    @Exclude
    private final String mId;
    @PropertyName(NAME)
    private final String mName;
    @PropertyName(TIMESTAMP)
    private final Object mTimestamp;

    /**
     * Create an empty FileInfo.
     */
    public FileInfo() {
        mId = null;
        mName = null;
        mTimestamp = ServerValue.TIMESTAMP;
    }

    /**
     * Create a FileInfo with an ID.
     * @param id The ID of the file.
     * @param copy The FileInfo to copy from.
     */
    private FileInfo(String id, FileInfo copy) {
        mId = id;
        mName = copy.getName();
        mTimestamp = copy.getRawTimestamp();
    }

    /**
     * Create a FileInfo from the firebase database.
     * @param dataSnapshot The dataSnapshot to read from.
     * @return The FileInfo.
     */
    public static FileInfo fromFirebase(DataSnapshot dataSnapshot) {
        return new FileInfo(dataSnapshot.getKey(), dataSnapshot.getValue(FileInfo.class));
    }

    /**
     * Get the id of the FileInfo
     * @return The id
     */
    @Exclude()
    public String getId() {
        return mId;
    }

    /**
     * Get the name of the FileInfo
     * @return The filename
     */
    @PropertyName(NAME)
    public String getName() {
        return mName;
    }

    /**
     * Get the raw timestamp object, to be written to firebase.
     * @return The raw timestamp object.
     */
    @PropertyName(TIMESTAMP)
    public Object getRawTimestamp() {
        return mTimestamp;
    }

    /**
     * Get the timestamp value.
     * @return The timestamp value or -1 if it does not exist.
     */
    @Exclude
    public long getTimestamp() {
        if (mTimestamp instanceof Long) {
            return (long) mTimestamp;
        }
        if (mTimestamp instanceof Integer) {
            return (long) mTimestamp;
        }
        return -1;
    }
}
