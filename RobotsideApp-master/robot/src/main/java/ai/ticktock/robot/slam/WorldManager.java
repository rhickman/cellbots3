package ai.cellbots.robot.slam;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.EventProcessor;
import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.World;
import ai.cellbots.common.concurrent.AtomicEnum;
import ai.cellbots.tangocommon.CloudWorldManager;

/**
 * WorldManager manages a list of worlds to store and update.
 */
public abstract class WorldManager implements ThreadedShutdown {
    private static final String TAG = WorldManager.class.getSimpleName();
    public enum State {
        INITIALIZING, // The WorldManager is initializing
        WAITING, // The WorldManager is waiting
        LOCKED, // The WorldManager is locked for an update of the world list
        STOPPING, // The WorldManager is stopping
        SHUTDOWN // The WorldManager is shutdown
    }

    @SuppressWarnings("unused")
    private final String mUserUuid;
    private final Listener mListener;

    private final EventProcessor mListenerProcessor;

    /**
     * Called when the WorldManager is updated.
     */
    public interface Listener {
        /**
         * Called when the WorldManager is updated.
         */
        void onStateUpdate();
    }

    private AtomicEnum<State> mState;

    /**
     * Starts the WorldManager system.
     *
     * @param userUuid The user uuid to save.
     * @param listener The listener.
     */
    public WorldManager(String userUuid, Listener listener) {
        mState = new AtomicEnum<>(State.INITIALIZING);
        mUserUuid = userUuid;
        mListener = listener;
        mListenerProcessor = new EventProcessor(TAG, new EventProcessor.Processor() {
            @Override
            public boolean update() {
                if (mListener != null) {
                    mListener.onStateUpdate();
                }
                return true;
            }

            @Override
            public void shutdown() {
            }
        });
    }

    /**
     * Called on update.
     */
    protected void onStateUpdate() {
        mListenerProcessor.onEvent();
    }

    /**
     * Gets the state of the system.
     *
     * @return The current state of the system.
     */
    public final State getState() {
        return mState.get();
    }

    /**
     * Sets the state of the world manager.
     *
     * @param state The state of the system.
     */
    protected final void setState(State state) {
        mState.set(state);
        onStateUpdate();
    }

    /**
     * Loads a detailed world.
     *
     * @param uuid The new world uuid.
     * @return The detailed world, or null if could not be loaded.
     */
    public abstract DetailedWorld getDetailedWorld(String uuid);

    /**
     * Gets the list of worlds.
     *
     * @return The list of worlds, or null if the list could not be obtained.
     */
    public abstract World[] getWorlds();

    /**
     * Sets the promptLink for cloud prompts.
     *
     * @param promptLink The promptLink.
     */
    public abstract void setPromptLink(CloudWorldManager.PromptLink promptLink);

    /**
     * Removes a world locally, forcing re-download.
     *
     * @param world The world to remove.
     */
    public abstract void removeWorld(World world);

    /**
     * Shutdown the WorldManager.
     */
    @Override
    final public void shutdown() {
        mListenerProcessor.shutdown();
        onShutdown();
    }

    /**
     * Called when shutdown() is called.
     */
    protected abstract void onShutdown();

    /**
     * Shutdown the WorldManager.
     */
    @Override
    final public void waitShutdown() {
        mListenerProcessor.waitShutdown();
        onWaitShutdown();
    }

    /**
     * Called when waitShutdown() is called.
     */
    protected abstract void onWaitShutdown();
}
