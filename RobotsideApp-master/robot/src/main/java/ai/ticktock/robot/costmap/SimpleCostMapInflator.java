package ai.cellbots.robot.costmap;

import android.util.Log;

import org.apache.commons.lang.math.NumberUtils;

import ai.cellbots.robot.state.RobotSessionGlobals;

/*
 * Alternatives to improve inflation process:
 *
 * 1) Create JNI to use Gaussian Blur: http://blog.ivank.net/fastest-gaussian-blur.html
 *    See that method #4 in the URL is faster than the others.
 *
 * 2) Use existent OpenCV gaussian filter in Java, Python or C++:
 *    https://docs.opencv.org/master/dc/dd3/tutorial_gausian_median_blur_bilateral_filter.html
 *    By counterpart, it requires an image as input.
 *
 *
 * Gaussian blur for inflating cost maps:
 *
 * Gaussian blur is an image processing method to smooth the contour of an image, but, this process
 * can be applied to a byte[][] data structure because, the contour, can be thought as the region
 * where the cost map has free space but is near an obstacle.
 */

/**
 * The simple CostMap inflator, expands the radius of a CostMap by the robot radius.
 */
public class SimpleCostMapInflator extends CostMapInflator {
    private static final String TAG = SimpleCostMapInflator.class.getSimpleName();

    private static final byte LETHAL_OBSTACLE = Byte.MAX_VALUE;
    private static final byte INSCRIBED_INFLATED_OBSTACLE = 120;

    /**
     * Creates the CostMapInflator
     *
     * @param resolution   The resolution of the CostMap, being the width of a square in meters.
     * @param robotRadius  The physical radius of the robot (in meters).
     * @param radiusFactor The factor by which the robot radius is multiplied.
     */
    public SimpleCostMapInflator(double resolution, double robotRadius, double radiusFactor) {
        super(resolution, robotRadius, radiusFactor);
    }

    /**
     * Inflates a CostMap by the robot radius.
     *
     * @param costMap The CostMap.
     * @return Inflated CostMap.
     */
    @Override
    public byte[] inflateCostMapFullRadius(CostMap costMap) {
        double robotRadius = getRobotRadius();
        return inflateCostMap(costMap, robotRadius);
    }

    /**
     * Inflates a CostMap by a factor of the robot radius.
     *
     * @param costMap The CostMap.
     * @return Inflated CostMap.
     */
    @Override
    public byte[] inflateCostMapFactorRadius(CostMap costMap) {
        double robotRadius = getRobotRadius();
        double radiusFactor = getRadiusFactor();
        return inflateCostMap(costMap, robotRadius * radiusFactor);
    }

    /**
     * Inflates a CostMap by mutating the data.
     *
     * @param costMap                 The CostMap.
     * @param inflationRadiusInMeters The radius by which the CostMap is inflated.
     * @return The inflated cost map data.
     */
    private byte[] inflateCostMap(CostMap costMap, double inflationRadiusInMeters) {
        int inflationRadiusInCells = (int) Math.ceil(inflationRadiusInMeters / getResolution());
        Log.i(TAG, "Inflating map with radius " + inflationRadiusInMeters + " meters, equivalent to " +
                        inflationRadiusInCells + " cells, CostMap resolution " + getResolution());
        // CostMap limits
        int costMapXLowerLimit = costMap.getLowerXLimit();
        int costMapXUpperLimit = costMap.getUpperXLimit();
        int costMapYLowerLimit = costMap.getLowerYLimit();
        int costMapYUpperLimit = costMap.getUpperYLimit();
        // CostMap Width
        int costMapWidth = costMapXUpperLimit - costMapXLowerLimit;
        // Copy entire cost map data - this will be the cost map data to be returned
        byte[] costMapCopy = costMap.getFullCostRegion();

        if (inflationRadiusInCells <= 0) {
            Log.i(TAG, "Inscribed radius is less than or equal to zero, so we ignore");
            return costMapCopy;
        }

        // Go through all cost map cells.
        for (int x = costMapXLowerLimit; x < costMapXUpperLimit; x++) {
            for (int y = costMapYLowerLimit; y < costMapYUpperLimit; y++) {
                // If it's an obstacle, don't modify it.
                if (CostMap.isObstacle(costMap.getCost(x, y))) {
                    continue;
                }
                // Take the neighbor limits of each cell.
                int lowerXLimit = x - inflationRadiusInCells;
                int lowerYLimit = y - inflationRadiusInCells;
                int upperXLimit = x + inflationRadiusInCells;
                int upperYLimit = y + inflationRadiusInCells;
                // Find the neighbor with higher cost.
                byte higherCost = costMap.getHighestCostInRegion(
                        // If any neighbor is outside the cost map, it will be omitted.
                        lowerXLimit < costMapXLowerLimit ? costMapXLowerLimit : lowerXLimit,
                        lowerYLimit < costMapYLowerLimit ? costMapYLowerLimit : lowerYLimit,
                        upperXLimit > costMapXUpperLimit ? costMapXUpperLimit : upperXLimit,
                        upperYLimit > costMapYUpperLimit ? costMapYUpperLimit : upperYLimit);
                // Update the cost based on the neighbor with highest cost.
                costMapCopy[(y - costMapYLowerLimit) * costMapWidth + x - costMapXLowerLimit]
                        = computeInflatedCost(higherCost);
            }
        }
        return costMapCopy;
    }

    /**
     * Computes the new cost of the cell given the maximum cost of its neighbors.
     *
     * @param cost Maximum cost of its neighbors
     * @return Updated cost value of the cell
     */
    private byte computeInflatedCost(byte cost) {
        if (cost == Byte.MAX_VALUE) {
            // It's an obstacle
            return LETHAL_OBSTACLE;
        }
        // TODO (playerfive): Maybe put a parameter modifying inscribedRadius
        else if (cost >= INSCRIBED_INFLATED_OBSTACLE) {
            return INSCRIBED_INFLATED_OBSTACLE;
        }
        else {
            // Proportional relationship with the cost
            return cost;
        }
    }
}
