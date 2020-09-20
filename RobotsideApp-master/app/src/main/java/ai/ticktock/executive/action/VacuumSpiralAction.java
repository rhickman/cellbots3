package ai.cellbots.executive.action;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.Transform;
import ai.cellbots.executive.ExecutivePlanner;
import ai.cellbots.executive.WorldState;
import ai.cellbots.robotlib.Controller;

/**
 * Drives to a point specified by a transform and vacuums. Parameters:
 * * map: The map to execute against.
 * * location: The location to go to.
 */
public class VacuumSpiralAction extends DriveActionBase {

    /**
     * Creates a vacuum spiral action.
     */
    public VacuumSpiralAction() {
    }

    /**
     * Handles driving to a point and vacuuming a spiral.
     */
    private final class VacuumSpiralActionInstance extends DriveActionBaseInstance {
        /**
         * Create the ActionInstance.
         * @param goal The goal to be achieved.
         * @param callback The callback for the ExecutivePlanner executing the action.
         */
        private VacuumSpiralActionInstance(ActionGoal goal, ExecutivePlanner.Callback callback) {
            super(goal, callback);
        }

        /**
         * Get the transform target for this goal from its parameters and data.
         * @param worldState The state of the world.
         * @param controller The controller.
         * @param world The world object itself.
         * @return The transform to go to, or null to skip the drive step.
         */
        @Override
        protected Transform getGoalPosition(WorldState worldState, Controller controller,
                DetailedWorld world) {
            return (Transform)getGoal().getParameters().get("location");
        }

        /**
         * Get the map target for this goal from its parameters and data.
         * @param worldState The state of the world.
         * @param controller The controller.
         * @param world The world object itself.
         * @return The map to go to.
         */
        @Override
        protected String getGoalMap(WorldState worldState, Controller controller,
                DetailedWorld world) {
            return (String)getGoal().getParameters().get("map");
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
            return Controller.GoalPointAction.VACUUM_SPIRAL;
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
            return 0;
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
        return new VacuumSpiralActionInstance(goal, callback);
    }

}