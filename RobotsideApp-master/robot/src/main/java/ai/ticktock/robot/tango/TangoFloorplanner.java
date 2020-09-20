package ai.cellbots.robot.tango;


import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.google.atap.tango.reconstruction.Tango3dReconstruction;
import com.google.atap.tango.reconstruction.Tango3dReconstructionConfig;
import com.google.atap.tango.reconstruction.TangoFloorplanLevel;
import com.google.atap.tango.reconstruction.TangoPolygon;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import ai.cellbots.common.Transform;
import ai.cellbots.common.World;
import ai.cellbots.tangocommon.TangoTransformUtil;

/**
 * Uses the Tango Service data to build a floor plan 2D. Provides higher level functionality
 * built on top of the {@code Tango3dReconstruction}.
 * Given a point cloud, it will report a callback with the floorplan polygons.
 * It abstracts all the needed thread management and pose requesting logic.
 */
public class TangoFloorplanner {

    private static final String TAG = TangoFloorplanner.class.getSimpleName();
    private final TangoPointCloudManager mPointCloudBuffer;

    private Tango3dReconstruction mTango3dReconstruction = null;
    private OnFloorplanAvailableListener mCallback = null;
    private volatile Handler mHandler = null;

    private volatile boolean mIsFloorplanningActive = false;

    private Runnable mRunnableCallback = null;

    private final Deque<Transform> mTransformList;

    private final Object mTransformListLock = new Object();


    /**
     * Callback for when meshes are available.
     */
    public interface OnFloorplanAvailableListener {
        void onFloorplanAvailable(TangoFloorplanner floorplanner, List<World.FloorPlanLevel> levels);
    }

    @SuppressWarnings("CallToThreadStartDuringObjectConstruction")
    public TangoFloorplanner(OnFloorplanAvailableListener callback) {
        mCallback = callback;
        mTransformList = new LinkedList<>();
        Tango3dReconstructionConfig config = new Tango3dReconstructionConfig();
        // Configure the 3D reconstruction library to work in "floorplan" mode.
        config.putBoolean("use_floorplan", true);
        config.putBoolean("generate_color", false);
        // Simplify the detected contours by allowing a maximum error of 5cm.
        config.putDouble("floorplan_max_error", 0.05);
        mTango3dReconstruction = new Tango3dReconstruction(config);
        mPointCloudBuffer = new TangoPointCloudManager();

        HandlerThread handlerThread = new HandlerThread("mesherCallback");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());

