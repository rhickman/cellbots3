package ai.cellbots.robotlib.planning;

import java.util.ArrayList;
import java.util.List;

import ai.cellbots.common.Transform;

/**
 * WIP: Class that performs a Navigation algorithm on a map. For the moment it finds all the paths
 * between an origin and goal and it gives the first one.
 * TODO: evolve the Algorithm into Dijkstra's and take rotations into account.
 */
public class NavigationAlgorithm {
    // Grid for the local map.
    private int[][] mLocalMap;
    // Path from origin to goal.
    private List<List<PathNode>> mPathList = new ArrayList<>();
    // Temporary list of nodes making a path.
    private List<PathNode> mTempPath = new ArrayList<>();

    /**
     * Class constructor.
     *
     * @param localMap, grid that will be used to move around.
     */
    public NavigationAlgorithm(int[][] localMap) {
        // Set the local map to navigate.
        mLocalMap = new int[localMap.length][];
        for (int i = 0; i < localMap.length; i++) {
            mLocalMap[i] = localMap[i].clone();
        }
    }

    /**
     * Search algorithm.
     *
     * @param origin, current transform.
     * @param goal,   target transform.
     * @return first path found from origin to goal as a list of transforms.
     */
    public List<Transform> findPath(Transform origin, Transform goal) throws NoPathException {

        // Make a PathNode out of origin and goal.
        PathNode mOrigin = new PathNode(origin, mLocalMap);
        PathNode mGoal = new PathNode(goal, mLocalMap);

        if (mOrigin.isOutOfMap()) {
            throw new Error("Origin is out of map");
        }
        if (mGoal.isOutOfMap()) {
            throw new Error("Goal is out of map");
        }
        // Explore local map to find all paths.
        explore(mOrigin, mGoal);

        List<Transform> transformPath = new ArrayList<>();
        if (mPathList.isEmpty()) {
            throw new NoPathException("Goal is out of reachable area");
        }
        for (PathNode n : mPathList.get(0)) {
            transformPath.add(n.getTransform());
        }
        return transformPath;
    }

    /**
     * Explore local map to find all possible paths between target and goal.
     *
     * @param origin point as a PathNode object.
     * @param goal   point as a PathNode object.
     */
    private void explore(PathNode origin, PathNode goal) {
        // Check border cases
        // 1) Goal was reached
        if ((origin.getTransform()).equals(goal.getTransform())) {
            // Add a copy of current path to the end of path list.
            List<PathNode> tempPathCopy = new ArrayList<>();
            tempPathCopy.addAll(mTempPath);
            mPathList.add(tempPathCopy);
            return;
        }
        // 2) Coordinate has already been visited.
        if (mTempPath.contains(goal)) {
            return;
        }

        // Populate list of children for the node.
        List<PathNode> nodeChildren = origin.adjacentNodes();

        for (PathNode child : nodeChildren) {
            // Add goal to path
            mTempPath.add(child);
            explore(child, goal);
            // Remove goal from temp_path
            mTempPath.remove(child);
        }
    }
}