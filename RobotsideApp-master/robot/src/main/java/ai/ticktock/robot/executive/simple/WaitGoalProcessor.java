package ai.cellbots.robot.executive.simple;

import java.util.Date;

import ai.cellbots.robot.executive.Executive;
import ai.cellbots.robot.executive.Goal;
import ai.cellbots.robot.executive.WorldState;

/**
 * Process wait goals.
 */
final public class WaitGoalProcessor extends GoalProcessor {

    public static final GoalProcessorFactory WAIT_GOAL_FACTORY = new GoalProcessorFactory() {
        /**
         * Create the goal processor.
         *
         * @param goal The goal for the processor.
         * @return The new goal processor.
         */
        @Override
        public GoalProcessor createGoalProcessor(Goal goal) {
            return new WaitGoalProcessor(goal);
        }
    };

    private long mStartTime = 0;
    private boolean mStarted = false;

    /**
     * Create the drive goal processor.
     * @param goal The goal.
     */
    private WaitGoalProcessor(Goal goal) {
        super(goal);
    }

    /**
     * Process a wait goal.
     *
     * @param delegate The delegate for the planner.
     * @param worldState The world state.
     * @param cancel  True if the goal should be cancelled.
     * @param preempt True if the goal should be preempted.
     * @return The state of the goal.
     */
    @Override
    public Executive.GoalState processGoal(Executive.Delegate delegate, WorldState worldState,
            boolean cancel, boolean preempt) {
        if (!mStarted) {
            mStarted = true;
            mStartTime = new Date().getTime();
        }
        if ((long)getGoal().getParameters().get("time") + mStartTime < new Date().getTime()) {
            return Executive.GoalState.COMPLETE;
        }
        if (cancel) {
            return Executive.GoalState.REJECT;
        }
        if (preempt) {
            return Executive.GoalState.PREEMPT;
        }
        return Executive.GoalState.RUNNING;
    }
}
