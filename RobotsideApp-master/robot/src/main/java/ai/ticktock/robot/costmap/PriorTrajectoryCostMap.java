package ai.cellbots.robot.costmap;

import android.util.Log;

import java.util.List;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.Transform;

/**
 * Generates a static grid CostMap from a detailed world's prior trajectories.
 */
public class PriorTrajectoryCostMap extends GridCostMap {
    private static final String TAG = PriorTrajectoryCostMap.class.getSimpleName();
    // Radius of the circle by which a path node is grown in the CostMap
    private final static double PATH_GROWING_RADIUS = 0.25;
    // Half the radius of the circle by which a path node is grown in the CostMap
    private final static double HALF_PATH_GROWING_RADIUS = PATH_GROWING_RADIUS / 2;

    // Padding added to the CostMap. In meters.
    private final static double DEFAULT_X_PADDING = 3.0;
    private final static double DEFAULT_Y_PADDING = 3.0;

    // The MAX_COST should not be used. If the robot is in the background region, it will be
    // stuck there forever instead of finding a good path.
    public final static byte BACKGROUND_COST = MAX_FREE_COST / 6;
    public final static byte MIDDLE_COST = BACKGROUND_COST / 2;

    /**
     * Creates the PriorTrajectoryCostMap.
     *
     * @param initialWorld The initial world.
     * @param resolution   The resolution of the CostMap, being the width of a square in meters.
     * @param xPadding     The padding in meters to extend lower and upper x limits.
     * @param yPadding     The padding in meters to extend lower and upper y limits.
     */
    public PriorTrajectoryCostMap(DetailedWorld initialWorld, double resolution, double xPadding,
                                  double yPadding) {
        super(Source.PRIOR_TRAJECTORY, resolution);
        Log.i(TAG, "Creating PriorTrajectoryCostMap");
        setValid(false);
        onCostMapUpdate();
        if (initialWorld != null) {
            // Get prior (smoothed) trajectory from world and its boundaries.
            if (initialWorld.getCustomTransformCount() == 0) {
                List<Transform> priorTrajectory = initialWorld.getSmoothedPath();
                double[][] mapBoundaries = initialWorld.getSmoothedPathLimits();
                gridFrom(priorTrajectory, mapBoundaries, xPadding, yPadding);
            } else {
                List<Transform> priorTrajectory = initialWorld.getCustomTransforms();
                double[][] mapBoundaries = initialWorld.getCustomTransformsLimits();
                gridFrom(priorTrajectory, mapBoundaries, xPadding, yPadding);
            }
            setValid(true);
            onCostMapUpdate();
        }
        Log.i(TAG, "Created a prior trajectory successfully. " + toString());
    }

    /**
    * Get if the CostMap requires inflation.
    *
    * @return True if the CostMap requires inflation.
    */
    public boolean requiresInflation() {
        return false;
    }

    /**
     * Creates the PriorTrajectoryCostMap with padding values by default.
     *
     * @param initialWorld The initial world.
     * @param resolution   The resolution of the CostMap, being the width of a square in meters.
     */
    public PriorTrajectoryCostMap(DetailedWorld initialWorld, double resolution) {
        this(initialWorld, resolution, DEFAULT_X_PADDING, DEFAULT_Y_PADDING);
    }

    /**
     * Creates the PriorTrajectoryCostMap.
     *
     * @param trajectory The prior trajectory as a list of transforms.
     * @param boundaries The boundaries for the new cost map in world coordinates.
     * @param resolution The resolution of the CostMap, being the width of a square in meters.
     * @param xPadding   The padding in meters to extend lower and upper x limits.
     * @param yPadding   The padding in meters to extend lower and upper y limits.
     */
    public PriorTrajectoryCostMap(List<Transform> trajectory, double[][] boundaries,
                                  double resolution, double xPadding, double yPadding) {
        super(Source.PRIOR_TRAJECTORY, resolution);
        gridFrom(trajectory, boundaries, xPadding, yPadding);
        setValid(true);
    }

    /**
     * Creates the PriorTrajectoryCostMap with padding values by default.
     *
     * @param trajectory The prior trajectory as a list of transforms.
     * @param boundaries The boundaries for the new cost map in world coordinates.
     * @param resolution The resolution of the CostMap, being the width of a square in meters.
     */
    public PriorTrajectoryCostMap(List<Transform> trajectory, double[][] boundaries,
                                  double resolution) {
        this(trajectory, boundaries, resolution, DEFAULT_X_PADDING, DEFAULT_Y_PADDING);
    }

    /**
     * Makes a CostMap grid from a trajectory, with a given bounding box.
     *
     * @param trajectory A list of transforms to make the cost map from, in world coordinates.
     * @param boundaries The boundaries to determine the grid limits from.
     * @param xPad       The x padding in meters.
     * @param yPad       The y padding in meters.
     */
    @SuppressWarnings("ConstantConditions")
    private void gridFrom(List<Transform> trajectory,
                          double[][] boundaries, double xPad, double yPad) {
        if (trajectory == null) {
            Log.w(TAG, "Null trajectory");
        }
        if (boundaries == null || boundaries[0] == null || boundaries[1] == null) {
            Log.wtf(TAG, "Null boundary");
        }
        if (xPad < 0 || yPad < 0) {
            Log.wtf(TAG, "Invalid padding, " + xPad + ", " + yPad);
        }
        // Make a grid for the region, with an initial value for costs other than 0.
        double lowerX = boundaries[0][0] - PATH_GROWING_RADIUS - xPad;
        double lowerY = boundaries[0][1] - PATH_GROWING_RADIUS - yPad;
        double upperX = boundaries[1][0] + PATH_GROWING_RADIUS + xPad;
        double upperY = boundaries[1][1] + PATH_GROWING_RADIUS + yPad;
        double[][] positions = new double[][]{{lowerX, lowerY}, {upperX, upperY}};
        // Create the grid.
        makeGrid(positions, BACKGROUND_COST);

        // Set the prior trajectory on the grid, growing the path nodes by a circle
        // around each node. Set the cost at middle cost for outer half of the circle
        // and minimum cost for the inner half of the circle. Avoid unnecessary operations
        // if rounding puts both halves of the circle at the same grid location.
        if ((int) Math.round(PATH_GROWING_RADIUS / getResolution()) !=
                (int) Math.round(HALF_PATH_GROWING_RADIUS / getResolution()))
        {
            setInflatedCosts(trajectory, PATH_GROWING_RADIUS, MIDDLE_COST);
        }
        setInflatedCosts(trajectory, HALF_PATH_GROWING_RADIUS, MIN_COST);
    }

    /**
     * Shuts down the CostMap.
     */
    @Override
    public void shutdown() {
        // Does nothing since there is no thread.
    }

    /**
     * Waits for the CostMap to shutdown.
     */
    @Override
    public void waitShutdown() {
        // Does nothing since there is no thread.
    }

    /**
     * Returns a string of the CostMap description.
     *
     * @return CostMap description string.
     */
    @Override
    public String toString() {
        return super.toString();
    }
}
