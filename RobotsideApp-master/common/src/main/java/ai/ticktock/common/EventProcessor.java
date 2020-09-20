package ai.cellbots.common;


import android.util.Log;

/**
 * A thread that processes events.
 */
public class EventProcessor implements ThreadedShutdown {
    private static final String TAG = EventProcessor.class.getSimpleName();

    /**
     * Interface class that processes events.
     */
    public interface Processor {
        /**
         * Called when an event is received.
         * @return True if the looping thread should continue.
         */
        boolean update();

        /**
         * Called to shutdown the looped object.
         */
        void shutdown();
    }

    private final Processor mTarget;
    private final Object mSemaphore = new Object();
    private final Thread mThread;
    private int mSignalCount = 0;
    private boolean mShutdown = false;

    /**
     * Create an event processor
     * @param name The name of the event processor.
     * @param target The target to process.
     */
    public EventProcessor(String name, Processor target) {
        mTarget = target;

        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!mShutdown) {
                    synchronized (mSemaphore) {
                        if (mSignalCount == 0) {
                            try {
                                mSemaphore.wait();
                            } catch (InterruptedException e) {
                                Log.w(TAG, "Semaphore wait interrupted:", e);
                            }
                        }
                        if (mSignalCount <= 0) {
                            continue;
                        }
                        mSignalCount--;
                    }
                    if (!mShutdown) {
                        mTarget.update();
                    }
                }
                mTarget.shutdown();
            }
        }, name);
        mThread.start();
    }

    /**
     * Called to signal a new event.
     */
    public void onEvent() {
        synchronized (mSemaphore) {
            if (mSignalCount < Integer.MAX_VALUE) {
                mSignalCount++;
            }
            mSemaphore.notifyAll();
        }
    }

    /**
     * Start the shutdown of the system.
     */
    @Override
    public void shutdown() {
        mShutdown = true;
        synchronized (mSemaphore) {
            mSemaphore.notifyAll();
        }
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
            Log.w(TAG, "Shutdown interrupted", e);
        }
    }
}
