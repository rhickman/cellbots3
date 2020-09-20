package ai.cellbots.robot.executive.simple;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.Transform;
import ai.cellbots.robot.executive.Executive;
import ai.cellbots.robot.executive.Goal;
import ai.cellbots.robot.executive.Types;
import ai.cellbots.robot.executive.WorldObject;
import ai.cellbots.robot.executive.WorldState;

/**
 * Create random goals to drive towards.
 */
final public class RandomDriverGoalProcessor extends GoalProcessor {
    private static final long RANDOM_SEED = 1337;
    private static final Random sRandom = new Random(RANDOM_SEED);

    public static final GoalProcessorFactory RANDOM_DRIVER_GOAL_FACTORY
            = new GoalProcessorFactory() {
        /**
         * Create a new goal processor.
         *
         * @param goal The goal for the processor.
         * @return The new goal processor.
         */
        @Override
        public GoalProcessor createGoalProcessor(Goal goal) {
            return new RandomDriverGoalProcessor(goal);
        }
    };

    /**
     * Create a goal processor.
     *
     * @param goal The goal.
     */
    private RandomDriverGoalProcessor(Goal goal) {
       super(goal);
    }

    /**
     * Process a goal.
     *
     * @param delegate The delegate for the planner.
     * @param worldState The world state.
     * @param cancel  True if the goal should be cancelled.
     * @param preempt True if the goal should be preempted.
     * @return The execution state.
     */
    @Override
    public Executive.GoalState processGoal(Executive.Delegate delegate, WorldState worldState,
            boolean cancel, boolean preempt) {
        if (cancel) {
            return Executive.GoalState.REJECT;
        }
        if (preempt) {
            return Executive.GoalState.PREEMPT;
        }

        DetailedWorld world = worldState.getWorld();
        HashMap<String, Object> params = new HashMap<>();
        params.put("map", worldState.getState(Types.WorldStateKey.ROBOT_MAP));
        LinkedList<Transform> pois = new LinkedList<>();
        for (WorldObject worldObject : worldState.getWorldObjects()) {
            if (worldObject.getType().getName().equals("point_of_interest")) {
                pois.add((Transform) worldObject.getValue("location"));
            }
        }

        synchronized (sRandom) {
            if (!pois.isEmpty()) {
                params.put("location", pois.get(sRandom.nextInt(pois.size())));
            } else if (world.getCustomTransformCount() > 0) {
                params.put("location", world.getCustomTransform(
                        sRandom.nextInt(world.getCustomTransformCount())));
            } else if (world.getSmoothedTransformCount() > 0) {
                params.put("location", world.getSmoothedTransform(
                        sRandom.nextInt(world.getSmoothedTransformCount())));
            } else {
                return Executive.GoalState.REJECT;
            }
        }
        delegate.addGoal(
                new Goal("drivePoint", "1.0.0", params,
                        getGoal().getUserUuid(), getGoal().getRobotUuid(), 0,
                        delegate.getNextSequence(), getGoal().getPriority() + 10));
        return Executive.GoalState.RUNNING;
    }
}
