package ai.cellbots.common.cloud;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.Objects;

import ai.cellbots.common.Strings;

/**
 * Monitors a single key in the cloud, possibly keyed by the user and or the robot id. The monitor
 * handles the synchronization issues involved with changes to the user and robot ids if relevant
 * to the process of synchronization.
 */
public class CloudSingletonMonitor {
    private static final String TAG = CloudSingletonMonitor.class.getSimpleName();

    private final CloudPath mPath;
    private final Listener mListener;
    private final Object mLock;

    private ValueEventListener mValueEventListener = null;
    private String mUserUuid = null;
    private String mRobotUuid = null;
    private String mEntityUuid = null;
    private boolean mShutdown = false;

    private boolean mSuperOnUpdateCalled = false;
    private boolean mSuperOnShutdownCalled = false;

    private int mListenerCount = 0;

    /**
     * A listener for the singleton state.
     */
    public interface Listener {
        /**
         * Called when a new DataSnapshot
         * @param dataSnapshot The new data element.
         */
        void onDataSnapshot(DataSnapshot dataSnapshot);
        /**
         * Called after the listener is terminated.
         */
        void afterListenerTerminated();
        /**
         * Called before the listener is terminated.
         */
        void beforeListenerStarted();
    }

    /**
     * Create a new cloud singleton monitor.
     * @param lock The object to use for synchronization
     * @param path The path to monitor.
     * @param listener The listener for the object.
     */
    public CloudSingletonMonitor(@NonNull Object lock, @NonNull CloudPath path, @NonNull Listener listener) {
        Objects.requireNonNull(listener);
        Objects.requireNonNull(path);
        Objects.requireNonNull(lock);
        mPath = path;
        mListener = listener;
        mLock = lock;
    }

