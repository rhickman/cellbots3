package ai.cellbots.robot.tango;

import android.os.HandlerThread;
import android.util.Log;

import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import ai.cellbots.robot.costmap.OctoMapCostMap;


/**
 * Updates the Tango based on grid cost maps.
 */
public class TangoOctoMapCostMap extends OctoMapCostMap<TangoPointCloudData>  {
    private static final String TAG = TangoOctoMapCostMap.class.getSimpleName();

    private static final int MIN_POINTS = 10;

    // TODO(playerone) All the octomap parameters should be moved to OctoMapCostMap.
    private static final double GRID_CELL_SIZE = 0.10;
    private static final double GRID_LENGTH_X = 3.0;
    private static final double GRID_LENGTH_Y = 3.0;

    private static final float OCTOMAP_RESOLUTION = (float) 0.1;  // Size of the voxels
    private static final float OCTOMAP_MIN_DISTANCE = (float) 0.30;  // Minimal valid distance for
    // points from the point cloud
    private static final float OCTOMAP_MAX_DISTANCE = (float) 4.00;  //  Maximal valid distance for
    // points from the point cloud
    private static final float OCTOMAP_PROB_HIT = (float) 0.95;  // Probability of hit
    private static final float OCTOMAP_PROB_MISS = (float) 0.20;  // Probability of miss
    private static final float OCTOMAP_CLAMPING_MIN = (float) 0.20;  // Octomap clamping minimal
    private static final float OCTOMAP_CLAMPING_MAX = (float) 0.51;  // Octomap clamping maximal
    private static final float POINT_CLOUD_CONFIDENCE = (float) 0.90; // Minimal point cloud confidence
    private static final float MAX_HEIGHT_FROM_DEVICE = (float) 1.0;  // Max height from device
    private static final float MAX_HEIGHT_FROM_FLOOR = (float) 0.3;  // Max height from floor

    // Size of the bumper wall y camera reference frame
    private static final int BUMPER_SIDE_X_LENGTH = 6;
    private static final int BUMPER_SIDE_Y_LENGTH = 10;

    private final TangoPointCloudManager mPointCloudManager;
    private OctomapGridCostmapGenerator mOctomapGridCostmapGenerator = null;
    private boolean mOctomapIsEmpty = true;

    private final ExecutorService mExecutor;
    List<Future<?>> mExecutorFutures = new ArrayList<>();

    // Boolean that avoids Octomap to be used when it was stopped
    private final AtomicBoolean mUpdateOctomap = new AtomicBoolean(false);
    // Boolean that is set to true if the runnable mUpdateWithPointCloudTask() is executing
    private final AtomicBoolean mIsProcessingOctomap = new AtomicBoolean(false);

    static {
        System.loadLibrary("octomap_jni");
    }

    /**
     * Stores a point cloud for later update.
     * @param next The point cloud to process.
     */
    @Override
    protected void processUpdate(TangoPointCloudData next) {
        setValid(true); // TODO: only if point cloud was valid
        // TODO: pass the pointcloud to OctoMap.
        // TODO: setGrid().
        onCostMapUpdate();
    }

    /**
     * Called to update the pose.
     * @param cloudData The cloud pose data.
     */
    public void onPointCloud(TangoPointCloudData cloudData) {
        // Copy the latest point cloud and feed it to processUpdate()
        TangoPointCloudManager tangoPointCloudManager = new TangoPointCloudManager();
        tangoPointCloudManager.updatePointCloud(cloudData);
        saveUpdate(tangoPointCloudManager.getLatestPointCloud());
    }


