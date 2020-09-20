package ai.cellbots.robot.control;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ai.cellbots.common.EventProcessor;
import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.cloud.RTCDatabaseChannel;
import ai.cellbots.common.data.WebRTCCall;
import ai.cellbots.common.webrtc.CloudWebRTCListener;
import ai.cellbots.common.webrtc.RTCConnection;
import ai.cellbots.common.webrtc.WebRTCLib;
import ai.cellbots.robot.state.RobotSessionGlobals;
import ai.cellbots.robot.vision.CameraImage;

/**
 * Manage a set of WebRTC calls for a robot. A robot can transmit over many WebRTC calls to any
 * number of devices, limited only by the network bandwidth. The process works as follows:
 * - Call is written to firebase, containing only a UUID and timestamp
 * - A local Call object is created
 * - The Call object creates an Offer session description in firebase
 * - The Call object is updated when an Answer session is written in firebase
 * - Image transmission starts
 * - When a firebase call has not been update for UPDATE_TIMEOUT, the call is removed from firebase
 * - When the firebase call is deleted, the Call is shutdown and disposed locally
 */
public class CloudWebRTCManager implements ThreadedShutdown {
    private final static String TAG = CloudWebRTCManager.class.getSimpleName();
    private final Context mParent;
    private final RobotSessionGlobals mSession;
    private ValueEventListener mValueEventListener = null;
    private final Set<String> mLastCalls = new HashSet<>(); // Store the list of calls in firebase
    private final Map<String, Call> mCallStorage = new HashMap<>(); // The list of calls locally
    private final RTCDatabaseChannel mDatabaseChannel;
    private final EventProcessor mUpdateEventProcessor;
    private final EventProcessor mFrameEventProcessor;
    @SuppressWarnings("unused")
    private final RTCDataListener mDataListener;
    private boolean mShutdown = false;
    private CameraImage mCameraImage;
    private boolean mNeedsImage = true;

    private static final int WEBRTC_WIDTH = 1920;
    private static final int WEBRTC_HEIGHT = 1080;
    private static final int WEBRTC_FRAMERATE = 30;

    /**
     * Called when data is updated from WebRTC.
     */
    public interface RTCDataListener {
        /**
         * Called when data is updated from WebRTC.
         *
         * @param webRTCManager The manager.
         * @param dataChannel The data channel.
         */
        @SuppressWarnings("unused")
        void onDataUpdate(CloudWebRTCManager webRTCManager, RTCDatabaseChannel dataChannel);
    }

    /**
     * The internal call storage for each call used by the system.
     */
    private final class Call implements VideoCapturer {
        private final Context mParent;
        private WebRTCCall mCall = null;
        private long mLastUpdate;
        private RTCConnection mConnection = null;
        private boolean mShutdown = false;
        private boolean mConnectingStarted = false;

        private VideoCapturer.CapturerObserver mCapturerObserver = null;
        private boolean mCaptureStarted;

        private final RTCConnection.Listener mListener = new CloudWebRTCListener() {
            /**
             * Called when a connection is terminated. Does nothing.
             *
             * @param connection The connection for the event.
             */
            @Override
            public void onTermination(@NonNull RTCConnection connection) {
                Log.i(TAG, "Call terminated: " + connection.getCallUuid());
                mDatabaseChannel.terminateConnection(connection);
                mUpdateEventProcessor.onEvent();
            }

            /**
             * Create video renderer. Null since the robot does not render videos.
             *
             * @param connection The connection for the event.
             * @return Always null.
             */
            @Override
            public VideoRenderer createVideoRenderer(@NonNull RTCConnection connection) {
                return null;
            }

            /**
             * Creates a video capturer.
             *
             * @param connection The connection for the event.
             * @return The video capturer, which is the call object itself.
             */
            @Override
            public VideoCapturer createVideoCapturer(@NonNull RTCConnection connection) {
                return Call.this;
            }

            /**
             * Called on creation of an RTC connection.
             */
            @Override
            public void onCreateConnection(@NonNull RTCConnection connection) {
                synchronized (Call.this) {
                    if (mConnection == null && !mShutdown && mConnectingStarted) {
                        mConnection = connection;
                        startCapture(WEBRTC_WIDTH, WEBRTC_HEIGHT, WEBRTC_FRAMERATE);
                        mDatabaseChannel.onNewConnection(connection);
                        mUpdateEventProcessor.onEvent();
                        return;
                    }
                }
                // An invalid connection has been created, ether because we created a duplication
                // connection, were shutdown or are still waiting for connecting to start. If so we
                // shutdown the invalid connection to avoid building up garbage connections.
                connection.shutdown();
            }

            /**
             * Called when a message is received by the RTC connection.
             *
             * @param connection The connection for the event.
             * @param message The message data for the event.
             */
            @Override
            public void onMessage(@NonNull RTCConnection connection, byte[] message) {
                Log.i(TAG, "Message: " + new String(message));
                mDatabaseChannel.onMessage(connection, message);
                mUpdateEventProcessor.onEvent();
            }
        };

