package ai.cellbots.robot.costmap;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import ai.cellbots.common.Polygon;
import ai.cellbots.common.Transform;

/**
 * Avoids polygons of geometry. Should be used for cases where there are few polygons, such as the
 * computer vision output or the bumper.
 */
// TODO(playerfour) make GeometryCostMap as a subclass of CostMap and not GridCostMap
public class GeometryCostMap extends GridCostMap {
    private static final String TAG = GeometryCostMap.class.getSimpleName();
    private final double mRobotRadius;
    /**
     * Stores the geometry to add to the system.
     */
    final public static class Geometry {
        private final long mExpireTime;
        private final Polygon mPolygon;
        private final byte mCost;

        /**
         * Creates a geometry object.
         * @param expireTime The time to expire the object in ms (currently not used).
         * @param cost       The cost of the object (currently not used).
         * @param polygon    The Polygon list for the object.
         */
        public Geometry(long expireTime, byte cost, Polygon polygon) {
            mExpireTime = expireTime;
            mCost = cost;
            mPolygon = polygon;
        }
    }

    // TODO(playerone) Use ConcurrentLinkedQueue and remove all synchronization. This is part 3.
    private final LinkedList<Geometry> mGeometryList = new LinkedList<>();

    /**
     * Creates the CostMap.
     * @param source      The source of the CostMap data.
     * @param resolution  The resolution of the CostMap, being the width of a square in meters.
     * @param robotRadius The physical radius of the robot (in meters).
     */
    public GeometryCostMap(Source source, double resolution, double robotRadius) {
        super(source, resolution);
        mRobotRadius = robotRadius;
    }

    /**
     * Clears out the expired geometries and update.
     */
    // TODO(playerone) remove sync.
    public synchronized void clear(long timeStamp) {
        update(timeStamp, new LinkedList<Geometry>());
    }

    /**
     * Updates the CostMap with geometries.
     *
     * @param timeStamp The timestamp at which the update is called.
     * @param list      The new geometries to add to the CostMap.
     */
    public synchronized void update(long timeStamp, Collection<Geometry> list) {
        // Remove geometries that are expired.
        Iterator<Geometry> iterator = mGeometryList.iterator();
        while (iterator.hasNext()){
            Geometry g = iterator.next();
            if (g.mExpireTime < timeStamp) {
                iterator.remove();
            }
        }
        // Clear the associated grid.
        clearGrid();

        // Add the new geometries.
        if (!list.isEmpty()) {
            mGeometryList.addAll(list);
        }

        // Create the new grid with valid geometries.
        if (!mGeometryList.isEmpty()) {
            setValid(true);
            fillCostMapWithGeometries();
            Log.d(TAG, "Costmap created " + getSource() + ": X: ["
                    + getLowerXLimit() + ", " + getUpperXLimit()
                    + "] Y: [" + getLowerYLimit()
                    + ", " + getUpperYLimit() + "]");
        } else {
            setValid(false);
            Log.d(TAG, "Costmap not created " + getSource());
        }
        onCostMapUpdate();
    }

    /**
     * Sets a costmap grid and fills it with the geometry polygons.
     */
    // TODO(playerone) remove sync
    private synchronized void fillCostMapWithGeometries() {
        if (mGeometryList.isEmpty()) {
            Log.wtf(TAG, "Empty geometries list");
            setValid(false);
            return;
        }
        // Get the grid boundaries to determine the size of the cost map.
        double[][] positions = findGeometriesBoundaries();
        if (positions == null) {
            Log.wtf(TAG, "Could not find grid boundaries");
            setValid(false);
            return;
        }
        // Create the grid.
        makeGrid(positions, MIN_COST);
        for (Geometry g : mGeometryList) {
            int size = g.mPolygon.getSize();
            List<Transform> points = g.mPolygon.getPoints();
            // DiscretizedVertices contain only X and Y.
            int[][] discretizedVertices = new int[size][2];
            for (int i = 0; i < size; i++) {
                double[] pointPosition = points.get(i).getPosition();
                discretizedVertices[i][0] = (int) Math.floor(pointPosition[0] / getResolution());
                discretizedVertices[i][1] = (int) Math.floor(pointPosition[1] / getResolution());
            }
            // TODO mCost is not used, function below sets polygon regions to OBSTACLE_COST.
            drawPolygonOnGrid(discretizedVertices);
        }
    }

    /**
     * Finds the boundaries of all the geometry polygons in X and Y.
     * The boundaries are extended by robot radius in each direction.
     *
     * @return array of doubles {{minX, minY}}, {{maxX, maxY}}.
     */
    // TODO(playerone) remove sync
    private synchronized double[][] findGeometriesBoundaries() {
        if (mGeometryList.isEmpty()) {
            Log.wtf(TAG, "Empty geometries list");
            return null;
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Geometry g : mGeometryList) {
            int size = g.mPolygon.getSize();
            List<Transform> points = g.mPolygon.getPoints();
            for (int i = 0; i < size; i++) {
                double[] pointPosition = points.get(i).getPosition();
                minX = Math.min(minX, pointPosition[0]);
                maxX = Math.max(maxX, pointPosition[0]);
                minY = Math.min(minY, pointPosition[1]);
                maxY = Math.max(maxY, pointPosition[1]);
            }
        }
        return new double[][]{{minX - mRobotRadius, minY - mRobotRadius},
                              {maxX + mRobotRadius, maxY + mRobotRadius}};
    }

    /**
     * Checks if the CostMap requires inflation.
     *
     * @return True if the CostMap requires inflation.
     */
    public boolean requiresInflation() {
        return true;
    }

    /**
     * Shuts down the geometry CostMap.
     */
    @Override
    public void shutdown() {
        // Do nothing since there is no thread.
    }

    /**
     * Waits for the shutdown of the geometry CostMap.
     */
    @Override
    public void waitShutdown() {
        // Do nothing since there is no thread.
    }

    /**
     * Gets locations for a given costmap.
     * 
     * @return An ArrayList of all the grid cells in the costmap.
     */
    public List<CostMapPose> getAllCostMapPoses() {
        return new ArrayList<>();
    }

    /**
     * Returns a string of the CostMap description.
     *
     * @return CostMap description string.
     */
    @Override
    public String toString() {
        return "GeometryCostMap(" + getSource() + ")";
    }
}
