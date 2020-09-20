package ai.cellbots.robot.vision;

import android.content.Context;
import android.util.Log;

import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;

import java.util.ArrayList;

import ai.cellbots.common.EventProcessor;
import ai.cellbots.common.Polygon;
import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.Transform;
import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.costmap.GeometryCostMap;
import ai.cellbots.robot.ros.ROSNodeManager;

/**
 * Computer vision system that sits in robot system. This system receives many sensor data including
 * camera, depth, pose, IMU, and so on. Processes the sensor data relevantly and primarily represents
 * its output as ComputerVisionCostMap.
 */
// TODO(playerfour) write tests starting from point cloud and camera position to geometry costmap fusing
public class VisionSystemManager implements ThreadedShutdown {
    private static final String TAG = VisionSystemManager.class.getSimpleName();
    private final Context mParent;
    private final ROSNodeManager mROSNodeManager;
    private final EventProcessor mVisionEventProcessor;
    private final GeometryCostMap mCostMap;

    private boolean mNeedUpdate = true;
    private DepthImage mNextDepthImage;
    private CameraImage mNextColorImage;
    private double[] mNextWorldToCameraPose;

    // The time interval after which the detected geometries are expired (in ms).
    private static final long EXPIRATION_INTERVAL = 5000;

    /**
     * Creates the computer vision system manager.
     *
     * @param parent         The parent context.
     * @param resolution     The resolution of the CostMap, being the width of a square in meters.
     * @param robotRadius    The physical radius of the robot (in meters).
     * @param rosNodeManager The ros node manager.
     */
    public VisionSystemManager(Context parent, double resolution, double robotRadius, ROSNodeManager rosNodeManager) {
        Log.i(TAG, "Creating vision system manager");
        mParent = parent;
        mROSNodeManager = rosNodeManager;
        mCostMap = new GeometryCostMap(CostMap.Source.COMPUTER_VISION, resolution, robotRadius);
        mVisionEventProcessor = new EventProcessor(TAG, new EventProcessor.Processor() {
            @Override
            public boolean update() {
                CameraImage localColor;
                DepthImage localDepth;
                double[] localWorldToCameraPose;
                synchronized (VisionSystemManager.this) {
                    localColor = mNextColorImage;
                    localDepth = mNextDepthImage;
                    localWorldToCameraPose = mNextWorldToCameraPose;
                }
                if (localColor != null && localDepth != null) {
                    processVisionUpdate(localColor, localDepth, localWorldToCameraPose);
                }
                synchronized (VisionSystemManager.this) {
                    mNeedUpdate = true;
                }
                return true;
            }

            @Override
            public void shutdown() {
                // Do relevant job before final shutdown.
            }
        });
    }

    /**
     * Starts the shutdown of VisionSystemManager.
     */
    @Override
    public final void shutdown() {
        Log.i(TAG, "Shutting down");
        mVisionEventProcessor.shutdown();
    }

    /**
     * Waits for VisionSystemManager to shutdown.
     */
    @Override
    public final void waitShutdown() {
        Log.i(TAG, "Waiting for shutting down");
        mVisionEventProcessor.waitShutdown();
    }

    /**
     * Called when a new depth image that is synchronized with a color image is produced.
     *
     * @param colorImage The color image.
     * @param depthImage The depth image.
     * @param worldToCameraPose The pose from world origin (adf) to current frame
     *                          represented as [tx, ty, tz, qx, qy, qz, qw]
     */
    public synchronized void onColorDepth(CameraImage colorImage, DepthImage depthImage, double[] worldToCameraPose) {
        if (!mNeedUpdate) {
            return;
        }
        mNextColorImage = new CameraImage(colorImage);
        mNextDepthImage = new DepthImage(depthImage);
        mNextWorldToCameraPose = new double[worldToCameraPose.length];
        System.arraycopy(worldToCameraPose, 0, mNextWorldToCameraPose, 0, worldToCameraPose.length);
        mNeedUpdate = false;
        mVisionEventProcessor.onEvent();
    }

