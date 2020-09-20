package ai.cellbots.robot.navigation;

import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import ai.cellbots.common.Geometry;
import ai.cellbots.common.Transform;
import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.costmap.CostMapPose;
import ai.cellbots.robot.driver.RobotModel;
import ai.cellbots.robot.state.RobotSessionGlobals;

/**
 * The simple velocity generator.
 */
public class SimpleVelocityGenerator implements VelocityGenerator {
    private final static String TAG = SimpleVelocityGenerator.class.getSimpleName();
    private final static double UPDATE_INTERVAL_SECONDS = 0.1;  // In seconds.
    private final static double CARROT_DISTANCE_MAX_SQUARED = 2.89;  // In squared meters.
    private final static double STOP_ANGLE_LIMIT = Math.PI / 4;  // The angle limit in radian.
    private final static double FULL_SPEED_ANGLE = Math.PI / 8;
    // If the robot is far away from the path as much as this cell, it will stop.
    private final static double MAX_DISTANCE_FROM_START_NODE_IN_CELL = 2.5;
    // The maximum distance from the current robot to a node. In cell squared.
    private final static int MAX_DISTANCE_FROM_NODE_IN_CELL_SQUARED = 9;

    private final RobotSessionGlobals mSession;
    private final double mResolution;

    private double[] mCurrentVelocity;

    /**
     * Creates the trajectory rollout system.
     *
     * @param session The session variables.
     * @param resolution The resolution of the CostMap in meters.
     */
    public SimpleVelocityGenerator(RobotSessionGlobals session, double resolution) {
        mSession = session;
        mResolution = resolution;
        mCurrentVelocity = new double[]{0, 0};
        double maxSpeed = session.getRobotModel().getMaxLinearSpeed();
        if (mResolution < UPDATE_INTERVAL_SECONDS * maxSpeed) {
            throw new IllegalArgumentException("Speed is too fast for update interval. " +
            "Robot speed is " + maxSpeed);
        }
        Log.i(TAG, "Created");
    }

    /**
     * Simulates the next velocity.
     *
     * @param model The robot model.
     * @param goalVelocity The goal velocity for the robot.
     * @param velocity The velocity of the robot.
     * @param time The time to simulate in seconds.
     * @return The new velocity vector.
     */
    private static double[] simulateVelocity(RobotModel model, double[] goalVelocity,
                                             double[] velocity, double time) {
        double deltaLinear = goalVelocity[0] - velocity[0];
        deltaLinear = Math.max(-model.getMaxLinearAcceleration() * time,
                Math.min(model.getMaxLinearAcceleration() * time, deltaLinear));
        double deltaAngular = goalVelocity[1] - velocity[1];
        deltaAngular = Math.max(-model.getMaxAngularAcceleration() * time,
                Math.min(model.getMaxAngularAcceleration() * time, deltaAngular));
        return new double[]{
                Math.max(-model.getMaxLinearSpeed(),
                        Math.min(model.getMaxLinearSpeed(), velocity[0] + deltaLinear)),
                Math.max(-model.getMaxAngularSpeed(),
                        Math.min(model.getMaxAngularSpeed(), velocity[1] + deltaAngular))};
    }

    /**
     * Causes the robot to smoothly slow to a stopped velocity.
     * @return The velocity output.
     */
    private double[] brakeToStop() {
        mCurrentVelocity = simulateVelocity(mSession.getRobotModel(),
                new double[]{0.0, 0.0}, mCurrentVelocity, UPDATE_INTERVAL_SECONDS);
        return mCurrentVelocity.clone();
    }

