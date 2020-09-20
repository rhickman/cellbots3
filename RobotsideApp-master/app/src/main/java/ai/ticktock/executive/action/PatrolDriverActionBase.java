package ai.cellbots.executive.action;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.Transform;
import ai.cellbots.executive.ExecutivePlanner;
import ai.cellbots.executive.MacroGoal;
import ai.cellbots.executive.Types;
import ai.cellbots.executive.WorldState;
import ai.cellbots.robotlib.Controller;

/**
 * Patrols the region around a specific point.
 */
public abstract class PatrolDriverActionBase extends Action {
    private static final double DISTANCE = 3.0;
    private static final long RANDOM_SEED = 1337;
    private final Random mRandom = new Random(RANDOM_SEED);

    /**
     * Patrols a given course.
     */
    protected abstract class PatrolDriverActionInstanceBase extends ActionInstance {
        /**
         * Get the transform target for this goal from its parameters and data.
         * @param worldState The state of the world.
         * @param controller The controller.
         * @param world The world object itself.
         * @return The transform to go to, or null to skip the drive step.
         */
        protected abstract Transform getGoalPosition(WorldState worldState,
                Controller controller, DetailedWorld world);

        /**
         * Get the map target for this goal from its parameters and data.
         * @param worldState The state of the world.
         * @param controller The controller.
         * @param world The world object itself.
         * @return The map to go to, or null to skip the drive step.
         */
        protected abstract String getGoalMap(WorldState worldState,
                Controller controller, DetailedWorld world);

        /**
         * Create the ActionInstance.
         * @param goal The goal to be achieved.
         * @param callback The callback for the ExecutivePlanner executing the action.
         */
        protected PatrolDriverActionInstanceBase(ActionGoal goal, ExecutivePlanner.Callback callback) {
            super(goal, callback);
        }

        /**
         * Creates a random goal near the target.
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
            final double distanceSquared = DISTANCE * DISTANCE;

            // If cancelled, stop the goal.
            if (cancel) {
                return ExecutivePlanner.GoalState.REJECT;
            }

            // Get the target point and map, either is null or map is wrong, reject goal.
            Transform targetPoint = getGoalPosition(worldState, controller, world);
            String goalMap = getGoalMap(worldState, controller, world);
            if (targetPoint == null || goalMap == null) {
                return ExecutivePlanner.GoalState.REJECT;
            }
            if (!goalMap.equals(world.getUuid())) {
                return ExecutivePlanner.GoalState.REJECT;
            }

            // Get a list of points around the target.
            LinkedList<Transform> targetPoints = new LinkedList<>();
            if (world.getCustomTransformCount() != 0) {
                for (int i = 0; i < world.getCustomTransformCount(); i++) {
                    Transform tf = world.getCustomTransform(i);
                    if (tf.planarDistanceToSquared(targetPoint) < distanceSquared) {
                        targetPoints.add(tf);
                    }
                }
            } else {
                for (int i = 0; i < world.getSmoothedTransformCount(); i++) {
                    Transform tf = world.getSmoothedTransform(i);
                    if (tf.planarDistanceToSquared(targetPoint) < distanceSquared) {
                        targetPoints.add(tf);
                    }
                }
            }

            // If there are no points, reject the goal.
            if (targetPoints.isEmpty()) {
                return ExecutivePlanner.GoalState.REJECT;
            }

            // Create a random point at a target point.
            HashMap<String, Object> params = new HashMap<>();
            params.put("map", worldState.getState(Types.WorldStateKey.ROBOT_MAP));
            params.put("location", targetPoints.get(mRandom.nextInt(targetPoints.size())));
            addGoal(new MacroGoal("drivePoint", "1.0.0", params,
                    getGoal().getUserUuid(), getGoal().getRobotUuid(), 0.0,
                    getNextSequence(), getGoal().getPriority() + 10));

            return ExecutivePlanner.GoalState.PREEMPT;
        }
    }

    /**
     * Determines if the goal should run given a state of the system.
     * @param worldState The current world state.
     * @param goal The goal to be evaluated.
     * @param controller The controller.
     * @param world The world.
     * @return Always true since PatrolDriverActions can always be run.
     */
    @Override
    public boolean query(WorldState worldState, MacroGoal goal, Controller controller,
            DetailedWorld world) {
        return true;
    }
}