    /**
     * Processes an image.
     *
     * @param colorImage The color image.
     * @param depthImage The depth image.
     * @param worldToCameraPose The pose from world origin (adf) to current frame
     *                          represented as [tx, ty, tz, qx, qy, qz, qw]
     */
    private void processVisionUpdate(CameraImage colorImage, DepthImage depthImage, double[] worldToCameraPose) {
        int[] depthImageSize = {depthImage.getWidth(), depthImage.getHeight()};
        int[] colorImageSize = {colorImage.getWidth(), colorImage.getHeight()};
        double[] depthImageIntrinsics = {depthImage.getCameraInfo().getFx(), depthImage.getCameraInfo().getFy(),
                depthImage.getCameraInfo().getCx(), depthImage.getCameraInfo().getCy()};
        double[] colorImageIntrinsics = {colorImage.getCameraInfo().getFx(), colorImage.getCameraInfo().getFy(),
                colorImage.getCameraInfo().getCx(), colorImage.getCameraInfo().getCy()};
        int colorToDepthRatio = colorImageSize[0] / depthImageSize[0];

        // Make sure that color and depth images are scaled properly
        if (colorImageSize[1] / depthImageSize[1] != colorToDepthRatio) {
            Log.e(TAG, "Depth image is not scaled equally in width and height compared to color image");
            return;
        }
        if ((int) (colorImageIntrinsics[0] / depthImageIntrinsics[0]) != colorToDepthRatio ||
                (int) (colorImageIntrinsics[1] / depthImageIntrinsics[1]) != colorToDepthRatio ||
                (int) (colorImageIntrinsics[2] / depthImageIntrinsics[2]) != colorToDepthRatio ||
                (int) (colorImageIntrinsics[3] / depthImageIntrinsics[3]) != colorToDepthRatio) {
            Log.e(TAG, "Depth image intrinsics are not scaled equally compared to the color image intrinsics");
            return;
        }

        // Send the color and depth images to the floor object detection function
        float[] boundingRects;
        boundingRects = FloorObjectDetectorJNINative.processDepthAndColorImages(colorImage.getTimestamp(), depthImage.getFloats(),
                colorImage.getBytes(), depthImageSize, colorImageSize, depthImageIntrinsics, colorImageIntrinsics);
        // TODO timestamps should be consistent (double or long) across the code
        long timeStamp = (long) colorImage.getTimestamp()*1000;
        // Setting the expiration interval of geometries to 5000 ms (5 seconds).
        long expireTime = timeStamp + EXPIRATION_INTERVAL;
        // TODO replace this with CostMap.OBSTACLE_COST
        byte obstacleCost = 127;
        if (boundingRects == null) {
            Log.d(TAG, "Bounding rectangles array is null");
        } else if (boundingRects[0] == 0) {
            Log.d(TAG, "No bounding rectangle found");
            mCostMap.clear(timeStamp);
        } else {
            Log.d(TAG, "Total number of bounding rectangles found: " + boundingRects[0]);
            ArrayList<GeometryCostMap.Geometry> boundingGeometries =
                    new ArrayList<>((int) boundingRects[0]);
            ArrayList<Polygon> polygons =
                    new ArrayList<>((int) boundingRects[0]);
            // Vector3, Quaternion and Matrix4 are Rajawali classes
            Vector3 translation = new Vector3(worldToCameraPose[0],
                    worldToCameraPose[1], worldToCameraPose[2]);
            Quaternion rotation = new Quaternion(worldToCameraPose[6], worldToCameraPose[3],
                    worldToCameraPose[4], worldToCameraPose[5]);
            // Quaternions in Rajawali are not the same as in Tango, left-hand rotation is needed to match Tango
            rotation.conjugate();
            Matrix4 poseMatrix = new Matrix4();
            poseMatrix.setAll(translation, new Vector3(1, 1, 1), rotation);
            for (int i = 0; i < boundingRects[0]; i++) {
                float X_1 = boundingRects[i * 6 + 1];
                float Y_1 = boundingRects[i * 6 + 2];
                float Z_1 = boundingRects[i * 6 + 3];
                float X_2 = boundingRects[i * 6 + 4];
                float Y_2 = boundingRects[i * 6 + 5];
                float Z_2 = boundingRects[i * 6 + 6];
                Vector3 corner_1 = new Vector3(X_1, Y_1, Z_1);
                Vector3 corner_2 = new Vector3(X_2, Y_2, Z_2);
                Vector3 corner_1_transformed = corner_1.multiply(poseMatrix.getDoubleValues());
                Vector3 corner_2_transformed = corner_2.multiply(poseMatrix.getDoubleValues());
                Polygon currentPolygon = new Polygon(new Transform[]{
                        new Transform(corner_1_transformed.x, corner_1_transformed.y, corner_1_transformed.z, 0),
                        new Transform(corner_2_transformed.x, corner_2_transformed.y, corner_2_transformed.z, 0)});
                polygons.add(currentPolygon);
                boundingGeometries.add(new GeometryCostMap.Geometry(expireTime, obstacleCost, currentPolygon));
            }
            mROSNodeManager.sendComputerVisionMarkers(timeStamp, polygons);
            mCostMap.update(timeStamp, boundingGeometries);
        }
        return;
    }

    /**
     * Returns the cost map managed by vision system.
     *
     * @return The CostMap
     */
    public final CostMap getCostMap() {
        return mCostMap;
    }
}