    /**
     * Compute the motion simulation to a point
     * @param pose Current position and orientation of the robot.
     * @param currentVelocity The current velocity.
     * @param target The target pose for the robot.
     * @param closeToGoalDistanceSquared Squared distance in which the robot is close to the goal.
     * @param speedLimit The limit to the robot's speed.
     * @param interval The update interval in seconds.
     * @return The next velocity.
     */
    private double[] nextVelocity(Transform pose, double[] currentVelocity, Transform target,
                double closeToGoalDistanceSquared, double speedLimit, double interval) {
        double dx = target.getPosition(0) - pose.getPosition(0);
        double dy = target.getPosition(1) - pose.getPosition(1);
        double targetAngle = Math.atan2(dy, dx);
        double deltaAngle = Geometry.wrapAngle(targetAngle - pose.getRotationZ());

        // Note, maximum velocity is defined as stopping distance - reaction distance buffer.
        // E.g. d = distance, R = reaction time, v = velocity, a = max acceleration
        // d - Rv = v^2 / (2a)
        // 0 = v^2 / (2a) + Rv - d
        // Quadratic formula: v = -aR + sqrt(a^2R^2 + 2da)

        // The reaction time for angular velocity. The robot computes the maximum angular velocity
        // with this reaction time. As this value increases, the angular speed decreases the chance
        // of rotational overshoot will decrease.
        double reactionTime = 10.0 * UPDATE_INTERVAL_SECONDS;
        double angReactionVel = reactionTime * mSession.getRobotModel().getMaxAngularAcceleration();
        double angularSpeed = -angReactionVel + Math.sqrt(angReactionVel * angReactionVel
                + 2 * Math.abs(deltaAngle) * mSession.getRobotModel().getMaxAngularAcceleration());
        if (deltaAngle < 0) {
            angularSpeed = -angularSpeed;
        }

        if (Math.abs(deltaAngle) >= STOP_ANGLE_LIMIT) {
            return simulateVelocity(mSession.getRobotModel(),
                    new double[]{0, angularSpeed},
                    currentVelocity, interval);
        } else {
            double targetDistanceSquared = target.planarDistanceToSquared(pose);
            if (targetDistanceSquared < closeToGoalDistanceSquared) {
                return simulateVelocity(mSession.getRobotModel(), new double[]{0, 0},
                        currentVelocity, interval);
            }

            double reactionVel = reactionTime * mSession.getRobotModel().getMaxLinearAcceleration();
            // TODO(playerone) Avoid using Math.sqrt().
            double maxSmoothSpeed = -reactionVel + Math.sqrt(
                    reactionVel * reactionVel +
                            2 * Math.sqrt(targetDistanceSquared) *
                                    mSession.getRobotModel().getMaxLinearAcceleration());

            speedLimit = Math.min(speedLimit, Math.min(
                    mSession.getRobotModel().getMaxLinearSpeed(), maxSmoothSpeed));

            double velocity = Math.abs(deltaAngle) < FULL_SPEED_ANGLE ? speedLimit
                    : (1 - (Math.abs(deltaAngle) - FULL_SPEED_ANGLE)
                    / (STOP_ANGLE_LIMIT - FULL_SPEED_ANGLE)) * speedLimit;

            return simulateVelocity(mSession.getRobotModel(),
                    new double[]{velocity, angularSpeed},
                    currentVelocity, interval);
        }
    }

    /**
     * The current robot pose.
     *
     * @param currentRobotPose The current robot position.
     * @param velocity The velocity.
     * @param time The time interval in seconds.
     * @return The new transform.
     */
    private static Transform simulatePosition(
            Transform currentRobotPose, double[] velocity, double time) {
        return new Transform(
                currentRobotPose.getPosition(0)
                        + velocity[0] * Math.cos(currentRobotPose.getRotationZ()) * time,
                currentRobotPose.getPosition(1)
                        + velocity[0] * Math.sin(currentRobotPose.getRotationZ()) * time,
                currentRobotPose.getPosition(2),
                currentRobotPose.getRotationZ() + velocity[1] * time);
    }

