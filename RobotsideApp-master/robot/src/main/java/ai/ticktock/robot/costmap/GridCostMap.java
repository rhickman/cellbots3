package ai.cellbots.robot.costmap;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import ai.cellbots.common.Geometry;
import ai.cellbots.common.Transform;
import ai.cellbots.common.concurrent.AtomicByteArray;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Stores a CostMap in grid format. This is centered coordinates, so the (0, 0) index covers
 * (- resolution / 2, resolution / 2) and (- resolution / 2, resolution / 2) area.
 *
 * This class is conditionally thread safe because of reallocation of mGrid.
 */
public abstract class GridCostMap extends CostMap {
    private static final String TAG = GridCostMap.class.getSimpleName();
    private AtomicByteArray mGrid;
    private AtomicInteger mWidth = new AtomicInteger(0);
    private AtomicInteger mHeight = new AtomicInteger(0);

    // Offset of the grid from the world's origin, in world coordinates.
    private AtomicInteger mStartX = new AtomicInteger(0);
    private AtomicInteger mStartY = new AtomicInteger(0);

    /**
     * Creates the GridCostMap.
     *
     * @param source     The source of the CostMap data.
     * @param resolution The resolution of the CostMap, being the width of a square in meters.
     */
    @SuppressWarnings("WeakerAccess")
    protected GridCostMap(Source source, double resolution) {
        super(source, resolution);
    }

    /**
     * Creates a GridCostMap taking an existing grid as input.
     *
     * @param source     The source of the CostMap data.
     * @param resolution The resolution of the CostMap, being the width of a square in meters.
     * @param grid       The new grid. The grid will be used as the CostMap's grid, so no further
     *                   operations may be executed upon grid or unexpected behavior will result.
     * @param width      The width of the grid
     * @param height     The height of the grid
     * @param startX     The initial x position of the grid in the CostMap coordinates.
     * @param startY     The initial y position of the grid in the CostMap coordinates.
     */
    @SuppressWarnings("WeakerAccess")
    protected GridCostMap(Source source, double resolution, byte[] grid, int width, int height,
            int startX, int startY) {
        this(source, resolution);
        mGrid = new AtomicByteArray(grid.length);
        checkArgument(mGrid.copyFrom(grid));
        mWidth.set(width);
        mHeight.set(height);
        mStartX.set(startX);
        mStartY.set(startY);
    }

    /**
     * Sets the CostMap grid. The caller is still responsible for calling onCostMapUpdate();
     *
     * @param grid   The new grid. The grid will be used as the CostMap's grid, so no further
     *               operations may be executed upon grid or unexpected behavior will result.
     * @param width  The width of the grid
     * @param height The height of the grid
     * @param startX The initial x position of the grid in the CostMap coordinates.
     * @param startY The initial y position of the grid in the CostMap coordinates.
     */
    @SuppressWarnings("WeakerAccess")
    protected void setGrid(byte[] grid, int width, int height, int startX, int startY) {
        if (grid == null) {
            Log.wtf(TAG, "Input grid is null");
        }
        if (width <= 0 || height <= 0) {
            Log.wtf(TAG, "Invalid width or height. " + width + ", " + height);
        }
        checkArgument(grid.length == width * height);
        mGrid = new AtomicByteArray(grid.length);
        checkArgument(mGrid.copyFrom(grid));
        mWidth.set(width);
        mHeight.set(height);
        mStartX.set(startX);
        mStartY.set(startY);
    }

    /**
     * Converts the bounding box limits given in world coordinates to costmap
     * coordinates and makes a costmap grid setting the initial cost of its cells.
     *
     * The caller is still responsible for calling onCostMapUpdate();
     *
     * @param bbox  An array of doubles {{minX, minY}}, {{maxX, maxY}} representing bounding box
     *              in real world coordinates.
     * @param initialCost  The initial cost for the grid's elements.
     */
    @SuppressWarnings("WeakerAccess")
    protected void makeGrid(double[][] bbox, byte initialCost) {
        int startX = (int) Math.floor(bbox[0][0] / getResolution());
        int startY = (int) Math.floor(bbox[0][1] / getResolution());
        // If an upper limit of the grid fall exactly on an edge, new cell needs to be created.
        // Therefore, a small value is added to the upper limits.
        int endX = (int) Math.ceil(bbox[1][0] / getResolution() + getResolution() / 1e9);
        int endY = (int) Math.ceil(bbox[1][1] / getResolution() + getResolution() / 1e9);
        if (startX >= endX || startY >= endY) {
            Log.wtf(TAG, "invalid grid positions: " + startX + ", " + startY + ", " +
                    endX + ", " + endY);
        }
        mWidth.set(endX - startX);
        mHeight.set(endY - startY);
        mGrid = new AtomicByteArray(mWidth.get() * mHeight.get());
        byte[] newArray = new byte[mWidth.get() * mHeight.get()];
        Arrays.fill(newArray, initialCost);
        checkArgument(mGrid.copyFrom(newArray));
        mStartX.set(startX);
        mStartY.set(startY);
    }

