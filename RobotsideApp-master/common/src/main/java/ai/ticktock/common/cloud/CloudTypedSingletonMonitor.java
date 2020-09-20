package ai.cellbots.common.cloud;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;

/**
 * A singleton monitor that converts the objects it receives using the DataFactory system. The
 * object must support the relevant getUserUuid and getRobotUuid interfaces if the path supports
 * these variables.
 */
public class CloudTypedSingletonMonitor<T> extends ai.cellbots.common.cloud.CloudSingletonMonitor {
    private final Listener<T> mListener;
    private final DataFactory<T> mFactory;
    private final Context mParent;
    private final Object mLock;

    /**
     * A listener for the factory. Called to create objects.
     * @param <T> The type of objects to be created.
     */
    public interface Listener<T> {
        /**
         * Called when data is ready. Called on synchronized(mLock){}.
         * @param data The new data element. Could be null.
         */
        void onData(T data);
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
     * Create a CloudTypedSingletonMonitor without a listener.
     * @param parent The context parent for logging.
     * @param lock The object to use for synchronization
     * @param path The path to monitor.
     * @param factory The factory for the object.
     */
    @SuppressWarnings("ConstantConditions")
    protected CloudTypedSingletonMonitor(@NonNull Context parent, @NonNull Object lock, @NonNull CloudPath path,
            @NonNull DataFactory<T> factory) {
        this(parent, lock, path, null, factory);
    }

    /**
     * Create a CloudTypedSingletonMonitor.
     * @param parent The context parent for logging.
     * @param lock The object to use for synchronization
     * @param path The path to monitor.
     * @param listener The listener for the object.
     * @param factory The factory for the object.
     */
    public CloudTypedSingletonMonitor(@NonNull Context parent, @NonNull Object lock, @NonNull CloudPath path,
            @NonNull final Listener<T> listener, @NonNull DataFactory<T> factory) {
        super(lock, path);
        mParent = parent;
        if (path.hasUserPath() != factory.isUserFactory()) {
            throw new Error("CloudPath user: " + path.hasUserPath() + " factory: " + factory.isUserFactory());
        }
        if (path.hasRobotPath() != factory.isRobotFactory()) {
            throw new Error("CloudPath robot: " + path.hasRobotPath() + " factory: " + factory.isRobotFactory());
        }
        mListener = listener;
        mLock = lock;
        mFactory = factory;
    }

    /**
     * Check if a data object is up-to-date with the latest listener.
     * @param data The data element to check.
     * @return True if it is up to date.
     */
    final protected boolean isCurrent(@NonNull T data) {
        synchronized (mLock) {
            return mFactory.isCurrent(getUserUuid(), getRobotUuid(), data);
        }
    }

    /**
     * Convert the dataSnapshot.
     * @param dataSnapshot The new data.
     * @return The converted object, or null if conversion failed.
     */
    private T convertData(DataSnapshot dataSnapshot) {
        if (dataSnapshot.getValue() == null) {
            return null;
        }
        return mFactory.createLogged(mParent, getUserUuid(), getRobotUuid(), dataSnapshot);
    }

    /**
     * Called after a listener is terminated. Called on synchronized(mLock){}.
     */
    @Override
    protected void afterListenerTerminated() {
        if (mListener != null) {
            mListener.afterListenerTerminated();
        }
    }
    /**
     * Called before a listener is started. Called on synchronized(mLock){}.
     */
    @Override
    protected void beforeListenerStarted() {
        if (mListener != null) {
            mListener.beforeListenerStarted();
        }
    }
    /**
     * Called after a DataSnapshot is created. Called on synchronized(mLock){}.
     * @param dataSnapshot The DataSnapshot.
     */
    @Override
    final protected void onDataSnapshot(DataSnapshot dataSnapshot) {
        onData(convertData(dataSnapshot));
    }
    /**
     * Called when data is ready. Called on synchronized(mLock){}.
     * @param data The new data element. Could be null.
     */
    protected void onData(T data) {
        if (mListener != null) {
            mListener.onData(data);
        }
    }
}