    /**
     * Compute the motion simulation to a point
     * @param pose Current position and orientation of the robot.
     * @param currentVelocity The current velocity.
     * @param target The target pose for the robot.
     * @param closeToGoalDistanceSquared Squared distance in which the robot is close to the goal.
     * @param costMap The CostMap.
     * @param outputPath To store the route taken to the goal.
     * @return CostMap traversal cost or negative 1 if there is an obstacle.
     */
    private int simulateTarget(Transform pose, double[] currentVelocity, Transform target,
                double closeToGoalDistanceSquared, CostMap costMap, List<Transform> outputPath) {
        int cost = 0;
        HashSet<CostMapPose> posesConsidered = new HashSet<>();
        outputPath.add(pose);
        while (true) {
            currentVelocity = nextVelocity(pose, currentVelocity, target,
                    closeToGoalDistanceSquared, Double.MAX_VALUE, UPDATE_INTERVAL_SECONDS);
            pose = simulatePosition(pose, currentVelocity, UPDATE_INTERVAL_SECONDS);
            outputPath.add(pose);
            CostMapPose costMapPose = costMap.discretize(pose.getPosition(0), pose.getPosition(1));
            if (!posesConsidered.contains(costMapPose)) {
                byte nextCost = costMap.getCost(costMapPose);
                if (CostMap.isObstacle(nextCost)) {
                    return -1;
                }
                posesConsidered.add(costMapPose);
                cost += (((int)nextCost) & 0xFF) + 1;
            }
            if (pose.planarDistanceToSquared(target) < closeToGoalDistanceSquared
                    && currentVelocity[0] == 0 && currentVelocity[1] == 0) {
                return cost;
            }
        }
    }

    /**
     * Compute the speed limit from the bins.
     *
     * @param bins The point cloud safety controller bins
     * @return The speed limit
     */
    private double computeSpeedLimit(int[] bins) {
        int points = 0;
        for (int i = 0; i < bins.length; i++) {
            points += bins[i];
            if (points > PointCloudSafetyController.getNumberOfBlockers()) {
                // The reaction time for linear velocity. The robot computes the maximum linear
                // velocity with this reaction time.
                double reactionTime = 2.0 * UPDATE_INTERVAL_SECONDS;
                double reactionVel = reactionTime * mSession.getRobotModel().getMaxLinearAcceleration();
                return -reactionVel + Math.sqrt(reactionVel * reactionVel
                        + 2 * i * PointCloudSafetyController.getBinDistance()
                        * mSession.getRobotModel().getMaxLinearAcceleration());
            }
        }
        return Double.MAX_VALUE;
    }

