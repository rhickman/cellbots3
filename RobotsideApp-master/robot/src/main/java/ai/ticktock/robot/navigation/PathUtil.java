package ai.cellbots.robot.navigation;

import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.costmap.CostMapPose;

/**
 * Utility class for path operations.
 */
public final class PathUtil {
    /**
     * Checks if a given path passes through an obstacle in the costmap.
     *
     * @param path    The path.
     * @param costMap The cost map.
     * @return True if both the path and the costmap are not null and the path goes through an obstacle.
     */
    public static final boolean isPathThroughObstacle(Path path, CostMap costMap) {
        if (path == null || !path.isValid() ||
                costMap == null || !costMap.isValid()) {
            return false;
        }
        for (int i = 0; i < path.size(); i++) {
            CostMapPose cell = path.get(i);
            boolean isInObstacle = CostMap.isObstacle(costMap.getCost(cell));
            if (isInObstacle) {
                return true;
            }
        }
        return false;
    }
}