    // Runnable task that adds the latest point cloud from the shared point cloud manager
    // into the octomap. This is run using mHandler.
    // TODO(playerone) This "handler" logic can be safely removed from the system and the code moved
    // to processUpdate(). The code in processUpdate() is run in a separate thread by
    // EventProcessor.
    private Runnable mUpdateWithPointCloudTask = new Runnable() {
        @Override
        public void run() {
            // To avoid a race condition if mUpdateOctomap is set to false before
            // mIsProcessingOctomap is set to true, it's used synchronized().
            synchronized (TangoOctoMapCostMap.this) {
                // Run this runnable task when Octomap is not stopped
                if (!mUpdateOctomap.get()) {
                    Log.w(TAG, "Octomap stopped");
                    return;
                }
                Log.d(TAG, "updating octomap with latest point cloud");
                // Octomap is processing
                mIsProcessingOctomap.set(true);
            }

            // Query the pose corresponding to the point cloud timestamp.
            TangoPointCloudData cloudData = mPointCloudManager.getLatestPointCloud();
            if (cloudData == null) {
                Log.w(TAG, "null point cloud retrieved from point cloud manager");
                mIsProcessingOctomap.set(false);
                return;
            }
            if (cloudData.numPoints < MIN_POINTS) {
                Log.w(TAG, "Refusing to use point cloud with only " + cloudData.numPoints);
                mIsProcessingOctomap.set(false);
                return;
            }
            if (cloudData.timestamp < 0.0D) {
                Log.w(TAG, "Refusing to use point cloud with incorrect time stamp (" +
                        cloudData.timestamp + ")");
                mIsProcessingOctomap.set(false);
                return;
            }

            TangoPoseData depthPose;
            try {
                depthPose = TangoSupport.getPoseAtTime(cloudData.timestamp,
                        TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                        TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                        TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                        TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                        TangoSupport.ROTATION_IGNORED);
            } catch (TangoErrorException e) {
                Log.w(TAG, "Ignore transform", e);
                // Setting mIsProcessingOctomap to false to avoid locking up the system
                mIsProcessingOctomap.set(false);
                return;
            }
            if (depthPose.statusCode != TangoPoseData.POSE_VALID) {
                Log.w(TAG, "Could not extract a valid depth pose. ts = " + depthPose.timestamp);
                // We need to set mIsProcessingOctomap to false before returning
                mIsProcessingOctomap.set(false);
                return;
            }

            // Incorporate the current point cloud to the octomap.
            long start_time = System.currentTimeMillis();
            addPointCloudToOctomap(cloudData, depthPose);
            Log.v(TAG, "Point cloud processing time: " +
                    (double) (System.currentTimeMillis() - start_time) / 1000.0);

            if (!mOctomapIsEmpty) {
                updateCostmapGrid(depthPose);
            }
            // Octomap is  not  processing anymore
            mIsProcessingOctomap.set(false);
        }
    };

    /**
     * Class constructor
     *
     * @param pointCloudManager point cloud manager used to store them and use them thread safe
     * @param resolution size of the voxel (in meters)
     */
    TangoOctoMapCostMap(TangoPointCloudManager pointCloudManager, double resolution) {
        super(resolution);
        HandlerThread handlerThread = new HandlerThread("octomapUpdater");
        handlerThread.start();
        mExecutor = Executors.newFixedThreadPool(1);
        mPointCloudManager = pointCloudManager;
    }

    /**
     * Function called to process a new point cloud. It should be called after
     * adding a new point cloud is added to pointCloudManager
     */
    private void updateWithLatestPointCloud() {
        boolean allDone = true;
        for (Future<?> previousFuture : mExecutorFutures) {
            allDone &= previousFuture.isDone(); // check if future is done
        }

        if (allDone) {
            mExecutorFutures.clear();
            Future<?> future = mExecutor.submit(mUpdateWithPointCloudTask);
            mExecutorFutures.add(future);
        } else {
            mPointCloudManager.getLatestPointCloud();
            Log.e(TAG, "Trying to update octomap when it is still processing. Skip updating octomap.");
        }
    }

    /**
     * Get octomap resolution or voxel size
     *
     * @return octomap resolution
     */
    public double getOctomapResolution() {
        return OCTOMAP_RESOLUTION;
    }

    /**
     * Get current position from Tango with the configuration needed
     *
     * @return position from tango device
     */
    private TangoPoseData getTangoPosition() {
        TangoPoseData pose;
        try {
            pose = TangoSupport.getPoseAtTime(0.0,
                    TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                    TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                    TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                    TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                    TangoSupport.ROTATION_IGNORED);
        } catch (TangoErrorException e) {
            Log.i(TAG, "Ignore transform", e);
            return null;
        }
        if (pose.statusCode != TangoPoseData.POSE_VALID) {
            Log.w(TAG, "Could not extract a valid pose. ts = " + pose.timestamp);
            mIsProcessingOctomap.set(false);
            return null;
        }
        return pose;
    }

    /**
     * Stops octomap changing the atomic boolean mUpdateOctomap
     */
    public void stopOctomap() {
        mUpdateOctomap.set(false);
    }

