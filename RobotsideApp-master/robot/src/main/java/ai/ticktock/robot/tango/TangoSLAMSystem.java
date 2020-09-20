package ai.cellbots.robot.tango;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.google.atap.tangoservice.experimental.TangoImageBuffer;
import com.projecttango.tangosupport.TangoSupport;
import com.projecttango.tangosupport.ux.TangoUx;
import com.projecttango.tangosupport.ux.UxExceptionEvent;
import com.projecttango.tangosupport.ux.UxExceptionEventListener;

import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.Transform;
import ai.cellbots.common.World;
import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.costmap.FloorplanCostMap;
import ai.cellbots.robot.costmap.PriorTrajectoryCostMap;
import ai.cellbots.robot.slam.SLAMSystem;
import ai.cellbots.robot.state.RobotSessionGlobals;
import ai.cellbots.robot.vision.CameraImage;
import ai.cellbots.robot.vision.CameraInfo;
import ai.cellbots.robot.vision.DepthImage;
import ai.cellbots.robot.vision.PointCloud;
import ai.cellbots.tangocommon.CloudWorldManager;
import ai.cellbots.tangocommon.TangoTransformUtil;

/**
 * Manages the handling of Tango.
 */
public class TangoSLAMSystem extends SLAMSystem implements Tango.OnFrameAvailableListener,
        UxExceptionEventListener, TangoFloorplanner.OnFloorplanAvailableListener {
    private static final String TAG = TangoSLAMSystem.class.getSimpleName();

    private static final int COLOR_TO_DEPTH_RATIO = 12;

    // Has fisheye camera or not.
    private final boolean mHaveFisheye;

    // TODO(playerone) enable this
    //private TangoOctoMapCostMap mTangoOctomapCostMap;
    //private final TangoPointCloudManager mOctomapPointCloudManager = new TangoPointCloudManager();
    //private final TangoPointCloudManager mPointCloudManager = new TangoPointCloudManager();

    private final TangoFloorplanner mTangoFloorplanner;
    private final TangoUx mTangoUx;

    // CostMaps.
    private final FloorplanCostMap mFloorplanCostMap;
    private final PriorTrajectoryCostMap mTangoPriorTrajectoryCostMap;

    private final Tango mTango;

    private final AtomicBoolean mLocalized;
    private final AtomicBoolean mPreviousLocalizationStatus;

    private CameraImage mColorImage;
    private CameraImage mSyncColorImage;
    private DepthImage mDepthImage;
    // Pose array represented as [tx, ty, tz, qx, qy, qz, qw]
    private double[] mWorldToCameraPoseArray;
    private final HashMap<CameraInfo.SensorType, CameraInfo> mCameraIdToIntrinsics = new HashMap<>();

    private final Object colorImageLock = new Object();

    /**
     * Creates the tango-based SLAM system.
     * @param parent The parent android context.
     * @param transformListener The transform listener.
     * @param colorDepthListener The color and depth data listener.
     * @param cameraImageListener The camera image listener.
     * @param pointCloudListener The point cloud listener.
     * @param session The session state.
     * @param resolution The floorplan resolution.
     */
    public TangoSLAMSystem(Context parent, TransformListener transformListener,
            ColorDepthListener colorDepthListener, CameraImageListener cameraImageListener,
            PointCloudListener pointCloudListener, StatusUpdateListener statusUpdateListener,
            RobotSessionGlobals session, double resolution) {
        super(parent, transformListener, colorDepthListener,
                cameraImageListener, pointCloudListener, statusUpdateListener, session);

        Log.i(TAG, "Build manufacturer = " + Build.MANUFACTURER);
        // Note that ASUS phone doesn't support fisheye camera, so we disable it.
        if ("asus".equals(Build.MANUFACTURER)) {
            mHaveFisheye = false;
        } else {
            mHaveFisheye = true;
        }
        mLocalized = new AtomicBoolean(false);
        mPreviousLocalizationStatus = new AtomicBoolean(false);
        setState(State.INITIALIZING);
        synchronized (this) {
            mTangoUx = new TangoUx(parent);
            mTangoUx.setUxExceptionEventListener(this);

            mFloorplanCostMap = new FloorplanCostMap(session.getWorld(), resolution);
            if (getSession().getWorld() != null) {
                mTangoPriorTrajectoryCostMap = new PriorTrajectoryCostMap(session.getWorld(),
                        resolution);
            } else {
                mTangoPriorTrajectoryCostMap = null;
            }

            // TODO(playerone) get from the options if we want Octomap or not
            //mTangoOctomapCostMap = new TangoOctoMapCostMap(mOctomapPointCloudManager, resolution);
            // TODO(playerone) get the height from the RobotDriver, for now we are using 0.5m
            //double tangoDeviceHeight = mCloudConnector.getTangoDeviceHeight();
            //Log.i(TAG, "Get device height from metadata: " + tangoDeviceHeight);
            //mTangoOctomapCostMap.init((float)0.5);

            mTango = new Tango(getParent(), new Runnable() {
                // Called on the UI thread when the Tango is started
                @Override
                public void run() {
                    // We call the onTangoStarted() logic in a new thread so that it does not
                    // slow down the UI thread.
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            onTangoStarted();
                        }
                    }).start();
                }
            });

            if (getSession().getWorld() == null) {
                mTangoFloorplanner = new TangoFloorplanner(this);
                mTangoFloorplanner.startFloorplanning();
            } else {
                mTangoFloorplanner = null;
            }
        }
    }

    /**
     * Called when a new floorplan is available.
     *
     * @param tangoFloorplanner The floorplanner while made the call.
     * @param levels the levels of the floorplan.
     */
    @Override
    public synchronized void onFloorplanAvailable(
            TangoFloorplanner tangoFloorplanner, List<World.FloorPlanLevel> levels) {
        if (mTangoFloorplanner != tangoFloorplanner) {
            return;
        }
        Log.d(TAG, "Levels: " + levels.size());
        mFloorplanCostMap.onFloorplan(levels);
    }

    /**
     * Shuts down the Tango system.
     */
    @Override
    public synchronized void shutdown() {
        // If the tango was immediately shutdown, there's no need to keep connecting to it.
        if (getState() == State.SHUTDOWN || getState() == State.STOPPING) {
            return;
        }
        setState(State.STOPPING);
        statusUpdated();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mTango.disconnect();
                } catch (TangoErrorException e) {
                    //reportException(Error.exception_tango_error, e);
                } catch (IllegalArgumentException e) {
                    // Do nothing, since the tango is already disconnected by service shutdown.
                    Log.w(TAG, "Ignore exception:", e);
                }
                synchronized (TangoSLAMSystem.this) {
                    if (mTangoUx != null) {
                        mTangoUx.stop();
                    }
                    if (mTangoFloorplanner != null) {
                        mTangoFloorplanner.stopFloorplanning();
                    }
                    mLocalized.set(false);
                    mPreviousLocalizationStatus.set(false);
                    setTransform(null);
                    setState(State.SHUTDOWN);
                    statusUpdated();
                    // TODO(playerone) enable this
                    //mTangoOctomapCostMap.stopOctomap();
                    //mTangoOctomapCostMap.delete();
                }
            }
        }).start();
    }

    /**
     * Gets a collection of CostMaps.
     *
     * @return The CostMap collection.
     */
    @Override
    public Collection<CostMap> getCostMaps() {
        ArrayList<CostMap> r = new ArrayList<>(3);
        r.add(mFloorplanCostMap);
        // TODO(playerone) enable this.
        //r.add(mTangoOctomapCostMap);
        if (mTangoPriorTrajectoryCostMap != null) {
            r.add(mTangoPriorTrajectoryCostMap);
        }
        return Collections.unmodifiableList(r);
    }

    /**
     * Saves the map to a file.
     *
     * @param mapName The map file name to save.
     */
    @Override
    public synchronized void saveMap(final String mapName) {
        if (getState() != State.MAPPING) {
            return;
        }
        setState(State.SAVING_MAP);
        statusUpdated();

        // Stop floor planning
        mTangoFloorplanner.stopFloorplanning();

        Thread saver = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] fileData;
                synchronized (TangoSLAMSystem.this) {
                    if (getState() != State.SAVING_MAP) {
                        return;
                    }
                    fileData = mTangoFloorplanner.generateFileData(getSession().getUserUuid(), false);
                    Log.i(TAG, "Saving map: " + mapName);
                }
                CloudWorldManager.saveFromTango(getParent(), mTango, mapName, fileData);
                Log.i(TAG, "Map has been saved: " + mapName);
                synchronized (TangoSLAMSystem.this) {
                    if (getState() == State.SAVING_MAP) {
                        setState(State.SAVED_MAP);
                        statusUpdated();
                    }
                }
            }
        });
        saver.start();

    }

    /**
     * Gets if the camera is localized.
     *
     * @return True if the camera is localized.
     */
    public boolean isLocalized() {
        return mLocalized.get();
    }

    /**
     * Gets if the system is currently mapping the world.
     *
     * @return True if the system is currently mapping.
     */
    public boolean isMapping() {
        return getState() == State.MAPPING;
    }

    /**
     * Gets if we can save the current world.
     *
     * @return True if the world can be saved.
     */
    public boolean canSaveWorld() {
        return mLocalized.get() && getState() == State.MAPPING;
    }

    /**
     * Waits for the slam system to shutdown.
     */
    @Override
    public void waitShutdown() {
        shutdown();
        while (getState() != State.SHUTDOWN) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted during shutdown:", e);
            }
        }
    }

    /**
     * Called when the tango is started so we can connect to the device.
     */
    private synchronized void onTangoStarted() {
        if (getState() == State.STOPPING || getState() == State.SHUTDOWN) {
            return;
        }
        if (getSession().getWorld() == null) {
            Log.i(TAG, "Initializing for mapping");
            try {
                TangoConfig config = setupTangoConfig(mTango, null);
                startupTango(config);
                setState(State.MAPPING);
                statusUpdated();
            } catch (TangoErrorException | TangoInvalidException e) {
                Log.e(TAG, "Error in initialization of mapping", e);
                setState(State.INITIALIZING);
                statusUpdated();
                setError();
            }
        } else {
            Log.i(TAG, "Initializing for navigation");
            try {
                TangoConfig config = setupTangoConfig(mTango, getSession().getWorld());
                startupTango(config);
                setState(State.NAVIGATING);
                // TODO(playerone) Why we don't call statusUpdated()?
            } catch (TangoErrorException | TangoInvalidException e) {
                Log.e(TAG, "Error in initialization of navigation", e);
                setState(State.INITIALIZING);
                statusUpdated();
                setError();
            }
        }
    }

    /**
     * Sets up the tango configuration object. Make sure mTango object is initialized before
     * making this call.
     *
     * @param tango The tango to configure.
     * @param world The detailed world to load.
     * @return The configuration for the tango.
     */
    private TangoConfig setupTangoConfig(Tango tango, DetailedWorld world) {
        // Create a new Tango Configuration and enable the HelloMotionTrackingActivity API.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);
        if (world == null) {
            config.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, true);
        } else {
            CloudWorldManager.loadToTango(world, config);
        }

        return config;
    }

    /**
     * Set up the callback listeners for the Tango Service and obtain other parameters required
     * after Tango connection.
     * Listen to new Pose data.
     *
     * @param config The tango configuration
     */
    private void startupTango(TangoConfig config) {
        // Lock configuration and connect to Tango.
        // Select coordinate frame pair.
        final List<TangoCoordinateFramePair> framePairs = new ArrayList<>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE));

        TangoSupport.initialize(mTango);
        mTangoUx.start();
        mTango.connect(config);

        if (mTangoFloorplanner != null) {
            // Set camera intrinsics to TangoFloorplanner.
            mTangoFloorplanner.setDepthCameraCalibration(mTango.getCameraIntrinsics
                    (TangoCameraIntrinsics.TANGO_CAMERA_DEPTH));
        }

        if (mHaveFisheye) {
            mTango.experimentalConnectOnFrameListener(
                    TangoCameraIntrinsics.TANGO_CAMERA_FISHEYE, this);
        }
        mTango.experimentalConnectOnFrameListener(
                TangoCameraIntrinsics.TANGO_CAMERA_COLOR, this);

        // Listen for new Tango data.
        mTango.connectListener(framePairs, new Tango.TangoUpdateCallback() {
            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                synchronized (TangoSLAMSystem.this) {
                    State state = getState();
                    if (state != State.MAPPING && state != State.NAVIGATING) {
                        return;
                    }
                    mTangoUx.updatePoseStatus(pose.statusCode);

                    // Taken from the tango example, this controls whether or not we are
                    // localized. We do not want to save the map until we are localized, because
                    // we do not want to save invalid maps.
                    if ((pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION)
                            && (pose.targetFrame
                            == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE)) {
                        boolean localized = pose.statusCode == TangoPoseData.POSE_VALID;
                        mLocalized.set(localized);
                        if (!localized) {
                            Log.w(TAG, "Localization failed. timestamp = " + pose.timestamp);
                            setTransform(null);
                        }
                        if (mPreviousLocalizationStatus.get() != mLocalized.get()) {
                            statusUpdated();
                            mPreviousLocalizationStatus.set(mLocalized.get());
                        }
                    }
                    if (mLocalized.get() &&
                            (pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) &&
                            (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION)) {
                        if (state == State.MAPPING) {
                            TangoFloorplanner floorPlanner = mTangoFloorplanner;
                            if (floorPlanner != null) {
                                floorPlanner.logPose(pose);
                            }
                        }
                        setTransform(TangoTransformUtil.poseToTransform(pose));
                    }
                }
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                // We are not using onXyzIjAvailable for this app.
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData tangoPointCloudData) {
                State state = getState();
                if (state != State.MAPPING && state != State.NAVIGATING) {
                    Log.v(TAG, "Ignore point cloud for wrong state: " + state);
                    return;
                }
                Log.v(TAG, "Store point cloud");

                mTangoUx.updatePointCloud(tangoPointCloudData);

                /*try{
                    mPointCloudManager.updatePointCloud(pointCloud);
                    mOctomapPointCloudManager.updatePointCloud(pointCloud);
                } catch (TangoInvalidException e) {
                    Log.w(TAG, "Exception thrown updating Point Cloud", e);
                    return;
                }*/

                // Send point cloud to tango floor planner
                if (mTangoFloorplanner != null) {
                    // This is OK to do here because it's very fast, it only adds the point cloud
                    // to the internal manager of the floor planner.
                    // TODO(playerone) Consider attempt to re-use the same PointCloud manager.
                    mTangoFloorplanner.onPointCloudAvailable(tangoPointCloudData);
                }

                TangoPoseData depthPose;
                Transform depthTransform;
                try {
                    depthPose = TangoSupport.getPoseAtTime(tangoPointCloudData.timestamp,
                            TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                            TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                            TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                            TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                            TangoSupport.ROTATION_IGNORED);
                    depthTransform = TangoTransformUtil.poseToTransform(
                            depthPose, tangoPointCloudData.timestamp);
                } catch (TangoErrorException | TangoInvalidException e) {
                    Log.d(TAG, "Could not get a valid pose. Pose ignored. ", e);
                    return;
                }
                if (depthPose.statusCode != TangoPoseData.POSE_VALID) {
                    Log.d(TAG, "Could not get a valid depth pose. ts = " + depthPose.timestamp);
                    return;
                }

                //mTangoOctomapCostMap.onPointCloud(pointCloud);
                synchronizeColorDepth(tangoPointCloudData);

                float[] points = new float[tangoPointCloudData.numPoints * 4];
                if (tangoPointCloudData.numPoints > 0) {
                    tangoPointCloudData.points.rewind();
                    tangoPointCloudData.points.get(points);
                }
                PointCloud pointCloud = new PointCloud(tangoPointCloudData.timestamp,
                        PointCloud.Format.X_Y_Z_I, points);
                setNewPointCloud(pointCloud, depthTransform);

                // TODO(playerone) enable this.
                /*
                if (mTangoOctomapCostMap != null) {
                    mTangoOctomapCostMap.updateWithLatestPointCloud();
                    mOctomapPointCloudManager.updatePointCloud(pointCloud);
                }*/
            }

            @Override
            public void onTangoEvent(final TangoEvent event) {
                mTangoUx.updateTangoEvent(event);
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // We are not using onFrameAvailable for this application.
            }
        });
    }

    /**
     * On frame available from Tango.
     *
     * @param tangoImageBuffer The tango image buffer callback.
     * @param cameraId         The camera id callback.
     */
    @Override
    public void onFrameAvailable(TangoImageBuffer tangoImageBuffer, int cameraId) {
        if (getState() != State.NAVIGATING) {
            return;
        }

        CameraInfo.SensorType sensorType;
        if (TangoCameraIntrinsics.TANGO_CAMERA_COLOR == cameraId) {
            sensorType = CameraInfo.SensorType.COLOR;
        } else if (TangoCameraIntrinsics.TANGO_CAMERA_FISHEYE == cameraId) {
            sensorType = CameraInfo.SensorType.FISHEYE;
        } else {
            Log.w(TAG, "Invalid sensorType id: " + cameraId);
            return;
        }

        CameraInfo cameraInfo;
        if (mCameraIdToIntrinsics.containsKey(sensorType)) {
            cameraInfo = mCameraIdToIntrinsics.get(sensorType);
        } else {
            try {
                TangoCameraIntrinsics intrinsics = mTango.getCameraIntrinsics(cameraId);
                cameraInfo = new CameraInfo(sensorType, intrinsics.cx, intrinsics.cy,
                        intrinsics.fx, intrinsics.fy);
                mCameraIdToIntrinsics.put(sensorType, cameraInfo);
            } catch (TangoErrorException e) {
                Log.v(TAG, "Ignore tango exception for invalid intrinsics:", e);
                return;
            }
        }

        if (tangoImageBuffer.format == TangoImageBuffer.YCRCB_420_SP
                && sensorType == CameraInfo.SensorType.COLOR) {
            synchronized (colorImageLock) {
                int imageLength = CameraImage.calculateImageByteLength(
                        CameraImage.Format.Y_CR_CB_420, tangoImageBuffer.width, tangoImageBuffer.height);
                byte[] imageData = (mColorImage != null) ? mColorImage.getBytes() : null;
                if (imageData == null || imageData.length != imageLength) {
                    imageData = new byte[imageLength];
                }
                tangoImageBuffer.data.get(imageData);
                mColorImage = new CameraImage(cameraInfo, CameraImage.Format.Y_CR_CB_420,
                        tangoImageBuffer.timestamp, tangoImageBuffer.width,
                        tangoImageBuffer.height, imageData);
            }
            setNewColorImage(mColorImage);
        } else if (TangoCameraIntrinsics.TANGO_CAMERA_COLOR == cameraId) {
            Log.w(TAG, "Frame ignored for wrong format: " + tangoImageBuffer.format);
        }
    }

    /**
     * Creates depth image from point cloud and sends it along with color to the listener interface
     * Only pixels corresponding to point cloud are set, no upsampling is done in this function
     *
     * @param pointCloudData The tango point cloud.
     */
    private void synchronizeColorDepth(TangoPointCloudData pointCloudData) {
        if (getState() != State.NAVIGATING || mColorImage == null) {
            return;
        }

        TangoPoseData pose;
        synchronized (colorImageLock) {
            // Copy the latest color image for synchronization purposes. If we have an old color
            // image, we can recycle the image bytes to speed up allocation.
            if (mSyncColorImage != null) {
                mSyncColorImage = new CameraImage(mColorImage, mSyncColorImage.getBytes());
            } else {
                mSyncColorImage = new CameraImage(mColorImage, null);
            }
        }

        // Depth values below are used to project the point cloud to a depth image
        // They are not the native intrinsics of the depth sensor
        // They are set to generate a depth image that is a scaled version of the color image
        CameraInfo depthCameraInfo = new CameraInfo(CameraInfo.SensorType.DEPTH,
                1.0 / COLOR_TO_DEPTH_RATIO, mSyncColorImage.getCameraInfo());
        int depthImageWidth = mColorImage.getWidth() / COLOR_TO_DEPTH_RATIO;
        int depthImageHeight = mColorImage.getHeight() / COLOR_TO_DEPTH_RATIO;

        float[] floatBuffer = mDepthImage != null ? mDepthImage.getFloats() : null;
        if (floatBuffer == null || floatBuffer.length
                != DepthImage.calculateImageFloatLength(depthImageWidth, depthImageHeight)) {
            floatBuffer = new float[DepthImage.calculateImageFloatLength(depthImageWidth,
                    depthImageHeight)];
        }
        mDepthImage = new DepthImage(depthCameraInfo, pointCloudData.timestamp,
                depthImageWidth, depthImageHeight, floatBuffer);
        try {
            pose = TangoSupport.calculateRelativePose(mSyncColorImage.getTimestamp(),
                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR, pointCloudData.timestamp,
                    TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH);
        } catch (TangoErrorException ex) {
            Log.w(TAG, "Pose invalid", ex);
            return;
        }
        // Vector3, Quaternion and Matrix4 are Rajawali classes
        Vector3 translation = new Vector3(pose.translation[0],
                pose.translation[1], pose.translation[2]);
        Quaternion rotation = new Quaternion(pose.rotation[3], pose.rotation[0],
                pose.rotation[1], pose.rotation[2]);
        // Quaternions in Rajawali are not the same as in Tango, left-hand rotation is needed to match Tango
        rotation.conjugate();
        Matrix4 poseMatrix = new Matrix4();
        poseMatrix.setAll(translation, new Vector3(1, 1, 1), rotation);
        double depthImageCx = mDepthImage.getCameraInfo().getCx();
        double depthImageCy = mDepthImage.getCameraInfo().getCy();
        double depthImageFx = mDepthImage.getCameraInfo().getFx();
        double depthImageFy = mDepthImage.getCameraInfo().getFy();
        float[] depthBuffer = new float[depthImageWidth * depthImageHeight];
        Arrays.fill(depthBuffer, 0, depthBuffer.length, 0.0f);
        for (int i = 0; i < pointCloudData.numPoints; i++) {
            float x = pointCloudData.points.get(i * 4);
            float y = pointCloudData.points.get(i * 4 + 1);
            float z = pointCloudData.points.get(i * 4 + 2);
            Vector3 originalPoint = new Vector3(x, y, z);
            Vector3 transformedPoint = originalPoint.multiply(poseMatrix.getDoubleValues());
            int pixelX = (int) ((depthImageFx * (transformedPoint.x / transformedPoint.z)) + depthImageCx + 0.5);
            int pixelY = (int) ((depthImageFy * (transformedPoint.y / transformedPoint.z)) + depthImageCy + 0.5);
            float depth = (float) transformedPoint.z;
            if (pixelX >= 0 && pixelY >= 0 && pixelX < depthImageWidth && pixelY < depthImageHeight) {
                depthBuffer[pixelY * depthImageWidth + pixelX] = depth;
            }
        }
        System.arraycopy(depthBuffer, 0, mDepthImage.getFloats(), 0, depthBuffer.length);
        TangoPoseData worldToColorCamera;
        mWorldToCameraPoseArray = null;
        try {
            worldToColorCamera = TangoSupport.getPoseAtTime(mColorImage.getTimestamp(),
                    TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                    TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                    TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                    TangoSupport.ROTATION_IGNORED);
        } catch (TangoErrorException ex) {
            worldToColorCamera = null;
        }
        if (worldToColorCamera != null) {
            if (worldToColorCamera.statusCode == TangoPoseData.POSE_VALID) {
                mWorldToCameraPoseArray = new double[] {worldToColorCamera.translation[0],
                        worldToColorCamera.translation[1], worldToColorCamera.translation[2],
                        worldToColorCamera.rotation[0], worldToColorCamera.rotation[1],
                        worldToColorCamera.rotation[2], worldToColorCamera.rotation[3]};
            } else {
                Log.e(TAG, "World to camera pose is invalid");
            }

        }
        setNewColorDepth(mSyncColorImage, mDepthImage, mWorldToCameraPoseArray);
    }


    /**
     * Listener interface from TangoUx, which reports Tango events such as localization, blocked
     * camera, etc.
     */
    @Override
    public void onUxExceptionEvent(UxExceptionEvent uxExceptionEvent) {
        /*// Translate event into tabulated human readable form and log to Firebase.
        String status;
        switch (uxExceptionEvent.getType()) {
            case UxExceptionEvent.TYPE_COLOR_CAMERA_OVER_EXPOSED:
                status = getParent().getString(R.string.tango_event_camera_over_exposed);
                break;
            case UxExceptionEvent.TYPE_COLOR_CAMERA_UNDER_EXPOSED:
                status = getParent().getString(R.string.tango_event_camera_under_exposed);
                break;
            case UxExceptionEvent.TYPE_FEW_FEATURES:
                status = getParent().getString(R.string.tango_event_few_features);
                break;
            case UxExceptionEvent.TYPE_FEW_DEPTH_POINTS:
                status = getParent().getString(R.string.tango_event_few_depth_points);
                break;
            case UxExceptionEvent.TYPE_FISHEYE_CAMERA_OVER_EXPOSED:
                status = getParent().getString(R.string.tango_event_fisheye_camera_over_exposed);
                break;
            case UxExceptionEvent.TYPE_LYING_ON_SURFACE:
                status = getParent().getString(R.string.tango_event_device_covered);
                break;
            case UxExceptionEvent.TYPE_MOTION_TRACK_INVALID:
                status = getParent().getString(R.string.tango_event_motion_track_invalid);
                break;
            case UxExceptionEvent.TYPE_MOVING_TOO_FAST:
                status = getParent().getString(R.string.tango_event_moving_too_fast);
                break;
            default:
                status = getParent().getString(R.string.tango_event_unknown);
                break;
        }
        if (uxExceptionEvent.getStatus() == UxExceptionEvent.STATUS_DETECTED) {
            status += getParent().getString(R.string.tango_event_detected);

        } else {
            status += getParent().getString(R.string.tango_event_resolved);
        }
        mCloudConnector.logStatus(null, mRobotUuid, mRobotVersion, mWorld, status);*/
    }
}
