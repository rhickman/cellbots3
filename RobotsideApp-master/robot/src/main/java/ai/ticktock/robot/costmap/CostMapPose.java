package ai.cellbots.robot.costmap;

import android.support.annotation.NonNull;

import ai.cellbots.common.Transform;

/**
 * CostMapPose interface. Provides information about a pose on the costmap. To be used by local
 * and global planner.
 */
public class CostMapPose {
    private final int mX;
    private final int mY;

    /**
     * Convert to a point at a given resolution
     * @param resolution The resolution.
     * @param z The z position.
     * @param rotation The quaternion rotation.
     * @param timestamp The transform timestamp.
     * @return The transform.
     */
    public Transform toWorldCoordinates(double resolution, double z, double[] rotation, double timestamp) {
        return new Transform(
                new double[]{(getX() + 0.5) * resolution, (getY() + 0.5) * resolution, z},
                rotation, timestamp);
    }

    /**
     * Convert to a point at a given resolution
     * @param resolution The resolution.
     * @return The transform.
     */
    public Transform toWorldCoordinates(double resolution) {
        return toWorldCoordinates(resolution, 0, new double[]{0, 0, 0, 1}, 0);
    }

    /**
     * Class constructor.
     *
     * @param x  The x coordinate.
     * @param y  The y coordinate.
     */
    public CostMapPose(int x, int y) {
        mX = x;
        mY = y;
    }

    /**
     * Adds an offset to a coordinate in the x and y axis.
     *
     * @param horizontalOffset to add to in the x axis.
     * @param verticalOffset   to add to in the y axis.
     * @return offset coordinate.
     */
    public CostMapPose offsetBy(int horizontalOffset, int verticalOffset) {
        return new CostMapPose(mX + horizontalOffset, mY + verticalOffset);
    }

    /**
     * Adds an offset to a coordinate in the x axis.
     *
     * @param horizontalOffset to add.
     * @return offset coordinate.
     */
    @SuppressWarnings("unused")
    public CostMapPose horizontalOffsetBy(int horizontalOffset) {
        return offsetBy(horizontalOffset, 0);
    }

    /**
     * Adds an offset to a coordinate in the y axis.
     *
     * @param verticalOffset to add.
     * @return offset coordinate.
     */
    @SuppressWarnings("unused")
    public CostMapPose verticalOffsetBy(int verticalOffset) {
        return offsetBy(0, verticalOffset);
    }

    /**
     * Calculates the distance to another pose in the x axis.
     *
     * @param target A GridCostMapCoordinate to calculate the horizontal distance to.
     * @return distance in the x axis.
     */
    public int horizontalDistanceTo(@NonNull CostMapPose target) {
        return target.getX() - mX;
    }

    /**
     * Calculates the distance to another pose in the y axis.
     *
     * @param target A GridCostMapPose to calculate the horizontal distance to.
     * @return distance in the y axis.
     */
    public int verticalDistanceTo(@NonNull CostMapPose target) {
        return target.getY() - mY;
    }

    /**
     * Calculates the squared distance to get to another pose.
     *
     * @param target A GridCostMapPose to calculate the squared distance to.
     * @return the squared distance.
     */
    public double squaredDistanceTo(@NonNull CostMapPose target) {
        int dx = horizontalDistanceTo(target);
        int dy = verticalDistanceTo(target);
        return dx * dx + dy * dy;
    }

    /**
     * Makes a copy of a GridCostMapPose.
     *
     * @return copy of the GridCostMapPose.
     */
    public CostMapPose copy() {
        return new CostMapPose(mX, mY);
    }

    /**
     * Gets the x coordinate of a GridCostMapPose.
     *
     * @return x coordinate.
     */
    public int getX() {
        return mX;
    }

    /**
     * Gets the y coordinate of a GridCostMapPose.
     *
     * @return y coordinate.
     */
    public int getY() {
        return mY;
    }

    @Override
    public String toString() {
        return ("[ " + mX + ", " + mY + "]");
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CostMapPose)) {
            return false;
        }

        CostMapPose target = (CostMapPose) obj;
        return target.getX() == mX && target.getY() == mY;
    }

    // HashCode method. This implementation was made following the guide to make a canonical
    // hashCode method in:
    // http://web.archive.org/web/20150207205800/https://developer.android.com/reference/java/lang/
    // Object.html
    @Override
    public int hashCode() {
        // Start with a non-zero constant.
        int code = 17;

        // Include a hash for each field.
        code = 31 * code + mX;
        code = 31 * code + mY;

        return code;
    }
}
