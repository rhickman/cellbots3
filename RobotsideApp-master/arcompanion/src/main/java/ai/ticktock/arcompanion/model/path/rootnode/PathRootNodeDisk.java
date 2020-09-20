package ai.cellbots.arcompanion.model.path.rootnode;

import org.rajawali3d.primitives.NPrism;

/**
 * A root path node for robot's path node using disk.
 * Renders them in batch for improved performance.
 */
public class PathRootNodeDisk extends NPrism {
    private static final double HEIGHT = 0.02;
    private static final int NUM_SIDES = 20;

    public PathRootNodeDisk(double radius) {
        super(NUM_SIDES, radius, radius, 0.0, HEIGHT, true);
        initialize();
    }

    /**
     * Initializes the object.
     */
    private void initialize() {
        setDoubleSided(true);
        // Enable rendering the child for improved performance.
        setRenderChildrenAsBatch(true);
        setVisible(false);
    }
}
