package ai.cellbots.arcompanion.model.path.rootnode;

import android.graphics.Color;

/**
 * A root path node for rendering the current world's smoothed path nodes.
 * Rendered in a batch of cylinders with color black.
 *
 * Created by rpham64 on 10/1/17.
 */

public class SmoothedPathRootNode extends PathRootNode {

    public SmoothedPathRootNode() {
        super();
        setColor(Color.BLACK);
    }

    public SmoothedPathRootNode(float length, float radius, int segmentsL, int segmentsC) {
        super(length, radius, segmentsL, segmentsC);
        setColor(Color.BLACK);
    }
}