    /**
     * Clears the CostMap grid. The caller is still responsible for calling onCostMapUpdate();
     *
     */
    protected void clearGrid() {
        checkArgument(mGrid.copyFrom(new byte[0]));
        mWidth.set(0);
        mHeight.set(0);
        mStartX.set(0);
        mStartY.set(0);
    }

    /**
     * Gets the CostMap data at a given point in the CostMap coordinates, specified by integer.
     *
     * @param x The x coordinate in the CostMap coordinates. Between x limits.
     * @param y The y coordinate in the CostMap coordinates. Between y limits.
     * @return Cost in byte.
     */
    @Override
    public byte getCost(int x, int y) {
        CostMapPose cell = new CostMapPose(x, y);
        if (!includesCoordinate(cell)) {
            return MAX_COST;
        }
        return mGrid.get((cell.getY() - getLowerYLimit()) * mWidth.get() +
                          cell.getX() - getLowerXLimit());
    }

    /**
     * Sets the cost of a list of transforms, with a given inflation radius.
     * The cost is even for all the elements.
     *
     * @param transforms The list of transforms to set the costs for.
     * @param radius     The inflation radius in meters for each pose on the list.
     * @param cost       The cost to set.
     */
    @SuppressWarnings("WeakerAccess")
    protected void setInflatedCosts(List<Transform> transforms, double radius, byte cost) {
        if (transforms == null) {
            Log.w(TAG, "Input transform is null");
            return;
        }
        for (int i = 0; i < transforms.size(); i++) {
            CostMapPose pathPose = discretize(transforms.get(i).getPosition(0),
                    transforms.get(i).getPosition(1));
            inflateCost(pathPose, radius, cost);
        }
    }

    /**
     * Inflates the cost (with a single value) for a given pose.
     *
     * @param pose   The pose to inflate.
     * @param radius The inflation radius in meters for the pose.
     * @param cost   The cost to set.
     */
    @SuppressWarnings("WeakerAccess")
    protected void inflateCost(CostMapPose pose, double radius, byte cost) {
        int discretizedRadius = (int) Math.round(radius / getResolution());
        int width = mWidth.get();
        for (int j = -discretizedRadius; j <= discretizedRadius; j++) {
            int x = pose.getX() + j;
            for (int k = -discretizedRadius; k <= discretizedRadius; k++) {
                int y = pose.getY() + k;
                if (includesCoordinate(new CostMapPose(x, y)) &&
                        j * j + k * k <= discretizedRadius * discretizedRadius) {
                    mGrid.set((y - getLowerYLimit()) * width + x - getLowerXLimit(), cost);
                }
            }
        }
    }

    /**
     * Gets the size of this CostMap.
     *
     * @return The size of this CostMap.
     */
    public int getSize() { return mGrid.length(); }

    /**
     * Gets the lower limit of the region of interest as a grid cell coordinate.
     *
     * @return The x coordinate of the lower limit of the region of interest, in grid cells.
     */
    public int getLowerXLimit() {
        return mStartX.get();
    }

    /**
     * Gets the lower limit of the region of interest as a grid cell coordinate.
     *
     * @return The y coordinate of the lower limit of the region of interest, in grid cells.
     */
    public int getLowerYLimit() {
        return mStartY.get();
    }

    /**
     * Gets the upper limit of the region of interest as a grid cell coordinate.
     *
     * @return The x coordinate of the upper limit of the region of interest, in grid cells.
     */
    public int getUpperXLimit() {
        return getLowerXLimit() + mWidth.get();
    }

    /**
     * Gets the width of the bounding box containing the CostMap, in grid cells.
     *
     * @return The width of the bounding box containing the CostMap, in grid cells.
     */
    public int getBoundingWidth() {
        return mWidth.get();
    }

    /**
     * Gets the height of the bounding box containing the CostMap, in grid cells.
     *
     * @return The height of the bounding box containing the CostMap, in grid cells.
     */
    public int getBoundingHeight() {
        return mHeight.get();
    }

    /**
     * Gets the upper limit of the region of interest as a grid cell coordinate.
     *
     * @return The y coordinate of the upper limit of the region of interest, in grid cells.
     */
    public int getUpperYLimit() {
        if (getSize() == 0) {
            return getLowerYLimit();
        }
        return getLowerYLimit() + mHeight.get();
    }

