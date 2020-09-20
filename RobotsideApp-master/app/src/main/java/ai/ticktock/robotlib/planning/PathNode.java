package ai.cellbots.robotlib.planning;

import java.util.ArrayList;
import java.util.List;

import ai.cellbots.common.Transform;

/**
 * Class used to handle data of a particular transform and a list of it's adjacent nodes (allowed
 * movements) with their associated costs.
 */
public class PathNode {
    // Weight of allowed movements.
    // TODO: assign a different weight for the different movements.
    private static final double MOVEMENT_WEIGHT = 1.0;

    private Transform mTransform;
    private int[][] mTransformGrid;

    /**
     * Class constructor. Creates a PathNode from a given transform and stores information about
     * the grid to navigate.
     *
     * @param nodeTransform, current node.
     * @param transformGrid, grid to navigate.
     */
    public PathNode(Transform nodeTransform, int[][] transformGrid) {
        mTransform = nodeTransform;
        mTransformGrid = new int[transformGrid.length][];
        for(int i = 0; i < transformGrid.length; i++)
            mTransformGrid[i] = transformGrid[i].clone();
    }

    /**
     * Find the adjacent nodes to the current PathNode.
     *
     * @return list of adjacent nodes as objects of the PathNode class.
     */
    protected List<PathNode> adjacentNodes() {
        List<PathNode> adjNodesList = new ArrayList<>();

        // Find the adjacent nodes according to the allowed movements.
        PathNode[] nextPoses = {moveNorthWest(), moveNorth(), moveNorthEast()};

        //  In each case I check that the adjacent nodes don't fall out of the grid limits.
        for (int i = 0; i < nextPoses.length; i++) {
            if (!nextPoses[i].isOutOfMap()) {
                // Add adjacent node to list to return.
                adjNodesList.add(nextPoses[i]);
            }
        }
        return adjNodesList;
    }

    /**
     * Check if a PathNode is out of map boundaries.
     *
     * @return true if is out of map, false if not.
     */
    public boolean isOutOfMap() {
        if (mTransform.getPosition(0) < 0 || mTransform.getPosition(0)
                > mTransformGrid.length - 1) {
            return true;
        }
        if (mTransform.getPosition(1) < 0 || mTransform.getPosition(1)
                > mTransformGrid[0].length - 1) {
            return true;
        }
        return false;
    }

    // TODO: adapt this to make movements according to rotation angle. Use a dictionary.
    private PathNode moveNorthWest() {
        Transform next_position = new Transform(mTransform.getPosition(0) - 1,
                mTransform.getPosition(1) - 1, 0, 0);
        return new PathNode(next_position, mTransformGrid);
    }

    private PathNode moveNorthEast() {
        Transform next_position = new Transform(mTransform.getPosition(0) - 1,
                mTransform.getPosition(1) + 1, 0, 0);
        return new PathNode(next_position, mTransformGrid);
    }

    private PathNode moveNorth() {
        Transform next_position = new Transform(mTransform.getPosition(0) - 1,
                mTransform.getPosition(1), 0, 0);
        return new PathNode(next_position, mTransformGrid);
    }

    protected Transform getTransform() {
        return mTransform;
    }
}
