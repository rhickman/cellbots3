package ai.cellbots.robot.navigation;

import android.util.Log;

import ai.cellbots.common.Geometry;
import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.Transform;
import ai.cellbots.robot.manager.SoundManager;
import ai.cellbots.robot.state.RobotSessionGlobals;
import ai.cellbots.robot.vision.PointCloud;

/**
 * Safety controller using point cloud.
 *
 * For the given point cloud, counts the number of points in the frontal view. If the number of
 * points in the view is more than NUMBER_OF_BLOCKERS, claim to be blocked.
 * Also put them into bins, so any external components, such as local planner, can utilize it.
 */
public class PointCloudSafetyController implements ThreadedShutdown {
    private static final String TAG = PointCloudSafetyController.class.getSimpleName();
    private static final int MIN_POINTS = 10;
    private static final int NUMBER_OF_BLOCKERS = 100;
    private static final double SIDE_OUTWARD_PADDING = 0;
    private static final double FRONT_OUTWARD_PADDING = 0.75;
    // How far above the floor to start checking for point cloud stopping, in meters.
    private static final double VERTICAL_BUFFER = 0.25;
    private static final double BIN_DISTANCE = 0.1;
    private static final double MAX_BIN_DISTANCE = 1.5;

    private static final float COLOR_BLACK = Float.intBitsToFloat(0x000000);
    private static final float COLOR_RED = Float.intBitsToFloat(0xFF0000);
    private static final float COLOR_GREEN = Float.intBitsToFloat(0x00FF00);
    private static final float COLOR_BLUE = Float.intBitsToFloat(0x0000FF);
    private static final float COLOR_YELLOW = Float.intBitsToFloat(0xFFFF00);

    private final RobotSessionGlobals mSession;
    private boolean mIsBlocked;
    private int[] mBinData;
    private Transform mTransform;
    private final SoundManager mSoundManager;
    private SoundManager.Sound mBlockSound = null;

    /**
     * Starts the PointCloudSafetyController
     *
     * @param session  The session.
     */
    public PointCloudSafetyController(RobotSessionGlobals session, SoundManager soundManager) {
        mIsBlocked = false;
        mBinData = new int[(int) Math.ceil(MAX_BIN_DISTANCE / BIN_DISTANCE)];
        mSession = session;
        mSoundManager = soundManager;
    }

