package ai.cellbots.executive.action;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.Transform;
import ai.cellbots.executive.ExecutivePlanner;
import ai.cellbots.executive.WorldObject;
import ai.cellbots.executive.WorldState;
import ai.cellbots.robotlib.Controller;

/**
 * Patrol a region around a point of interest.
 */
public class PatrolDriverPOI extends PatrolDriverActionBase {
    /**
     * Patrol around a point of interest.
     */
    final private class PatrolDriverPOIInstance extends PatrolDriverActionInstanceBase {
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
            String objectUuid = (String) getGoal().getParameters().get("target");
            WorldObject object = worldState.getWorldObject(objectUuid);
            if (object != null) {
                return (Transform) object.getValue("location");
            }
            return null;
        }

        /**
         * Get the map target for this goal from its parameters and data.
         * @param worldState The state of the world.
         * @param controller The controller.
         * @param world The world object itself.
         * @return The map to go to, or null to skip the drive step.
         */
        protected String getGoalMap(WorldState worldState,
                Controller controller, DetailedWorld world) {
            String objectUuid = (String) getGoal().getParameters().get("target");
            WorldObject object = worldState.getWorldObject(objectUuid);
            if (object != null) {
                return object.getMapUuid();
            }
            return null;
        }

        /**
         * Create the ActionInstance.
         * @param goal The goal to be achieved.
         * @param callback The callback for the ExecutivePlanner executing the action.
         */
        private PatrolDriverPOIInstance(ActionGoal goal, ExecutivePlanner.Callback callback) {
            super(goal, callback);
        }
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
        return new PatrolDriverPOIInstance(goal, callback);
    }
}