package ai.cellbots.common.webrtc;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;

import ai.cellbots.common.Strings;
import ai.cellbots.common.cloud.RTCDatabaseChannel;
import ai.cellbots.common.data.Robot;
import ai.cellbots.common.data.WebRTCCall;


/**
 * A view for WebRTC calls. Uses OpenGL shaders to render YUV frames from the WebRTC call.
 */
public class WebRTCView extends GLSurfaceView  {
    private static final String TAG = WebRTCView.class.getSimpleName();
    private final Context mParent; // The parent context.
    private RTCConnection mPeerConnectionContainer; // Connection to the call
    private ValueEventListener mAnswerListener; // The listener for firebase
    private String mUserUuid; // The user uuid
    private String mRobotUuid; // The robot uuid
    private boolean mIsVisible = true; // True if view is visible
    private WebRTCCall mWebRTCCall = null; // The current call

    private RTCDatabaseChannel mDatabaseChannel = null;

    private final WebRTCRenderer mYuvRender = new WebRTCRenderer(new WebRTCRenderer.Listener() {
        @Override
        public void onFrame() {
            requestRender();
        }

        @Override
        public void updateSize(int width, int height) {
            Log.i(TAG, "Screen size updated: " + width + " " + height);
        }
    });

    /**
     * Sets the database channel. Can only be called once per WebRTCView.
     * @param databaseChannel The database channel for WebRTC database forwarding.
     */
    public synchronized void setDatabaseChannel(RTCDatabaseChannel databaseChannel) {
        if (mDatabaseChannel == null) {
            mDatabaseChannel = databaseChannel;
        } else {
            throw new IllegalStateException("DatabaseChannel can only be set once.");
        }
    }

    /**
     * Create a new video renderer.
     * @param callUuid The callUUid.
     * @return The VideoRenderer.
     */
    private VideoRenderer createVideoRenderer(String callUuid) {
        return new VideoRenderer(mYuvRender.getVideoRenderer(callUuid));
    }

    /**
     * Setup OpenGL renderer.
     */
    private void setupGlState() {
        setPreserveEGLContextOnPause(true);
        setEGLContextClientVersion(2);
        setRenderer(mYuvRender);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    /**
     * Create the WebRTCView view.
     * @param context The context.
     */
    public WebRTCView(Context context) {
        super(context);
        mParent = context;
        setupGlState();
    }

    /**
     * Create the WebRTCView.
     * @param context The parent context.
     * @param attrs The attributes.
     */
    public WebRTCView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mParent = context;
        setupGlState();
    }

    /**
     * Set the current call robot.
     * @param userUuid The user uuid.
     * @param r The robot object.
     */
    public synchronized void setCallRobot(final String userUuid, Robot r) {
        setCallRobot(userUuid, r, false);
    }

    /**
     * Set the current call robot.
     * @param userUuid The user uuid.
     * @param r The robot object.
     * @param forceSession True if we should force a call restart.
     */
    private synchronized void setCallRobot(final String userUuid, Robot r, boolean forceSession) {
        final String robotUuid = (r != null) ? r.uuid : null;
        setCallRobot(userUuid, robotUuid, forceSession);
    }

    private final RTCConnection.Listener mRTCListener = new CloudWebRTCListener() {
        /**
         * Called when the call is terminated. If the connection is still the current connection,
         * this forces a restart of the call. For example, if the robot was stopped and started
         * again, then the video view will keep running.
         * @param connection The connection for the event.
         */
        @Override
        public void onTermination(@NonNull RTCConnection connection) {
            Log.i(TAG, "RTC Call terminated");
            RTCDatabaseChannel channel = mDatabaseChannel;
            if (channel != null) {
                channel.terminateConnection(connection);
            }
            synchronized (WebRTCView.this) {
                if (mPeerConnectionContainer == connection) {
                    Log.i(TAG, "Force restart for broken call");
                    setCallRobot(mUserUuid, mRobotUuid, true);
                }
            }
            requestRender();
        }

        /**
         * Creates the video renderer.
         * @param connection The connection for the event.
         * @return A new video renderer from the WebRTCView.
         */
        @Override
        public VideoRenderer createVideoRenderer(@NonNull RTCConnection connection) {
            return WebRTCView.this.createVideoRenderer(connection.getCallUuid());
        }

        /**
         * Creates the video capturer. Should not be called.
         * @param connection The connection for the event.
         * @return Always null.
         */
        @Override
        public VideoCapturer createVideoCapturer(@NonNull RTCConnection connection) {
            return null;
        }

        /**
         * Called on creation of an RTC connection.
         */
        @Override
        public void onCreateConnection(@NonNull RTCConnection connection) {
            onRTCConnectionCreated(connection);
            RTCDatabaseChannel channel = mDatabaseChannel;
            if (channel != null) {
                channel.onNewConnection(connection);
            }
        }

        /**
         * Called when a message is received by the RTC connection.
         * @param connection The connection for the event.
         * @param message The message data for the event.
         */
        @Override
        public void onMessage(@NonNull RTCConnection connection, byte[] message) {
            RTCDatabaseChannel channel = mDatabaseChannel;
            if (channel != null) {
                channel.onMessage(connection, message);
            }
        }
    };

