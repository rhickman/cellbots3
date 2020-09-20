package ai.cellbots.executive.action;


import java.util.HashMap;
import java.util.Random;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.data.ExecutiveStateCommand;
import ai.cellbots.executive.ExecutivePlanner;
import ai.cellbots.executive.MacroGoal;
import ai.cellbots.executive.Types;
import ai.cellbots.executive.WorldState;
import ai.cellbots.robotlib.Controller;

/**
 * Creates random goals at a priority 10 points higher than itself. Used for patrol.
 */
public class RandomDriverAction extends Action {
    private static final long RANDOM_SEED = 1337;
    private final Random mRandom = new Random(RANDOM_SEED);

    /**
     * Instance of the random goal generator.
     */
    private final class RandomDriverActionInstance extends ActionInstance {
        /**
         * Create the ActionInstance.
         * @param goal The goal to be achieved.
         * @param callback The callback for the ExecutivePlanner executing the action.
         */
        private RandomDriverActionInstance(ActionGoal goal, ExecutivePlanner.Callback callback) {
            super(goal, callback);
        }

        /**
         * Called when the action is updated. Always creates a new random goal.
         * @param worldState The state of the world, currently.
         * @param cancel True if we should cancel the goal.
         * @param controller The controller.
         * @param preemptGoal True if we should preempt the goal.
         * @param world The current world of the goal.
         * @return A goal state to be returned from the ExecutivePlanner. Always PREEMPT.
         */
        @Override
        public ExecutivePlanner.GoalState update(WorldState worldState, boolean cancel,
                Controller controller, boolean preemptGoal, DetailedWorld world) {
            if (worldState.getState(Types.WorldStateKey.ROBOT_EXECUTIVE_STATE)
                    == ExecutiveStateCommand.ExecutiveState.RANDOM_DRIVER) {
                HashMap<String, Object> params = new HashMap<>();
                params.put("map", worldState.getState(Types.WorldStateKey.ROBOT_MAP));
                if (world.getCustomTransformCount() > 0) {
                    params.put("location", world.getCustomTransform(
                            mRandom.nextInt(world.getCustomTransformCount())));
                } else if (world.getSmoothedTransformCount() > 0) {
                    params.put("location", world.getSmoothedTransform(
                            mRandom.nextInt(world.getSmoothedTransformCount())));
                }
                if (params.containsKey("location")) {
                    addGoal(new MacroGoal("drivePoint", "1.0.0", params,
                            getGoal().getUserUuid(), getGoal().getRobotUuid(), 0.0,
                            getNextSequence(), getGoal().getPriority() + 10));
                    return ExecutivePlanner.GoalState.PREEMPT;
                }
            }
            if (cancel) {
                return ExecutivePlanner.GoalState.REJECT;
            }
            if (preemptGoal) {
                return ExecutivePlanner.GoalState.PREEMPT;
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
     * @return Always true since we always create a goal.
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
    public ActionInstance onCreateInstance(ActionGoal goal, ExecutivePlanner.Callback callback) {
        return new RandomDriverActionInstance(goal, callback);
    }
}
