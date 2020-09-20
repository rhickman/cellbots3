package ai.cellbots.robot.navigation;

import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.costmap.CostMapPose;

/**
 * Generate a path for the given costmap using Dijkstra path finding algorithm.
 */
public class DijkstraPathFinder extends PathFinder {
    CostMap mMap;

    // Maximum value allowed for a neighbor cell to be considered a possible place to go to. Cells
    // whose cost are above this value are taken as occupied.
    final static byte NEIGHBOR_COST_LIMIT = 100;
    // A scale factor used to compute the score for a cell based on the cost of the target and the
    // distance to it. Brings cost [0, ..., 127] to a range similar than the distance [1,..,1.4].
    final static double COST_SCALE_FACTOR = 0.1;

    /**
     * Class constructor.
     */
    public DijkstraPathFinder() {
    }

    public void setCostMap(CostMap costMap) {
        mMap = costMap;
    }

    /**
     * Finds a path between two CostMap poses, origin and target, on the map.
     *
     * @param origin The CostMapPose of the origin of the path.
     * @param target The CostMapPose of the target of the path.
     * @return Path found.
     */
    public Path computePlan(CostMapPose origin, CostMapPose target) {
        // TODO: (FlorGrosso) use a collection class that better approximates a Fibonacci heap, with
        // O(log(N)) time for add/poll/remove methods. A priority queue provides O(log(N)) time for
        // add/poll, but O(N) for remove(Object) and contains(Object).
        PriorityQueue<DijkstraNode> unvisitedNodes = new PriorityQueue<>();
        HashMap<CostMapPose, DijkstraNode> poseToNode = new HashMap<>();

        for (CostMapPose costMapPose : mMap.getAllCostMapPoses()) {
            DijkstraNode node = new DijkstraNode(costMapPose);
            if (costMapPose.equals(origin)) {
                node.setScore(0);
            }
            poseToNode.put(costMapPose, node);
            unvisitedNodes.add(node);
        }

        DijkstraNode currentNode = null;

        while (!unvisitedNodes.isEmpty()) {
            // Get the node with the lowest cost.
            currentNode = unvisitedNodes.poll();

            // We reached our target, end it here.
            if (currentNode.getPose().equals(target)) {
                break;
            }

            // Find all nodes connected to the current node.
            List<CostMapPose> neighbors = mMap.neighborsFor(currentNode.getPose(), NEIGHBOR_COST_LIMIT);

            // Visit neighbors.
            for (CostMapPose neighbor : neighbors) {
                DijkstraNode neighborNode = poseToNode.get(neighbor);

                double updatedCostToOrigin = currentNode.getScore() + COST_SCALE_FACTOR *
                        mMap.getCost(currentNode.getPose()) + currentNode.distanceTo(neighborNode);
                if (updatedCostToOrigin < neighborNode.getScore()) {
                    unvisitedNodes.remove(neighborNode);
                    neighborNode.setScore(updatedCostToOrigin);
                    neighborNode.setPrevious(currentNode);
                    unvisitedNodes.add(neighborNode);
                }
            }
        }
        return this.buildPathFromNode(currentNode);
    }

    /**
     * Build a path between origin and target from the list of connected nodes.
     *
     * @return Path from origin to target with the lowest cost.
     */
    private Path buildPathFromNode(DijkstraNode node) {
        Path path = new Path(node.getPose());
        while (node.hasPrevious()) {
            node = node.getPrevious();
            path.addElementToTail(node.getPose());
        }
        // Reverse path to go from origin to target.
        path.reverse();
        return path;
    }
}
