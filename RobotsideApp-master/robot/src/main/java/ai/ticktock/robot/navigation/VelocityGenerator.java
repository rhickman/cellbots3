package ai.cellbots.robot.navigation;

import java.util.List;

import ai.cellbots.common.Transform;
import ai.cellbots.robot.costmap.CostMap;

/**
 * VelocityGenerator generates velocity commands given a new path and the robot pose.
 */
interface VelocityGenerator {
    /**
     * Generate the command velocity for the robot.
     * @param currentRobotPose Current position and orientation of the robot.
     * @param goal The goal transform.
     * @param costMap The CostMap.
     * @param path Way that the robot should drive
     * @param isBlocked True if the robot is blocked and cannot drive forward
     * @param bins The point cloud safety controller bins
     * @param closeToGoalDistance The distance in which the robot is close to the goal.
     * @param ticksSinceLastUpdate The number of ticks since the last update. Ignored when negative.
     * @param outputPath The output path for the robot.
     * @return The command velocity. May be null.
     */
    double[] computeVelocities(Transform currentRobotPose, Transform goal, CostMap costMap,
            Path path, boolean isBlocked, int[] bins, double closeToGoalDistance,
            long ticksSinceLastUpdate, List<Transform> outputPath);

    /**
     * If true, then the local planner will not complete the goal until a null is returned.
     * @return True if we should wait for null.
     */
    boolean isStopManager();

}
