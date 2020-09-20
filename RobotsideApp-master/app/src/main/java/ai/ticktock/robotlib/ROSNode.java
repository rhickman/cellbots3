package ai.cellbots.robotlib;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;

import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.experimental.TangoImageBuffer;

import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.ros.internal.message.MessageBuffers;
import org.ros.message.MessageListener;
import org.ros.message.Time;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.NodeMain;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;
import org.ros.rosjava_geometry.Quaternion;
import org.ros.rosjava_geometry.Transform;
import org.ros.rosjava_geometry.Vector3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import ai.cellbots.common.Polygon;
import geometry_msgs.Point;
import geometry_msgs.Pose;
import geometry_msgs.PoseStamped;
import geometry_msgs.TransformStamped;
import geometry_msgs.Twist;
import nav_msgs.OccupancyGrid;
import nav_msgs.Path;
import sensor_msgs.CompressedImage;
import sensor_msgs.Image;
import sensor_msgs.PointCloud2;
import sensor_msgs.PointField;
import tf2_msgs.TFMessage;
import visualization_msgs.Marker;

/**
 * ROS node for cellbots robots. The node actually does very little, and is only used for debugging
 * purposes between the robot and a laptop or android phone.
 */
public class ROSNode extends AbstractNodeMain implements NodeMain {
    @SuppressWarnings("unused")
    private static final String TAG = ROSNode.class.getSimpleName();
    private double mRobotSpeed = 0;
    private double mRobotAng = 0;
    private boolean mHaveRobotUpdate = false;
    private boolean mHaveStarted = false;
    private final boolean mHaveFisheye;

    private RobotDriver.RobotModel mModel = null;

    private Publisher<tf2_msgs.TFMessage> mTfPublisher = null;
    private Publisher<nav_msgs.Path> mPathPublisher = null;
    private Publisher<sensor_msgs.PointCloud2> mPointCloudPublisher = null;

    private Publisher<sensor_msgs.Image> mColorImagePublisher = null;
    private Publisher<sensor_msgs.Image> mFisheyeImagePublisher = null;
    private Publisher<sensor_msgs.CompressedImage> mColorCompressedImagePublisher = null;
    private Publisher<sensor_msgs.CompressedImage> mFisheyeCompressedImagePublisher = null;
    private Publisher<nav_msgs.OccupancyGrid> mOccupancyGridPublisher = null;
    private Publisher<geometry_msgs.Twist> mTwistVelocityPublisher = null;

    private Publisher<sensor_msgs.Image> mDepthImagePublisher = null;
    private Publisher<visualization_msgs.Marker> mMarkerPublisher = null;

    private TwistVelocityGenerator mTwistVelocityGenerator;

    private ConnectedNode mNode = null;
    private boolean mShutdown = false;

    private Thread mCameraColorPublisherThread = null;
    private final AtomicReference<sensor_msgs.Image> mNextCameraColor = new AtomicReference<>();
    private Thread mCameraFisheyePublisherThread = null;
    private final AtomicReference<sensor_msgs.Image> mNextCameraFisheye = new AtomicReference<>();
    private Thread mCameraCompressedColorPublisherThread = null;
    private final AtomicReference<sensor_msgs.CompressedImage> mNextCameraCompressedColor =
            new AtomicReference<>();
    private Thread mCameraCompressedFisheyePublisherThread = null;
    private final AtomicReference<sensor_msgs.CompressedImage> mNextCameraCompressedFisheye =
            new AtomicReference<>();

    private static final String[] POINT_CLOUD_FIELD_NAMES = {"x", "y", "z", "i", "rgb"};
    private static final int FLOAT_SIZE = 4;

    private static final double MARKER_WIDTH = 0.05;
    private static final int MARKER_ID_CV = 0;

    /**
     * Constructor
     */
    ROSNode(TwistVelocityGenerator twistGenerator, boolean fisheye) {
        mTwistVelocityGenerator = twistGenerator;
        mHaveFisheye = fisheye;
    }

