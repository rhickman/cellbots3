package ai.cellbots.arcompanion.model.path.line;

import android.graphics.Color;

import org.rajawali3d.math.vector.Vector3;

import java.util.Stack;

/**
 * A Line3D object rendering of a smoothed-out version of the OriginalPathLine.
 *
 * Created by rpham64 on 10/2/17.
 */

public class SmoothedPathLine extends PathLine {

    public SmoothedPathLine(Stack<Vector3> pathNodes) {
        super(pathNodes);
        setColor(Color.BLACK);
    }

    public SmoothedPathLine(Stack<Vector3> points, float thickness) {
        super(points, thickness);
        setColor(Color.BLACK);
    }
}
