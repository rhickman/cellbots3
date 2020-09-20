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
 * Process patrol driver goals.
 */
final public class PatrolDriverGoalProcessor extends GoalProcessor {
    public static final GoalProcessorFactory PATROL_DRIVER_GOAL_FACTORY
            = new PatrolDriverGoalProcessorFactory(PoseSource.TRANSFORM);
    public static final GoalProcessorFactory PATROL_DRIVER_POI_GOAL_FACTORY
            = new PatrolDriverGoalProcessorFactory(PoseSource.POI);

    private static final double DISTANCE = 3.0;
    private static final long RANDOM_SEED = 1337;
    private static final Random sRandom = new Random(RANDOM_SEED);

    /**
     * The source of pose for the robot.
     */
    private enum PoseSource {
        TRANSFORM, // A transform in
        POI // A POI in
    }

    /**
     * Create the patrol driver goal.
     */
    static final private class PatrolDriverGoalProcessorFactory implements GoalProcessorFactory {
        private final PoseSource mPoseSource;

        /**
         * Create the factory.
         *
         * @param poseSource The source for pose data.
         */
        private PatrolDriverGoalProcessorFactory(PoseSource poseSource) {
            mPoseSource = poseSource;
        }

        /**
         * Create a goal processor.
         *
         * @param goal The goal for the processor.
         * @return The goal processor.
         */
        @Override
        public GoalProcessor createGoalProcessor(Goal goal) {
            return new PatrolDriverGoalProcessor(goal, mPoseSource);
        }
    }

    private final PoseSource mPoseSource;

    /**
     * Create the processor.
     *
     * @param goal The goal to process.
     * @param poseSource The source of the pose data.
     */
    private PatrolDriverGoalProcessor(Goal goal, PoseSource poseSource) {
        super(goal);
        mPoseSource = poseSource;
    }

    /**
     * Get the goal position of this goal.
     *
     * @param worldState The world state.
     * @return The goal point transform.
     */
    private Transform getGoalPosition(WorldState worldState) {
        if (mPoseSource == PoseSource.POI) {
            WorldObject target = worldState.getWorldObject(getGoal().getParameters().get("target").toString());
            if (target != null) {
                return (Transform)target.getValue("location");
            }
            return null;
        } else {
            return (Transform)getGoal().getParameters().get("location");
        }
    }

    /**
     * Get the goal world of this transform.
     *
     * @param worldState The world state.
     * @return The goal world uuid.
     */
    private String getGoalWorld(WorldState worldState) {
        if (mPoseSource == PoseSource.POI) {
            WorldObject target = worldState.getWorldObject(getGoal().getParameters().get("target").toString());
            if (target != null) {
                return target.getMapUuid();
            }
            return null;
        } else {
            return (String) getGoal().getParameters().get("map");
        }
    }

    /**
     * Process the goal.
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
        // If cancelled, stop the goal.
        if (cancel) {
            return Executive.GoalState.REJECT;
        }

        // If we are preempted, stop so another goal can execute.
        if (preempt) {
            return Executive.GoalState.PREEMPT;
        }

        final double distSq = DISTANCE * DISTANCE;
        DetailedWorld world = worldState.getWorld();


        // Get the target point and map, either is null or map is wrong, reject goal.
        Transform targetPoint = getGoalPosition(worldState);
        String goalMap = getGoalWorld(worldState);
        if (targetPoint == null || goalMap == null) {
            return Executive.GoalState.REJECT;
        }
        if (!goalMap.equals(world.getUuid())) {
            return Executive.GoalState.REJECT;
        }

        // Get a list of points around the target.
        LinkedList<Transform> targetPoints = new LinkedList<>();
        if (world.getCustomTransformCount() != 0) {
            for (int i = 0; i < world.getCustomTransformCount(); i++) {
                Transform tf = world.getCustomTransform(i);
                if (tf.planarDistanceToSquared(targetPoint) < distSq) {
                    targetPoints.add(tf);
                }
            }
        } else {
            for (int i = 0; i < world.getSmoothedTransformCount(); i++) {
                Transform tf = world.getSmoothedTransform(i);
                if (tf.planarDistanceToSquared(targetPoint) < distSq) {
                    targetPoints.add(tf);
                }
            }
        }

        // If there are no points, reject the goal.
        if (targetPoints.isEmpty()) {
            return Executive.GoalState.REJECT;
        }

        // Create a random point at a target point.
        HashMap<String, Object> params = new HashMap<>();
        params.put("map", worldState.getState(Types.WorldStateKey.ROBOT_MAP));
        synchronized (sRandom) {
            params.put("location", targetPoints.get(sRandom.nextInt(targetPoints.size())));
        }
        delegate.addGoal(new Goal("drivePoint", "1.0.0", params,
                getGoal().getUserUuid(), getGoal().getRobotUuid(), 0,
                delegate.getNextSequence(), getGoal().getPriority() + 10));

        return Executive.GoalState.RUNNING;
    }
}
