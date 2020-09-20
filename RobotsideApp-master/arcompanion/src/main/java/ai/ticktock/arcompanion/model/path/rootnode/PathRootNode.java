package ai.cellbots.arcompanion.model.path.rootnode;

import org.rajawali3d.materials.Material;
import org.rajawali3d.primitives.Cylinder;

/**
 * A root path node for rendering the Cellbots robot's path nodes as cylinders in AR view.
 */
public abstract class PathRootNode extends Cylinder {
    private static final float LENGTH = 0.05f;
    private static final float RADIUS = 0.02f;
    private static final int NUM_LENGTH_SEGMENTS = 1;
    private static final int NUM_CIRCLE_SEGMENTS = 40;

    protected PathRootNode() {
        super(LENGTH, RADIUS, NUM_LENGTH_SEGMENTS, NUM_CIRCLE_SEGMENTS);
        initialize();
    }

    protected PathRootNode(float length, float radius, int segmentsL, int segmentsC) {
        super(length, radius, segmentsL, segmentsC);
        initialize();
    }

    private void initialize() {
        Material material = new Material(true);
        setMaterial(material);
        setDoubleSided(true);
        setRenderChildrenAsBatch(true);
        // Path node floats in space at position (0, 0, 0) due to having no position set.
        // Since we want only its children (all path nodes) to be visible, setting root cube to be
        // invisible hides the random floating path node.
        setVisible(false);
    }
}