    /**
     * Generates the command velocity for the robot.
     * @param currentRobotPose Current position and orientation of the robot.
     * @param goal The goal transform.
     * @param costMap The CostMap.
     * @param path Way that the robot should drive
     * @param isBlocked True if the robot is blocked and cannot drive forward
     * @param bins The point cloud safety controller bins
     * @param closeToGoalDistance The distance in which the robot is close to the goal.
     * @param timeSinceLastUpdate The time interval since last update in ms. Ignored when negative.
     * @param outputPath The output path for the robot.
     * @return The command velocity. May be null.
     */
    @Override
    public double[] computeVelocities(Transform currentRobotPose, Transform goal,
            CostMap costMap, Path path, boolean isBlocked, int[] bins, double closeToGoalDistance,
            long timeSinceLastUpdate, List<Transform> outputPath) {
        double closeToGoalDistanceSquared = closeToGoalDistance * closeToGoalDistance;
        final long startTimeInNano = System.nanoTime();
        if (timeSinceLastUpdate < 0) {
            mCurrentVelocity = new double[]{0.0, 0.0};
        }
        if (path == null || path.size() < 1) {
            Log.v(TAG, "Path is too short or null");
            return brakeToStop();
        }

        StringBuilder binLogMessage = new StringBuilder("Bins [");
        for (int bin : bins) {
            binLogMessage.append(bin);
            binLogMessage.append(", ");
        }
        binLogMessage.append("], speed limit = ").append(computeSpeedLimit(bins));
        Log.v(TAG, binLogMessage.toString());

        Transform[] pathTf = new Transform[path.size()];
        for (int i = 0; i < path.size(); i++) {
            pathTf[i] = costMap.toWorldCoordinates(path.get(i));
        }
        // Replace the last point with the actual goal of the path since the input path does not
        // include the goal but the last transform in the path is (very) close to the goal.
        pathTf[pathTf.length - 1] = goal;

        // Find the closest node to the robot, taken to be the start of the path.
        double closestSquaredDist = Double.MAX_VALUE;
        int closestNode = -1;
        for (int i = 0; i < pathTf.length; i++) {
            double squaredDistance = pathTf[i].planarDistanceToSquared(currentRobotPose);
            if (closestSquaredDist > squaredDistance) {
                closestSquaredDist = squaredDistance;
                closestNode = i;
            }
        }

        // If we are too far away from the path then stop.
        // TODO(playerone) doublecheck if we really need this. The current position may be far away
        // from the path such as we have generated a path and teleop the robot to get far away.
        // May be better to replan instead of stop.
        if (Math.sqrt(closestSquaredDist) > mResolution * MAX_DISTANCE_FROM_START_NODE_IN_CELL) {
            Log.w(TAG, "The robot is too far away from the start of the path");
            return brakeToStop();
        }

        // Find the first node out side of the range of the robot
        Map<Integer, Double> scores = new HashMap<>();
        Map<Integer, List<Transform>> outputPaths = new HashMap<>();
        for (int i = closestNode; i < pathTf.length; i++) {
            double distanceSquared = pathTf[i].planarDistanceToSquared(currentRobotPose);
            // If the distance from the current robot to a node is larger than
            // MAX_DISTANCE_FROM_NODE_IN_CELL_SQUARED, skip.
            if (distanceSquared <
                    mResolution * mResolution * MAX_DISTANCE_FROM_NODE_IN_CELL_SQUARED) {
                continue;
            }
            if (distanceSquared > CARROT_DISTANCE_MAX_SQUARED) {
                break;
            }
            List<Transform> currentOutputPath = new LinkedList<>();
            int cost = simulateTarget(currentRobotPose, mCurrentVelocity, pathTf[i],
                    closeToGoalDistanceSquared, costMap, currentOutputPath);
            // TODO(playerone) remove sqrt() somehow.
            double score = (cost / 50) + 1.0 / Math.sqrt(distanceSquared);
            Log.v(TAG, "Node i=" + i + " cost=" + cost + " dist_squared=" + distanceSquared +
                       " score=" + score);
            if (cost >= 0) {
                scores.put(i, score);
                outputPaths.put(i, currentOutputPath);
            }
        }

        int bestNode = pathTf.length - 1;
        Transform target = pathTf[pathTf.length - 1];
        if (!scores.isEmpty()) {
            double lowestScore = Double.MAX_VALUE;
            for (Map.Entry<Integer, Double> score : scores.entrySet()) {
                if (score.getValue() < lowestScore) {
                    lowestScore = score.getValue();
                    bestNode = score.getKey();
                    target = pathTf[score.getKey()];
                }
            }
        }

        Log.i(TAG, "Target node = " + bestNode + " start = " + closestNode + " of " + pathTf.length);

        double goalDistanceSquared =
                pathTf[pathTf.length - 1].planarDistanceToSquared(currentRobotPose);
        if (goalDistanceSquared < closeToGoalDistanceSquared) {
            double[] stop = brakeToStop();
            Log.i(TAG, "Near target " + stop[0] + " " + stop[1]);
            if (stop[0] == 0 && stop[1] == 0) {
                Log.i(TAG, "Exiting");
                return null;
            }
            outputPath.add(currentRobotPose);
            outputPath.add(pathTf[pathTf.length - 1]);
            return stop;
        }

        if (outputPaths.containsKey(bestNode)) {
            outputPath.addAll(outputPaths.get(bestNode));
        } else if (!outputPaths.isEmpty()) {
            Log.w(TAG, "No path for node " + bestNode);
        }

        double delta_seconds = timeSinceLastUpdate < 0 ?
                UPDATE_INTERVAL_SECONDS : timeSinceLastUpdate / 1000.0;

        mCurrentVelocity = nextVelocity(currentRobotPose,
                mCurrentVelocity, target, closeToGoalDistanceSquared,
                isBlocked ? 0.0 : computeSpeedLimit(bins), delta_seconds);

        Log.d(TAG, "Velocity computing done. " + (System.nanoTime() - startTimeInNano) / 1000 +
                " micro seconds. Current velocity " + mCurrentVelocity[0] +
                " angular " + mCurrentVelocity[1] +
                " pose dist_squared" + target.planarDistanceToSquared(currentRobotPose));

        return mCurrentVelocity.clone();
    }


    /**
     * If true, then the local planner will not complete the goal until a null is returned.
     * @return True if we should wait for null.
     */
    @Override
    public boolean isStopManager() {
        return true;
    }
}
