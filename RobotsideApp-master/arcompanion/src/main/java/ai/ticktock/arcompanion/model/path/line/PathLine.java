package ai.cellbots.arcompanion.model.path.line;

import org.rajawali3d.materials.Material;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Line3D;

import java.util.Stack;

/**
 * A Line3D object for rendering a world's original, smoothed, custom and goal paths.
 * Requires a Stack of Vector3 objects with each of the respective path nodes' positions.
 *
 * Created by rpham64 on 10/2/17.
 */

public abstract class PathLine extends Line3D{

    public static final float PATH_LINE_THICKNESS = 1f;

    protected PathLine(Stack<Vector3> pathNodes) {
        super(pathNodes, PATH_LINE_THICKNESS);
        init();
    }

    protected PathLine(Stack<Vector3> points, float thickness) {
        super(points, thickness);
        init();
    }

    public void init() {
        Material m = new Material(true);
        setMaterial(m);
        setDoubleSided(true);
    }
}
