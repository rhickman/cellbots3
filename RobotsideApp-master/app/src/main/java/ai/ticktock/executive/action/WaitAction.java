package ai.cellbots.executive.action;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.Transform;
import ai.cellbots.executive.ExecutivePlanner;
import ai.cellbots.executive.WorldState;
import ai.cellbots.robotlib.Controller;

/**
 * Waits for a time.
 */
public class WaitAction extends DriveActionBase {
    /**
     * Wait action instance.
     */
    private final class WaitActionInstance extends DriveActionBaseInstance {
        /**
         * Create the ActionInstance.
         * @param goal The goal to be achieved.
         * @param callback The callback for the ExecutivePlanner executing the action.
         */
        private WaitActionInstance(ActionGoal goal, ExecutivePlanner.Callback callback) {
            super(goal, callback);
        }

        /**
         * Get the transform target for this goal from its parameters and data.
         * @param worldState The state of the world.
         * @param controller The controller.
         * @param world The world object itself.
         * @return Always null, since we skip the drive step.
         */
        @Override
        protected Transform getGoalPosition(WorldState worldState, Controller controller,
                DetailedWorld world) {
            return null;
        }

        /**
         * Get the map target for this goal from its parameters and data.
         * @param worldState The state of the world.
         * @param controller The controller.
         * @param world The world object itself.
         * @return Always null, since we skip the drive step.
         */
        @Override
        protected String getGoalMap(WorldState worldState, Controller controller,
                DetailedWorld world) {
            return null;
        }

        /**
         * Get the goal point action for the system.
         * @param worldState The state of the world.
         * @param controller The controller.
         * @param world The world object itself.
         * @return The goal point action.
         */
        protected Controller.GoalPointAction getGoalPointAction(WorldState worldState,
                Controller controller, DetailedWorld world) {
            return Controller.GoalPointAction.NO_ACTION;
        }

        /**
         * Get the wait time for this goal from its parameters and data.
         * @param worldState The state of the world.
         * @param controller The controller.
         * @param world The world object itself.
         * @return The time to wait in milliseconds, always zero.
         */
        @Override
        protected long getWaitTime(WorldState worldState, Controller controller,
                DetailedWorld world) {
            return (long) getGoal().getParameters().get("time");
        }
    }

    /**
     * Create an instance of an action.
     * @param goal The goal to be handled.
     * @param callback The callback from the ExecutivePlanner.
     * @return An ActionInstance subclass for handling the goal.
     */
    @Override
    public ActionInstance onCreateInstance(ActionGoal goal, ExecutivePlanner.Callback callback) {
        return new WaitActionInstance(goal, callback);
    }
}
