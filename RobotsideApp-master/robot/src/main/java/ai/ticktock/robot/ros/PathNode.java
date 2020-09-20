package ai.cellbots.robot.ros;

import org.ros.message.Time;
import org.ros.namespace.GraphName;
import org.ros.node.NodeConfiguration;
import org.ros.node.topic.Publisher;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import ai.cellbots.common.Transform;
import ai.cellbots.robot.costmap.CostMapPose;
import geometry_msgs.PoseStamped;
import nav_msgs.Path;

/**
 * Visualize paths.
 */
class PathNode extends ROSNode {
    /**
     * The type of path to be visualized.
     */
    @SuppressWarnings("WeakerAccess")
    public enum PathType {
        LOCAL,
        GLOBAL
    }

    private final PathType mType;
    private Publisher<Path> mPublisher;

    /**
     * Constructor
     *
     * @param nodeConfiguration The configuration for this node.
     * @param type              The type of path from this node.
     */
    PathNode(NodeConfiguration nodeConfiguration, PathType type) {
        super(nodeConfiguration, "cellbots/path/" + type.toString().toLowerCase());
        mType = type;
        start();
    }

    /**
     * Start the node.
     */
    @Override
    protected void onStart() {
        mPublisher = getNode().newPublisher(
                GraphName.of("/path/" + mType.toString().toLowerCase()), Path._TYPE);
    }

    /**
     * Convert a Transform to a ROS PoseStamped in "/map".
     *
     * @param l The Transform.
     * @return The PoseStamped.
     */
    private geometry_msgs.PoseStamped toPose(Transform l) {
        PoseStamped sf = getNode().getTopicMessageFactory().newFromType(PoseStamped._TYPE);

        sf.getPose().getPosition().setX(l.getPosition(0));
        sf.getPose().getPosition().setY(l.getPosition(1));
        sf.getPose().getPosition().setZ(l.getPosition(2));

        sf.getPose().getOrientation().setX(l.getRotation(0));
        sf.getPose().getOrientation().setY(l.getRotation(1));
        sf.getPose().getOrientation().setZ(l.getRotation(2));
        sf.getPose().getOrientation().setW(l.getRotation(3));

        sf.getHeader().setFrameId("/map");
        sf.getHeader().setStamp(Time.fromMillis((long) (l.getTimestamp() * 1000.0)));
        return sf;
    }

    /**
     * Publish the path.
     *
     * @param path The list of transforms to publish.
     */
    @SuppressWarnings("WeakerAccess")
    public void publishPath(List<Transform> path) {
        if (mPublisher == null || getNode() == null) {
            return;
        }

        Path msg = mPublisher.newMessage();
        msg.getHeader().setStamp(Time.fromMillis(System.currentTimeMillis()));
        msg.getHeader().setFrameId("/map");
        LinkedList<PoseStamped> ps = new LinkedList<>();
        for (ai.cellbots.common.Transform t : path) {
            ps.add(toPose(t));
        }
        msg.setPoses(ps);
        mPublisher.publish(msg);
    }

    /**
     * Publish the path.
     *
     * @param path       The list of CostMapPoses to publish.
     * @param resolution The resolution of the CostMap.
     */
    @SuppressWarnings("WeakerAccess")
    public void publishPath(List<CostMapPose> path, double resolution) {
        ArrayList<Transform> tfs = new ArrayList<>(path.size());
        for (CostMapPose pose : path) {
            tfs.add(pose.toWorldCoordinates(resolution));
        }
        publishPath(tfs);
    }
}