    public synchronized void init(float deviceHeight) {
        mOctomapIsEmpty = true;
        initNativeCustomSettings(OCTOMAP_RESOLUTION, OCTOMAP_MIN_DISTANCE, OCTOMAP_MAX_DISTANCE,
                OCTOMAP_PROB_HIT, OCTOMAP_PROB_MISS, OCTOMAP_CLAMPING_MIN, OCTOMAP_CLAMPING_MAX,
                POINT_CLOUD_CONFIDENCE, deviceHeight, MAX_HEIGHT_FROM_DEVICE,
                MAX_HEIGHT_FROM_FLOOR);
        // Sets atomic boolean to true. Enables updateWithLatestPointCloud().
        mUpdateOctomap.set(true);

        mOctomapGridCostmapGenerator = new OctomapGridCostmapGenerator(
                GRID_LENGTH_X, GRID_LENGTH_Y, GRID_CELL_SIZE);
    }

    /**
     * Remove Octomap from memory only when updateWithLatestPointCloud() is not executing
     */
    public void delete() {
        // Sets atomic boolean to false. Disables updateWithLatestPointCloud().
        Thread waitForStopOctomap = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (TangoOctoMapCostMap.this) {
                    if (!mIsProcessingOctomap.get()) {
                        stopOctomap();
                        //deleteNative();
                        mExecutor.shutdown();
                        try {
                            mExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Exception trying to shutdown octomap update executor", e);
                        }
                        mOctomapIsEmpty = true;
                    }
                }
            }
        });
        waitForStopOctomap.start();
    }

    /**
     * Clears octomap tree.
     */
    private synchronized void clearOctomap() {
        mOctomapIsEmpty = true;
        clearMapNative();
    }

    /**
     * Adds safely a point cloud to Octomap from a given position. It checks that Octomap is being
     * updated and synchronize native access method.
     *
     * @param tangoPointCloud point cloud to be added
     * @param pointCloudPose position of the point cloud
     */
    private void addPointCloudToOctomap(TangoPointCloudData tangoPointCloud,
            TangoPoseData pointCloudPose) {
        // Check null arguments and don't process data if Octomap is stopped
        if (tangoPointCloud == null || pointCloudPose == null || !mUpdateOctomap.get()) {
            return;
        }
        synchronized (TangoOctoMapCostMap.this) {
            addPointCloudNative(tangoPointCloud, pointCloudPose);
            mOctomapIsEmpty = false;
        }
    }

    /**
     * Export a .ot file with all the octomap information
     *
     * @param exportPath file where .ot should be stored
     * @return 1 if fails
     */
    private synchronized int exportMapToFile(String exportPath) {
        synchronized (TangoOctoMapCostMap.this) {
            if (!mOctomapIsEmpty) {
                return exportMapNative(exportPath);
            }
        }
        return 1;
    }

    /**
     * Updates the costmap grid from Octomap.
     *
     * @param pose position of the robot
     * @return true if operation was successful
     */
    private synchronized boolean updateCostmapGrid(TangoPoseData pose) {
        if (mOctomapIsEmpty && pose != null && mOctomapGridCostmapGenerator.updateCostMap(pose)) {
            setGrid(mOctomapGridCostmapGenerator.getBuffer(), getGridCellsX(), getGridCellsY(),
                    getGridStartX(), getGridStartY());
            setValid(true);
            onCostMapUpdate();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Gets compressed Octomap as a binary.
     *
     * @return byte array with octomap data, null if octomap is empty.
     */
    private synchronized byte[] getOctomapBinary() {
        if (!mOctomapIsEmpty) {
            return getOctomapBinaryNative();
        }
        return null;
    }

    /**
     * Gets full Octomap not as a binary.
     *
     * @return byte array with octomap data, null if octomap is empty.
     */
    private byte[] getOctomapFull() {
        synchronized (TangoOctoMapCostMap.this) {
            if (!mOctomapIsEmpty) {
                return getOctomapFullNative();
            }
        }
        return null;
    }

    /**
     * Casts a ray in Octomap from from the current position of the robot and find the position of
     * the closest object in map frame.
     *
     * @param  endPose position of the closest object.
     * @return true if octomap is not empty.
     */
    private boolean castRay(TangoPoseData endPose) {
        if (!mOctomapIsEmpty) {
            TangoPoseData pose = getTangoPosition();
            if (pose != null) {
                synchronized (TangoOctoMapCostMap.this) {
                    return castRayNative(pose, endPose);
                }
            }
        }
        return false;
    }

    /**
     * Casts a ray in Octomap from from the current position of the robot and returns the distance.
     *
     * @return distance from the closest object, -1 if no object is found.
     */
    private double castRayDistance() {
        if (!mOctomapIsEmpty) {
            TangoPoseData pose = getTangoPosition();
            if (pose != null) {
                synchronized (TangoOctoMapCostMap.this) {
                    return castRayDistanceNative(pose);
                }
            }
        }
        return -1.0;
    }

    /**
     * Cast several rays in Octomap from from the current position of the robot in different angles
     *
     * @param anglesArray angles to be used from +PI to -PI
     * @param distancesList array on which the distances will be stored, -1 if for that angle no
     *                      collision was detected.
     * @return true if octomap is not empty
     */
    private boolean castRaysDistances(float[] anglesArray, ArrayList<Float> distancesList) {
        if (!mOctomapIsEmpty) {
            TangoPoseData pose = getTangoPosition();
            if (pose != null) {
                synchronized (TangoOctoMapCostMap.this) {
                    return castRaysDistancesNative(pose, anglesArray, distancesList);
                }
            }
        }
        return false;
    }

    /**
     * Initialize native Octomap with default values
     */
    private native void initNative();

    /**
     * Initialize Octomap using non default values
     *
     * @param resolution voxel size in Octomap
     * @param min_dis minimal distance for the points in the pointcloud
     * @param max_dis maximal distance for the points in the pointcloud
     * @param prob_hit probability of the point to be a true hit
     * @param prob_miss probability of the point to be a miss
     * @param clamping_min minimal value of probability for a node in octomap
     * @param clamping_max maximal value of probability for a node in octomap
     * @param point_cloud_confidence confidence required for a point to be added in octomap
     * @param device_height height of the device from the floor
     * @param max_height_from_device maximal point height from the device to be added
     * @param min_height_from_device minimal point height from the device to be added
     */
    private native void initNativeCustomSettings(float resolution, float min_dis, float max_dis,
            float prob_hit, float prob_miss,
            float clamping_min, float clamping_max,
            float point_cloud_confidence, float device_height,
            float max_height_from_device,
            float min_height_from_device);

    /**
     * Native delete Octomap object from memory
     */
    private native void deleteNative();

    /**
     * Native clear Octomap
     */
    private native void clearMapNative();

    /**
     * Native export octomap binary to a given path
     *
     * @param exportPath path to which export the file
     */
    private native int exportMapNative(String exportPath);

    /**
     * Native add point cloud to Octomap
     *
     * @param tangoPointCloud Tango point cloud to be added
     */
    private native void addPointCloudNative(TangoPointCloudData tangoPointCloud,
            TangoPoseData pointCloudPose);

    /**
     * Add a false point cloud that indicates a bumper collision in octomap
     *
     * @param tangoPointCloud Tango point cloud to be added
     * @param pointCloudPose Pose of the sensor
     */
    // TODO(playerone) we don't need this function since we are putting the bumper into BumperCostMap.
    private native void addBumperNative(TangoPointCloudData tangoPointCloud,
            TangoPoseData pointCloudPose);

    /**
     * Cast ray into Octomap representation, get the closest occupied voxel if it exists
     *
     * @param startPose Pose of the position from where the ray is casted and its orientation
     * @param endPose Position of the closest occupied voxel
     * @return boolean true if something is found, false otherwise or if the space is not discovered
     */
    private native boolean castRayNative(TangoPoseData startPose, TangoPoseData endPose);

    /**
     * Cast ray into Octomap representation and get the distance to the closer object
     *
     * @param startPose Pose of the position from where the ray is casted and its orientation
     * @return distance to the closest voxel on that direction, -1 if nothing is found
     */
    private native double castRayDistanceNative(TangoPoseData startPose);

    /**
     * Cast rays with the given angles into Octomap representation and get the distance to the closer
     * object
     *
     * @param startPose Pose of the position from where the ray is casted and its orientation
     * @return true if it found an obstacle for any of the rays, false otherwise
     */
    private native boolean castRaysDistancesNative(TangoPoseData startPose, float[] anglesArray,
            ArrayList<Float> distancesList);

    /**
     * Get Octomap compressed binary data as byte array
     *
     * @return compressed binary data as byte array
     */
    private native byte[] getOctomapBinaryNative();

    /**
     * Get Octomap full no binary data as jbyteArray
     *
     * @return full no binary data as jbyteArray
     */
    private native byte[] getOctomapFullNative();

    /**
     * Returns the elements number of the grid
     *
     * @return he elements number of the grid
     */
    public int getDistanceGridSize() {
        return mOctomapGridCostmapGenerator.getGridSize();
    }

    /**
     * Returns length of the grin in X
     *
     * @return length of the grin in X (in meters)
     */
    public double getGridLengthX() {
        return mOctomapGridCostmapGenerator.getGridLengthX();
    }

    /**
     * Returns length of the grin in Y
     *
     * @return length of the grin in Y (in meters)
     */
    public double getGridLengthY() {
        return mOctomapGridCostmapGenerator.getGridLengthY();
    }

    /**
     * Returns the cell size of the grid
     *
     * @return cell size of the grid
     */
    public double getGridCellSize() {
        return mOctomapGridCostmapGenerator.getCellSize();
    }

    /**
     * Returns the number of elements in X of the grid
     *
     * @return number of elements in X of the grid
     */
    public int getGridCellsX() {
        return (int) Math.floor(getGridLengthX() / getGridCellSize());
    }

    /**
     * Returns the number of elements in Y of the grid
     *
     * @return number of elements in Y of the grid
     */
    public int getGridCellsY() {
        return (int) Math.floor(getGridLengthY() / getGridCellSize());
    }

    /**
     * Returns the start position of the grid in X
     *
     * @return start position of the grid in X
     */
    public int getGridStartX() {
        return (int) Math.floor(mOctomapGridCostmapGenerator.getLastRobotPosition().translation[0] /
                getGridCellSize() - getGridCellsX()/2);
    }

    /**
     * Returns the start position of the grid in Y
     *
     * @return start position of the grid in Y
     */
    public int getGridStartY() {
        return (int) Math.floor(mOctomapGridCostmapGenerator.getLastRobotPosition().translation[0] /
                getGridCellSize()- getGridCellsY()/2);
    }


    // Methods of the OccupancyGridGenerator interface for ROS (disabled)
/*
    *//**
     * Fills the header message
     *
     * @param header Header message
     *//*
    @Override
    public void fillHeader(Header header) {
        // Setting the map in the center of the map frame ("/map")
        header.setFrameId("/map");
        header.setStamp(Time.fromMillis(System.currentTimeMillis()));
    }

    *//**
     * Fills with the MapMetaData of the map
     *
     * @param information MetaData for the map
     *//*
    @Override
    public void fillInformation(MapMetaData information) {
        information.setMapLoadTime(Time.fromMillis(System.currentTimeMillis()));
        // Set origin to the center of the map
        Pose origin = information.getOrigin();
        // Get the position of the robot
        TangoPoseData pose = null;
        if (!mOctomapIsEmpty) {
            pose = getTangoPosition();
        }
        if (pose != null) {
            // Sets the map into the robot, respect to the map frame
            origin.getPosition().setX(-1 * GRID_LENGTH_X / 2 + pose.translation[0]);
            origin.getPosition().setY(-1 * GRID_LENGTH_Y / 2 + pose.translation[1]);
            origin.getPosition().setZ(pose.translation[2]);
        }
        // Does not rotate the map
        Quaternion.identity().toQuaternionMessage(origin.getOrientation());
        // Fill other ROS message information
        information.setWidth((int) (GRID_LENGTH_X / GRID_CELL_SIZE));     // [cell]
        information.setHeight((int) (GRID_LENGTH_Y / GRID_CELL_SIZE));    // [cell]
        information.setResolution((float) GRID_CELL_SIZE);              // [m/cell]
    }

    *//**
     * Generates the message to be published with ROS
     *
     * @return ChannelBuffer
     *//*
    @Override
    public ChannelBuffer generateChannelBufferData() {
        ChannelBufferOutputStream output = new ChannelBufferOutputStream(
                MessageBuffers.dynamicBuffer());
        try {
            // Write into the buffer the ROS data
            synchronized (OctoMap.this) {
                output.write(mOctomapGridCostmapGenerator.getBuffer());
            }
        } catch (Exception e) {
            throw new RuntimeException("Empty map generator generateChannelBufferData() error: "
                    + e.getMessage());
        }
        return output.buffer();
    }*/

}
