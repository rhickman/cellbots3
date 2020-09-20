package ai.cellbots.arcompanion.model.path.rootnode;

import android.graphics.Color;

/**
 * A path root node for rendering the current world's original path nodes.
 * Rendered in a batch of cylinders with color yellow.
 *
 * Created by rpham64 on 10/1/17.
 */

public class OriginalPathRootNode extends PathRootNode {

    public OriginalPathRootNode() {
        super();
        setColor(Color.YELLOW);
    }

    public OriginalPathRootNode(float length, float radius, int segmentsL, int segmentsC) {
        super(length, radius, segmentsL, segmentsC);
        setColor(Color.YELLOW);
    }

}
