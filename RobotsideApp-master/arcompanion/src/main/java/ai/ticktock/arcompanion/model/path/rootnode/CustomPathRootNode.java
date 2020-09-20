package ai.cellbots.arcompanion.model.path.rootnode;

import org.rajawali3d.materials.Material;

/**
 * PathRootNode for custom path. Rendered in a batch of disks.
 */
public class CustomPathRootNode extends PathRootNodeDisk {
    private static final double RADIUS = 0.30;  // In meters.

    /**
     * Constructs CustomPathRootNode.
     */
    public CustomPathRootNode() {
        super(RADIUS);
        initialize();
    }

    /**
     * Constructs CustomPathRootNode.
     *
     * @param radius  Radius of the disk in meters.
     */
    @SuppressWarnings("unused")
    public CustomPathRootNode(double radius) {
        super(radius);
        initialize();
    }

    /**
     * Initializes the node. Sets the color and opacity of the disk.
     */
    private void initialize() {
        Material material = new Material(true);
        setTransparent(true);
        material.setColor(new float[]{1.0f, 1.0f, 0.0f, 0.1f});  // {r, g, b, a}
        setMaterial(material);
    }
}
