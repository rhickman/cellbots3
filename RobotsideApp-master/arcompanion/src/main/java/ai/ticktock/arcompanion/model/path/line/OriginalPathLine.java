package ai.cellbots.arcompanion.model.path.line;

import android.graphics.Color;

import org.rajawali3d.math.vector.Vector3;

import java.util.Stack;

/**
 * A Line3D object rendering a world's original path. Drawn according to a device's position when
 * being used to create the world/ADF for the first time.
 *
 * Created by rpham64 on 10/2/17.
 */

public class OriginalPathLine extends PathLine {

    public OriginalPathLine(Stack<Vector3> originalPathNodes) {
        super(originalPathNodes);
        setColor(Color.YELLOW);
    }

    public OriginalPathLine(Stack<Vector3> originalPathNodes, float thickness) {
        super(originalPathNodes, thickness);
        setColor(Color.YELLOW);
    }
}