    /**
     * Gets the neighbors for a cost map pose whose cost is less than or equal to this value.
     *
     * @return An ArrayList of GridCostMapPose.
     */
    public ArrayList<CostMapPose> neighborsFor(CostMapPose costMapPose, byte maxCost) {
        ArrayList<CostMapPose> adjacentCells = new ArrayList<>();
        int[][] offsets = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

        for (int[] offset : offsets) {
            CostMapPose adjacentCell = costMapPose.offsetBy(offset[0], offset[1]);
            if (includesCoordinate(adjacentCell) && getCost(adjacentCell) <= maxCost) {
                adjacentCells.add(adjacentCell);
            }
        }
        return adjacentCells;
    }

    /**
     * Gets all the poses in the cost map as a list of GridCostMapPose.
     *
     * @return a list of all the poses in the cost map.
     */
    @Override
    public List<CostMapPose> getAllCostMapPoses() {
        if (getSize() == 0) {
            return Collections.emptyList();
        }
        int width = mWidth.get();
        int height = mHeight.get();
        ArrayList<CostMapPose> coordinatesList = new ArrayList<>(width * height);
        for (int y = 0; y < width; y++) {
            for (int x = 0; x < height; x++) {
                CostMapPose pose = new CostMapPose(x, y);
                coordinatesList.add(pose);
            }
        }
        return coordinatesList;
    }

    /**
     * Returns a string of the CostMap description.
     *
     * @return CostMap description string.
     */
    @Override
    public String toString() {
        return "Resolution=" + getResolution() + ", width=" + mWidth.get() + ", height="
                + mHeight.get() + ", StartX=" + mStartX.get() + ", StartY=" + mStartY.get() +
                ", lower limit=(" + getLowerXLimit() + ", " + getLowerYLimit() +
                "), upper limit=(" + getUpperXLimit() + ", " + getUpperYLimit() + ")";
    }

    /**
     * Get the raw grid of the map.
     *
     * @return The raw grid.
     */
    protected byte[] getGrid() {
        return mGrid.clone();
    }

    /**
     * Checks if a grid pose is within a cost map's limits or falls out of it.
     *
     * @param coordinate A GridCostMapPose to check its boundaries, transformed by the start point.
     * @return True if the cost map includes the coordinate.
     */
    private boolean includesCoordinate(CostMapPose coordinate) {
        return includesCoordinate(coordinate.getX(), coordinate.getY());
    }

    /**
     * Checks if a grid pose is within a cost map's limits or falls out of it.
     *
     * @param xIndex X position relative to the start.
     * @param yIndex Y position relative to the start.
     * @return True if the cost map includes the coordinate.
     */
    private boolean includesCoordinate(int xIndex, int yIndex) {
        xIndex -= getLowerXLimit();
        yIndex -= getLowerYLimit();
        return getSize() != 0
                && xIndex >= 0 && xIndex < mWidth.get()
                && yIndex >= 0 && yIndex < mHeight.get();
    }

    /**
     * Gets the entire CostMap data, by getting the region from the lower limit to the upper limit.
     *
     * @return The entire CostMap, starting at the lower limits.
     */
    public byte[] getFullCostRegion() {
        return getGrid();
    }

    /**
     * Takes all vertices of a polygon and discretizes its sides on the Grid using a technique
     * called "super cover line".
     * NOTE: To draw a closed polygon, vertices must be ordered so that the last point can be
     * connected to the first one.
     *
     * @param vertices Polygon vertices in int[][] coordinates.
     */
    void drawPolygonOnGrid(int[][] vertices) {
        // Sanity checks
        if (vertices == null) {
            Log.w(TAG, "drawPolygonOnGrid: null vertices");
            return;
        }
        byte[] array = mGrid.clone();
        int width = mWidth.get();
        int height = mHeight.get();
        int startX = mStartX.get();
        int startY = mStartY.get();
        for (int j = 0; j < vertices.length; j++) {
            try {
                // Fill as occupied those cells that are "covered" by the line
                array = Geometry.drawLineOnGrid(vertices[j],
                        // Join the last vertex with the first one
                        vertices[(j + 1) % vertices.length],
                        array, width, height, startX, startY, OBSTACLE_COST);
            } catch (Exception e) {
                Log.w(TAG, e.getMessage());
            }
        }
        // TODO(playerone) strictly speaking this is not thread safe. We need to use CAS.
        checkArgument(mGrid.copyFrom(array));
    }
}
