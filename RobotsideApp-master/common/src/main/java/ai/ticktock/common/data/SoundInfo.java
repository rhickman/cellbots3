package ai.cellbots.common.data;

import android.support.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;
import com.google.firebase.database.ServerValue;

/**
 * Store the information of a sound saved in firebase.
 */
@IgnoreExtraProperties
public class SoundInfo implements Comparable<SoundInfo> {
    @PropertyName("name")
    private final String mName;
    @PropertyName("id")
    private final String mId;
    @PropertyName("timestamp")
    private final Object mTimestamp;
    @Exclude
    private final String mUserUuid;


    /**
     * Get the ID of the sound.
     * @return The sound id.
     */
    @PropertyName("id")
    public String getId() {
        return mId;
    }


    /**
     * Get the name of the sound.
     * @return The sound name.
     */
    @PropertyName("name")
    public String getName() {
        return mName;
    }

    /**
     * Convert to string.
     * @return String.
     */
    @Exclude
    @Override
    public String toString() {
        return getName();
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
     * Get the value of the timestamp.
     * @return The timestamp value.
     */
    @Exclude
    public long getTimestamp() {
        if (mTimestamp == null) {
            return 0;
        }
        if (mTimestamp instanceof Long) {
            return (long) mTimestamp;
        }
        if (mTimestamp instanceof Integer) {
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
     * Creates the sound.
     */
    public SoundInfo() {
        mName = null;
        mId = null;
        mTimestamp = ServerValue.TIMESTAMP;
        mUserUuid = null;
    }

    /**
     * Creates the sound.
     * @param sound The sound info.
     * @param userUuid The user info.
     */
    private SoundInfo(SoundInfo sound, String userUuid) {
        mName = sound.getName();
        mId = sound.getId();
        mUserUuid = userUuid;
        mTimestamp = sound.getRawTimestamp();
    }

    /**
     * Creates the sound info object from firebase.
     * @param snapshot The snapshot.
     * @param userUuid The user id.
     * @return The SoundInfo.
     */
    public static SoundInfo fromFirebase(DataSnapshot snapshot, String userUuid) {
        return new SoundInfo(snapshot.getValue(SoundInfo.class), userUuid);
    }

    /**
     * Compare sound two sounds for sorting by name.
     * @param otherSound The other sound for comparator.
     * @return The comparator result value.
     */
    @Override
    public int compareTo(@NonNull SoundInfo otherSound) {
        if (otherSound.getName() == null || getName() == null) {
            return 0;
        }
        return getName().compareTo(otherSound.getName());
    }
}
