package ai.cellbots.common.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.PropertyName;
import com.google.firebase.database.ServerValue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a call to a WebRTC listener.
 */

public class WebRTCCall {
    public static final String OFFER_ICE_CANDIDATES = "offer_ice_candidates";
    public static final String ANSWER_ICE_CANDIDATES = "answer_ice_candidates";
    public static final String OFFER_SESSION = "offer_session";
    public static final String ANSWER_SESSION = "answer_session";
    public static final String TIMESTAMP = "timestamp";
    // Keep alive in firebase so we can delete dead calls, in milliseconds.
    public static final long UPDATE_SPACING = 10000L;
    // Keep alive timeout, in milliseconds.
    public static final long UPDATE_TIMEOUT = 60000L;

    @Exclude
    private final String mUuid;
    @Exclude
    private final String mRobotUuid;
    @Exclude
    private final String mUserUuid;
    @PropertyName(OFFER_SESSION)
    private final WebRTCSession mOfferSession;
    @PropertyName(ANSWER_SESSION)
    private final WebRTCSession mAnswerSession;
    @PropertyName(TIMESTAMP)
    private final Object mTimestamp;
    @PropertyName(OFFER_ICE_CANDIDATES)
    private final Map<String, IceCandidate> mOfferIceCandidates;
    @PropertyName(ANSWER_ICE_CANDIDATES)
    private final Map<String, IceCandidate> mAnswerIceCandidates;


    public WebRTCCall() {
        mUserUuid = null;
        mRobotUuid = null;
        mUuid = null;
        mOfferSession = null;
        mAnswerSession = null;
        mTimestamp = ServerValue.TIMESTAMP;
        mOfferIceCandidates = Collections.unmodifiableMap(new HashMap<String, IceCandidate>());
        mAnswerIceCandidates = Collections.unmodifiableMap(new HashMap<String, IceCandidate>());
    }

    public WebRTCCall(String userUuid, String robotUuid) {
        mUuid = UUID.randomUUID().toString();
        mUserUuid = userUuid;
        mRobotUuid = robotUuid;
        mOfferSession = null;
        mAnswerSession = null;
        mTimestamp = ServerValue.TIMESTAMP;
        mOfferIceCandidates = Collections.unmodifiableMap(new HashMap<String, IceCandidate>());
        mAnswerIceCandidates = Collections.unmodifiableMap(new HashMap<String, IceCandidate>());
    }

    public WebRTCCall(String userUuid, String robotUuid, String uuid, WebRTCCall copy) {
        mUserUuid = userUuid;
        mRobotUuid = robotUuid;
        mUuid = uuid;
        mOfferSession = copy.getOfferSession();
        mAnswerSession = copy.getAnswerSession();
        mTimestamp = copy.getRawTimestamp();
        mOfferIceCandidates = Collections.unmodifiableMap(copy.getOfferIceCandidates());
        mAnswerIceCandidates = Collections.unmodifiableMap(copy.getAnswerIceCandidates());
    }

    public static WebRTCCall fromFirebase(String userUuid, String robotUuid, DataSnapshot dataSnapshot) {
        return new WebRTCCall(userUuid, robotUuid,
                dataSnapshot.getKey(), dataSnapshot.getValue(WebRTCCall.class));
    }

    @Exclude
    public String getUuid() {
        return mUuid;
    }
    @Exclude
    public String getUserUuid() {
        return mUserUuid;
    }
    @Exclude
    public String getRobotUuid() {
        return mRobotUuid;
    }
    @PropertyName(OFFER_SESSION)
    public WebRTCSession getOfferSession() {
        return mOfferSession;
    }
    @PropertyName(ANSWER_SESSION)
    public WebRTCSession getAnswerSession() {
        return mAnswerSession;
    }
    @PropertyName(TIMESTAMP)
    public Object getRawTimestamp() {
        return mTimestamp;
    }
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

    /**
     * Get the offer ICE Candidates.
     * @return The offer ICE candidates.
     */
    @PropertyName(OFFER_ICE_CANDIDATES)
    public Map<String, IceCandidate> getOfferIceCandidates() {
        return Collections.unmodifiableMap(mOfferIceCandidates);
    }

    /**
     * Get the answer ICE Candidates.
     * @return The answer ICE candidates.
     */
    @PropertyName(ANSWER_ICE_CANDIDATES)
    public Map<String, IceCandidate> getAnswerIceCandidates() {
        return Collections.unmodifiableMap(mAnswerIceCandidates);
    }

    /**
     * Convert to String.
     * @return String.
     */
    @Override
    public String toString() {
        return "WebRTCCall('" + mUuid + "', " + mTimestamp + ", "
                + (mOfferSession == null ? "NULL" : mOfferSession) + ", "
                + (mAnswerSession == null ? "NULL" : mAnswerSession) + ")";
    }
}
