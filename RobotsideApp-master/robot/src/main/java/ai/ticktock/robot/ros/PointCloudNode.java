package ai.cellbots.robot.ros;

import org.jboss.netty.buffer.ChannelBuffers;
import org.ros.message.Time;
import org.ros.namespace.GraphName;
import org.ros.node.NodeConfiguration;
import org.ros.node.topic.Publisher;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;

import ai.cellbots.robot.vision.PointCloud;
import sensor_msgs.PointCloud2;
import sensor_msgs.PointField;

/**
 * ROS Node for point clouds.
 */
class PointCloudNode extends ROSNode {
    private Publisher<PointCloud2> mPointCloudPublisher = null;

    private static final String[] POINT_CLOUD_FIELD_NAMES = {"x", "y", "z", "i", "rgb"};
    private static final int FLOAT_SIZE = 4;
    private static final float PURPLE_COLOR = Float.intBitsToFloat(0xFF00FF);


    /**
     * Constructor
     *
     * @param nodeConfiguration The configuration for this node.
     */
    PointCloudNode(NodeConfiguration nodeConfiguration) {
        super(nodeConfiguration, "cellbots/point_cloud");
        start();
    }

    /**
     * Called when the system is started.
     */
    @Override
    protected void onStart() {
        mPointCloudPublisher = getNode().newPublisher(
                GraphName.of("/point_cloud"), PointCloud2._TYPE);
    }

    /**
     * Publish a point cloud.
     *
     * @param pointCloud  The PointCloud containing the image data.
     * @param colorsValid True if the point cloud colors are valid.
     * @param pointColors The coloration of the point cloud by the system.
     */
    @SuppressWarnings("WeakerAccess")
    public void publishPointCloud(PointCloud pointCloud, boolean colorsValid, float[] pointColors) {
        if (mPointCloudPublisher.getNumberOfSubscribers() > 0
                && pointCloud != null && pointCloud.getPointCount() > 0) {
            if (pointCloud.getFormat() != PointCloud.Format.X_Y_Z_I) {
                throw new Error("Unsupported point cloud format: " + pointCloud.getFormat());
            }

            sensor_msgs.PointCloud2 pt = mPointCloudPublisher.newMessage();
            LinkedList<PointField> fields = new LinkedList<>();
            int offset = 0;

            for (String field : POINT_CLOUD_FIELD_NAMES) {
                PointField ptf = getNode().getTopicMessageFactory().newFromType(PointField._TYPE);
                ptf.setCount(1);
                ptf.setDatatype(PointField.FLOAT32);
                ptf.setName(field);
                ptf.setOffset(offset);
                offset += FLOAT_SIZE;

                fields.add(ptf);
            }

            pt.setFields(fields);

            pt.setWidth(pointCloud.getPointCount());
            pt.setHeight(1);
            pt.setPointStep(FLOAT_SIZE * fields.size());
            pt.setIsDense(true);
            pt.setRowStep(pt.getWidth());
            pt.setIsBigendian(false);

            byte[] content = new byte[pointCloud.getPointCount() * FLOAT_SIZE * fields.size()];
            ByteBuffer writer = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
            float[] points = pointCloud.getPoints();

            if (pointColors != null && colorsValid) {
                for (int i = 0; i < pointCloud.getPointCount() && i < pointColors.length; i++) {
                    for (int k = 0; k < 4; k++) {
                        writer.putFloat(points[(i * 4) + k]);
                    }
                    writer.putFloat(pointColors[i]);
                }
                for (int i = pointColors.length; i < pointCloud.getPointCount(); i++) {
                    for (int k = 0; k < 4; k++) {
                        writer.putFloat(points[(i * 4) + k]);
                    }
                    writer.putFloat(PURPLE_COLOR);
                }
            } else {
                for (int i = 0; i < pointCloud.getPointCount(); i++) {
                    for (int k = 0; k < 4; k++) {
                        writer.putFloat(points[(i * 4) + k]);
                    }
                    writer.putFloat(PURPLE_COLOR);
                }
            }

            pt.setData(ChannelBuffers.copiedBuffer(ByteOrder.LITTLE_ENDIAN, content));
            pt.getHeader().setStamp(Time.fromMillis((long) (pointCloud.getTimestamp() * 1000.0)));
            pt.getHeader().setFrameId("/camera_depth");
            mPointCloudPublisher.publish(pt);
        }
    }
}