    /**
     * Called for a new set of point cloud.
     *
     * @param pointCloudData A new point cloud.
     * @param pointColors    Output point colors. Mostly for visualization.
     * @param depthLocation  The Transform depth location.
     */
    public void onPointCloud(PointCloud pointCloudData,
            float[] pointColors, Transform depthLocation) {
        Transform baseLocation = mTransform;
        if (pointCloudData == null) {
            Log.w(TAG, "point cloud is null");
            return;
        }
        if (baseLocation == null) {
            Log.w(TAG, "No base transform point");
            return;
        }
        if (pointCloudData.getPointCount() < MIN_POINTS) {
            Log.v(TAG, "Refusing to use point cloud with only " + pointCloudData.getPointCount());
            return;
        }
        if (pointCloudData.getTimestamp() < 0.0D) {
            Log.w(TAG, "Refusing to use point cloud with incorrect time stamp (" +
                    pointCloudData.getTimestamp() + ")");
            return;
        }

        double[] point = new double[3];
        double[] rotation = {1.0, 0, 0, 0};

        // Check the points between lower limit and upper limit to see if the robot is blocked.
        double lowerLimit = new Transform(depthLocation).getPosition(2) -
                (mSession.getRobotModel().getDeviceZ() - VERTICAL_BUFFER);
        double upperLimit = mSession.getRobotModel().getHeight() + lowerLimit;

        double[] forwards = {+Math.cos(baseLocation.getRotationZ()),
                +Math.sin(baseLocation.getRotationZ())};
        double[] sideways = {-Math.sin(baseLocation.getRotationZ()),
                +Math.cos(baseLocation.getRotationZ())};
        double robotWidth = mSession.getRobotModel().getWidth();
        double robotHalfLength = mSession.getRobotModel().getLength() / 2;
        double[] off = {0, 0};

        int blockers = 0;

        Log.d(TAG, "Running depth on " + pointCloudData.getPointCount() + " points");

        int[] bins = new int[(int) Math.ceil(MAX_BIN_DISTANCE / BIN_DISTANCE) + 1];

        for (int i = 0; i < pointCloudData.getPointCount(); i++) {
            pointColors[i] = COLOR_BLACK;
        }

        for (int i = 0; i < pointCloudData.getPointCount(); i += 10) {
            pointCloudData.getPoint(i, point);
            Transform rw = new Transform(depthLocation, new Transform(point, rotation, 0));
            if ((rw.getPosition(2) > lowerLimit) && (rw.getPosition(2) < upperLimit)) {
                off[0] = rw.getPosition(0) - baseLocation.getPosition(0);
                off[1] = rw.getPosition(1) - baseLocation.getPosition(1);
                try {
                    double side = Geometry.dotProduct(off, sideways);
                    if (Math.abs(side) <
                            (robotWidth / 2) + SIDE_OUTWARD_PADDING) {
                        double front = Geometry.dotProduct(off, forwards);
                        if (front > robotHalfLength && front < MAX_BIN_DISTANCE + robotHalfLength) {
                            bins[(int) Math.round((front - robotHalfLength) / BIN_DISTANCE)]++;
                        }
                        if (front < (robotHalfLength + FRONT_OUTWARD_PADDING) && front > 0) {
                            blockers++;
                            pointColors[i] = COLOR_RED;
                        } else {
                            pointColors[i] = COLOR_YELLOW;
                        }
                    } else {
                        pointColors[i] = COLOR_GREEN;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Dimension mismatch exception in Geometry.dotProduct()", e);
                    // return point cloud blocked
                    synchronized (this) {
                        mBinData = bins;
                        mIsBlocked = true;
                        return;
                    }
                }
            } else {
                pointColors[i] = COLOR_BLUE;
            }
        }

        synchronized (this) {
            if (mBlockSound != null && !mBlockSound.isPlaying()) {
                // It was previously playing but done.
                mBlockSound = null;
            }
            mBinData = bins;
            if (blockers > NUMBER_OF_BLOCKERS) {
                mIsBlocked = true;
                if (mSoundManager != null && (mBlockSound == null || !mBlockSound.isPlaying())) {
                    mBlockSound = mSoundManager.playCommandSound(SoundManager.CommandSound.STOP);
                }
                Log.i(TAG, "Blockers: " + blockers + " status blocked");
            } else {
                Log.v(TAG, "Blockers: " + blockers + " status free");
                mIsBlocked = false;
            }
        }
    }

    /**
     * Sets the base transform.
     *
     * @param transform The transform.
     */
    public void setTransform(Transform transform) {
        mTransform = transform;
    }

    /**
     * Checks if the robot is blocked by the TangoPointCloudData
     *
     * @return True if the robot is blocked
     */
    public synchronized boolean isBlocked() {
        return mIsBlocked;
    }

    /**
     * Gets the status of the bins.
     *
     * @return The bins array.
     */
    public synchronized int[] getBinData() {
        return mBinData.clone();
    }

    /**
     * Gets the number of points that triggers stopping.
     *
     * @return The number of blocking points.
     */
    public static int getNumberOfBlockers() { return NUMBER_OF_BLOCKERS; }

    /**
     * Gets the size of each bin in meters.
     *
     * @return The size of the bin. In meters.
     */
    public static double getBinDistance() { return BIN_DISTANCE; }

    /**
     * Shuts down the point cloud safety controller.
     */
    @Override
    public void shutdown() {
        // Do nothing.
    }

    /**
     * Waits for the point cloud safety controller to finish shutting down.
     */
    @Override
    public void waitShutdown() {
        // Do nothing.
    }
}
