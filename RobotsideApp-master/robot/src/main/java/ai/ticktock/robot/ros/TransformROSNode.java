package ai.cellbots.robot.ros;

import org.ros.message.Time;
import org.ros.namespace.GraphName;
import org.ros.node.NodeConfiguration;
import org.ros.node.topic.Publisher;
import org.ros.rosjava_geometry.Quaternion;
import org.ros.rosjava_geometry.Vector3;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import ai.cellbots.common.Transform;
import geometry_msgs.TransformStamped;
import tf2_msgs.TFMessage;

/**
 * Publish all the ROS transforms.
 */
class TransformROSNode extends ROSNode {
    private Publisher<TFMessage> mTfPublisher = null;
    private Publisher<TFMessage> mStaticTfPublisher = null;

    private List<Transform> mInitialListTransforms;
    private List<String> mInitialListNames;

    /**
     * Constructor
     *
     * @param nodeConfiguration The configuration for this node.
     */
    TransformROSNode(NodeConfiguration nodeConfiguration) {
        super(nodeConfiguration, "cellbots/tf");
        start();
    }

    /**
     * Function called by ROS to start the system up.
     */
    @Override
    public synchronized void onStart() {
        mTfPublisher = getNode().newPublisher(
                GraphName.of("/tf"), TFMessage._TYPE);
        mStaticTfPublisher = getNode().newPublisher(
                GraphName.of("/tf_static"), TFMessage._TYPE);
        mStaticTfPublisher.setLatchMode(true);
        if (mInitialListNames != null && mInitialListTransforms != null) {
            publishPointOfInterestPoses(mInitialListTransforms, mInitialListNames);
        }
    }

    /**
     * Convert a Transform to a ROS TransformStamped.
     *
     * @param l      The Transform.
     * @param frame  The ROS coordinate child frame.
     * @param parent The ROS coordinate parent frame.
     * @return The ROS TransformStamped.
     */
    private geometry_msgs.TransformStamped toTransform(ai.cellbots.common.Transform l, String frame,
            @SuppressWarnings("SameParameterValue") String parent) {
        TransformStamped sf = getNode().getTopicMessageFactory().newFromType(TransformStamped._TYPE);
        org.ros.rosjava_geometry.Transform tf = new org.ros.rosjava_geometry.Transform(
                new Vector3(l.getPosition(0), l.getPosition(1), l.getPosition(2)),
                new Quaternion(l.getRotation(0), l.getRotation(1),
                        l.getRotation(2), l.getRotation(3)));

        sf.getHeader().setFrameId(parent);
        sf.getHeader().setStamp(Time.fromMillis((long) (l.getTimestamp() * 1000.0)));
        sf.setTransform(tf.toTransformMessage(sf.getTransform()));

        sf.setChildFrameId(frame);
        return sf;
    }

    /**
     * Publish the device and base poses.
     *
     * @param device The device transform in the map frame.
     * @param base The base transform in the map frame.
     */
    @SuppressWarnings("WeakerAccess")
    public void publishRobotPose(Transform device, Transform base) {
        if (mTfPublisher == null || getNode() == null || (device == null && base == null)) {
            return;
        }
        if (mTfPublisher.getNumberOfSubscribers() > 0) {
            TFMessage mm = mTfPublisher.newMessage();
            LinkedList<TransformStamped> tf = new LinkedList<>();
            if (device != null) {
                tf.add(toTransform(device, "/device", "/map"));
            }
            if (base != null) {
                tf.add(toTransform(base, "/base_link", "/map"));
            }
            mm.setTransforms(tf);
            mTfPublisher.publish(mm);
        }
    }

    /**
     * Publish the device and base poses.
     *
     * @param depthLocation The device depth location in the map frame.
     */
    @SuppressWarnings("WeakerAccess")
    public void publishPointCloudPose(Transform depthLocation) {
        if (mTfPublisher == null || getNode() == null || depthLocation == null) {
            return;
        }
        if (mTfPublisher.getNumberOfSubscribers() > 0) {
            TFMessage mm = mTfPublisher.newMessage();
            LinkedList<TransformStamped> tf = new LinkedList<>();
            tf.add(toTransform(depthLocation, "/camera_depth", "/map"));
            mm.setTransforms(tf);
            mTfPublisher.publish(mm);
        }
    }

    /**
     * Publish the POI poses.
     *
     * @param transforms The locations.
     * @param names      The names.
     */
    @SuppressWarnings("WeakerAccess")
    public synchronized void publishPointOfInterestPoses(List<Transform> transforms, List<String> names) {
        if (transforms == null || names == null) {
            return;
        }
        if (mStaticTfPublisher == null || getNode() == null) {
            mInitialListTransforms = new ArrayList<>(transforms);
            mInitialListNames = new ArrayList<>(names);
            return;
        }
        TFMessage mm = mStaticTfPublisher.newMessage();
        LinkedList<TransformStamped> tf = new LinkedList<>();
        for (int i = 0; i < transforms.size() && i < names.size(); i++) {
            tf.add(toTransform(transforms.get(i), "/pois/" + names.get(i), "/map"));
        }
        mm.setTransforms(tf);
        mStaticTfPublisher.publish(mm);
    }
}
