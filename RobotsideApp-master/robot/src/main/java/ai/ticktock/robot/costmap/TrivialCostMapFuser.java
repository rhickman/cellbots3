package ai.cellbots.robot.costmap;

import android.util.Log;

import java.util.Collection;

import ai.cellbots.robot.state.RobotSessionGlobals;

/**
 * A simple CostMapFuser which merges all the values with 1.0 weights. The following assumptions are
 * made:
 * --- Input cost maps have all the same resolution.
 */
public class TrivialCostMapFuser extends CostMapFuser {
    private static final String TAG = CostMapFuser.class.getSimpleName();
    double mResolution;

    /**
     * Creates the CostMapFuser
     *
     * @param session    The session variables.
     * @param resolution The resolution data.
     */
    public TrivialCostMapFuser(RobotSessionGlobals session, double resolution) {
        super(session, resolution);
        mResolution = resolution;
    }

    /**
     * Fuses together CostMaps by mutating the data provided.
     *
     * @param costMaps The CostMaps to be fused.
     * @param source   The source to set to the fused CostMap.
     */
    @Override
    public CostMap fuseCostMaps(Collection<CostMap> costMaps, CostMap.Source source) {
        Log.v(TAG, "CostMap fusing initiated");
        // Find x and y lower and upper limits for the merged cost map, based on the input cost
        // maps parameters.
        int[][] costMapLimits = findGridLimits(costMaps);
        int xLowerLimit = costMapLimits[0][0];
        int yLowerLimit = costMapLimits[1][0];
        int xUpperLimit = costMapLimits[0][1];
        int yUpperLimit = costMapLimits[1][1];

        // Make a new grid, filling the cells with data from the input cost maps.
        int width = xUpperLimit - xLowerLimit;
        int height = yUpperLimit - yLowerLimit;
        byte[] costMapData = new byte[width * height];

        for (CostMap costMap : costMaps) {
            for (int x = costMap.getLowerXLimit(); x < costMap.getUpperXLimit(); x++) {
                for (int y = costMap.getLowerYLimit(); y < costMap.getUpperYLimit(); y++) {
                    byte cost = costMapData[(y - yLowerLimit) * width + x - xLowerLimit];
                    byte addCost = costMap.getCost(x, y);
                    if (CostMap.isObstacle(cost) || CostMap.isObstacle(addCost)) {
                        costMapData[(y - yLowerLimit) * width + x - xLowerLimit] = CostMap.OBSTACLE_COST;
                    } else {
                        int sum = ((int)cost & 0xFF) + ((int)addCost & 0xFF);
                        sum = Math.max(CostMap.MIN_COST, Math.min(CostMap.MAX_FREE_COST, sum));
                        costMapData[(y - yLowerLimit) * width + x - xLowerLimit] = (byte) sum;
                    }
                }
            }
            Log.v(TAG, "Fused " + costMap.getSource() + " into merged CostMap");
        }
        Log.v(TAG, "New merged CostMap");

        // Make a fixed grid cost map from the new grid.
        return new FixedGridCostMap(source, mResolution, width, height,
                xLowerLimit, yLowerLimit, costMapData);
    }

    /**
     * Finds x and y grid limits, based on the extreme coordinate values for a collection of input
     * CostMaps.
     *
     * @param costMaps The CostMaps to find the limits for.
     * @return An array in the form {{x_lower, x_upper}, {y_lower, y_upper}}.
     */
    private int[][] findGridLimits(Collection<CostMap> costMaps) {
        if (costMaps.isEmpty()) {
            return new int[][]{{0, 0}, {0, 0}};
        }
        int xLowerLimit = Integer.MAX_VALUE;
        int yLowerLimit = Integer.MAX_VALUE;
        int xUpperLimit = Integer.MIN_VALUE;
        int yUpperLimit = Integer.MIN_VALUE;

        for (CostMap costMap : costMaps) {
            xLowerLimit = Math.min(xLowerLimit, costMap.getLowerXLimit());
            yLowerLimit = Math.min(yLowerLimit, costMap.getLowerYLimit());
            xUpperLimit = Math.max(xUpperLimit, costMap.getUpperXLimit());
            yUpperLimit = Math.max(yUpperLimit, costMap.getUpperYLimit());
        }
        Log.i(TAG, "Fused CostMap dimensions are " + ": X: [" + xLowerLimit + ", " + xUpperLimit +
                "] Y: [" + yLowerLimit + ", " + yUpperLimit + "]");

        return new int[][]{{xLowerLimit, xUpperLimit}, {yLowerLimit, yUpperLimit}};
    }
}
