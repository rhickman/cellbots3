package ai.cellbots.robotlib.cv;

import android.util.Log;

import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangosupport.TangoSupport;

import java.util.List;

import ai.cellbots.common.Polygon;
import ai.cellbots.common.Transform;
import ai.cellbots.robotlib.ROSNode;
import ai.cellbots.tangocommon.TangoTransformUtil;

/**
 * Manages computer vision processing.
 */
public class CVManager {
    public static final String TAG = CVManager.class.getSimpleName();

    private static final int DEFAULT_WINDOW_SIZE = 7;
    private static final int DEFAULT_COLOR_TO_DEPTH_RATIO = 1;

    private final Thread mProcessThread;
    private final int mWindowSize;
    private final int mColorToDepthRatio;
    private final ROSNode mRosNode;
    private final CVAlgorithm mCVAlgorithm;

    private boolean mShutdown = false;

    private boolean mRequirePointCloud = true;
    private boolean mRequireImage = true;

    private byte[] mColorImage = null;
    private int mColorImageWidth;
    private int mColorImageHeight;
    private double mColorImageTimestamp;

    private float[] mPoints;
    private int mPointCount;
    private double mPointCloudTimestamp;

    private float[] mDepthImage;
    private int mDepthImageWidth;
    private int mDepthImageHeight;

    private double mDepthCameraCx;
    private double mDepthCameraCy;
    private double mDepthCameraFx;
    private double mDepthCameraFy;

    private TangoSupport.TangoMatrixTransformData mWorldToColorCamera = null;

    /**
     * Creates the CV algorithm.
     * @param rosNode The ROS Node to output debug images.
     * @param algorithm The algorithm.
     */
    public CVManager(ROSNode rosNode, CVAlgorithm algorithm) {
        this(rosNode, algorithm, DEFAULT_WINDOW_SIZE, DEFAULT_COLOR_TO_DEPTH_RATIO);
    }

    /**
     * Creates the CV algorithm.
     * @param rosNode The ROS Node to output debug images.
     * @param algorithm The algorithm.
     * @param windowSize The size of the window for point expansion.
     */
    public CVManager(ROSNode rosNode, CVAlgorithm algorithm, int windowSize, int colorToDepthRatio) {
        mCVAlgorithm = algorithm;
        if (windowSize > 0) {
            mWindowSize = windowSize;
        } else {
            mWindowSize = DEFAULT_WINDOW_SIZE;
        }
        if (colorToDepthRatio > 0) {
            mColorToDepthRatio = colorToDepthRatio;
        } else {
            mColorToDepthRatio = DEFAULT_COLOR_TO_DEPTH_RATIO;
        }
        mShutdown = false;
        mRosNode = rosNode;
        mProcessThread = new Thread(new Runnable() {
            @Override
            public void run() {
                mainThread();
            }
        });
        mProcessThread.start();
    }

    /**
     * Main looping thread that creates all the data.
     */
    private void mainThread() {
        while (!mShutdown) {
            synchronized (this) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Wait thread interrupted");
                }
                if (mShutdown) {
                    break;
                }
            }

            // Compute the merged Point Clouds.
            if (mDepthImage == null || mDepthImage.length != mDepthImageHeight * mDepthImageWidth) {
                mDepthImage = new float[mDepthImageWidth * mDepthImageHeight];
            }
            TangoPoseData pose;
            try {
                pose = TangoSupport.calculateRelativePose(mColorImageTimestamp,
                        TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR, mPointCloudTimestamp,
                        TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH);
            } catch (TangoErrorException ex) {
                pose = null;
            }

            if (pose != null) {
                Transform tf = TangoTransformUtil.poseToTransform(pose);
                for (int i = 0; i < mDepthImage.length; i++) {
                    mDepthImage[i] = 0;
                }
                for (int i = 0; i < mPointCount; i++) {
                    Transform pt = new Transform(tf, new Transform(
                            mPoints[i * 4], mPoints[i * 4 + 1], mPoints[i * 4 + 2], 0));

                    float depth = (float) pt.getPosition(2);

                    int pixelX = (int) ((mDepthCameraFx * (pt.getPosition(0) / pt.getPosition(2))) + mDepthCameraCx);
                    int pixelY = (int) ((mDepthCameraFy * (pt.getPosition(1) / pt.getPosition(2))) + mDepthCameraCy);
                    // if depth image is set to have same size as color image, upsamples around the projected depth points
                    // else only the projected points are set in the depth image
                    if (mColorToDepthRatio == 1) {
                        for (int x = pixelX - mWindowSize; x <= pixelX + mWindowSize; x++) {
                            for (int y = pixelY - mWindowSize; y <= pixelY + mWindowSize; y++) {
                                if (x >= 0 && y >= 0 && x < mDepthImageWidth && y < mDepthImageHeight) {
                                    mDepthImage[(y * mDepthImageWidth) + x] = depth;
                                }
                            }
                        }
                    } else {
                        if (pixelX >= 0 && pixelY >= 0 && pixelX < mDepthImageWidth && pixelY < mDepthImageHeight) {
                            mDepthImage[(pixelY * mDepthImageWidth) + pixelX] = depth;
                        }
                    }
                }

                try {
                    mWorldToColorCamera = TangoSupport.getMatrixTransformAtTime(
                            mColorImageTimestamp,
                            TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                            TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                            TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                            TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                            TangoSupport.ROTATION_IGNORED);
                } catch (TangoErrorException ex) {
                    mWorldToColorCamera = null;
                }

                List<Polygon> polygons = null;
                if (mWorldToColorCamera != null) {
                    if (mWorldToColorCamera.statusCode == TangoPoseData.POSE_VALID) {
                        polygons = mCVAlgorithm.execute(mColorImageTimestamp, mDepthImage,
                                mColorImage, mColorImageWidth, mColorImageHeight, mWorldToColorCamera, mColorToDepthRatio);
                    } else {
                        Log.d(TAG, "World to camera pose is invalid, vision algorithm not executed.");
                    }

                }

                if (mRosNode != null) {
                    mRosNode.sendDepthImage(mPointCloudTimestamp, mDepthImage, mColorImageWidth, mColorImageHeight);
                    if (polygons != null) {
                        mRosNode.sendVisionMarkers(mPointCloudTimestamp, polygons);
                    }
                }
            }

