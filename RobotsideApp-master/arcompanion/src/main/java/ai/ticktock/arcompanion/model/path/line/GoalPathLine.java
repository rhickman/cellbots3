package ai.cellbots.arcompanion.model.path.line;

import android.graphics.Color;

import org.rajawali3d.math.vector.Vector3;

import java.util.Stack;

/**
 * Line3D object rendering a robot's path towards a goal.
 *
 * Created by rpham64 on 10/2/17.
 */

public class GoalPathLine extends PathLine {

    public GoalPathLine(Stack<Vector3> pathNodes) {
        super(pathNodes);
        setColor(Color.GREEN);
    }

    @SuppressWarnings("unused")
    public GoalPathLine(Stack<Vector3> pathNodes, float thickness) {
        super(pathNodes, thickness);
        setColor(Color.GREEN);
    }
}
