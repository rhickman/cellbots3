package ai.cellbots.common.webrtc;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RTCStatsCollectorCallback;
import org.webrtc.RTCStatsReport;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoTrack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import ai.cellbots.common.data.WebRTCSession;

/**
 * A thread-safe RTC Connection that wraps a WebRTC library PeerConnection and PeerConnectionFactory
 * that we use. Unfortunately, the WebRTC library was not designed to be thread-safe, so we wrote
 * this wrapper to get around these issues. To manage a connection, ICE candidates and Session
 * Descriptions must be managed. The Session Descriptions list the session state and encryption
 * keys. The ICE candidates are candidates to bind the connections. Each Connection has a remote
 * and local session description. The local session description is created and written to the
 * signalling server (currently firebase), while the remote session description is read from the
 * signalling server. The ICE candidates are similarly passed through the signalling server, the
 * local ones being written while the remote ones being read. On the server, these signals are
 * referred to as the "offer" and "answer", the "offer" being the robot side and "answer" being
 * the companion side.
 *
 * The process of a connection is as follows:
 * - The connection is started with a UUID, user uuid, and robot uuid
 * - If the connection is an offer connection, the offer is created and sent to the listener
 * - The connection's remote is set externally using setRemoteAnswer or setRemoteOffer
 * - If the connection is an answer connection, the answer is created and sent to the listener
 * - The cached ice candidates are written to the connection
 * - The connection goes until the shutdown method is called, or the connection breaks and the
 *   listener's terminate method is called
 */
final public class RTCConnection {
    private static final String TAG = RTCConnection.class.getSimpleName();
    // The ice candidates currently associated with the WebRTC connection.
    private final Map<String, IceCandidate> mIceCandidates = new HashMap<>();
    private final String mUserUuid; // User uuid
    private final String mRobotUuid; // Robot uuid
    private final String mCallUuid; // Call uuid
    private final boolean mIsAnswer; // True if it is companion app (answer side) session
    private final Context mParent; // The parent context of the system
    private final PeerConnection mPeerConnection; // The peer connection
    private final PeerConnectionFactory mPeerConnectionFactory; // Factory
    private final Listener mListener; // External state listener

    private boolean mSetRemote = false; // Remote session has been set
    private boolean mShutdown = false; // Set when the call is shutting down
    private boolean mIceCandidateReady = false; // Ready to start writing ICE candidates

    // Buffer for ice candidates before they are ready.
    private Map<String, ai.cellbots.common.data.IceCandidate> mIceCandidateBuffer = null;

    // Store the data channel
    private DataChannel mDataChannel = null;

    // Store the messages to write to the data channel
    private final LinkedList<byte[]> mDataChannelMessages = new LinkedList<>();

    /**
     * Send a message over the data channel. The messages could arrive out of order.
     * @param newData The new data to send down the channel.
     */
    public void sendDataMessage(byte[] newData) {
        synchronized (mDataChannelMessages) {
            mDataChannelMessages.add(newData);
        }
        processMessages();
    }