        if (callback != null) {
            /*
             * This runnable processes the saved point clouds and meshes and triggers the
             * onFloorplanAvailable callback with the generated {@code TangoPolygon} instances.
             */
            mRunnableCallback = new Runnable() {
                @Override
                public void run() {
                    List<World.FloorPlanLevel> levels;
                    // Synchronize access to mTango3dReconstruction. This runs in TangoFloorplanner
                    // thread.
                    synchronized (TangoFloorplanner.this) {
                        if (!mIsFloorplanningActive) {
                            return;
                        }

                        if (mPointCloudBuffer.getLatestPointCloud() == null) {
                            return;
                        }

                        // Get the latest point cloud data.
                        TangoPointCloudData cloudData = mPointCloudBuffer.getLatestPointCloud();
                        TangoPoseData depthPose;
                        try {
                            depthPose = TangoSupport.getPoseAtTime(
                                    cloudData.timestamp,
                                    TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                                    TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                                    TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                                    TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                                    TangoSupport.ROTATION_IGNORED);
                        } catch (TangoErrorException error) {
                            Log.w(TAG, "Error in mesher, skipping pose");
                            return;
                        }
                        if (depthPose.statusCode != TangoPoseData.POSE_VALID) {
                            Log.e(TAG, "Could not extract a valid depth pose");
                            return;
                        }

                        // Update the mesh and floorplan representation.
                        mTango3dReconstruction.updateFloorplan(cloudData, depthPose);

                        // Extract the full set of floorplan levels.
                        levels = extractFloorplanLevels();
                    }
                    // Provide the new floorplan polygons to the app via callback.
                    mCallback.onFloorplanAvailable(TangoFloorplanner.this, levels);
                }
            };
        }
    }

    /**
     * Synchronize access to mTango3dReconstruction. This runs in UI thread.
     */
    public synchronized void release() {
        mIsFloorplanningActive = false;
        mTango3dReconstruction.release();
    }

    public void startFloorplanning() {
        mIsFloorplanningActive = true;
    }

    public void stopFloorplanning() {
        mIsFloorplanningActive = false;
    }

    public synchronized void setDepthCameraCalibration(TangoCameraIntrinsics calibration) {
        mTango3dReconstruction.setDepthCameraCalibration(calibration);
    }

    /**
     * Receives the depth point cloud. This method retrieves and stores the depth camera pose
     * and point cloud to use when updating the {@code Tango3dReconstruction}.
     *
     * @param tangoPointCloudData the depth point cloud.
     */
    public void onPointCloudAvailable(final TangoPointCloudData tangoPointCloudData) {
        if (!mIsFloorplanningActive || (tangoPointCloudData == null) ||
                (tangoPointCloudData.points == null)) {
            return;
        }
        mPointCloudBuffer.updatePointCloud(tangoPointCloudData);
        mHandler.removeCallbacksAndMessages(null);
        mHandler.post(mRunnableCallback);
    }

    /**
     * Extracts a floorplan from the Tango.
     * @return The floorplan
     */
    @SuppressLint("LogConditional")
    private synchronized List<World.FloorPlanLevel> extractFloorplanLevels() {
        List<TangoFloorplanLevel> floorplanLevels = mTango3dReconstruction.extractFloorplanLevels();
        List<World.FloorPlanLevel> fp = new ArrayList<>(floorplanLevels.size());

        Log.i(TAG, "Save floorplan levels: " + floorplanLevels.size());
        for (TangoFloorplanLevel level : floorplanLevels) {
            mTango3dReconstruction.selectFloorplanLevel(level);
            List<TangoPolygon> polygons = mTango3dReconstruction.extractFloorplan();
            Log.i(TAG,
                    "Level: " + level.minZ + " to " + level.maxZ + " polygons " + polygons.size());
            List<World.FloorPlanPolygon> polygonList = new ArrayList<>(polygons.size());
            for (TangoPolygon polygon : polygons) {
                polygonList.add(new World.FloorPlanPolygon(polygon.vertices2d,
                        polygon.isClosed, polygon.area, polygon.layer));
            }
            World.FloorPlanLevel fl = new World.FloorPlanLevel(level.minZ, level.maxZ, polygonList);
            fp.add(fl);
        }

        return fp;
    }

    /**
     * Generate the file bytes from the transforms.
     *
     * @return The bytes.
     */
    @SuppressLint("LogConditional")
    public byte[] generateFileData(String user, boolean correctPoses) {
        Transform[] transforms;
        synchronized (mTransformListLock) {
            transforms = mTransformList.toArray(new Transform[0]);
        }

        synchronized (this) {
            byte[] userBytes = user.getBytes();
            int len = 1 + 4 + 4 + 4 + 4 + userBytes.length;

            Transform[] correctedTransforms = new Transform[transforms.length];

            if (correctPoses) {
                // Correct transform positions and compute size.
                for (int i = 0; i < transforms.length; i++) {
                    TangoPoseData newPoseData = TangoSupport.getPoseAtTime(
                            transforms[i].getTimestamp(),
                            TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                            TangoPoseData.COORDINATE_FRAME_DEVICE,
                            TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                            TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                            TangoSupport.ROTATION_IGNORED);
                    correctedTransforms[i] = TangoTransformUtil.poseToTransform(newPoseData);
                }
            } else {
                correctedTransforms = transforms;
            }

            for (int i = 0; i < transforms.length; i++) {
                len += correctedTransforms[i].getByteLength();
            }

            List<World.FloorPlanLevel> fp = extractFloorplanLevels();
            for (World.FloorPlanLevel level : fp) {
                len += level.getByteLength();
            }

            byte[] fileData = new byte[len];

            ByteBuffer locationBuffer = ByteBuffer.wrap(fileData);

            // Version = 3
            locationBuffer.put((byte) 3);

            // Write username length and userBytes
            locationBuffer.putInt(userBytes.length);
            locationBuffer.put(userBytes);

            // Write tf count and then each tf
            locationBuffer.putInt(mTransformList.size());
            for (Transform l : correctedTransforms) {
                l.toByteBuffer(locationBuffer);
            }

            // No custom transforms
            locationBuffer.putInt(0);

            // Write level count and then each level
            locationBuffer.putInt(fp.size());
            for (World.FloorPlanLevel fl : fp) {
                fl.toByteBuffer(locationBuffer);
            }

            return fileData;
        }
    }

    /**
     * Log the Position and Orientation of the given pose in the Logcat as information.
     *
     * @param pose the pose to log.
     */
    public void logPose(TangoPoseData pose) {
        synchronized (mTransformListLock) {
            mTransformList.add(TangoTransformUtil.poseToTransform(pose));
        }

        if (!Log.isLoggable(TAG, Log.INFO)) {
            return;
        }

        StringBuilder stringBuilder = new StringBuilder();

        float[] translation = pose.getTranslationAsFloats();
        stringBuilder.append("Position: ");
        stringBuilder.append(translation[0]);
        stringBuilder.append(", ");
        stringBuilder.append(translation[1]);
        stringBuilder.append(", ");
        stringBuilder.append(translation[2]);

        float[] orientation = pose.getRotationAsFloats();
        stringBuilder.append(". Orientation: ");
        stringBuilder.append(orientation[0]);
        stringBuilder.append(", ");
        stringBuilder.append(orientation[1]);
        stringBuilder.append(", ");
        stringBuilder.append(orientation[2]);
        stringBuilder.append(", ");
        stringBuilder.append(orientation[3]);

        if (Log.isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, stringBuilder.toString());
        }
    }
}