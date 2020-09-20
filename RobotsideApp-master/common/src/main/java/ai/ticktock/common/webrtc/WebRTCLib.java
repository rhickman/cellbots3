package ai.cellbots.common.webrtc;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.RTCStats;
import org.webrtc.RTCStatsReport;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import ai.cellbots.common.data.WebRTCCall;
import ai.cellbots.common.data.WebRTCSession;

/**
 * The constants and library for WebRTC.
 */
public enum WebRTCLib {
    ;
    private static final String TAG = WebRTCLib.class.getSimpleName();
    public static final String LOCAL_MEDIA_SOURCE = "LOCAL";
    public static final String VIDEO_TRACK = "VIDEO_TRACK";
    public static final String DATA_CHANNEL = "DATA_CHANNEL";

    public interface IceServersListener {
        void onIceServers(List<PeerConnection.IceServer> iceServers);

        void onIceServerError();
    }

    /**
     * Create a list of IceServers for the system to connect to.
     * @param listener The listener.
     */
    public static void createIceServersList(final IceServersListener listener) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.e(TAG, "Invalid firebase user");
                    listener.onIceServerError();
                }
            });
            t.start();
            return;
        }
        user.getIdToken(true).addOnCompleteListener(new OnCompleteListener<GetTokenResult>() {
            public void onComplete(@NonNull Task<GetTokenResult> task) {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "Firebase token get task failed");
                    listener.onIceServerError();
                    return;
                }
                String userToken = task.getResult().getToken();
                if (userToken == null) {
                    Log.w(TAG, "Firebase token was invalid");
                    listener.onIceServerError();
                    return;
                }
                createIceServersList(listener, userToken);
            }
        });
    }

    /**
     * Get the URL of the WebRTC ICE servers.
     * @return The cloud function url.
     */
    private static URL getCloudFunctionUrl() {
        FirebaseApp app = FirebaseApp.getInstance();
        if (app == null) {
            Log.w(TAG, "Firebase application is null");
            return null;
        }
        String urlStr = "https://us-central1-" + app.getOptions().getProjectId()
                + ".cloudfunctions.net/getWebRTCIceServers";
        try {
            return new URL(urlStr);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Unable to create url: " + urlStr, e);
            return null;
        }
    }

    /**
     * Get the response of a cloud function.
     * @param url The URL for the system to connect to.
     * @param userToken The token for the user.
     */
    private static String executeCloudFunction(URL url, String userToken) {
        String objectData;
        try {
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            String basicAuth = "Bearer " + userToken;
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", basicAuth.trim());
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "*/*");

            conn.setReadTimeout(10000);
            conn.setConnectTimeout(15000);

            conn.connect();

            OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
            writer.flush();

            if (conn.getResponseCode() != 201 && conn.getResponseCode() != 200) {
                Log.i(TAG, "Response: " + conn.getResponseCode());
                //noinspection ThrowCaughtLocally
                throw new IOException("Response code is invalid: " + conn.getResponseCode());
            }
            InputStream inputStr = conn.getInputStream();
            objectData = IOUtils.toString(inputStr, "UTF-8");
            Log.i(TAG, "Object data: " + objectData);

            conn.disconnect();
            inputStr.close();
        } catch (MalformedURLException e) {
            Log.e(TAG, "URL Exception, should not occur", e);
            return null;
        } catch (IOException e) {
            Log.e(TAG, "Failed to connect", e);
            return null;
        }
        return objectData;
    }

    /**
     * Process the ICE severs response data.
     * @param objectData The objectData from the JSON HTTP request.
     * @return The ICE servers data
     */
    private static List<PeerConnection.IceServer> processIceResponseJson(String objectData) {
        try {
            JSONObject result = new JSONObject(objectData);
            JSONArray servers = result.getJSONArray("ice_servers");
            List<PeerConnection.IceServer> iceServers = new ArrayList<>(servers.length());
            for (int i = 0; i < servers.length(); i++) {
                JSONObject object = servers.getJSONObject(i);
                PeerConnection.IceServer.Builder serverBuilder
                        = PeerConnection.IceServer.builder(object.getString("url"));
                if (object.has("username")) {
                    serverBuilder.setUsername(object.getString("username"));
                }
                if (object.has("credential")) {
                    serverBuilder.setPassword(object.getString("credential"));
                }
                iceServers.add(serverBuilder.createIceServer());
            }
            return iceServers;
        } catch (JSONException e) {
            Log.e(TAG, "Failed to read object: " + objectData, e);
            return null;
        }
    }

    /**
     * Create a list of IceServers for the system to connect to.
     * @param listener The listener.
     * @param userToken The current user authentication token.
     */
    public static void createIceServersList(final IceServersListener listener, final String userToken) {
        Thread iceServers = new Thread(new Runnable() {
            @Override
            public void run() {
                URL url = getCloudFunctionUrl();
                if (url == null) {
                    listener.onIceServerError();
                    return;
                }
                String objectData = executeCloudFunction(url, userToken);
                if (objectData == null) {
                    listener.onIceServerError();
                    return;
                }
                List<PeerConnection.IceServer> iceServers = processIceResponseJson(objectData);
                if (iceServers == null) {
                    listener.onIceServerError();
                    return;
                }
                listener.onIceServers(Collections.unmodifiableList(iceServers));
            }
        });
        iceServers.start();
    }

    /**
     * Convert a data IceCandidate to a WebRTC IceCandidate.
     * @param iceCandidate The candidate for ICE.
     */
    public static IceCandidate iceCandidateFromData(@NonNull ai.cellbots.common.data.IceCandidate iceCandidate) {
        return new IceCandidate(iceCandidate.getSdpMid(), iceCandidate.getSdpMLineIndex(), iceCandidate.getSdp());
    }

    /**
     * Convert a WebRTC IceCandidate to a data IceCandidate.
     * @param iceCandidate The candidate for ICE.
     */
    public static ai.cellbots.common.data.IceCandidate iceCandidateToData(@NonNull IceCandidate iceCandidate) {
        return new ai.cellbots.common.data.IceCandidate(
                iceCandidate.sdp, iceCandidate.sdpMid,
                iceCandidate.sdpMLineIndex, iceCandidate.serverUrl);
    }

    /**
     * Convert a WebRTC session to a data session
     */
    public static SessionDescription sessionFromData(@NonNull WebRTCSession session) {
        return new SessionDescription(
                SessionDescription.Type.fromCanonicalForm(session.getType()),
                session.getDescription());
    }

    /**
     * Convert a WebRTC session from a data session
     */
    public static WebRTCSession sessionToData(@NonNull SessionDescription session) {
        return new WebRTCSession(session.type.canonicalForm(), session.description);
    }

    private static final IceCandidate[] CONVERT_TO_CANDIDATE_ARRAY = {};

    /**
     * Called when candidates are updated.
     */
    public interface IceCandidateCallback {
        /**
         * Called when a candidate is added.
         * @param candidate The candidate.
         */
        void onIceCandidateAdded(ai.cellbots.common.data.IceCandidate candidate);
    }

    /**
     * Update a PeerConnection's ICE candidates from the database.
     * @param connection The connection to be updated.
     * @param storedIceCandidates The hashmap of ICE candidates stored in the connection.
     * @param setIceCandidates The set of ICE candidates to be set to.
     * @param callback The callback candidate.
     */
    public static void updateIceCandidates(@NonNull PeerConnection connection,
            @NonNull Map<String, IceCandidate> storedIceCandidates,
            Map<String, ai.cellbots.common.data.IceCandidate> setIceCandidates,
            IceCandidateCallback callback) {
        if (setIceCandidates == null) {
            return;
        }
        for (Map.Entry<String, ai.cellbots.common.data.IceCandidate> entry : setIceCandidates.entrySet()) {
            if (!storedIceCandidates.containsKey(entry.getKey())) {
                IceCandidate candidate = iceCandidateFromData(entry.getValue());
                connection.addIceCandidate(candidate);
                storedIceCandidates.put(entry.getKey(), candidate);
                if (callback != null) {
                    callback.onIceCandidateAdded(entry.getValue());
                }
            }
        }
        final LinkedList<String> removes = new LinkedList<>();
        final LinkedList<IceCandidate> removesC = new LinkedList<>();
        for (Map.Entry<String, IceCandidate> entry : storedIceCandidates.entrySet()) {
            if (!setIceCandidates.containsKey(entry.getKey())) {
                removes.add(entry.getKey());
                removesC.add(entry.getValue());
            }
        }
        for (String remove : removes) {
            storedIceCandidates.remove(remove);
        }
        if (!removesC.isEmpty()) {
            connection.removeIceCandidates(removesC.toArray(CONVERT_TO_CANDIDATE_ARRAY));
        }
    }

    private static final String EVENT_CALL_UPDATE_ANSWER = "CALL_UPDATE_ANSWER";
    private static final String EVENT_CALL_UPDATE_OFFER = "CALL_UPDATE_OFFER";

    private static final String EVENT_PARAM_CALL_UUID = "CALL_UUID";
    private static final String EVENT_PARAM_CALL_EVENT = "CALL_EVENT";
    private static final String EVENT_PARAM_CALL_TIMESTAMP = "CALL_TIMESTAMP";
    private static final String EVENT_PARAM_CALL_STATUS = "CALL_STATUS";
    private static final String EVENT_PARAM_CALL_IP = "CALL_IP_";

    private static final String EVENT_JSON_CALL_STATUS_ID = "id";
    private static final String EVENT_JSON_CALL_STATUS_TYPE = "type";
    private static final String EVENT_JSON_CALL_STATUS_TIMESTAMP = "timestamp";
    private static final String EVENT_JSON_CALL_STATUS_CONTENT = "content";

    private static final String EVENT_PARAM_USER_UUID = "USER_UUID";
    private static final String EVENT_PARAM_ROBOT_UUID = "ROBOT_UUID";
    private static final String EVENT_CALL_STARTED = "CALL_STARTED";
    private static final String EVENT_CALL_EXPIRED = "CALL_EXPIRED";

    /**
     * Log the start of a call.
     * @param parent The parent context.
     * @param call The call uuid.
     */
    public static void logCallStart(@NonNull Context parent, @NonNull WebRTCCall call) {
        // Build event
        Bundle statusEvent = new Bundle();
        statusEvent.putString(EVENT_PARAM_ROBOT_UUID, call.getRobotUuid());
        statusEvent.putString(EVENT_PARAM_USER_UUID, call.getUserUuid());
        statusEvent.putString(EVENT_PARAM_CALL_UUID, call.getUuid());

        // Upload to Firebase
        FirebaseAnalytics.getInstance(parent).logEvent(EVENT_CALL_STARTED, statusEvent);
    }

    /**
     * Log when a call expires.
     * @param parent The parent context.
     * @param call The call uuid.
     */
    public static void logCallExpire(@NonNull Context parent, @NonNull WebRTCCall call) {
        // Build event
        Bundle statusEvent = new Bundle();
        statusEvent.putString(EVENT_PARAM_ROBOT_UUID, call.getRobotUuid());
        statusEvent.putString(EVENT_PARAM_USER_UUID, call.getUserUuid());
        statusEvent.putString(EVENT_PARAM_CALL_UUID, call.getUuid());

        // Upload to Firebase
        FirebaseAnalytics.getInstance(parent).logEvent(EVENT_CALL_EXPIRED, statusEvent);
    }

    /**
     * Log an empty call, e.g. an update without a PeerConnection.
     * @param parent The parent context.
     * @param isAnswer True if answer side.
     * @param callUuid The call uuid.
     * @param event The call event.
     */
    static void logEmptyCall(@NonNull Context parent, boolean isAnswer,
            @NonNull String callUuid, @NonNull String event) {
        Bundle statusEvent = new Bundle();
        statusEvent.putString(EVENT_PARAM_CALL_UUID, callUuid);
        statusEvent.putString(EVENT_PARAM_CALL_EVENT, event);
        FirebaseAnalytics.getInstance(parent).logEvent(
                isAnswer ? EVENT_CALL_UPDATE_ANSWER : EVENT_CALL_UPDATE_OFFER,
                statusEvent);

    }

    /**
     * Log a call update, with an status update from the call.
     * @param parent The parent context.
     * @param isAnswer True if answer side.
     * @param callUuid The call uuid.
     * @param event The call event.
     * @param connection The PeerConnection for the call.
     * @param rtcStatsReport The RTC status report from the call.
     */
    static void logCallConnection(boolean isAnswer, @NonNull Context parent,
            @NonNull String callUuid, @NonNull String event,
            @NonNull PeerConnection connection, @NonNull RTCStatsReport rtcStatsReport) {
        Bundle statusEvent = new Bundle();

        statusEvent.putString(EVENT_PARAM_CALL_UUID, callUuid);
        statusEvent.putString(EVENT_PARAM_CALL_EVENT, event);
        statusEvent.putDouble(EVENT_PARAM_CALL_TIMESTAMP, rtcStatsReport.getTimestampUs());

        if (rtcStatsReport.getStatsMap().containsKey("RTCTransport_video_1")) {
            Map<String, Object> objects = rtcStatsReport.getStatsMap().get(
                    "RTCTransport_video_1").getMembers();
            if (objects.containsKey("selectedCandidatePairId")) {
                String candidates = objects.get(
                        "selectedCandidatePairId").toString();
                String[] ids = candidates.split("_");
                for (int i = 1; i < ids.length; i++) {
                    if (!rtcStatsReport.getStatsMap().containsKey(
                            "RTCIceCandidate_" + ids[i])) {
                        continue;
                    }
                    String type = rtcStatsReport.getStatsMap().get(
                            "RTCIceCandidate_" + ids[i]).getType();
                    Map<String, Object> statsMap = rtcStatsReport.getStatsMap()
                            .get("RTCIceCandidate_" + ids[i]).getMembers();
                    String ip = statsMap.containsKey("ip") ? statsMap.get("ip").toString()
                            : "NULL";
                    String port = statsMap.containsKey("port") ? statsMap.get(
                            "port").toString()
                            : "NULL";
                    String protocol = statsMap.containsKey("protocol") ? statsMap.get(
                            "protocol").toString() : "NULL";
                    String re = type + " " + ip + " " + port + " " + protocol;
                    statusEvent.putString(EVENT_PARAM_CALL_IP + i, re);
                    Log.v(TAG, "IP: " + i + ": " + re);
                }
            }
        }

        // Parameter map
        JSONObject statusBase = new JSONObject();
        for (Map.Entry<String, RTCStats> r : rtcStatsReport.getStatsMap().entrySet()) {
            try {
                JSONObject statusElement = new JSONObject();
                Log.v(TAG, r.getKey() + ": " + r.getValue().getId() + " "
                        + r.getValue().getType());
                statusElement.put(EVENT_JSON_CALL_STATUS_ID, r.getValue().getId());
                statusElement.put(EVENT_JSON_CALL_STATUS_TYPE,
                        r.getValue().getType());
                statusElement.put(EVENT_JSON_CALL_STATUS_TIMESTAMP,
                        r.getValue().getTimestampUs());
                JSONObject statusContent = new JSONObject();
                for (Map.Entry<String, Object> s : r.getValue().getMembers()
                        .entrySet()) {

                    if (s.getValue() instanceof String[]) {
                        JSONArray array = new JSONArray();
                        for (String sv : (String[]) s.getValue()) {
                            array.put(sv);
                        }
                        statusContent.put(s.getKey(), array);
                    } else {
                        statusContent.put(s.getKey(), s.getValue().toString());
                    }
                    Log.v(TAG, "   " + s.getKey() + ": " + s.getValue());
                }
                statusElement.put(EVENT_JSON_CALL_STATUS_CONTENT, statusContent);
                statusBase.put(r.getKey(), statusElement);
            } catch (JSONException e) {
                Log.e(TAG, "Error with status: " + r.getKey(), e);
            }
        }
        statusEvent.putString(EVENT_PARAM_CALL_STATUS, statusBase.toString());

        FirebaseAnalytics.getInstance(parent).logEvent(
                isAnswer ? EVENT_CALL_UPDATE_ANSWER : EVENT_CALL_UPDATE_OFFER,
                statusEvent);
    }

    public static final String CALL_EVENT_SIGNALING_STATE_CHANGE = "SIGNALING_STATE_CHANGE:";
    public static final String CALL_EVENT_ICE_STATE_CHANGE = "ICE_STATE_CHANGE:";
    public static final String CALL_EVENT_ICE_RECEIVING_CHANGE = "ICE_RECEIVING_CHANGE:";
    public static final String CALL_EVENT_ICE_GATHERING_STATE = "ICE_GATHERING_STATE:";
    public static final String CALL_EVENT_ADD_LOCAL_ICE_CANDIDATE = "ADD_LOCAL_ICE_CANDIDATE";
    public static final String CALL_EVENT_ADD_REMOTE_ICE_CANDIDATE = "ADD_REMOTE_ICE_CANDIDATE";
    public static final String CALL_EVENT_REMOVE_LOCAL_ICE_CANDIDATES = "REMOVE_LOCAL_ICE_CANDIDATES";
    public static final String CALL_EVENT_ADD_MEDIA_STREAM = "ADD_MEDIA_STREAM";
    public static final String CALL_EVENT_REMOVE_MEDIA_STREAM = "REMOVE_MEDIA_STREAM";
    public static final String CALL_EVENT_ADD_DATA_CHANNEL = "ADD_DATA_CHANNEL:";
    public static final String CALL_EVENT_RENEGOTIATION_NEEDED = "RENEGOTIATION_NEEDED";
    public static final String CALL_EVENT_RECEIVER = "RECEIVER:";
    public static final String CALL_EVENT_KEEP_ALIVE = "KEEP_ALIVE";
    public static final String CALL_EVENT_SET_REMOTE_ANSWER = "SET_REMOTE_ANSWER";
    public static final String CALL_EVENT_SET_REMOTE_OFFER = "SET_REMOTE_OFFER";
    public static final String CALL_EVENT_CREATE_OFFER_SESSION = "CREATE_OFFER_SESSION";
    public static final String CALL_EVENT_SET_OFFER_SESSION = "SET_OFFER_SESSION";
    public static final String CALL_EVENT_CREATE_ANSWER_SESSION = "CREATE_ANSWER_SESSION";
    public static final String CALL_EVENT_SET_ANSWER_SESSION = "SET_ANSWER_SESSION";
    public static final String CALL_CREATE_FAILURE = "CREATE_FAILURE:";
    public static final String CALL_SET_FAILURE = "SET_FAILURE:";

}