    /**
     * Attempt to send all messages currently buffered through the data channel.
     */
    private void processMessages() {
        byte[][] messages;
        synchronized (mDataChannelMessages) {
            messages = mDataChannelMessages.toArray(new byte[0][0]);
            mDataChannelMessages.clear();
        }
        synchronized (this) {
            if (mDataChannel != null && !mShutdown) {
                for (byte[] message : messages) {
                    mDataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(message), false));
                }
                return;
            }
        }
        // If we could not send the data, return it.
        synchronized (mDataChannelMessages) {
            for (byte[] message : messages) {
                mDataChannelMessages.push(message);
            }
        }
    }

    /**
     * The listener for the RTCConnection, called when events occur on the RTC connection.
     */
    public interface Listener {
        /**
         * Called when the connection is terminated.
         * @param connection The connection for the event.
         */
        void onTermination(@NonNull RTCConnection connection);

        /**
         * Called when an ICE candidate is added locally to the connection.
         * @param connection The connection for the event.
         * @param iceCandidate The new ice candidate that was added locally.
         */
        void onAddLocalIceCandidate(@NonNull RTCConnection connection,
                @NonNull ai.cellbots.common.data.IceCandidate iceCandidate);

        /**
         * Called when ICE candidates are removed locally from the connection.
         * @param connection The connection for the event.
         * @param iceCandidates The ice candidates removed locally.
         */
        void onRemoveLocalIceCandidate(@NonNull RTCConnection connection,
                @NonNull List<ai.cellbots.common.data.IceCandidate> iceCandidates);

        /**
         * Create (or return) a video renderer for the track.
         * @param connection The connection for the event.
         * @return Return the video renderer, or null if video does not need to be saved.
         */
        VideoRenderer createVideoRenderer(@NonNull RTCConnection connection);

        /**
         * Called when an answer session is created.
         * @param connection The connection for the event.
         * @param session The session data.
         */
        void onCreateAnswerSession(@NonNull RTCConnection connection, @NonNull WebRTCSession session);

        /**
         * Called when an offer session is created.
         * @param connection The connection for the event.
         * @param session The session data.
         */
        void onCreateOfferSession(@NonNull RTCConnection connection, @NonNull WebRTCSession session);

        /**
         * Create (or return) a video capturer for the track.
         * @param connection The connection for the event.
         * @return Return the video capturer, or null if video does not need to be saved.
         */
        VideoCapturer createVideoCapturer(@NonNull RTCConnection connection);

        /**
         * Called on creation of an RTC connection.
         * @param connection The connection for the event.
         */
        void onCreateConnection(@NonNull RTCConnection connection);

        /**
         * Called when a message is received by the RTC connection.
         * @param connection The connection for the event.
         * @param message The message data for the event.
         */
        void onMessage(@NonNull RTCConnection connection, byte[] message);
    }

    /**
     * Creates a new WebRTC Connection object.
     * @param isAnswer Is the answer side (companion app) of the call.
     * @param parent The parent context for system.
     * @param userUuid The uuid of the current user.
     * @param robotUuid The uuid of the current robot.
     * @param callUuid The uuid of the current call.
     * @param listener The listener for the call.
     */
    public static void createRTCConnection(final @NonNull Context parent, final boolean isAnswer,
            final @NonNull String userUuid, final @NonNull String robotUuid,
            final @NonNull String callUuid, final @NonNull Listener listener) {
        WebRTCLib.createIceServersList(new WebRTCLib.IceServersListener() {
            @Override
            public void onIceServers(List<PeerConnection.IceServer> iceServers) {
                RTCConnection connection = new RTCConnection(parent, isAnswer,
                        userUuid, robotUuid, callUuid, listener, iceServers);
                listener.onCreateConnection(connection);
            }

            @Override
            public void onIceServerError() {

            }
        });
    }

    /**
     * Creates a new WebRTC Connection object.
     * @param isAnswer Is the answer side (companion app) of the call.
     * @param parent The parent context for system.
     * @param userUuid The uuid of the current user.
     * @param robotUuid The uuid of the current robot.
     * @param callUuid The uuid of the current call.
     * @param listener The listener for the call.
     * @param iceServers The list of ICE servers.
     */
    private RTCConnection(@NonNull Context parent, boolean isAnswer,
            @NonNull String userUuid, @NonNull String robotUuid, @NonNull String callUuid,
            @NonNull Listener listener, @NonNull List<PeerConnection.IceServer> iceServers) {
        synchronized (this) {
            mUserUuid = userUuid;
            mRobotUuid = robotUuid;
            mCallUuid = callUuid;
            mIsAnswer = isAnswer;
            mParent = parent;
            mListener = listener;

            final MediaConstraints constraints = new MediaConstraints();

            PeerConnectionFactory.initializeAndroidGlobals(mParent, true);
            PeerConnectionFactory.Options opts = new PeerConnectionFactory.Options();
            mPeerConnectionFactory = new PeerConnectionFactory(opts);

            mPeerConnection = mPeerConnectionFactory.createPeerConnection(
                    iceServers, constraints,
                    new PeerConnection.Observer() {
                        /**
                         * Called when the signal is set. Simply logs the latest update.
                         * @param signalingState The new state.
                         */
                        @Override
                        public void onSignalingChange(
                                PeerConnection.SignalingState signalingState) {
                            Log.v(TAG, "Signaling state: " + signalingState);
                            logCallState(
                                    WebRTCLib.CALL_EVENT_SIGNALING_STATE_CHANGE + signalingState);
                        }

                        /**
                         * Called when the ICE state changes. Logs the update and if state FAILED,
                         * calls shutdown and terminate in a new thread.
                         * @param iceConnectionState The new state.
                         */
                        @Override
                        public void onIceConnectionChange(
                                PeerConnection.IceConnectionState iceConnectionState) {
                            Log.v(TAG, "ICE state: " + iceConnectionState);
                            logCallState(
                                    WebRTCLib.CALL_EVENT_ICE_STATE_CHANGE + iceConnectionState);
                            if (iceConnectionState == PeerConnection.IceConnectionState.FAILED) {
                                Thread t = new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        shutdown();
                                        mListener.onTermination(RTCConnection.this);
                                    }
                                });
                                t.start();
                            }
                        }

                        /**
                         * Called when the connection receiving state changes. Simply logs.
                         * @param b The new state.
                         */
                        @Override
                        public void onIceConnectionReceivingChange(boolean b) {
                            Log.v(TAG, "ICE connection receiving change: " + b);
                            logCallState(WebRTCLib.CALL_EVENT_ICE_RECEIVING_CHANGE + b);
                        }

                        /**
                         * Called when the connection gathering state changes. Simply logs.
                         * @param iceGatheringState The new state.
                         */
                        @Override
                        public void onIceGatheringChange(
                                PeerConnection.IceGatheringState iceGatheringState) {
                            Log.v(TAG, "ICE gathering state change: " + iceGatheringState);
                            logCallState(
                                    WebRTCLib.CALL_EVENT_ICE_GATHERING_STATE + iceGatheringState);
                        }

                        /**
                         * Called when a new local ICE candidate is added. Logs and sends the
                         * ICE candidate out via the listener.
                         * @param iceCandidate The ICE candidate state.
                         */
                        @Override
                        public void onIceCandidate(IceCandidate iceCandidate) {
                            Log.v(TAG, "ICE candidate added: " + iceCandidate);
                            ai.cellbots.common.data.IceCandidate candidate
                                    = WebRTCLib.iceCandidateToData(iceCandidate);
                            mListener.onAddLocalIceCandidate(RTCConnection.this, candidate);
                            logCallState(WebRTCLib.CALL_EVENT_ADD_LOCAL_ICE_CANDIDATE);
                        }

                        /**
                         * Called when local ICE candidates are removed. Logs and sends the
                         * ICE candidates out via the listener.
                         * @param iceCandidates The ICE candidates to be removed.
                         */
                        @Override
                        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
                            Log.v(TAG, "ICE candidates removed: " + iceCandidates.length);
                            List<ai.cellbots.common.data.IceCandidate> out
                                    = new ArrayList<>(iceCandidates.length);
                            for (IceCandidate iceCandidate : iceCandidates) {
                                out.add(WebRTCLib.iceCandidateToData(iceCandidate));
                            }
                            mListener.onRemoveLocalIceCandidate(RTCConnection.this, out);
                            logCallState(WebRTCLib.CALL_EVENT_REMOVE_LOCAL_ICE_CANDIDATES);
                        }

                        /**
                         * Called when a new media stream is added. Adds a renderer if the listener
                         * returns one and also logs.
                         * @param mediaStream The media stream to be added.
                         */
                        @Override
                        public void onAddStream(MediaStream mediaStream) {
                            Log.i(TAG, "Add media stream: " + mediaStream.label());
                            if (mediaStream.label().equals(WebRTCLib.LOCAL_MEDIA_SOURCE)) {
                                Log.i(TAG, "Stream is correct");
                                for (VideoTrack track : mediaStream.videoTracks) {
                                    Log.i(TAG, "Add video track: " + track);
                                    VideoRenderer renderer = mListener.createVideoRenderer(
                                            RTCConnection.this);
                                    if (renderer != null) {
                                        track.addRenderer(renderer);
                                    }
                                }
                            } else {
                                Log.w(TAG, "Reject pointless mediaStream: " + mediaStream);
                            }
                            logCallState(
                                    WebRTCLib.CALL_EVENT_ADD_MEDIA_STREAM + " " + mediaStream.label());
                        }

                        /**
                         * Called when a media stream is removed. Simply logs.
                         * @param mediaStream The media stream to be removed.
                         */
                        @Override
                        public void onRemoveStream(MediaStream mediaStream) {
                            Log.i(TAG, "Remove media stream: " + mediaStream.label());
                            logCallState(
                                    WebRTCLib.CALL_EVENT_REMOVE_MEDIA_STREAM + mediaStream.label());
                        }

                        /**
                         * Called when a data channel is added. Simply logs.
                         * @param dataChannel The data channel to be added.
                         */
                        @Override
                        public void onDataChannel(final DataChannel dataChannel) {
                            Log.i(TAG, "On data channel: " + dataChannel.label());
                            logCallState(
                                    WebRTCLib.CALL_EVENT_ADD_DATA_CHANNEL + dataChannel.label());
                            new Thread(new Runnable() {
                                public void run() {
                                    synchronized (RTCConnection.this) {
                                        if (mShutdown) {
                                            return;
                                        }
                                        if (mDataChannel != null) {
                                            return;
                                        }
                                        if (dataChannel.label().equals(WebRTCLib.DATA_CHANNEL)) {
                                            dataChannel.registerObserver(
                                                    new DataChannel.Observer() {
                                                        /**
                                                         * Called when the amount of buffered data
                                                         * is changed. The amount is ignored.
                                                         * @param l The bytes buffered.
                                                         */
                                                        @Override
                                                        public void onBufferedAmountChange(long l) {
                                                        }

                                                        /**
                                                         * Called when the state changes. The state
                                                         * is ignored.
                                                         */
                                                        @Override
                                                        public void onStateChange() {
                                                        }

                                                        /**
                                                         * Called when a new message is received.
                                                         * Sends the message to the listener.
                                                         * @param buffer The message buffer.
                                                         */
                                                        @Override
                                                        public void onMessage(DataChannel.Buffer buffer) {
                                                            byte[] b = new byte[buffer.data.remaining()];
                                                            buffer.data.get(b);
                                                            mListener.onMessage(RTCConnection.this, b);
                                                        }
                                                    });
                                            mDataChannel = dataChannel;
                                        }
                                    }
                                    processMessages();
                                }
                            }).start();
                        }

                        /**
                         * Called when a renegotiation is needed. Simply logs.
                         */
                        @Override
                        public void onRenegotiationNeeded() {
                            Log.v(TAG, "Renegotiation needed");
                            logCallState(WebRTCLib.CALL_EVENT_RENEGOTIATION_NEEDED);
                        }

                        /**
                         * Called when a track is added. Simply logs.
                         * @param rtpReceiver The receiver added.
                         * @param mediaStreams The media streams to be added.
                         */
                        @Override
                        public void onAddTrack(RtpReceiver rtpReceiver,
                                MediaStream[] mediaStreams) {
                            rtpReceiver.track().setEnabled(true);
                            Log.i(TAG,
                                    "Add receiver " + rtpReceiver + " for " + mediaStreams.length);
                            logCallState(WebRTCLib.CALL_EVENT_RECEIVER + rtpReceiver.track().id());
                        }
                    });

            if (!mIsAnswer) {
                MediaStream mediaStream = mPeerConnectionFactory.createLocalMediaStream(
                        WebRTCLib.LOCAL_MEDIA_SOURCE);
                VideoTrack videoTrack =
                        mPeerConnectionFactory.createVideoTrack(WebRTCLib.VIDEO_TRACK,
                                mPeerConnectionFactory.createVideoSource(
                                        mListener.createVideoCapturer(this)));
                videoTrack.setEnabled(true);
                mediaStream.addTrack(videoTrack);
                mPeerConnection.addStream(mediaStream);
                mDataChannel = mPeerConnection.createDataChannel(WebRTCLib.DATA_CHANNEL, new DataChannel.Init());
                mDataChannel.registerObserver(
                        new DataChannel.Observer() {
                            /**
                             * Called when the amount of buffered data is changed. The amount is ignored.
                             * @param l The bytes buffered.
                             */
                            @Override
                            public void onBufferedAmountChange(long l) {
                            }

                            /**
                             * Called when the state changes. The state is ignored.
                             */
                            @Override
                            public void onStateChange() {
                            }

                            /**
                             * Called when a new message is received. Sends the message to the listener.
                             * @param buffer The message buffer.
                             */
                            @Override
                            public void onMessage(DataChannel.Buffer buffer) {
                                byte[] b = new byte[buffer.data.remaining()];
                                buffer.data.get(b);
                                mListener.onMessage(RTCConnection.this, b);
                            }
                        });

                SdpObserver observer = new SdpObserver() {
                    /**
                     * On the successful creation of the local offer, the local offer is set.
                     * @param sessionDescription The description.
                     */
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        synchronized (RTCConnection.this) {
                            if (mShutdown) {
                                return;
                            }
                            Log.i(TAG, "Create offer session: " + sessionDescription);
                            mPeerConnection.setLocalDescription(this, sessionDescription);
                            logCallState(WebRTCLib.CALL_EVENT_CREATE_OFFER_SESSION);
                        }
                    }

                    /**
                     * On the successful creation of the local offer, set it.
                     */
                    @Override
                    public void onSetSuccess() {
                        WebRTCSession session;
                        synchronized (RTCConnection.this) {
                            if (mShutdown) {
                                return;
                            }
                            Log.i(TAG, "Set local offer session");
                            session = WebRTCLib.sessionToData(
                                    mPeerConnection.getLocalDescription());
                            logCallState(WebRTCLib.CALL_EVENT_SET_OFFER_SESSION);
                        }
                        mListener.onCreateOfferSession(RTCConnection.this, session);
                        iceCandidateValidate();
                    }

                    /**
                     * Called on failure.
                     * @param s Error string.
                     */
                    @Override
                    public void onCreateFailure(String s) {
                        Log.e(TAG, "Failed to create session, error: " + s);
                        logCallState(WebRTCLib.CALL_CREATE_FAILURE + s);
                    }

                    /**
                     * Called on failure.
                     * @param s Error string.
                     */
                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "Failed to set session, error: " + s);
                        logCallState(WebRTCLib.CALL_SET_FAILURE + s);
                    }
                };
                mPeerConnection.createOffer(observer, constraints);
            }
        }
    }

    /**
     * Get if connection is the answer side, e.g. the companion side.
     * @return True if an answer.
     */
    @SuppressWarnings("WeakerAccess")
    public boolean isAnswer() {
        return mIsAnswer;
    }

    /**
     * Get the call uuid for the connection.
     * @return The call uuid.
     */
    public String getCallUuid() {
        return mCallUuid;
    }
    /**
     * Get the robot uuid for the connection.
     * @return The robot uuid.
     */
    public String getRobotUuid() {
        return mRobotUuid;
    }
    /**
     * Get the user uuid for the connection.
     * @return The user uuid.
     */
    public String getUserUuid() {
        return mUserUuid;
    }

    /**
     * Create a new logged call state.
     */
    public void logKeepAlive() {
        logCallState(WebRTCLib.CALL_EVENT_KEEP_ALIVE);
    }

    // Locks for the logging. Logging calls can be interleaved, but if they happen
    // during a shutdown then the system will crash.
    private final Object mLogLock = new Object();
    private int mLoggerCount = 0;

    /**
     * Shutdown the connection and dispose of it.
     */
    public void shutdown() {
        synchronized (this) {
            while (true) {
                synchronized (mLogLock) {
                    if (mShutdown) {
                        return;
                    }
                    if (mLoggerCount == 0) {
                        mShutdown = true;
                        break;
                    }
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
        // Shutdown
        mPeerConnection.dispose();
        mPeerConnectionFactory.dispose();
        mDataChannel = null;
    }

    /**
     * Get if the connection is shutdown.
     * @return True if shutdown.
     */
    public boolean isShutdown() {
        return mShutdown;
    }

    /**
     * Logs the state of a call.
     * @param event An event string to be stored in the log.
     */
    private void logCallState(@NonNull final String event) {
        Log.i(TAG, "Logs call state: " + event);
        synchronized (mLogLock) {
            if (mShutdown || mPeerConnection == null) {
                WebRTCLib.logEmptyCall(mParent, mIsAnswer, mCallUuid, event);
                return;
            }
            mLoggerCount++;
        }
        mPeerConnection.getStats(new RTCStatsCollectorCallback() {
            @Override
            public void onStatsDelivered(RTCStatsReport rtcStatsReport) {
                if (mShutdown) {
                    WebRTCLib.logEmptyCall(mParent, mIsAnswer, mCallUuid, event);
                    return;
                }
                WebRTCLib.logCallConnection(mIsAnswer, mParent, mCallUuid,
                        event, mPeerConnection, rtcStatsReport);
            }
        });
        synchronized (mLogLock) {
            mLoggerCount--;
        }
    }

    /**
     * Set the remote ICE candidates from a map of candidates. This function caches the latest
     * ICE candidates until the connection is actually ready.
     * @param iceCandidates The remote ice candidates map.
     */
    public void updateRemoteIceCandidates(Map<String, ai.cellbots.common.data.IceCandidate> iceCandidates) {
        synchronized (this) {
            if (!mIceCandidateReady) {
                mIceCandidateBuffer = new HashMap<>(iceCandidates);
                return;
            }
            if (mShutdown) {
                return;
            }
            WebRTCLib.updateIceCandidates(
                    mPeerConnection, mIceCandidates, iceCandidates,
                    new WebRTCLib.IceCandidateCallback() {
                        @Override
                        public void onIceCandidateAdded(
                                ai.cellbots.common.data.IceCandidate candidate) {
                            logCallState(
                                    WebRTCLib.CALL_EVENT_ADD_REMOTE_ICE_CANDIDATE
                                            + candidate.computeUuid());
                        }
                    });
        }
    }

    /**
     * Determines if the connection is actually ready to accept ICE candidates. It is if both the
     * remote and local session descriptions are set and mSetRemote is true. If the ice candidates
     * are still buffered, then updateRemoteIceCandidates is called on the buffer before it is
     * cleared out.
     */
    private synchronized void iceCandidateValidate() {
        if (mShutdown) {
            return;
        }
        if (!mSetRemote) {
            return;
        }
        if (mPeerConnection.getLocalDescription() == null) {
            return;
        }
        if (mPeerConnection.getRemoteDescription() == null) {
            return;
        }
        mIceCandidateReady = true;
        if (mIceCandidateBuffer != null) {
            updateRemoteIceCandidates(mIceCandidateBuffer);
            mIceCandidateBuffer = null;
        }
    }

    /**
     * Sets the remote offer for the answer session.
     * @param session The remote session description.
     */
    @SuppressWarnings("WeakerAccess")
    public synchronized void setRemoteOffer(@NonNull WebRTCSession session) {
        if (mShutdown) {
            return;
        }
        if (!mIsAnswer) {
            return;
        }
        if (mSetRemote) {
            return;
        }
        mSetRemote = true;

        final SdpObserver createAnswerObserver = new SdpObserver() {
            /**
             * After the answer is created, we set it here.
             * @param sessionDescription Session description.
             */
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.i(TAG, "Created answer successfully");
                synchronized (RTCConnection.this) {
                    if (mShutdown) {
                        return;
                    }
                    mPeerConnection.setLocalDescription(this, sessionDescription);
                    logCallState(WebRTCLib.CALL_EVENT_CREATE_ANSWER_SESSION);
                }
            }

            /**
             * On setting of the answer session, forward to listener.
             */
            @Override
            public void onSetSuccess() {
                WebRTCSession session;
                Log.i(TAG, "Set local session successfully");
                synchronized (RTCConnection.this) {
                    if (mShutdown) {
                        return;
                    }
                    session = WebRTCLib.sessionToData(mPeerConnection.getLocalDescription());
                    logCallState(WebRTCLib.CALL_EVENT_SET_REMOTE_OFFER);
                }
                mListener.onCreateAnswerSession(RTCConnection.this, session);
                iceCandidateValidate();
            }

            /**
             * Called on failure.
             * @param s Error string.
             */
            @Override
            public void onCreateFailure(String s) {
                Log.w(TAG, "Error creating local session: " + s);
                logCallState(WebRTCLib.CALL_CREATE_FAILURE + s);
            }

            /**
             * Called on failure.
             * @param s Error string.
             */
            @Override
            public void onSetFailure(String s) {
                Log.w(TAG, "Error setting local session: " + s);
                logCallState(WebRTCLib.CALL_SET_FAILURE + s);
            }
        };

        final SdpObserver setRemoteObserver = new SdpObserver() {
            /**
             * An error if called.
             * @param sessionDescription Session description.
             */
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.w(TAG, "Created answer in set remote");
            }

            /**
             * Called once the remote offer session is set to create the remote offer.
             */
            @Override
            public void onSetSuccess() {
                Log.i(TAG, "Set remote session successfully");
                synchronized (RTCConnection.this) {
                    if (mShutdown) {
                        return;
                    }
                    MediaConstraints constraints = new MediaConstraints();
                    mPeerConnection.createAnswer(createAnswerObserver, constraints);
                }
                logCallState(WebRTCLib.CALL_EVENT_SET_ANSWER_SESSION);
                iceCandidateValidate();
            }

            /**
             * Called on failure.
             * @param s Error string.
             */
            @Override
            public void onCreateFailure(String s) {
                Log.w(TAG, "Error creating local session: " + s);
                logCallState(WebRTCLib.CALL_CREATE_FAILURE + s);
            }

            /**
             * Called on failure.
             * @param s Error string.
             */
            @Override
            public void onSetFailure(String s) {
                Log.w(TAG, "Error setting local session: " + s);
                logCallState(WebRTCLib.CALL_SET_FAILURE + s);
            }
        };

        mPeerConnection.setRemoteDescription(setRemoteObserver,
                WebRTCLib.sessionFromData(session));

        Log.i(TAG, "Finished set remote session method");
    }

    /**
     * Set a remote answer session.
     * @param session The session description to set.
     */
    public synchronized void setRemoteAnswer(@NonNull WebRTCSession session) {
        if (mShutdown) {
            return;
        }
        if (mIsAnswer) {
            return;
        }
        if (mSetRemote) {
            return;
        }
        mSetRemote = true;

        final SdpObserver setRemoteObserver = new SdpObserver() {
            /**
             * An error if called.
             * @param sessionDescription The created session.
             */
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.w(TAG, "Created answer in set remote");
            }

            /**
             * Called on successful set of the remote answer.
             */
            @Override
            public void onSetSuccess() {
                Log.i(TAG, "Set remote session successfully");
                logCallState(WebRTCLib.CALL_EVENT_SET_REMOTE_ANSWER);
                iceCandidateValidate();
            }

            /**
             * Called on failure.
             * @param s Error string.
             */
            @Override
            public void onCreateFailure(String s) {
                Log.w(TAG, "Error creating local session: " + s);
                logCallState(WebRTCLib.CALL_CREATE_FAILURE + s);
            }

            /**
             * Called on failure.
             * @param s Error string.
             */
            @Override
            public void onSetFailure(String s) {
                Log.w(TAG, "Error setting local session: " + s);
                logCallState(WebRTCLib.CALL_SET_FAILURE + s);
            }
        };

        mPeerConnection.setRemoteDescription(setRemoteObserver,
                WebRTCLib.sessionFromData(session));
    }

    /**
     * Get if we have set the remote session.
     * @return True if we have set the remote session.
     */
    public boolean haveSetRemote() {
        return mSetRemote;
    }
}
