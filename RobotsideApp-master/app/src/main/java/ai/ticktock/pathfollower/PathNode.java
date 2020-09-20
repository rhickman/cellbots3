package ai.cellbots.pathfollower;

import android.support.annotation.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import ai.cellbots.common.Transform;

/**
 * Stores a node within the path, storing a transform and the nearest neighbors. The transform
 * position of the node is immutable, however the list of close distances could change as the
 * graph changes.
 */
class PathNode {
    // Distances to nodes considered close to this node.
    private final Map<PathNode, Double> mCloseDistances;
    private final Transform mTransform;

    /**
     * Create the node.
     * @param tf The transform position.
     */
    PathNode(Transform tf) {
        mCloseDistances = new HashMap<>();
        mTransform = tf;
    }

    /**
     * Get transform of this node.
     * @return The transform.
     */
    Transform getTransform() {
        return mTransform;
    }

    /**
     * Get the close distance count.
     * @return The number of close distances.
     */
    int closeDistanceCount() {
        return mCloseDistances.size();
    }

    /**
     * Clear out all close distances.
     */
    void clearCloseDistances() {
        mCloseDistances.clear();
    }

    /**
     * Get the set of nodes of the close distances.
     * @return The unmodifiable set of close distances, nodes only.
     */
    Set<PathNode> getCloseDistanceNodes() {
        return Collections.unmodifiableSet(mCloseDistances.keySet());
    }

    /**
     * Get the set of nodes and distances of the close distances.
     * @return The unmodifiable set of close distances, nodes and distances.
     */
    Set<Map.Entry<PathNode, Double>> getCloseDistances() {
        return Collections.unmodifiableSet(mCloseDistances.entrySet());
    }

    /**
     * Set the close distance to a node.
     * @param next The node to be set as the close distance. Must not be null or self.
     * @param distance The next distance to compute.
     */
    void putCloseDistance(@NonNull PathNode next, double distance) {
        Objects.requireNonNull(next);
        if (next == this) {
            throw new IllegalArgumentException("Used self as a next path node");
        }
        if (distance < 0) {
            throw new IllegalArgumentException("Distance is less than 0: " + distance);
        }
        mCloseDistances.put(next, distance);
    }

    /**
     * Remove a close distance node from the system.
     * @param next The next node to remove.
     */
    void removeCloseDistance(@NonNull PathNode next) {
        Objects.requireNonNull(next);
        while (mCloseDistances.containsKey(next)) {
            mCloseDistances.remove(next);
        }
    }

}