            synchronized (this) {
                if (mShutdown) {
                    break;
                }
                mRequireImage = true;
                mRequirePointCloud = true;
            }
        }
    }

    /**
     * If true, an image update should be performed.
     * @return True if an image should be sent.
     */
    public boolean requireImage() {
        return mRequireImage;
    }

    /**
     * If true, a point cloud update should be performed.
     * @return True if the point cloud update should be performed.
     */
    public boolean requirePointCloud() {
        return mRequirePointCloud;
    }

    /**
     * Called to shutdown the CV system.
     */
    public synchronized void shutdown() {
        if (!mShutdown) {
            mShutdown = true;
            mCVAlgorithm.shutdown();
            this.notify();
        }
    }

    /**
     * Called to wait for the shutdown (e.g. for the processing thread to terminate).
     */
    public void waitShutdown() {
        shutdown();
        try {
            mProcessThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Thread interrupted during shutdown", e);
        }
        mCVAlgorithm.waitShutdown();
    }

    /**
     * Sends an image to the CV system.
     * @param imageTimestamp The image timestamp for publication.
     * @param intrinsics The intrinsics of the camera.
     * @param width The width of the image.
     * @param height The height of the image.
     * @param image The YCRCB_420_SP image itself.
     */
    public synchronized void sendImage(double imageTimestamp,
            TangoCameraIntrinsics intrinsics, int width, int height, byte[] image) {
        if (!mRequireImage || mShutdown || image == null || intrinsics == null) {
            return;
        }
        if (width <= 0 || height <= 0) {
            return;
        }
        mRequireImage = false;
        if (mColorImage == null || image.length != mColorImage.length) {
            mColorImage = image.clone();
        } else {
            System.arraycopy(image, 0, mColorImage, 0, image.length);
        }
        mColorImageTimestamp = imageTimestamp;
        mColorImageWidth = width;
        mColorImageHeight = height;
        // depth values below are used to project the point cloud to a depth image
        // they are not the native intrinsics of the depth sensor
        // they are set to generate a depth image that is a scaled version of the color image
        mDepthCameraCx = intrinsics.cx / mColorToDepthRatio;
        mDepthCameraCy = intrinsics.cy / mColorToDepthRatio;
        mDepthCameraFx = intrinsics.fx / mColorToDepthRatio;
        mDepthCameraFy = intrinsics.fy / mColorToDepthRatio;
        mDepthImageWidth = width / mColorToDepthRatio;
        mDepthImageHeight = height / mColorToDepthRatio;

        checkNotify();
    }

    /**
     * Sends a point cloud to the system.
     * @param pointCloudData The point cloud data to be sent.
     */
    public synchronized void sendPointCloud(TangoPointCloudData pointCloudData) {
        if (!mRequirePointCloud || mShutdown || pointCloudData == null) {
            return;
        }
        mRequirePointCloud = false;
        if (mPoints == null || pointCloudData.numPoints > (mPoints.length / 4)) {
            mPoints = new float[pointCloudData.numPoints * 4];
        }
        mPointCount = pointCloudData.numPoints;
        mPointCloudTimestamp = pointCloudData.timestamp;
        if (pointCloudData.points != null) {
            for (int i = 0; i < mPointCount * 4; i++) {
                mPoints[i] = pointCloudData.points.get(i);
            }
        } else {
            mPointCount = 0;
        }

        checkNotify();
    }

    /**
     * Checks if we are ready for processing, and if so notifies the processing thread.
     */
    private synchronized void checkNotify() {
        if (!mRequireImage && !mRequirePointCloud) {
            this.notifyAll();
        }
    }
}