    /**
     * Create a new cloud singleton monitor without a listener.
     * @param lock The object to use for synchronization
     * @param path The path to monitor.
     */
    protected CloudSingletonMonitor(@NonNull Object lock, @NonNull CloudPath path) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(lock);
        mPath = path;
        mListener = null;
        mLock = lock;
    }

    /**
     * Computes if the system has the correct listener.
     * @param userUuid The user's uuid.
     * @param robotUuid The robot's uuid.
     * @param entityUuid The entity's uuid.
     * @return True if the system has the same user and robot.
     */
    private boolean isSameListener(String userUuid, String robotUuid, String entityUuid) {
        synchronized (mLock) {
            // Ignore the update if we already have the correct object.
            if (mPath.hasUserPath() && !Strings.compare(userUuid, mUserUuid)) {
                return false;
            }
            if (mPath.hasRobotPath() && !Strings.compare(robotUuid, mRobotUuid)) {
                return false;
            }
            if (mPath.hasEntityPath() && !Strings.compare(entityUuid, mEntityUuid)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the user uuid of the system.
     * @return The user uuid, or null.
     */
    final public String getUserUuid() {
        synchronized (mLock) {
            return mUserUuid;
        }
    }

    /**
     * Gets the robot uuid of the system.
     * @return The robot uuid, or null.
     */
    final public String getRobotUuid() {
        synchronized (mLock) {
            return mRobotUuid;
        }
    }

    /**
     * Gets the entity uuid of the system.
     * @return The entity uuid, or null.
     */
    @SuppressWarnings("unused")
    final public String getEntityUuid() {
        synchronized (mLock) {
            return mEntityUuid;
        }
    }

    /**
     * Shutdown the listener.
     */
    final public void shutdown() {
        synchronized (mLock) {
            mShutdown = true;
            if (!mSuperOnShutdownCalled) {
                onShutdown();
                if (!mSuperOnShutdownCalled) {
                    throw new Error("onShutdown() did not call super.onShutdown()");
                }
            }
            if (mValueEventListener != null) {
                mPath.getDatabaseReference(mUserUuid, mRobotUuid, mEntityUuid)
                        .removeEventListener(mValueEventListener);
                mValueEventListener = null;
                afterListenerTerminated();
            }
        }
    }

    /**
     * Called to update the state of the monitor.
     * @param userUuid The current user uuid.
     * @param robotUuid The current robot uuid.
     */
    final public void update(final String userUuid, final String robotUuid) {
        update(userUuid, robotUuid, null);
    }


    /**
     * Called to update the state of the monitor.
     * @param userUuid The current user uuid.
     * @param robotUuid The current robot uuid.
     * @param entityUuid The current entity uuid.
     */
    final public void update(final String userUuid, final String robotUuid, final String entityUuid) {
        synchronized (mLock) {
            // If we shutdown, do not do anything.
            if (mShutdown) {
                return;
            }
            mSuperOnUpdateCalled = false;
            onUpdate();
            if (!mSuperOnUpdateCalled) {
                throw new Error("onUpdate() did not call superclass with super.onUpdate()");
            }
            // If we did not change state user or robot and we have set up the listener,
            // then do not do anything.
            if (isSameListener(userUuid, robotUuid, entityUuid) && mValueEventListener != null) {
                mRobotUuid = robotUuid;
                mUserUuid = userUuid;
                return;
            }
            // If we have a listener on an invalid object, deactivate it.
            if (mValueEventListener != null) {
                mPath.getDatabaseReference(mUserUuid, mRobotUuid, mEntityUuid)
                        .removeEventListener(mValueEventListener);
                mListenerCount++;
                mValueEventListener = null;
                afterListenerTerminated();
            }
            mRobotUuid = robotUuid;
            mUserUuid = userUuid;
            mEntityUuid = entityUuid;

            // If we do not have required path component, do not create a new listener.
            if (mUserUuid == null && mPath.hasUserPath()) {
                return;
            }
            if (mRobotUuid == null && mPath.hasRobotPath()) {
                return;
            }
            beforeListenerStarted();

            final int listenerNumber = mListenerCount;
            Log.v(TAG, "Adding a new listener for " + mPath.toString());

            mValueEventListener = mPath.getDatabaseReference(userUuid, robotUuid, entityUuid)
                    .addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            synchronized (mLock) {
                                // If we shutdown, do not do anything.
                                if (mShutdown) {
                                    return;
                                }

                                // Ignore data elements from listeners that are created after
                                // the current listener has been shutdown.
                                if (listenerNumber != mListenerCount) {
                                    return;
                                }

                                // If we have the wrong system, bail out.
                                if (!isSameListener(userUuid, robotUuid, entityUuid)) {
                                    return;
                                }

                                onDataSnapshot(dataSnapshot);
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });
        }
    }

    /**
     * Get the current path.
     * @return The path.
     */
    protected CloudPath getPath() {
        return mPath;
    }

    /**
     * Called during an update if we are not shutdown. Called on synchronized(mLock){}.
     */
    protected void onUpdate() {
        mSuperOnUpdateCalled = true;
    }

    /**
     * Called during a shutdown. Only called once - if we call shutdown() twice nothing happens.
     */
    protected void onShutdown() {
        mSuperOnShutdownCalled = true;
    }

    /**
     * Called after a listener is terminated. Called on synchronized(mLock){}.
     */
    protected void afterListenerTerminated() {
        if (mListener != null) {
            mListener.afterListenerTerminated();
        }
    }
    /**
     * Called before a listener is started. Called on synchronized(mLock){}.
     */
    protected void beforeListenerStarted() {
        if (mListener != null) {
            mListener.beforeListenerStarted();
        }
    }
    /**
     * Called after a DataSnapshot is created. Called on synchronized(mLock){}.
     * @param dataSnapshot The DataSnapshot.
     */
    protected void onDataSnapshot(DataSnapshot dataSnapshot) {
        if (mListener != null) {
            mListener.onDataSnapshot(dataSnapshot);
        }
    }
}
