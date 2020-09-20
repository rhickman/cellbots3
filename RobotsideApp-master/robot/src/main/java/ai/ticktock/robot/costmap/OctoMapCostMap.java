package ai.cellbots.robot.costmap;

import ai.cellbots.common.EventProcessor;

/**
 * Abstract OctoMap class for OctoMap systems.
 *
 * @param <T> The template to feed into OctoMap processing.
 */
public abstract class OctoMapCostMap<T> extends GridCostMap {
    private static final String TAG = OctoMapCostMap.class.getSimpleName();
    private final EventProcessor mEventProcessor;
    private T mNext = null;

    /**
     * Creates the OctoMap CostMap.
     * @param resolution The resolution.
     */
    protected OctoMapCostMap(double resolution) {
        super(Source.OCTOMAP, resolution);
        // TODO: initialize octomap

        mEventProcessor = new EventProcessor(TAG, new EventProcessor.Processor() {
            @Override
            public boolean update() {
                T next;
                synchronized (OctoMapCostMap.this) {
                    next = mNext;
                }
                processUpdate(next);
                return true;
            }

            @Override
            public void shutdown() {
                // TODO: block until OctoMap is shutdown
            }
        });

    }

    /**
     * Process a CostMap update. Should not be called from any subclass, only implemented within
     * the subclass. Calling from the subclass may lead to unexpected threading behavior.
     * @param next The next update to process.
     */
    protected abstract void processUpdate(T next);

    /**
     * The save the update value for the next update of the CostMap. As soon as set, the update
     * thread will process adding the update to the OctoMap system.
     * @param next Next update value to save.
     */
    final protected synchronized void saveUpdate(T next) {
        mNext = next;
        mEventProcessor.onEvent();
    }

    /**
     * Start the shutdown of the CostMap.
     */
    @Override
    public final void shutdown() {
        mEventProcessor.shutdown();
    }

    /**
     * Wait for the CostMap to shutdown.
     */
    @Override
    public final void waitShutdown() {
        mEventProcessor.waitShutdown();
    }

    /**
     * Returns a string of the CostMap description.
     *
     * @return CostMap description string.
     */
    @Override
    public String toString() {
        return "OctoMapCostMap(source=" + getSource() + ", resolution=" + getResolution() + ")";
    }
}