    /**
     * Set the current call robot. Actually creates the call, connection and keep alive thread.
     * @param userUuid The user uuid.
     * @param robotUuid The robot uuid.
     * @param forceSession True if we should force a call restart.
     */
    private synchronized void setCallRobot(final String userUuid, final String robotUuid, boolean forceSession) {
        boolean newSession = forceSession;

        if (!Strings.compare(mUserUuid, userUuid)) {
            newSession = true;
        }
        if (!Strings.compare(mRobotUuid, robotUuid)) {
            newSession = true;
        }
        if (!newSession) {
            return;
        }

        mWebRTCCall = null;

        String removeCall = null;
        if (mPeerConnectionContainer != null) {
            if (mDatabaseChannel != null) {
                mDatabaseChannel.terminateConnection(mPeerConnectionContainer);
            }
            removeCall = mPeerConnectionContainer.getCallUuid();
            mPeerConnectionContainer.shutdown();
            mPeerConnectionContainer = null;
            mYuvRender.setCallUuid(null);
        }

        if (mAnswerListener != null && mUserUuid != null && mRobotUuid != null
                && removeCall != null) {
            FirebaseDatabase.getInstance().getReference("robot_goals")
                    .child(mUserUuid).child(mRobotUuid).child("webrtc")
                    .child(removeCall).removeEventListener(mAnswerListener);
            mAnswerListener = null;
        }

        mUserUuid = userUuid;
        mRobotUuid = robotUuid;

        RTCDatabaseChannel databaseChannel = mDatabaseChannel;
        if (databaseChannel != null) {
            databaseChannel.update(mUserUuid, mRobotUuid);
        }

        if (userUuid == null || robotUuid == null || !mIsVisible) {
            return;
        }

        final WebRTCCall webRTCCall = new WebRTCCall(userUuid, robotUuid);
        final String callUuid = webRTCCall.getUuid();
        mWebRTCCall = webRTCCall;
        RTCConnection.createRTCConnection(mParent, true, userUuid, robotUuid, callUuid, mRTCListener);
    }

    private synchronized void onRTCConnectionCreated(final RTCConnection container) {
        final WebRTCCall webRTCCall = mWebRTCCall;
        final String callUuid = container.getCallUuid();
        final String userUuid = container.getUserUuid();
        final String robotUuid = container.getRobotUuid();
        if (webRTCCall == null || !Strings.compare(webRTCCall.getUuid(), callUuid)) {
            container.shutdown();
            return;
        }

        mPeerConnectionContainer = container;

        mAnswerListener = FirebaseDatabase.getInstance().getReference("robot_goals")
                .child(userUuid).child(robotUuid).child("webrtc")
                .child(callUuid).addValueEventListener(
                        new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                if (dataSnapshot.getValue() == null
                                        || dataSnapshot.getKey() == null) {
                                    return;
                                }
                                WebRTCCall call = WebRTCCall.fromFirebase(
                                        userUuid, robotUuid, dataSnapshot);
                                container.updateRemoteIceCandidates(call.getOfferIceCandidates());

                                if (call.getOfferSession() != null) {
                                    container.setRemoteOffer(call.getOfferSession());
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {
                            }
                        });

        Thread keepAlive = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    Log.i(TAG, "Keep alive");
                    if (container != mPeerConnectionContainer) {
                        Log.i(TAG, "Keep alive terminated for call: " + callUuid);
                        break;
                    }
                    FirebaseDatabase.getInstance().getReference("robot_goals")
                            .child(userUuid).child(robotUuid).child("webrtc")
                            .child(callUuid).child(WebRTCCall.TIMESTAMP)
                            .setValue(ServerValue.TIMESTAMP);
                    container.logKeepAlive();
                    try {
                        Thread.sleep(WebRTCCall.UPDATE_SPACING);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        });
        keepAlive.start();

        FirebaseDatabase.getInstance().getReference("robot_goals")
                .child(userUuid).child(robotUuid).child("webrtc")
                .child(callUuid).setValue(webRTCCall);

        mYuvRender.setCallUuid(callUuid);
    }

    /**
     * Called to set the visibility of the view. Causes the call to be stopped if the view is
     * made invisible or started if it is made visible.
     * @param visibility The visibility state.
     */
    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);

        boolean render = false;
        synchronized (this) {
            boolean oldVis = mIsVisible;

            mIsVisible = (visibility == VISIBLE);

            if (oldVis != mIsVisible) {
                render = true;
                setCallRobot(mUserUuid, mRobotUuid, true);
            }
        }
        if (render) {
            requestRender();
        }
    }
}