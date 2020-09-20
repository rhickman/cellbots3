package ai.cellbots.robot.executive.simple;

import ai.cellbots.robot.executive.Executive;
import ai.cellbots.robot.executive.Goal;
import ai.cellbots.robot.executive.WorldState;

/**
 * Processor for a given goal.
 */
public abstract class GoalProcessor {
    /**
     * Factory for a goal processor.
     */
    public interface GoalProcessorFactory {
        /**
         * Creates a goal processor object.
         *
         * @param goal The goal for the processor.
         * @return The new goal processor object.
         */
        GoalProcessor createGoalProcessor(Goal goal);
    }

    /**
     * Interface for a query object.
     */
    public interface GoalProcessorQuery {
        /**
         * Queries the goal.
         *
         * @param goal The goal for query.
         * @param worldState The world state.
         * @return True if the goal should be executed.
         */
        boolean query(@SuppressWarnings("UnusedParameters") Goal goal, WorldState worldState);
    }

    private final Goal mGoal;

    /**
     * Create the goal processor.
     *
     * @param goal The goal to be processed.
     */
    @SuppressWarnings("WeakerAccess")
    protected GoalProcessor(Goal goal) {
        mGoal = goal;
    }

    /**
     * Get the goal for this processor.
     *
     * @return The goal for this processor.
     */
    @SuppressWarnings("WeakerAccess")
    protected final Goal getGoal() {
        return mGoal;
    }

    /**
     * Process the given goal.
     *
     * @param delegate The delegate for the planner.
     * @param worldState The world state.
     * @param cancel  True if the goal should be cancelled.
     * @param preempt True if the goal should be preempted.
     * @return The state of the goal.
     */
    public abstract Executive.GoalState processGoal(Executive.Delegate delegate,
            WorldState worldState, boolean cancel, boolean preempt);

}
