package ai.cellbots.common.cloud;

import android.content.Context;
import android.support.annotation.NonNull;

/**
 * Monitor a path-dependent singleton in the cloud and store it for later retrieval. The stored
 * singleton only changes during an update.
 */

public class CloudStoredSingletonMonitor<T> extends CloudTypedSingletonMonitor<T> {
    private T mNext = null;
    private T mCurrent = null;
    private final Object mLock;
    private boolean mSync = false;
    private boolean mHasNext = false;

    /**
     * Create a CloudTypedSingletonMonitor without a listener.
     * @param parent The context parent for logging.
     * @param lock The object to use for synchronization
     * @param path The path to monitor.
     * @param factory The factory for the object.
     */
    public CloudStoredSingletonMonitor(@NonNull Context parent, @NonNull Object lock,
            @NonNull CloudPath path, @NonNull DataFactory<T> factory) {
        super(parent, lock, path, factory);
        mLock = lock;
    }

    /**
     * Called when data is ready. Called on synchronized(mLock){}.
     * @param data The new data element. Could be null.
     */
    protected void onData(T data) {
        mNext = data;
        mHasNext = true;
    }

    /**
     * Called after a listener is terminated. Called on synchronized(mLock){}.
     */
    @Override
    protected void afterListenerTerminated() {
        super.afterListenerTerminated();
        mSync = false;
        mNext = null;
        mHasNext = false;
    }

    /**
     * Called on update. Update the stored singleton.
     */
    protected void onUpdate() {
        super.onUpdate();
        if (mCurrent != null) {
            if (!isCurrent(mCurrent)) {
                mCurrent = null;
            }
        }
        if (mHasNext) {
            mCurrent = mNext;
            mSync = true;
            mHasNext = false;
        }
    }

    /**
     * Get the last object stored.
     * @return The current object, or null if it is invalid.
     */
    public T getCurrent() {
        synchronized (mLock) {
            if (mCurrent != null && isCurrent(mCurrent)) {
                return mCurrent;
            }
        }
        return null;
    }

    /**
     * Get if the listener is synchronized, which means that the object is a recent update from
     * the robot.
     * @return True if it is.
     */
    public boolean isSynchronized() {
        synchronized (mLock) {
            return mSync;
        }
    }
}
