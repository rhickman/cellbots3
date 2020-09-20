package ai.cellbots.robot.navigation;

import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.costmap.CostMapPose;
import ai.cellbots.robot.navigation.Path;

/**
 * PathFindingAlgorithm class. Parent class for navigation algorithms, which declares an abstract
 * method that finds a path between an origin and a target.
 */

public abstract class PathFinder {
    public abstract void setCostMap(CostMap map);
    public abstract Path computePlan(CostMapPose origin, CostMapPose target);
}