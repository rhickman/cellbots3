package ai.cellbots.common.webrtc;


import android.support.annotation.NonNull;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;

import ai.cellbots.common.data.IceCandidate;
import ai.cellbots.common.data.WebRTCCall;
import ai.cellbots.common.data.WebRTCSession;

/**
 * A WebRTC listener that stores ice candidates and sessions in the cloud properly.
 */
public abstract class CloudWebRTCListener implements RTCConnection.Listener {
    private DatabaseReference firebaseReference(@NonNull RTCConnection connection) {
        return FirebaseDatabase.getInstance().getReference("robot_goals")
                .child(connection.getUserUuid())
                .child(connection.getRobotUuid()).child("webrtc")
                .child(connection.getCallUuid());
    }

    /**
     * Called when a local ICE candidate is added. This simply writes it directly to firebase.
     * @param connection The connection for the event.
     * @param iceCandidate The new ice candidate that was added locally.
     */
    @Override
    public void onAddLocalIceCandidate(@NonNull RTCConnection connection,
            @NonNull IceCandidate iceCandidate) {
        firebaseReference(connection)
                .child(connection.isAnswer() ?
                        WebRTCCall.ANSWER_ICE_CANDIDATES : WebRTCCall.OFFER_ICE_CANDIDATES)
                .child(iceCandidate.computeUuid()).setValue(iceCandidate);
    }

    /**
     * Called when local ICE candidates are removed. Writes directly to firebase.
     * @param connection The connection for the event.
     * @param iceCandidates The ice candidates removed locally.
     */
    @Override
    public void onRemoveLocalIceCandidate(@NonNull RTCConnection connection,
            @NonNull List<IceCandidate> iceCandidates) {
        for (IceCandidate candidate : iceCandidates) {
            firebaseReference(connection)
                    .child(connection.isAnswer() ?
                            WebRTCCall.ANSWER_ICE_CANDIDATES : WebRTCCall.OFFER_ICE_CANDIDATES)
                    .child(candidate.computeUuid()).removeValue();
        }
    }

    /**
     * Called on an answer session being created. Written to firebase.
     * @param connection The connection for the event.
     * @param session The session data.
     */
    @Override
    public void onCreateAnswerSession(@NonNull RTCConnection connection, @NonNull WebRTCSession session) {
        firebaseReference(connection).child(WebRTCCall.ANSWER_SESSION).setValue(session);
    }

    /**
     * Called on an offer session being created. Written to firebase.
     * @param connection The connection for the event.
     * @param session The session data.
     */
    @Override
    public void onCreateOfferSession(@NonNull RTCConnection connection, @NonNull WebRTCSession session) {
        firebaseReference(connection).child(WebRTCCall.OFFER_SESSION).setValue(session);
    }
}
