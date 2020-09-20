package ai.cellbots.common;

/**
 * Item that shuts down and waits for shutdown.
 */
public interface ThreadedShutdown {
    /**
     * Shutdown the entity.
     */
    void shutdown();

    /**
     * Wait for the entity to shutdown.
     */
    void waitShutdown();
}