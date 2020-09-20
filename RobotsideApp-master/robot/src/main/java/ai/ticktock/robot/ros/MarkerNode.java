package ai.cellbots.robot.ros;

import org.ros.message.Time;
import org.ros.namespace.GraphName;
import org.ros.node.NodeConfiguration;
import org.ros.node.topic.Publisher;

import java.util.List;

import ai.cellbots.common.Polygon;
import geometry_msgs.Point;
import visualization_msgs.Marker;

/**
 * Publishes visualization markers.
 */
class MarkerNode extends ROSNode {
    private final String mName;
    private Publisher<Marker> mMarkerPublisher = null;

    // Width of computer vision markers.
    private static final double VISION_MARKER_WIDTH = 0.05;
    // ID of of computer vision markers.
    private static final int VISION_MARKER_ID = 0;

    /**
     * Constructor
     *
     * @param nodeConfiguration The configuration for this node.
     * @param name              The name of the node.
     */
    MarkerNode(NodeConfiguration nodeConfiguration, String name) {
        super(nodeConfiguration, "cellbots/marker/" + name);
        mName = name;
        start();
    }

    /**
     * Called to start the node.
     */
    @Override
    protected void onStart() {
        mMarkerPublisher = getNode().newPublisher(
                GraphName.of("/marker/" + mName), Marker._TYPE);
    }

    /**
     * Sends a list of polygon markers.
     *
     * @param timestamp The timestamp.
     * @param polygons  The list of polygons.
     * @param width     The width of the markers.
     * @param id        The ID of the markers.
     */
    private void sendPolygonMarkers(double timestamp, List<Polygon> polygons, double width, int id) {
        if (timestamp < 0 || polygons == null || getNode() == null) {
            return;
        }
        if (mMarkerPublisher == null || mMarkerPublisher.getNumberOfSubscribers() == 0) {
            return;
        }

        visualization_msgs.Marker msg = mMarkerPublisher.newMessage();
        msg.getScale().setX(width);
        msg.getScale().setY(width);
        msg.getScale().setZ(width);
        msg.getColor().setA(1.0f);
        msg.getColor().setR(0.0f);
        msg.getColor().setG(1.0f);
        msg.getColor().setB(0.0f);
        msg.getHeader().setStamp(Time.fromMillis((long) timestamp));
        msg.getHeader().setFrameId("/map");
        msg.setAction(Marker.ADD);
        msg.setId(id);
        msg.setType(Marker.LINE_LIST);

        for (Polygon polygon : polygons) {
            if (polygon == null || polygon.isEmpty()) {
                continue;
            }
            List<ai.cellbots.common.Transform> points = polygon.getPoints();
            for (int point = 0; point < points.size(); point++) {
                Point start = getNode().getTopicMessageFactory().newFromType(Point._TYPE);
                start.setX(points.get(point).getPosition(0));
                start.setY(points.get(point).getPosition(1));
                start.setZ(points.get(point).getPosition(2));

                Point end = getNode().getTopicMessageFactory().newFromType(Point._TYPE);
                end.setX(points.get((point + 1) % points.size()).getPosition(0));
                end.setY(points.get((point + 1) % points.size()).getPosition(1));
                end.setZ(points.get((point + 1) % points.size()).getPosition(2));

                msg.getPoints().add(start);
                msg.getPoints().add(end);
            }
        }
        mMarkerPublisher.publish(msg);
    }

    /**
     * Sends the vision markers.
     *
     * @param timestamp The timestamp.
     * @param polygons  The list of polygons.
     */
    @SuppressWarnings("WeakerAccess")
    public void sendComputerVisionMarkers(double timestamp, List<Polygon> polygons) {
        sendPolygonMarkers(timestamp, polygons, VISION_MARKER_WIDTH, VISION_MARKER_ID);
    }
}
