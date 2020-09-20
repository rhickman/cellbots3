package ai.cellbots.robot.navigation;

import android.support.annotation.NonNull;
import android.util.Log;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.PriorityQueue;

import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.costmap.CostMapPose;

/**
 * Generate a path for the given costmap using A* path finding algorithm.
 */
public class AStarPathFinder extends PathFinder {
    private static final String TAG = AStarPathFinder.class.getSimpleName();
    private CostMap mMap;

    /**
     * Class constructor.
     *
     */
    public AStarPathFinder() {}

    /**
     * Store GridCostMapPoses with a score.
     */
    final private class CostMapPoseScored extends CostMapPose implements Comparable<CostMapPoseScored> {
        private final int mScore;

        /**
         * Create the GridCostMapPoseScored.
         * @param pose The initial pose.
         * @param score The score.
         */
        private CostMapPoseScored(CostMapPose pose, int score) {
            super(pose.getX(), pose.getY());
            mScore = score;
        }

        /**
         * Get the score.
         * @return The score.
         */
        private int getScore() {
            return mScore;
        }

        /**
         * Compare to another instance.
         * @param gridCostMapPoseScored The other instance to compare to.
         * @return The resulting value.
         */
        @Override
        public int compareTo(@NonNull CostMapPoseScored gridCostMapPoseScored) {
            return Integer.compare(getScore(), gridCostMapPoseScored.getScore());
        }
    }

