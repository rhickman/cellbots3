package ai.cellbots.robot.costmap;

import android.util.Log;

import java.util.List;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.World;

/**
 * Creates a FloorplanCostMap, and generates CostMap for the floor plan.
 */

public class FloorplanCostMap extends GridCostMap {
    private static final String TAG = FloorplanCostMap.class.getSimpleName();
    // The minimum size of the polygon in the floor plan. In square meters.
    private static final double MIN_POLYGON_SIZE = 0.03;

    /**
     * Creates a FloorplanCostMap from a given World.
     *
     * @param initialWorld The initial detailed world.
     * @param resolution   The resolution of the CostMap in square meters.
     */
    public FloorplanCostMap(DetailedWorld initialWorld, double resolution) {
        super(Source.FLOORPLAN, resolution, new byte[0], 0, 0, 0, 0);
        Log.i(TAG, "Creating FloorplanCostMap");
        setValid(false);
        if (initialWorld != null) {
            setValid(true);
            onFloorplan(initialWorld.getLevels());
        }
        if (isValid()) {
            Log.i(TAG, "Created a floor plan cost map successfully. " + toString());
        } else {
            Log.i(TAG, "Could not create a floor plan cost map");
        }
        onCostMapUpdate();
    }

    /**
     * Called to set the floorplan from the floorplanner.
     *
     * @param levels The list of floorplan levels.
     */
    public void onFloorplan(List<World.FloorPlanLevel> levels) {
        List<World.FloorPlanPolygon> polygons = levels.get(0).getPolygons();
        // fill cost map with the floor plan polygons.
        fillCostMapWithPolygons(polygons);
        // Update cost map.
        onCostMapUpdate();
    }

    /**
     * Fills the grid cost map using floor plan polygons
     *
     * @param polygons FloorPlanPolygon list of polygons
     */
    private void fillCostMapWithPolygons(List<World.FloorPlanPolygon> polygons) {
        // Sanity checks.
        if (polygons.isEmpty()) {
            Log.w(TAG, "Empty polygon");
            setValid(false);
            return;
        }
        Log.i(TAG, "Filling FloorplanCostMap with polygons");

        // Get min and max (x,y) polygon points to determine the size of the cost map.
        double[][] positions = DetailedWorld.findFloorPlanBoundaries(polygons);
        if (positions == null) {
            Log.w(TAG, "Could not find floor plan boundaries");
            setValid(false);
            return;
        }

        // Create the grid.
        makeGrid(positions, MIN_COST);
        // For every furniture or wall polygon.
        for (World.FloorPlanPolygon polygon : polygons) {
            if ((polygon.getLayer() == World.FloorPlanPolygon.LAYER_WALLS) ||
                    (polygon.getLayer() == World.FloorPlanPolygon.LAYER_FURNITURE)) {
                // Drop polygon with less than 2 vertices. We accept polygon with two vertices
                // though.
                if (polygon.getVertices().length < 2) continue;
                // Drop too small polygon.
                if (polygon.getArea() < MIN_POLYGON_SIZE) continue;
                // Rasterize polygon vertices.
                int[][] discretizedVertices = discretizePolygonVertices(polygon.getVertices());
                // Draw the polygon on the grid.
                drawPolygonOnGrid(discretizedVertices);
            }
        }
    }

    /**
     * Given polygon vertices in double[][] coordinates, it returns discretized coordinates int[][].
     *
     * @param vertices Vertices of the polygon in double[][] coordinates.
     * @return Discretized coordinates int[][].
     */
    private int[][] discretizePolygonVertices(double[][] vertices) {
        // Sanity checks
        if (vertices == null) {
            Log.w(TAG, "discretizePolygonVertices: null vertices");
            return null;
        }

        int[][] discretizedVertices = new int[vertices.length][vertices[0].length];
        for (int x = 0; x < vertices.length; x++) {
            for (int y = 0; y < vertices[0].length; y++) {
                discretizedVertices[x][y] = (int) Math.floor(vertices[x][y] / getResolution());
            }
        }
        return discretizedVertices;
    }

    /**
     * Called to shutdown the Floorplan CostMap.
     */
    @Override
    public void shutdown() {
        // Does nothing, since the CostMap has no threads.
    }

    /**
     * Called to wait for the shutdown of the Floorplan CostMap.
     */
    @Override
    public void waitShutdown() {
        // Does nothing, since the CostMap has no threads.
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
