package ai.cellbots.robot.control;

/**
 * An action for the mediator.
 */
public class Action {
    // Timestamp to complete the action by in milliseconds. Negative for unlimited time.
    private final long mCompleteBy;

    /**
     * The status of an action.
     */
    public enum State {
        QUEUED, // The action is in the queue.
        EXECUTING, // The action is being executed by the controller.
        REJECTED, // The action is rejected by the controller.
        COMPLETED // The action is completed.
    }

    /**
     * Creates an action for the mediator.
     *
     * @param completeBy Timestamp to complete the action by in milliseconds.
     *                   Negative for unlimited time.
     */
    public Action(long completeBy) {
        mCompleteBy = completeBy;
    }

    /**
     * Gets the timestamp to complete an action in milliseconds. If negative, time is unlimited.
     *
     * @return Time to complete this action by. In milliseconds.
     */
    @SuppressWarnings("unused")
    public long getCompleteBy() {
        return mCompleteBy;
    }
}