    /**
     * Compute a new plan.
     * @param origin The CostMapPose of the origin of the path.
     * @param target The CostMapPose of the target of the path.
     * @return The Path, or null if it could not be computed.
     */
    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public Path computePlan(CostMapPose origin, CostMapPose target) {
        Log.i(TAG, "origin: " + origin + ", target: " + target);
        final CostMap map = mMap;
        if (map == null) {
            return null;
        }
        byte[] grid;
        int lowerX, lowerY;
        int width, height;
        synchronized (map) {
            grid = map.getFullCostRegion();
            lowerX = map.getLowerXLimit();
            lowerY = map.getLowerYLimit();
            width = map.getUpperXLimit() - map.getLowerXLimit();
            height = map.getUpperYLimit() - map.getLowerYLimit();
        }

        // Reject if the grid is empty.
        if (grid == null || grid.length == 0) {
            Log.w(TAG, "grid is null or empty");
            return null;
        }

        final boolean logCostMap = false;
        //noinspection ConstantConditions
        if (logCostMap) {
            for (int y = 0; y < height; y++) {
                StringBuilder line = new StringBuilder(String.format(Locale.US, "%04d, ", y) + " Grid [");
                for (int x = 0; x < width; x++) {
                    line.append(String.format(Locale.US, "%04d, ", grid[y * width + x]));
                }
                line.append("]");
                System.out.println(line);
                Log.v(TAG, line.toString());
            }
        }

        CostMapPose gridOrigin = new CostMapPose(origin.getX() - lowerX, origin.getY() - lowerY);
        CostMapPose gridTarget = new CostMapPose(target.getX() - lowerX, target.getY() - lowerY);

        // TODO: remove these checks once CostMap supports these bounds checks
        // Reject immediately if the target or origin are out of bounds for the CostMap.
        if (gridOrigin.getX() < 0 || gridOrigin.getY() < 0 || gridOrigin.getX() >= width
                || gridOrigin.getY() >= height) {
            Log.w(TAG, "origin is out of bound, origin=" + gridOrigin);
            return null;
        }
        if (gridTarget.getX() < 0 || gridTarget.getY() < 0 || gridTarget.getX() >= width
                || gridTarget.getY() >= height) {
            Log.w(TAG, "target is out of bound, target=" + gridTarget);
            return null;
        }

        // Reject immediately if the target or origin are in an obstacle
        if (CostMap.isObstacle(grid[gridOrigin.getY() * width + gridOrigin.getX()])) {
            Log.w(TAG, "origin is in obstacle, origin=" + gridOrigin);
            return null;
        }
        if (CostMap.isObstacle(grid[gridTarget.getY() * width + gridTarget.getX()])) {
            Log.w(TAG, "target is in obstacle, target=" + gridTarget);
            return null;
        }

        Log.i(TAG, "Finding path");

        boolean[] closedSet = new boolean[width * height];
        CostMapPose[] cameFrom = new CostMapPose[width * height];
        int[] gScores = new int[width * height];
        int[] fScores = new int[width * height];

        PriorityQueue<CostMapPoseScored> openSet = new PriorityQueue<>();
        HashSet<CostMapPose> openSetGuard = new HashSet<>();

        openSet.add(new CostMapPoseScored(gridOrigin, 0));
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                gScores[y * width + x] = Integer.MAX_VALUE;
                fScores[y * width + x] = Integer.MAX_VALUE;
                cameFrom[y * width + x] = null;
            }
        }
        gScores[gridOrigin.getY() * width + gridOrigin.getX()] = 0;
        fScores[gridOrigin.getY() * width + gridOrigin.getX()]
                = computeDiagonalDistanceHeuristic(origin, target);

        while (!openSet.isEmpty()) {
            CostMapPoseScored node = openSet.peek();

            if (node == null) {
                throw new Error("Non-empty open set, but null node");
            }

            if (gridTarget.equals(node)) {
                LinkedList<CostMapPose> path = new LinkedList<>();
                CostMapPose pose = node;
                while (pose != null) {
                    path.push(pose.offsetBy(lowerX, lowerY));
                    pose = cameFrom[pose.getY() * width + pose.getX()];
                }
                Log.i(TAG, "Path found with " + path.size() + " nodes");
                return new Path(path);
            }

            openSet.remove(node);
            openSetGuard.remove(new CostMapPose(node.getX(), node.getY()));
            closedSet[node.getY() * width + node.getX()] = true;

            for (int nx = -1; nx <= 1; nx++) {
                if (node.getX() + nx < 0) {
                    continue;
                }
                if (node.getX() + nx >= width) {
                    continue;
                }
                for (int ny = -1; ny <= 1; ny++) {
                    if (nx == 0 && ny == 0) {
                        continue;
                    }
                    if (node.getY() + ny < 0) {
                        continue;
                    }
                    if (node.getY() + ny >= height) {
                        continue;
                    }
                    CostMapPose neighbor = node.offsetBy(nx, ny);
                    if (closedSet[neighbor.getY() * width + neighbor.getX()]) {
                        continue;
                    }

                    // Skip if it is an obstacle.
                    if (CostMap.isObstacle(grid[neighbor.getY() * width + neighbor.getX()])) {
                        continue;
                    }

                    int dist = grid[neighbor.getY() * width + neighbor.getX()];
                    dist++;
                    if (nx == 0 || ny == 0) {
                        dist *= 10;
                    } else {
                        dist *= 14; // Square root 2 for diagonals
                    }
                    int gScore = gScores[node.getY() * width + node.getX()] + dist;
                    if (gScore >= gScores[neighbor.getY() * width + neighbor.getX()]) {
                        // Not a better path to the neighbor
                        if (!openSetGuard.contains(neighbor)) {
                            openSet.add(new CostMapPoseScored(neighbor,
                                    fScores[neighbor.getY() * width + neighbor.getX()]));
                            openSetGuard.add(neighbor);
                        }
                    } else {
                        cameFrom[neighbor.getY() * width + neighbor.getX()] = node;
                        gScores[neighbor.getY() * width + neighbor.getX()] = gScore;
                        fScores[neighbor.getY() * width + neighbor.getX()] = gScore
                                + computeDiagonalDistanceHeuristic(neighbor, target);
                        if (openSetGuard.contains(neighbor)) {
                            openSet.remove(new CostMapPoseScored(neighbor, 0));
                        }
                        openSet.add(new CostMapPoseScored(neighbor,
                                fScores[neighbor.getY() * width + neighbor.getX()]));
                    }
                }
            }
        }

        Log.i(TAG, "Path not found");
        return null;
    }

    /**
     * Compute the diagonal distance heuristic. This heuristic computes the cost of moving on the
     * shortest free-space path between two positions, assuming that all the intervening costs are
     * zero. The heuristic will reduce the search space since it will de-prioritize paths that are
     * far from the shortest path to the end pose.
     * @param start The start node.
     * @param end The end node.
     * @return The resulting heuristic.
     */
    private int computeDiagonalDistanceHeuristic(CostMapPose start, CostMapPose end) {
        int dx = Math.abs(start.getX() - end.getX());
        int dy = Math.abs(start.getY() - end.getY());
        if (dx > dy) {
            // (dy) * 14 + (dx - dy) * 10 -> dx * 10 + dy * 4
            return dx * 10 + dy * 4;
        }
        // (dx) * 14 + (dy - dx) * 10 -> dy * 10 + dx * 4
        return dx * 4 + dy * 10;
    }

    /**
     * Set the CostMap of the PathFinder.
     * @param costMap The costMap.
     */
    @Override
    public void setCostMap(CostMap costMap) {
        mMap = costMap;
    }
}