    /**
     * Shutdown the node.
     */
    public void shutdown() {
        mShutdown = true;
        synchronized (mNextCameraColor) {
            mNextCameraColor.notifyAll();
        }
        if (mHaveFisheye) {
            synchronized (mNextCameraFisheye) {
                mNextCameraFisheye.notifyAll();
            }
        }
        synchronized (mNextCameraCompressedColor) {
            mNextCameraCompressedColor.notifyAll();
        }
        if (mHaveFisheye) {
            synchronized (mNextCameraCompressedFisheye) {
                mNextCameraCompressedFisheye.notifyAll();
            }
        }
    }

    /**
     * Wait for a thread to shutdown.
     *
     * @param thread The thread.
     */
    private void waitThreadShutdown(Thread thread) {
        if (thread != null) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted waited for mCameraPublisher to stop", e);
            }
        }
    }

    /**
     * Wait for the node to shutdown
     */
    public void waitShutdown() {
        shutdown();
        waitThreadShutdown(mCameraColorPublisherThread);
        if (mHaveFisheye) {
            waitThreadShutdown(mCameraFisheyePublisherThread);
        }
        waitThreadShutdown(mCameraCompressedColorPublisherThread);
        if (mHaveFisheye) {
            waitThreadShutdown(mCameraCompressedFisheyePublisherThread);
        }
    }

    /**
     * Camera types
     */
    public enum Camera {
        COLOR, FISHEYE
    }

    /**
     * Set the model of the robot.
     *
     * @param model the new robot model.
     */
    synchronized void setRobotModel(RobotDriver.RobotModel model) {
        mModel = model;
    }

    /**
     * Function for ROS to get the default name of the node.
     *
     * @return The ROS GraphName of the node.
     */
    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("cellbots_robot");
    }

    /**
     * If true, we have a command to send to the robot. Clears command flag.
     *
     * @return True if we have a command.
     */
    synchronized boolean getRobotUpdate() {
        boolean haveUpdate = mHaveRobotUpdate;
        mHaveRobotUpdate = false;
        return haveUpdate;
    }

    /**
     * Get the commanded speed of the robot.
     *
     * @return The commanded speed of the robot in meters/second.
     */
    synchronized double getRobotSpeed() {
        return mRobotSpeed;
    }

    /**
     * Get the commanded angular speed of the robot.
     *
     * @return The commanded angular speed of the robot in radians/second.
     */
    synchronized double getRobotAngular() {
        return mRobotAng;
    }

    /**
     * Function called by ROS to start the system up.
     *
     * @param connectedNode The node from ROS.
     */
    @Override
    public void onStart(ConnectedNode connectedNode) {
        Subscriber<geometry_msgs.Twist> cmdVelListener
                = connectedNode.newSubscriber(GraphName.of("cmd_vel"), Twist._TYPE);
        cmdVelListener.addMessageListener(new MessageListener<Twist>() {
            @Override
            public void onNewMessage(Twist twist) {
                synchronized (ROSNode.this) {
                    mRobotSpeed = twist.getLinear().getX();
                    mRobotAng = twist.getAngular().getZ();
                    mHaveRobotUpdate = true;
                }
            }
        });

        Subscriber<geometry_msgs.Twist> joystickCmdVelListener
                = connectedNode.newSubscriber(GraphName.of("virtual_joystick/cmd_vel"),
                Twist._TYPE);
        joystickCmdVelListener.addMessageListener(new MessageListener<Twist>() {
            @Override
            public void onNewMessage(Twist twist) {
                synchronized (ROSNode.this) {
                    RobotDriver.RobotModel model = mModel;
                    if (model == null) {
                        return;
                    }
                    mRobotSpeed = twist.getLinear().getX() * model.getMaxSpeed();
                    mRobotAng = twist.getAngular().getZ() * model.getMaxAngular();
                    mHaveRobotUpdate = true;
                }
            }
        });

        mTfPublisher = connectedNode.newPublisher(
                GraphName.of("/tf"), TFMessage._TYPE);
        mPathPublisher = connectedNode.newPublisher(
                GraphName.of("/local_plan"), Path._TYPE);
        mPointCloudPublisher = connectedNode.newPublisher(
                GraphName.of("/point_cloud"), PointCloud2._TYPE);
        mColorImagePublisher = connectedNode.newPublisher(
                GraphName.of("/color/image_raw"), Image._TYPE);
        if (mHaveFisheye) {
            mFisheyeImagePublisher = connectedNode.newPublisher(
                    GraphName.of("/fisheye/image_raw"), Image._TYPE);
        }
        mColorCompressedImagePublisher = connectedNode.newPublisher(
                GraphName.of("/color/image_raw/compressed"), CompressedImage._TYPE);
        if (mHaveFisheye) {
            mFisheyeCompressedImagePublisher = connectedNode.newPublisher(
                    GraphName.of("/fisheye/image_raw/compressed"), CompressedImage._TYPE);
        }
        mDepthImagePublisher = connectedNode.newPublisher(GraphName.of("/depth/image"),
                Image._TYPE);
        mTwistVelocityPublisher = connectedNode.newPublisher(GraphName.of("/debug/cmd_vel"),
                Twist._TYPE);

        mCameraColorPublisherThread = publishQueueThread(
                mColorImagePublisher, mNextCameraColor);
        if (mHaveFisheye) {
            mCameraFisheyePublisherThread = publishQueueThread(
                    mFisheyeImagePublisher, mNextCameraFisheye);
        }
        mCameraCompressedColorPublisherThread = publishQueueThread(
                mColorCompressedImagePublisher, mNextCameraCompressedColor);
        if (mHaveFisheye) {
            mCameraCompressedFisheyePublisherThread = publishQueueThread(
                    mFisheyeCompressedImagePublisher, mNextCameraCompressedFisheye);
        }
        mPointCloudPublisher = connectedNode.newPublisher(
                GraphName.of("/point_cloud"), PointCloud2._TYPE);
        mOccupancyGridPublisher = connectedNode.newPublisher(
                GraphName.of("/occupancy_grid"), OccupancyGrid._TYPE);
        mOccupancyGridPublisher.setLatchMode(true);

        mMarkerPublisher = connectedNode.newPublisher(
                GraphName.of("/visualization_marker"), Marker._TYPE);

        mNode = connectedNode;
        mHaveStarted = true;
    }

    /**
     * Creates a thread that monitors an atomic reference and publishes any element in it.
     *
     * @param pub The publisher.
     * @param ref The reference.
     * @param <T> The type of variable.
     * @return The result to return.
     */
    private <T> Thread publishQueueThread(final Publisher<T> pub, final AtomicReference<T> ref) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                publishQueueLoop(pub, ref);
            }
        });
        t.start();
        return t;
    }

    /**
     * Implementation of a thread that publishes any element in an atomic reference.
     *
     * @param pub The publisher.
     * @param ref The reference.
     * @param <T> The type of variable.
     */
    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private <T> void publishQueueLoop(final Publisher<T> pub, final AtomicReference<T> ref) {
        while (!mShutdown) {
            T next = ref.getAndSet(null);
            if (next != null) {
                pub.publish(next);
            } else {
                synchronized (ref) {
                    try {
                        ref.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Convert a Transform to a ROS TransformStamped, defaulting parent to "/map".
     *
     * @param l     The Transform.
     * @param frame The ROS coordinate child frame.
     * @return The ROS TransformStamped.
     */
    private geometry_msgs.TransformStamped toTransform(ai.cellbots.common.Transform l,
            String frame) {
        return toTransform(l, frame, "/map");
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
        TransformStamped sf = mNode.getTopicMessageFactory().newFromType(TransformStamped._TYPE);
        Transform tf = new Transform(new Vector3(l.getPosition(0), l.getPosition(1),
                l.getPosition(2)), new Quaternion(l.getRotation(0), l.getRotation(1),
                l.getRotation(2), l.getRotation(3)));

        sf.getHeader().setFrameId(parent);
        sf.getHeader().setStamp(Time.fromMillis((long) (l.getTimestamp() * 1000.0)));
        sf.setTransform(tf.toTransformMessage(sf.getTransform()));

        sf.setChildFrameId(frame);
        return sf;
    }

    /**
     * Convert a Transform to a ROS PoseStamped in "/map".
     *
     * @param l The Transform.
     * @return The PoseStamped.
     */
    private geometry_msgs.PoseStamped toPose(ai.cellbots.common.Transform l) {
        PoseStamped sf = mNode.getTopicMessageFactory().newFromType(PoseStamped._TYPE);
        Pose tf = mNode.getTopicMessageFactory().newFromType(Pose._TYPE);
        geometry_msgs.Point v = mNode.getTopicMessageFactory().newFromType(
                geometry_msgs.Point._TYPE);
        geometry_msgs.Quaternion q = mNode.getTopicMessageFactory().newFromType(
                geometry_msgs.Quaternion._TYPE);

        v.setX(l.getPosition(0));
        v.setY(l.getPosition(1));
        v.setZ(l.getPosition(2));
        tf.setPosition(v);

        q.setX(l.getRotation(0));
        q.setY(l.getRotation(1));
        q.setZ(l.getRotation(2));
        q.setW(l.getRotation(3));
        tf.setOrientation(q);

        sf.getHeader().setFrameId("/map");
        sf.getHeader().setStamp(Time.fromMillis((long) (l.getTimestamp() * 1000.0)));
        sf.setPose(tf);
        return sf;
    }

    /**
     * Called when we have a robot update to send out.
     *
     * @param l                  The world Transform of the Tango device.
     * @param lb                 The world Transform of the base.
     * @param goals              The list of goal world Transforms.
     * @param ld                 The world Transform of the depth camera, could be null.
     * @param pointCloud         The cloud to send out.
     * @param pointColors        The colors of the point cloud.
     * @param colorsValid        True if the point cloud colors are valid for the current point
     *                           cloud.
     */
    void sendRobotUpdate(ai.cellbots.common.Transform l, ai.cellbots.common.Transform lb,
            List<ai.cellbots.common.Transform> goals, ai.cellbots.common.Transform ld,
            TangoPointCloudData pointCloud, float[] pointColors, boolean colorsValid) {
        if (!mHaveStarted) {
            Log.i(TAG, "Skipping sending out info for ROS, have not started yet");
            return;
        }

        if (mTfPublisher.getNumberOfSubscribers() > 0) {
            TFMessage mm = mTfPublisher.newMessage();
            LinkedList<TransformStamped> tf = new LinkedList<>();
            if (l != null) {
                tf.add(toTransform(l, "/device"));
            }
            if (lb != null) {
                tf.add(toTransform(lb, "/base_link"));
            }
            if (ld != null) {
                tf.add(toTransform(ld, "/camera_depth"));
            }

            if ((pointCloud != null) && (pointCloud.numPoints > 0) && (ld != null)) {
                tf.add(toTransform(new ai.cellbots.common.Transform(ld,
                        new ai.cellbots.common.Transform(
                                new double[]{pointCloud.points.get(0),
                                        pointCloud.points.get(1),
                                        pointCloud.points.get(2)},
                                new double[]{1, 0, 0, 0}, 0)), "/test_point"));
            }

            mm.setTransforms(tf);
            mTfPublisher.publish(mm);
        }

        if (mTwistVelocityPublisher.getNumberOfSubscribers() > 0) {
            // Publish Twist message
            Twist twistMessage = mTwistVelocityPublisher.newMessage();
            mTwistVelocityGenerator.fillAngular(twistMessage.getAngular());
            mTwistVelocityGenerator.fillLinear(twistMessage.getLinear());
            mTwistVelocityPublisher.publish(twistMessage);
        }

        if (goals != null && mPathPublisher.getNumberOfSubscribers() > 0) {
            Path path = mPathPublisher.newMessage();
            path.getHeader().setFrameId("/map");
            LinkedList<PoseStamped> ps = new LinkedList<>();
            for (ai.cellbots.common.Transform t : goals) {
                ps.add(toPose(t));
            }
            path.setPoses(ps);
            mPathPublisher.publish(path);
        }

        if (mPointCloudPublisher.getNumberOfSubscribers() > 0
                && pointCloud != null && pointCloud.numPoints > 0) {

            sensor_msgs.PointCloud2 pt = mPointCloudPublisher.newMessage();

            LinkedList<PointField> fields = new LinkedList<>();

            int offset = 0;
            for (String field : POINT_CLOUD_FIELD_NAMES) {
                PointField ptf = mNode.getTopicMessageFactory().newFromType(PointField._TYPE);
                ptf.setCount(1);
                ptf.setDatatype(PointField.FLOAT32);
                ptf.setName(field);
                ptf.setOffset(offset);
                offset += FLOAT_SIZE;

                fields.add(ptf);
            }

            pt.setFields(fields);

            pt.setWidth(pointCloud.numPoints);
            pt.setHeight(1);
            pt.setPointStep(FLOAT_SIZE * fields.size());
            pt.setIsDense(true);
            pt.setRowStep(pt.getWidth());
            pt.setIsBigendian(false);


            byte[] content = new byte[pointCloud.numPoints * FLOAT_SIZE * fields.size()];

            ByteBuffer writer = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);

            final float purple = Float.intBitsToFloat(0xFF00FF);

            for (int i = 0; i < pointCloud.numPoints; i++) {
                for (int k = 0; k < 4; k++) {
                    writer.putFloat(pointCloud.points.get((i * 4) + k));
                }
                if (colorsValid && pointColors != null && i < pointColors.length) {
                    writer.putFloat(pointColors[i]);
                } else {
                    writer.putFloat(purple);
                }
            }

            pt.setData(ChannelBuffers.copiedBuffer(ByteOrder.LITTLE_ENDIAN, content));

            pt.getHeader().setStamp(Time.fromMillis((long) (pointCloud.timestamp * 1000.0)));
            pt.getHeader().setFrameId("/camera_depth");

            mPointCloudPublisher.publish(pt);
        }
    }

    /**
     * Send the image.
     *
     * @param camera      The camera for the image.
     * @param transform   The transform for the image.
     * @param imageBuffer The image itself.
     */
    public void sendImage(Camera camera, ai.cellbots.common.Transform transform,
            TangoImageBuffer imageBuffer) {
        if (camera == Camera.FISHEYE && !mHaveFisheye) {
            return;
        }
        TFMessage mm = mTfPublisher.newMessage();
        LinkedList<TransformStamped> tf = new LinkedList<>();
        String frameId;
        if (camera == Camera.COLOR) {
            frameId = "/camera_color";
        } else if (camera == Camera.FISHEYE) {
            frameId = "/camera_fisheye";
        } else {
            Log.e(TAG, "Invalid camera: " + camera);
            return;
        }
        tf.add(toTransform(transform, frameId));
        mm.setTransforms(tf);
        mTfPublisher.publish(mm);

        sendImageUncompressed(camera, frameId, imageBuffer);
        sendImageCompressed(camera, frameId, imageBuffer);
    }

    private final ChannelBufferOutputStream mCameraCompressedColorOutputStream
            = new ChannelBufferOutputStream(MessageBuffers.dynamicBuffer());
    private final ChannelBufferOutputStream mCameraCompressedFisheyeOutputStream
            = new ChannelBufferOutputStream(MessageBuffers.dynamicBuffer());

    /**
     * Send the image, uncompressed.
     *
     * @param camera      The camera for the image.
     * @param frameId     The frame to publish.
     * @param imageBuffer The image itself.
     */
    private void sendImageCompressed(Camera camera, String frameId, TangoImageBuffer imageBuffer) {
        if (camera == Camera.FISHEYE && !mHaveFisheye) {
            return;
        }
        Publisher<sensor_msgs.CompressedImage> pub = camera == Camera.COLOR
                ? mColorCompressedImagePublisher : mFisheyeCompressedImagePublisher;
        final AtomicReference<sensor_msgs.CompressedImage> ref = camera == Camera.COLOR
                ? mNextCameraCompressedColor : mNextCameraCompressedFisheye;
        ChannelBufferOutputStream stream = camera == Camera.COLOR
                ? mCameraCompressedColorOutputStream : mCameraCompressedFisheyeOutputStream;

        if (pub.getNumberOfSubscribers() == 0) {
            return;
        }
        sensor_msgs.CompressedImage message = pub.newMessage();

        message.getHeader().setStamp(Time.fromMillis((long) imageBuffer.timestamp));
        message.getHeader().setFrameId(frameId);
        message.setFormat("jpeg");

        // TangoImageBuffer.YCRCB_480_888 == 35
        if (imageBuffer.format == TangoImageBuffer.YCRCB_420_SP
                || imageBuffer.format == 35) {
            byte[] copy = new byte[imageBuffer.data.limit()];
            imageBuffer.data.get(copy);
            YuvImage image = new YuvImage(copy, ImageFormat.NV21,
                    imageBuffer.width, imageBuffer.height, null);
            Rect rect = new Rect(0, 0, imageBuffer.width, imageBuffer.height);

            boolean r = image.compressToJpeg(rect, 20, stream);
            if (r) {
                message.setData(stream.buffer().copy());
            }
            stream.buffer().clear();

            if (r) {
                ref.set(message);
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (ref) {
                    ref.notifyAll();
                }
            } else {
                Log.w(TAG, "Failed to save image to JPEG");
            }
        } else {
            Log.e(TAG, "Invalid format: " + imageBuffer.format);
        }
    }

    /**
     * Send the image, uncompressed.
     *
     * @param camera      The camera for the image.
     * @param frameId     The frame to publish.
     * @param imageBuffer The image itself.
     */
    private void sendImageUncompressed(Camera camera, String frameId,
            TangoImageBuffer imageBuffer) {
        if (camera == Camera.FISHEYE && !mHaveFisheye) {
            return;
        }
        Publisher<sensor_msgs.Image> pub = camera == Camera.COLOR
                ? mColorImagePublisher : mFisheyeImagePublisher;
        final AtomicReference<sensor_msgs.Image> ref = camera == Camera.COLOR
                ? mNextCameraColor : mNextCameraFisheye;

        if (pub.getNumberOfSubscribers() == 0) {
            return;
        }
        sensor_msgs.Image message = pub.newMessage();

        message.getHeader().setStamp(Time.fromMillis((long) imageBuffer.timestamp));
        message.getHeader().setFrameId(frameId);
        message.setHeight(imageBuffer.height);
        message.setWidth(imageBuffer.width);
        message.setIsBigendian((byte) 0);

        // TangoImageBuffer.YCRCB_480_888 == 35
        if (imageBuffer.format == TangoImageBuffer.YCRCB_420_SP
                || imageBuffer.format == 35) {

            int uv_buffer_offset = imageBuffer.width * imageBuffer.height;

            byte[] image_out = new byte[imageBuffer.height * (imageBuffer.width / 2) * 4];

            // Expands the pixel data
            for (int i = 0; i < imageBuffer.height; i++) {
                //imageBuffer.data.get(uvInit, uv_buffer_offset + ((i / 2) * imageBuffer.width),
                // imageBuffer.width);
                for (int j = 0; j < imageBuffer.width; j += 2) {
                    // Get the YUV color for this pixel
                    byte y1Value = imageBuffer.data.get((i * imageBuffer.width) + j);
                    byte y2Value = imageBuffer.data.get((i * imageBuffer.width) + j + 1);
                    byte uValue = imageBuffer.data.get(
                            uv_buffer_offset + ((i / 2) * imageBuffer.width) + j + 1);
                    byte vValue = imageBuffer.data.get(
                            uv_buffer_offset + ((i / 2) * imageBuffer.width) + j);

                    int pos = (i * imageBuffer.width / 2 * 4) + (j / 2 * 4);

                    image_out[pos] = uValue;
                    image_out[pos + 1] = y1Value;
                    image_out[pos + 2] = vValue;
                    image_out[pos + 3] = y2Value;
                }
            }

            message.setData(ChannelBuffers.copiedBuffer(ByteOrder.LITTLE_ENDIAN, image_out));
            message.setStep(imageBuffer.width / 2 * 4);
            message.setEncoding("yuv422");
            ref.set(message);
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (ref) {
                ref.notifyAll();
            }
        } else {
            Log.e(TAG, "Invalid format: " + imageBuffer.format);
        }
    }

    /**
     * Send out a depth image that is registered with the color frame.
     *
     * @param timestamp  The timestamp.
     * @param depthImage The depth image floats.
     * @param width      The width of the depth image.
     * @param height     The height of the depth image.
     */
    public void sendDepthImage(double timestamp, float[] depthImage, int width, int height) {
        if (timestamp < 0 || depthImage == null || width <= 0 || height <= 0) {
            return;
        }
        if (depthImage.length != width * height) {
            return;
        }
        if (mDepthImagePublisher == null || mDepthImagePublisher.getNumberOfSubscribers() == 0) {
            return;
        }
        sensor_msgs.Image message = mDepthImagePublisher.newMessage();
        message.getHeader().setStamp(Time.fromMillis((long) timestamp));
        message.getHeader().setFrameId("/camera_depth");
        message.setHeight(height);
        message.setWidth(width);
        message.setIsBigendian((byte) 0);

        byte[] imageOut = new byte[width * height * 4];
        ByteBuffer writer = ByteBuffer.wrap(imageOut);
        for (float f : depthImage) {
            writer.putFloat(f);
        }
        message.setData(ChannelBuffers.copiedBuffer(ByteOrder.LITTLE_ENDIAN, imageOut));
        message.setStep(width * 4);
        message.setEncoding("32FC1");
        mDepthImagePublisher.publish(message);
    }

    /**
     * Send the vision markers.
     * @param timestamp The timestamp.
     * @param polygons The list of polygons.
     */
    public void sendVisionMarkers(double timestamp, List<Polygon> polygons) {
        if (timestamp < 0 || polygons == null) {
            return;
        }
        if (mMarkerPublisher == null || mMarkerPublisher.getNumberOfSubscribers() == 0) {
            return;
        }

        visualization_msgs.Marker msg = mMarkerPublisher.newMessage();
        msg.getScale().setX(MARKER_WIDTH);
        msg.getScale().setY(MARKER_WIDTH);
        msg.getScale().setZ(MARKER_WIDTH);
        msg.getColor().setA(1.0f);
        msg.getColor().setR(0.0f);
        msg.getColor().setG(0.0f);
        msg.getColor().setB(1.0f);
        msg.getHeader().setStamp(Time.fromMillis((long) timestamp));
        msg.getHeader().setFrameId("/map");
        msg.setAction(Marker.ADD);
        msg.setId(MARKER_ID_CV);
        msg.setType(Marker.LINE_LIST);

        for (Polygon polygon : polygons) {
            if (polygon == null || polygon.isEmpty()) {
                continue;
            }
            List<ai.cellbots.common.Transform> points = polygon.getPoints();
            for (int point = 0; point < points.size(); point++) {
                Point start = mNode.getTopicMessageFactory().newFromType(Point._TYPE);
                start.setX(points.get(point).getPosition(0));
                start.setY(points.get(point).getPosition(1));
                start.setZ(points.get(point).getPosition(2));

                Point end = mNode.getTopicMessageFactory().newFromType(Point._TYPE);
                end.setX(points.get((point + 1) % points.size()).getPosition(0));
                end.setY(points.get((point + 1) % points.size()).getPosition(1));
                end.setZ(points.get((point + 1) % points.size()).getPosition(2));

                msg.getPoints().add(start);
                msg.getPoints().add(end);
            }
        }
        mMarkerPublisher.publish(msg);
    }
}
