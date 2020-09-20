package ai.cellbots.common.data;

import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;

/**
 * A session description for WebRTC.
 */
@IgnoreExtraProperties
public class WebRTCSession {
    @PropertyName("description")
    private final String mDescription;
    @PropertyName("type")
    private final String mType;

    /**
     * Return the session description.
     * @return The session description.
     */
    @PropertyName("description")
    public String getDescription() {
        return mDescription;
    }
    /**
     * Return the session type.
     * @return The session type.
     */
    @PropertyName("type")
    public String getType() {
        return mType;
    }

    /**
     * Create a web RTC session.
     */
    @SuppressWarnings("unused")
    public WebRTCSession() {
        mType = null;
        mDescription = null;
    }

    /**
     * Create a web RTC session.
     * @param type The session type.
     * @param description The session description.
     */
    public WebRTCSession(String type, String description) {
        mType = type;
        mDescription = description;
    }

    /**
     * Convert to String.
     * @return String.
     */
    @Override
    public String toString() {
        return "WebRTCSession('" + mType + "', '" + mDescription + "')";
    }
}
