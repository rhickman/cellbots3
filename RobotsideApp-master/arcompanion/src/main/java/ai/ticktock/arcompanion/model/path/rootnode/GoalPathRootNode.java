package ai.cellbots.arcompanion.model.path.rootnode;

import org.rajawali3d.materials.Material;

/**
 * PathRootNode for robot's goal path. Rendered in a batch of disks.
 */

public class GoalPathRootNode extends PathRootNodeDisk {
    private static final double RADIUS = 0.15;  // In meters.

    /**
     * Constructs GoalPathRootNode.
     */
    public GoalPathRootNode() {
        super(RADIUS);
        initialize();
    }

    /**
     * Constructs CustomPathRootNode.
     *
     * @param radius  Radius of the disk in meters.
     */
    public GoalPathRootNode(float radius) {
        super(radius);
        initialize();
    }

    /**
     * Initializes the node. Sets the color and opacity of the disk.
     */
    private void initialize() {
        Material material = new Material(true);
        setTransparent(true);
        material.setColor(new float[]{0.0f, 1.0f, 0.0f, 0.2f});  // {r, g, b, a}
        setMaterial(material);
    }

}
