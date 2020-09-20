package ai.cellbots.common.data;

import android.support.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

/**
 * Info regarding an animation.
 */
@IgnoreExtraProperties
public class AnimationInfo implements Comparable<AnimationInfo> {
    @Exclude
    private final String mName;
    @Exclude
    private final String mUserUuid;

    /**
     * Get the name of the object.
     * @return The name.
     */
    public String getName() {
        return mName;
    }

    /**
     * Get the uuid of the object.
     * @return The uuid.
     */
    @SuppressWarnings("unused")
    public String getUserUuid() {
        return mUserUuid;
    }

    /**
     * Create an animation info object.
     */
    @SuppressWarnings("unused")
    private AnimationInfo() {
        mName = null;
        mUserUuid = null;
    }

    /**
     * Create an animation info object with a name.
     * @param userUuid The name of user.
     * @param name The name of animation.
     */
    public AnimationInfo(String userUuid, String name) {
        mName = name;
        mUserUuid = userUuid;
    }

    /**
     * Create an animation info object from firebase.
     * @param userUuid The name of user.
     * @param firebase The firebase info.
     * @return The firebase object.
     */
    public static AnimationInfo fromFirebase(DataSnapshot firebase, String userUuid) {
        return new AnimationInfo(userUuid, firebase.getKey());
    }

    /**
     * Convert to string
     * @return The string.
     */
    @Override
    public String toString() {
        return mName;
    }

    /**
     * Comparison function.
     * @param o Object to compare.
     * @return Compare order.
     */
    @Override
    public int compareTo(@NonNull AnimationInfo o) {
        if (mName == null) {
            return 0;
        }
        return mName.compareTo(o.getName());
    }
}
