package ai.cellbots.robot.tango;

import static java.lang.Math.round;

import com.google.atap.tangoservice.TangoPoseData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * {@code GridCostmap} is a 2D representation of the closest cell of the robot. It contains a grid,
 * each cell has the information if there is an obstacle or not in that cell.
 *
 * The following diagram represents the robot position (marked with an X) in the grid.
 *
 * Grid Length X (m)
 * /-------------------------------------------/
 * ___0_1__2__3_.__.__.__._____________________ ___
 * 0 |__|__|__|__|__|__|__|__|__|__|__|__|__|__|  |
 * 1 |__|__|__|__|__|__|__|__|__|__|__|__|__|__|  |
 * 2 |__|__|__|__|__|__|__|__|__|__|__|__|__|__|  |
 * 3 |__|__|__|__|__|__|__|__|__|__|__|__|__|__|  |
 * 4 |__|__|__|__|__|__|__|__|__|__|__|__|__|__|  |
 * 5 |__|__|__|__|__|__|__|__|__|__|__|__|__|__|  |
 * 6 |__|__|__|__|__|__|__|__|__|__|__|__|__|__|  |
 * 7 |__|__|__|__|__|__|__|_X|__|__|__|__|__|__|  | Grid Length Y (m)
 * . |__|__|__|__|__|__|__|__|__|__|__|__|__|__|  |
 * . |__|__|__|__|__|__|__|__|__|__|__|__|__|__|  |
 * . |__|__|__|__|__|__|__|__|__|__|__|__|__|__|  |
 * . |__|__|__|__|__|__|__|__|__|__|__|__|__|__|  |
 * . |__|__|__|__|__|__|__|__|__|__|__|__|__|__|  |
 * . |__|__|__|__|__|__|__|__|__|__|__|__|__|__|  |
 * . |__|__|__|__|__|__|__|__|__|__|__|__|__|__| _|_
 *
 * . /--/
 * CellSize
 *
 * The cell size should match the cell size used on the Octomap.
 */

public class OctomapGridCostmapGenerator {
    private static final String TAG = OctomapGridCostmapGenerator.class.getSimpleName();

    private final ByteBuffer mBuffer;
    private final double mGridLengthX;    // Length on X of the grid
    private final double mGridLengthY;    // Length on Y of the grid
    private final double mCellSize;       // Cell size of the grid
    private TangoPoseData mLastRobotPosition = null;

    static {
        System.loadLibrary("octomap_grid_costmap_generator_jni");
    }

    /**
     * Class constructor
     *
     * @param lengthX length of the grin in X (in meters)
     * @param lengthY length of the grin in Y (in meters)
     * @param cellSize cell size in meters
     */
    OctomapGridCostmapGenerator(double lengthX, double lengthY, double cellSize) {
        mGridLengthX = lengthX;
        mGridLengthY = lengthY;
        mCellSize = cellSize;
        mBuffer = ByteBuffer.allocateDirect(getGridSize());
        mBuffer.order(ByteOrder.nativeOrder());
        initCostmapNative(mCellSize, mGridLengthX, mGridLengthY);
    }

    /**
     * Re-calculate the CostMap with the provided robot pose
     *
     * @param robotPose position of the robot when the grid was updated
     * @return if it was successful in updating the grid
     */
    public synchronized boolean updateCostMap(TangoPoseData robotPose) {
        boolean result;
        mBuffer.rewind();
        setLastRobotPosition(robotPose);
        result = updateCostmapNative(mBuffer, robotPose);
        return result;
    }

    /**
     * Gets costmap data as a byte array.
     *
     * @return buffer with serialized data.
     */
    public synchronized byte[] getBuffer() {
        byte[] mByteArray = new byte[mBuffer.capacity()];
        mBuffer.rewind();
        // For every cell...
        for (int col = 0; col < mGridLengthX / mCellSize; col++) {
            for (int row = 0; row < mGridLengthY / mCellSize; row++) {
                int arrayIndex = (col + (int) round(mGridLengthX / mCellSize) * row);
                mByteArray[arrayIndex] = mBuffer.get();
            }
        }
        return mByteArray;
    }

    /**
     * Sets robot position.
     *
     * @param robotPosition position of the robot.
     */
    private void setLastRobotPosition(TangoPoseData robotPosition) {
        mLastRobotPosition = robotPosition;
    }

    /**
     * Gets robot position.
     *
     * @return position of the robot.
     */
    public TangoPoseData getLastRobotPosition() {
        return mLastRobotPosition;
    }

    /**
     * Gets grid size.
     *
     * @return grid size.
     */
    public int getGridSize() {
        return (int) round(mGridLengthX / mCellSize * mGridLengthY / mCellSize);
    }

    /**
     * Gets grid length in X.
     *
     * @return grid length in X.
     */
    public double getGridLengthX() {
        return mGridLengthX;
    }

    /**
     * Gets grid length in Y.
     *
     * @return grid length in Y.
     */
    public double getGridLengthY() {
        return mGridLengthY;
    }

    /**
     * Gets cell size.
     *
     * @return cell size.
     */
    public double getCellSize() {
        return mCellSize;
    }

    /**
     * Updates 2D costmap grid.
     *
     * @param costmap grid buffer reference
     * @param startPose Pose of the robot
     * @return True if the grid is successfully updated.
     */
    private native boolean updateCostmapNative(ByteBuffer costmap, TangoPoseData startPose);

    /**
     * Initializes 2D costmap grid.
     *
     * @param voxelSize size of the cell
     * @param gridLengthX size in meters on X of the grid
     * @param gridLengthY size in meters on Y of the grid
     * @return True if the grid is successfully updated.
     */
    private native boolean initCostmapNative(double voxelSize,
            double gridLengthX, double gridLengthY);
}