        /**
         * Create a call object.
         *
         * @param parent The parent context.
         * @param call The WebRTCCall itself.
         */
        private Call(@NonNull Context parent, @NonNull WebRTCCall call) {
            mParent = parent;
            mCall = null;
            setCall(call);
            mLastUpdate = new Date().getTime();
        }

        /**
         * Set the latest data from firebase.
         *
         * @param call The call read from firebase.
         */
        private synchronized void setCall(@NonNull WebRTCCall call) {
            long currentTime = new Date().getTime();
            if (mShutdown) {
                // Call shutdown again, since we may have set mShutdown = true but still have a call
                shutdown();
                return;
            }
            if (mCall == null || call.getTimestamp() != mCall.getTimestamp()) {
                if (mConnection != null) {
                    mConnection.logKeepAlive();
                }
                mLastUpdate = currentTime;
                Log.v(TAG, "Update timestamp for call: " + call.getUuid());
            }
            mCall = call;

            if (call.getOfferSession() == null
                    && call.getAnswerSession() == null
                    && mConnection == null && !mConnectingStarted) {
                Log.i(TAG, "Offering " + call.getUuid());

                WebRTCLib.logCallStart(mParent, call);

                mConnectingStarted = true;
                // Create new RTC connection, isAnswer = false since this is the robot / offer side.
                RTCConnection.createRTCConnection(mParent, false,
                        call.getUserUuid(), call.getRobotUuid(), call.getUuid(), mListener);
            } else if (call.getOfferSession() != null
                    && call.getAnswerSession() != null
                    && mConnection != null) {
                if (!mConnection.haveSetRemote()) {
                    mConnection.setRemoteAnswer(call.getAnswerSession());
                }
                mConnection.updateRemoteIceCandidates(call.getAnswerIceCandidates());
            }
        }

        /**
         * Check if the call has expired.
         *
         * @param expireTime The time for expiration if the last update is before this time.
         */
        private synchronized void checkExpire(long expireTime) {
            if (mLastUpdate < expireTime) {
                Log.i(TAG, "Call expired: " + mCall.getUuid());
                WebRTCLib.logCallExpire(mParent, mCall);
                FirebaseDatabase.getInstance().getReference("robot_goals")
                        .child(mCall.getUserUuid()).child(mCall.getRobotUuid())
                        .child("webrtc").child(mCall.getUuid()).removeValue();
            }
        }

        /**
         * Shutdown the connection. Note that this will not remove the call from firebase, that
         * can only occur if the call expires.
         */
        private synchronized void shutdown() {
            mShutdown = true;
            if (mConnection != null) {
                mConnection.shutdown();
                mConnection = null;
            }
        }

        /**
         * Initialize the video capturer interface.
         *
         * @param surfaceTextureHelper The surface texture helper, currently ignored.
         * @param context The parent context, ignored.
         * @param capturerObserver The capture observer from the current call.
         */
        @Override
        public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context context,
                               VideoCapturer.CapturerObserver capturerObserver) {
            mCapturerObserver = capturerObserver;
        }

        /**
         * Start capturing the video.
         *
         * @param width The width of the video. Ignored.
         * @param height The height of the video. Ignored.
         * @param framerate The framerate of the video. Ignored.
         */
        @Override
        public void startCapture(int width, int height, int framerate) {
            Log.i(TAG, "Capturer: Start with format " + width + " " + height + " " + framerate);
            mCapturerObserver.onCapturerStarted(true);
            mCaptureStarted = true;
        }

        /**
         * Stop capturing the video.
         *
         * @throws InterruptedException If interrupted.
         */
        @Override
        public void stopCapture() throws InterruptedException {
            Log.i(TAG, "Stop capture");
            mCapturerObserver.onCapturerStopped();
            mCaptureStarted = false;
        }

        /**
         * Set the capture format of the video.
         *
         * @param width The width of the new video. Ignored.
         * @param height The height of the new video. Ignored.
         * @param framerate The framerate of the new video. Ignored.
         */
        @Override
        public void changeCaptureFormat(int width, int height, int framerate) {
            Log.i(TAG, "Capturer: Change format " + width + " " + height + " " + framerate);
        }

        /**
         * Dispose of the video capturer.
         */
        @Override
        public void dispose() {
            Log.i(TAG, "Dispose capturer");
            mCaptureStarted = false;
        }

        /**
         * Returns true if the video is a screencast. Always false.
         *
         * @return True if a screencast.
         */
        @Override
        public boolean isScreencast() {
            return false;
        }

