package ai.cellbots.robot.navigation;

import static org.apache.commons.math3.util.FastMath.min;

import android.util.Log;

import java.util.List;

import ai.cellbots.common.Geometry;
import ai.cellbots.common.Transform;
import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.state.RobotSessionGlobals;

public class PurePursuitVelocityGenerator implements VelocityGenerator {
    private static final String TAG = PurePursuitVelocityGenerator.class.getSimpleName();

    // If two nodes are within this distance, the linear velocity will be calculated in proportion
    // to the distance.
    private static final double MAX_NODE_DISTANCE = 1.0;
    private final RobotSessionGlobals mSession;
    private final double mResolution;
    private final double mMaxNodeDistance;
    private final PurePursuit mPurePursuit = new PurePursuit(0.5);

    public PurePursuitVelocityGenerator(RobotSessionGlobals session, double resolution) {
        this(session, resolution, MAX_NODE_DISTANCE);
    }

    private PurePursuitVelocityGenerator(RobotSessionGlobals session, double resolution,
                                         double maxCloseNodeDistance) {
        mSession = session;
        mResolution = resolution;
        mMaxNodeDistance = maxCloseNodeDistance;
        if (mMaxNodeDistance <= 0) {
            throw new IllegalArgumentException(
                    "Max node distance must be larger than 0: " + mMaxNodeDistance);
        }
    }

    /**
     * Generates the command velocity for the robot.
     *
     * @param currentRobotPose     Current position and orientation of the robot.
     * @param goal                 The goal transform.
     * @param costMap              The CostMap.
     * @param path                 Way that the robot should drive
     * @param isBlocked            True if the robot is blocked and cannot drive forward
     * @param bins                 The point cloud safety controller bins
     * @param closeToGoalDistance  The distance in which the robot is close to the goal.
     * @param ticksSinceLastUpdate The number of ticks since the last update. Ignored when negative.
     * @param outputPath           The output path for the robot.
     * @return The command velocity. May be null.
     */
    @Override
    public double[] computeVelocities(Transform currentRobotPose, Transform goal,
                                      CostMap costMap, Path path, boolean isBlocked, int[] bins,
                                      double closeToGoalDistance, long ticksSinceLastUpdate,
                                      List<Transform> outputPath) {
        if (path == null || path.size() < 2) {
            return null;
        }
        double[] goalNode = {
                path.get(0).getX() * mResolution,
                path.get(0).getY() * mResolution
        };
        double[] helperNode = {
                path.get(1).getX() * mResolution,
                path.get(1).getY() * mResolution
        };

        int pathSize = path.size();

        // Ask next node only if it's not the last one
        boolean askNextNode = mPurePursuit.isCloseToNode(currentRobotPose, goalNode)
                && pathSize > 2;

        double kappa = mPurePursuit.computeCurvatureToReach(currentRobotPose, goalNode, helperNode);

        // TODO (playersix): Implement remove(index) in Path class
        if (askNextNode) {
            // Remove the head node in the path
            path.removeElement(path.get(0));
        }

        Log.i(TAG, "Update at: " + goalNode[0] + " " + goalNode[1] + " to " + helperNode[0] + " "
                + helperNode[1] + " ask next: " + askNextNode + " size: "
                + pathSize + " kappa: " + kappa);

        return reduceLinearVelocityProportionalToDistance(
                kappa,
                Geometry.getDistanceBetweenTwoPoints(currentRobotPose.getPosition(), goalNode),
                isReachingLastTrack(pathSize));
    }

    /**
     * Sees if the robot is close to the goal.
     *
     * @param pathSize Actual size of the path.
     * @return True if the path has only two nodes.
     */
    private boolean isReachingLastTrack(int pathSize) {
        return pathSize <= 2;
    }

    /**
     * Gets speed commands limiting linear velocity if the robot is reaching the final node, and the
     * angular velocity given a predefined value MAX_ANGULAR_VELOCITY.
     *
     * @param angularVelocity Angular velocity
     * @param distToGoal      Distance to the actual goal
     * @param isFinalNode     True if the robot is reaching the latest node
     * @return Linear and angular velocities.
     */
    private double[] reduceLinearVelocityProportionalToDistance(
            double angularVelocity, double distToGoal, boolean isFinalNode) {
        double clampedAngularVelocity =
                Math.min(Math.max(
                        -mSession.getRobotModel().getMaxAngularSpeed(), angularVelocity),
                        mSession.getRobotModel().getMaxAngularSpeed());
        return new double[]{
                // Limit linear velocity
                isFinalNode ? limitLinearVelocity(distToGoal)
                        : mSession.getRobotModel().getMaxLinearSpeed()
                        * (1 - (Math.abs(clampedAngularVelocity)
                        / mSession.getRobotModel().getMaxAngularSpeed())),
                // Limit angular velocity
                clampedAngularVelocity
        };
    }


    /**
     * Decreases linear velocity proportional to the distance to the goal.
     *
     * @param distance Distance to the next node
     * @return Linear velocity to control the robot
     */
    private double limitLinearVelocity(double distance) {
        distance = min(distance, mMaxNodeDistance) / mMaxNodeDistance;
        // The linearVelocity has to be proportional to the distance to the desiredNode
        return mSession.getRobotModel().getMaxLinearSpeed() * distance;
    }

    /**
     * If true, then the local planner will not complete the goal until a null is returned.
     *
     * @return True if we should wait for null.
     */
    @Override
    public boolean isStopManager() {
        return false;
    }
}