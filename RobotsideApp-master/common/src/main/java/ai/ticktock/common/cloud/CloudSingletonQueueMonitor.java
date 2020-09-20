package ai.cellbots.common.cloud;


import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;

/**
 * Monitors a one element queue in the cloud. The queue works by waiting for an object to appear
 * and then executing it. Once it is executed, the executed flag is set to true. This queuing
 * strategy is good for simple queues, but it should be considered un-reliable since it can easily
 * drop events if a client overrides an existing element. The object type and factory must meet
 * the criteria established by CloudTypedSingletonMonitor. Additionally, the object type must
 * implement the QueueData interface.
 */
public class CloudSingletonQueueMonitor<T extends CloudSingletonQueueMonitor.QueueData, S>
        extends CloudTypedSingletonMonitor<T> {
    private final String TAG = CloudSingletonQueueMonitor.class.getName();
    private final Listener<T, S> mListener;
    private final Object mLock;
    private final List<T> mQueue = new LinkedList<>();

    /**
     * An interface for data to be queued up into the CloudSingletonQueueMonitor.
     */
    public interface QueueData {
        /**
         * Get if the object is finished.
         * @return True if the object is finished.
         */
        boolean isFinished();

        /**
         * Get the timestamp of the object's created.
         * @return The timestamp of the object's creation time, or negative if is undefined.
         */
        long getTimestamp();
        /**
         * Get key to set to true to finish the object, so a future query will cause isFinished()
         * to return true.
         * @return The string name of the key.
         */
        String getIsFinishedKey();
    }

    public interface Listener<T, S> {
        /**
         * Called to determine if a queue element is valid.
         * @param data The element.
         * @return True if the element is acceptable.
         */
        boolean dataElementValid(T data);
        /**
         * Called to stop the current element.
         * @param stored The element to stop.
         */
        void storedElementFinish(S stored);
        /**
         * Called to determine the current element is finished.
         * @param stored The element to stop.
         * @return True if the element has stopped.
         */
        boolean storedElementIsFinished(S stored);
        /**
         * Called to create a new element stored from the data queue.
         * @param data The element.
         * @return A new stored element, or null to wait on the element with it in queue.
         */
        S storedElementFromDataElement(T data);
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
     * Create a CloudSingletonQueueMonitor.
     * @param parent The context parent for logging.
     * @param lock The object to use for synchronization
     * @param path The path to monitor.
     * @param listener The listener for the object.
     * @param factory The factory for the object.
     */
    public CloudSingletonQueueMonitor(@NonNull Context parent, @NonNull Object lock,
            @NonNull CloudPath path, @NonNull final Listener<T, S> listener, @NonNull DataFactory<T> factory) {
        super(parent, lock, path, factory);
        mListener = listener;
        mLock = lock;
    }

    private S mCurrent;
    private T mCurrentElement;
    private String mCurrentUserUuid;
    private String mCurrentRobotUuid;

    /**
     * Called during an update if we are not shutdown. Called on synchronized(mLock){}.
     */
    @Override
    protected void onUpdate() {
        super.onUpdate();
        synchronized (mLock) {
            T best = null;
            for (T element : mQueue) {
                if (!isCurrent(element)) {
                    Log.v(TAG, "Element is not current: " + element);
                    continue;
                }
                if (element.isFinished()) {
                    Log.v(TAG, "Element is finished: " + element);
                    continue;
                }
                if (!dataElementValid(element)) {
                    Log.v(TAG, "Element is invalid: " + element);
                    continue;
                }
                if (best == null || best.getTimestamp() < element.getTimestamp()) {
                    best = element;
                }
            }
            mQueue.clear();

            if (mCurrent != null && best != null) {
                Log.v(TAG, "Try to finish current " + mCurrent);
                storedElementFinish(mCurrent);
            }
            if (mCurrent != null && storedElementIsFinished(mCurrent)) {
                getPath().getDatabaseReference(mCurrentUserUuid, mCurrentRobotUuid)
                        .child(mCurrentElement.getIsFinishedKey()).setValue(true);
                mCurrent = null;
                mCurrentElement = null;
                mCurrentUserUuid = null;
                mCurrentRobotUuid = null;
            } else if (mCurrent != null) {
                Log.v(TAG, "Stuck waiting for: " + mCurrent);
            }

            if (mCurrent == null && best != null) {
                mCurrent = storedElementFromDataElement(best);
                if (mCurrent != null) {
                    mCurrentElement = best;
                    mCurrentUserUuid = getUserUuid();
                    mCurrentRobotUuid = getRobotUuid();
                } else {
                    // Re-queue the best element so that it will be tried again.
                    mQueue.add(best);
                }
            }
        }
    }

    /**
     * Called during a shutdown. Only called once - if we call shutdown() twice nothing happens.
     */
    @Override
    protected void onShutdown() {
        super.onShutdown();
        if (mCurrent != null) {
            storedElementFinish(mCurrent);
            mCurrent = null;
            mCurrentElement = null;
            mCurrentUserUuid = null;
            mCurrentRobotUuid = null;
        }
    }

    /**
     * Called after a listener is terminated. Called on synchronized(mLock){}.
     */
    @Override
    protected void afterListenerTerminated() {
        if (mCurrent != null) {
            storedElementFinish(mCurrent);
        }
        if (mListener != null) {
            mListener.afterListenerTerminated();
        }
    }
    /**
     * Called before a listener is started. Called on synchronized(mLock){}.
     */
    @Override
    protected void beforeListenerStarted() {
        if (mCurrent != null) {
            storedElementFinish(mCurrent);
        }
        if (mListener != null) {
            mListener.beforeListenerStarted();
        }
    }
    /**
     * Called when data is ready. Called on synchronized(mLock){}.
     * @param data The new data element. Could be null.
     */
    @Override
    final protected void onData(T data) {
        Log.v(TAG, "Got data " + data);
        if (data != null) {
            synchronized (mLock) {
                mQueue.add(data);
            }
        }
    }
    /**
     * Called to determine if a queue element is valid.
     * @param data The element.
     * @return True if the element is acceptable.
     */
    protected boolean dataElementValid(T data) {
        synchronized (mLock) {
            if (mListener != null) {
                return mListener.dataElementValid(data);
            }
        }
        return true;
    }
    /**
     * Called to stop the current element. Note that this has to eventually stop the element,
     * baring termination of the application. If it fails to do then resources could be leaked.
     * @param stored The element to stop.
     */
    protected void storedElementFinish(S stored) {
        synchronized (mLock) {
            if (mListener != null) {
                mListener.storedElementFinish(stored);
            }
        }
    }
    /**
     * Called to determine the current element is finished.
     * @param stored The element to stop.
     * @return True if the element has stopped.
     */
    protected boolean storedElementIsFinished(S stored) {
        synchronized (mLock) {
            if (mListener != null) {
                return mListener.storedElementIsFinished(stored);
            }
        }
        return true;
    }
    /**
     * Called to create a new element stored from the data queue.
     * @param data The element.
     * @return A new stored element, or null to wait on the element with it in queue.
     */
    protected S storedElementFromDataElement(T data) {
        synchronized (mLock) {
            if (mListener != null) {
                return mListener.storedElementFromDataElement(data);
            }
        }
        return null;
    }
}