        /**
         * Send an image out over the video call.
         *
         * @param buffer The image byte buffer.
         * @param width The width of the image.
         * @param height The height of the image.
         */
        private void sendImage(byte[] buffer, int width, int height) {
            VideoCapturer.CapturerObserver obs = mCapturerObserver;
            if (obs != null && mCaptureStarted) {
                //Log.v(TAG, "Send image width: " + width + " height: " + height);
                obs.onByteBufferFrameCaptured(buffer, width, height, 0, new Date().getTime());
            } else {
                if (obs == null) {
                    Log.v(TAG, "Ignore image because obs = null");
                }
                if (!mCaptureStarted) {
                    Log.v(TAG, "Ignore image because capture has been stopped");
                }
            }
        }
    }

    /**
     * Create a WebRTCManager.
     *
     * @param parent The parent context.
     * @param session The robot session.
     * @param databaseChannel The database channel.
     * @param dataListener The data listener.
     */
    public CloudWebRTCManager(Context parent, RobotSessionGlobals session,
                              RTCDatabaseChannel databaseChannel, RTCDataListener dataListener) {
        mParent = parent;
        mDatabaseChannel = databaseChannel;
        mSession = session;
        mDataListener = dataListener;
        mUpdateEventProcessor = new EventProcessor(TAG + "Update", new EventProcessor.Processor() {
            @Override
            public boolean update() {
                mDatabaseChannel.update(mSession.getUserUuid(), mSession.getRobotUuid());
                return false;
            }

            @Override
            public void shutdown() {
            }
        });

        mFrameEventProcessor = new EventProcessor(TAG + "Frame", new EventProcessor.Processor() {
            @Override
            public boolean update() {
                Log.v(TAG, "Begin image processing");
                CameraImage local;
                List<Call> calls = new LinkedList<>();
                synchronized (CloudWebRTCManager.this) {
                    local = mCameraImage;
                    calls.addAll(mCallStorage.values());
                }
                if (local != null && !calls.isEmpty()) {
                    for (Call call : calls) {
                        call.sendImage(local.getBytes(), local.getWidth(), local.getHeight());
                    }
                }
                synchronized (CloudWebRTCManager.this) {
                    mNeedsImage = true;
                }
                Log.v(TAG, "Complete image processing");
                return false;
            }

            @Override
            public void shutdown() {
            }
        });

        FirebaseDatabase.getInstance().getReference("robot_goals").child(session.getUserUuid())
                .child(session.getRobotUuid()).child("webrtc").addValueEventListener(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        updateDataState(dataSnapshot);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                    }
                });
    }

    /**
     * Clean up the stored calls to remove invalid calls.
     */
    private synchronized void cleanupCalls() {
        long currentTime = new Date().getTime();
        long expireTime = currentTime - WebRTCCall.UPDATE_TIMEOUT;

        LinkedList<String> blacklist = new LinkedList<>();

        for (Map.Entry<String, Call> call : mCallStorage.entrySet()) {
            call.getValue().checkExpire(expireTime);
            if (!mLastCalls.contains(call.getKey())) {
                blacklist.add(call.getKey());
            }
        }

        for (String call : blacklist) {
            if (mCallStorage.containsKey(call)) {
                Log.i(TAG, "Call terminated: " + call);
                mCallStorage.get(call).shutdown();
                mCallStorage.remove(call);
            }
        }
    }

    /**
     * Called to update the state of the calls.
     *
     * @param snapshot The data snapshot.
     */
    private synchronized void updateDataState(DataSnapshot snapshot) {
        if (mShutdown) {
            return;
        }

        Log.v(TAG, "Update calls, count: " + snapshot.getChildrenCount());
        mLastCalls.clear();
        for (DataSnapshot callSnapshot : snapshot.getChildren()) {
            final WebRTCCall call = WebRTCCall.fromFirebase(mSession.getUserUuid(),
                    mSession.getRobotUuid(), callSnapshot);
            //Log.i(TAG, "Call: " + call);

            mLastCalls.add(call.getUuid());
            if (mCallStorage.containsKey(call.getUuid())) {
                mCallStorage.get(call.getUuid()).setCall(call);
            } else {
                mCallStorage.put(call.getUuid(), new Call(mParent, call));
            }
        }

        cleanupCalls();
    }

    /**
     * Shutdown the system.
     */
    @Override
    public synchronized void shutdown() {
        mShutdown = true;
        // Close all transmitters.
        for (Call call : mCallStorage.values()) {
            call.shutdown();
        }
        mCallStorage.clear();
        // Stop all value event listeners.
        if (mValueEventListener != null) {
            FirebaseDatabase.getInstance().getReference("robot_goals").child(mSession.getUserUuid())
                    .child(mSession.getRobotUuid()).child("webrtc")
                    .removeEventListener(mValueEventListener);
            mValueEventListener = null;
        }
        mUpdateEventProcessor.shutdown();
        mFrameEventProcessor.shutdown();
    }

    /**
     * Wait for the system to shutdown.
     */
    public synchronized void waitShutdown() {
        shutdown();
        mUpdateEventProcessor.waitShutdown();
        mFrameEventProcessor.waitShutdown();
    }

    /**
     * Send an image to the calls.
     *
     * @param image The camera image.
     */
    public synchronized void sendImage(CameraImage image) {
        if (mNeedsImage && hasCall() && image.getFormat() == CameraImage.Format.Y_CR_CB_420) {
            mCameraImage = new CameraImage(image,
                    mCameraImage != null ? mCameraImage.getBytes() : null);
            mNeedsImage = false;
            mFrameEventProcessor.onEvent();
        }
    }

    /**
     * Returns whether a call exists. Used for determining if we should undertake processing
     * or transmitting an image.
     *
     * @return True if a call exists.
     */
    public boolean hasCall() {
        return !mCallStorage.isEmpty();
    }
}
