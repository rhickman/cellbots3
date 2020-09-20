package ai.cellbots.robot.executive.simple;

import android.util.Log;

import ai.cellbots.robot.control.Action;
import ai.cellbots.robot.control.AnimationAction;
import ai.cellbots.robot.executive.Executive;
import ai.cellbots.robot.executive.Goal;
import ai.cellbots.robot.executive.WorldState;

/**
 * Animation goal processor.
 */
public final class AnimationGoalProcessor extends GoalProcessor {
    private static final String TAG = AnimationGoalProcessor.class.getSimpleName();

    public static final GoalProcessorFactory ANIMATION_GOAL_FACTORY
            = new GoalProcessorFactory() {
        /**
         * Create a new goal processor.
         *
         * @param goal The goal for the processor.
         * @return The goal processor.
         */
        @Override
        public GoalProcessor createGoalProcessor(Goal goal) {
            return new AnimationGoalProcessor(goal);
        }
    };

    /**
     * Creates the animation goal processor.
     *
     * @param goal The goal to be read in.
     */
    private AnimationGoalProcessor(Goal goal) {
        super(goal);
        Log.w(TAG, "Created");
    }

    private AnimationAction mAnimationAction;

    /**
     * Processes an animation goal.
     *
     * @param delegate The delegate for the planner.
     * @param worldState The world state.
     * @param cancel  True if the goal should be cancelled.
     * @param preempt True if the goal should be preempted.
     * @return The goal state.
     */
    @Override
    public Executive.GoalState processGoal(Executive.Delegate delegate, WorldState worldState,
            boolean cancel, boolean preempt) {
        if (mAnimationAction == null) {
            if (getGoal().getParameters().get("animation") == null) {
                delegate.getActionMediator().setAction(null);
                return Executive.GoalState.REJECT;
            }
            final long unlimitedTime = -1;
            mAnimationAction = new AnimationAction(unlimitedTime,
                    getGoal().getParameters().get("animation").toString());
            delegate.getActionMediator().setAction(mAnimationAction);
        }
        Action.State state = delegate.getActionMediator().getActionState(mAnimationAction);
        if (state == Action.State.COMPLETED) {
            delegate.getActionMediator().setAction(null);
            return Executive.GoalState.COMPLETE;
        }
        if (state == Action.State.REJECTED) {
            delegate.getActionMediator().setAction(null);
            return Executive.GoalState.REJECT;
        }
        return Executive.GoalState.RUNNING;
    }
}
