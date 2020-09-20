package ai.cellbots.executive.action;

import android.util.Log;

import java.util.Date;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.executive.ExecutivePlanner;
import ai.cellbots.executive.MacroGoal;
import ai.cellbots.executive.WorldState;
import ai.cellbots.robotlib.Animation;
import ai.cellbots.robotlib.Controller;

/**
 * Executes an animation on the controller.
 */
public class AnimationAction extends Action {
    private static final String TAG = AnimationAction.class.getSimpleName();
    private static final long INVALID_ANIMATION_TIMEOUT = 10000; // milliseconds

    private class AnimationActionInstance extends Action.ActionInstance {
        private boolean mAnimationSent = false;
        private Date mTimeout = null;

        /**
         * Create the ActionInstance.
         * @param goal The goal to be achieved.
         * @param callback The callback for the ExecutivePlanner executing the action.
         */
        private AnimationActionInstance(ActionGoal goal, ExecutivePlanner.Callback callback) {
            super(goal, callback);
        }

        /**
         * Called when the action is updated.
         * @param worldState The state of the world, currently.
         * @param cancel True if we should cancel the goal.
         * @param controller The controller.
         * @param preemptGoal True if we should preempt the goal.
         * @param world The current world of the goal.
         * @return A goal state to be returned from the ExecutivePlanner.
         */
        @Override
        public ExecutivePlanner.GoalState update(WorldState worldState, boolean cancel,
                Controller controller, boolean preemptGoal, DetailedWorld world) {
            if (!mAnimationSent) {
                if (cancel) {
                    return ExecutivePlanner.GoalState.REJECT;
                }
                Animation anim = getAnimation(getGoal().getParameters().get("animation").toString());
                if (anim == null) {
                    if (mTimeout == null) {
                        mTimeout = new Date();
                        mTimeout.setTime(mTimeout.getTime() + INVALID_ANIMATION_TIMEOUT);
                    }
                    if (mTimeout.after(new Date())) {
                        return ExecutivePlanner.GoalState.WAIT;
                    }
                    Log.w(TAG, "Animation rejected for bad name");
                    return ExecutivePlanner.GoalState.REJECT;
                }
                controller.setAnimation(anim);
                mAnimationSent = true;
            } else if (controller.getAnimation() == null) {
                return ExecutivePlanner.GoalState.COMPLETE;
            } else if (cancel) {
                controller.cancelAnimation();
                return ExecutivePlanner.GoalState.REJECT;
            }
            return ExecutivePlanner.GoalState.WAIT;
        }
    }


    /**
     * Determines if the goal should run given a state of the system.
     * @param worldState The current world state.
     * @param goal The goal to be evaluated.
     * @param controller The controller.
     * @param world The world.
     * @return Always true since DriveActions can always be run.
     */
    @Override
    public boolean query(WorldState worldState, MacroGoal goal, Controller controller,
            DetailedWorld world) {
        return true;
    }

    /**
     * Create an instance of an action.
     * @param goal The goal to be handled.
     * @param callback The callback from the ExecutivePlanner.
     * @return An ActionInstance subclass for handling the goal.
     */
    @Override
    public ActionInstance onCreateInstance(ActionGoal goal,
            ExecutivePlanner.Callback callback) {
        return new AnimationActionInstance(goal, callback);
    }
}
