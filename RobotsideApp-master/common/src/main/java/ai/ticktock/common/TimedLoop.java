package ai.cellbots.common;

import android.support.annotation.NonNull;
import android.util.Log;

import java.util.Collection;
import java.util.Date;

/**
 * Manages update loop.
 */
public class TimedLoop implements ThreadedShutdown {
    private static final String TAG = TimedLoop.class.getSimpleName();

    /**
     * Class that is updated in a loop.
     */
    public interface Looped {
        /**
         * Called to update the looped object.
         * @return True if the looping thread should continue.
         */
        boolean update();

        /**
         * Called to shutdown the looped object.
         */
        void shutdown();
    }

    private final Looped mTarget;
    private final long mTimeout;
    private final Thread mThread;
    private boolean mShutdown = false;

    /**
     * Efficiently shutdown and wait for a group of ThreadedShutdown objects.
     * @param objects The collection of ThreadedShutdown objects.
     */
    public static void efficientShutdown(Collection<ThreadedShutdown> objects) {
        for (ThreadedShutdown s : objects) {
            s.shutdown();
        }
        for (ThreadedShutdown s : objects) {
            s.waitShutdown();
        }
    }

    /**
     * Creates a timed loop on an object.
     * @param name The name of the thread.
     * @param target The looping object.
     * @param timeout The timeout in milliseconds.
     * @param priority The thread priority.
     */
    public TimedLoop(@NonNull String name, @NonNull Looped target, long timeout, int priority) {
        mTarget = target;
        mTimeout = timeout;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!mShutdown) {
                    long lastUpdateStart = new Date().getTime();
                    if (!mTarget.update()) {
                        shutdown();
                    } else {
                        long wait = mTimeout - (new Date().getTime() - lastUpdateStart);
                        if (wait > 0) {
                            try {
                                Thread.sleep(wait);
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                    }
                }
                shutdown();
                mTarget.shutdown();
            }
        }, name);
        mThread.setPriority(priority);
        mThread.start();
    }

    /**
     * Creates a timed loop on an object.
     * @param name The name of the thread.
     * @param target The looping object.
     * @param timeout The timeout in milliseconds.
     */
    public TimedLoop(@NonNull String name, @NonNull Looped target, long timeout) {
        this(name, target, timeout, Thread.NORM_PRIORITY);
    }

    /**
     * Start the shutdown of the system.
     */
    @Override
    public void shutdown() {
        mShutdown = true;
    }

    /**
     * Wait for the system to shutdown.
     */
    @Override
    public void waitShutdown() {
        shutdown();
        try {
            mThread.join();
        } catch (InterruptedException e) {
            Log.i(TAG, "Shutdown interrupted", e);
        }
    }
}
