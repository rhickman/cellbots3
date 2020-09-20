package ai.cellbots.robot.costmap;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stores a fixed grid in a CostMap. All values of this class and subclasses should remain fixed
 * or final.
 */
public class FixedGridCostMap extends GridCostMap {
    private final AtomicBoolean mInflated = new AtomicBoolean(false);

    /**
     * Creates the GridCostMap, given a grid and an offset.
     *
     * @param source     The source of the CostMap data.
     * @param resolution The resolution of the CostMap, being the width of a square in meters.
     * @param width      The width of the grid.
     * @param height     The height of the grid.
     * @param startX     The start X position.
     * @param startY     The start Y position.
     * @param grid       The grid.
     */
    public FixedGridCostMap(Source source, double resolution, int width, int height,
                            int startX, int startY, byte[] grid) {
        this(source, resolution, width, height, startX, startY, grid, false);
    }

    /**
     * Creates the GridCostMap, given a grid and an offset.
     *
     * @param source     The source of the CostMap data.
     * @param resolution The resolution of the CostMap, being the width of a square in meters.
     * @param width      The width of the grid.
     * @param height     The height of the grid.
     * @param startX     The start X position.
     * @param startY     The start Y position.
     * @param grid       The grid.
     * @param inflated   True if inflated.
     */
     public FixedGridCostMap(Source source, double resolution, int width, int height,
                             int startX, int startY, byte[] grid, boolean inflated) {
         super(source, resolution);
         mInflated.set(inflated);
         setGrid(grid, width, height, startX, startY);
         setValid(true);
         onCostMapUpdate();
     }

    /**
     * Checks if this CostMap if mutable.
     *
     * @return True if the CostMap can be altered after creation.
     */
    public boolean isMutable() {
        return false;
    }

     /**
     * Gets if the CostMap requires inflation.
     *
     * @return True if the CostMap requires inflation.
     */
     public boolean requiresInflation() {
         return !mInflated.get();
     }

    /**
     * Shuts down the CostMap.
     */
    @Override
    public void shutdown() {
        // Does nothing as there is no thread in a FixedGridCostMap.
    }

    /**
     * Waits for the shutdown the CostMap.
     */
    @Override
    public void waitShutdown() {
        // Does nothing as there is no thread in a FixedGridCostMap.
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
        return super.getCost(x, y);
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
                && xIndex >= 0 && xIndex < (getUpperXLimit() - getLowerXLimit())
                && yIndex >= 0 && yIndex < (getUpperYLimit() - getLowerYLimit());
    }

    /**
     * Gets the lower limit of the region of interest as a grid cell coordinate.
     *
     * @return The x coordinate of the lower limit of the region of interest, in grid cells.
     */
    public int getLowerXLimit() {
        return super.getLowerXLimit();
    }

    /**
     * Gets the lower limit of the region of interest as a grid cell coordinate.
     *
     * @return The y coordinate of the lower limit of the region of interest, in grid cells.
     */
    public int getLowerYLimit() {
        return super.getLowerYLimit();
    }

    /**
     * Gets the upper limit of the region of interest as a grid cell coordinate.
     *
     * @return The x coordinate of the upper limit of the region of interest, in grid cells.
     */
    public int getUpperXLimit() {
        return super.getUpperXLimit();
    }

    /**
     * Gets the upper limit of the region of interest as a grid cell coordinate.
     *
     * @return The y coordinate of the upper limit of the region of interest, in grid cells.
     */
    public int getUpperYLimit() {
        return super.getUpperYLimit();
    }

    /**
     * Gets the highest CostMap cost in a region.
     *
     * @param xStart The start x coordinate in the CostMap coordinates.
     * @param yStart The start y coordinate in the CostMap coordinates.
     * @param xEnd   The end x coordinate in the CostMap coordinates.
     * @param yEnd   The end y coordinate in the CostMap coordinates.
     * @return The highest CostMap cost in a region.
     */
    @SuppressWarnings("WeakerAccess")
    public byte getHighestCostInRegion(int xStart, int yStart, int xEnd, int yEnd) {
        return super.getHighestCostInRegion(xStart, yStart, xEnd, yEnd);
    }

    /**
     * Gets the CostMap data over a region.
     *
     * @param xStart The start x coordinate in the CostMap coordinates. Include this.
     * @param yStart The start y coordinate in the CostMap coordinates. Include this.
     * @param xEnd   The end x coordinate in the CostMap coordinates. Exclude this.
     * @param yEnd   The end y coordinate in the CostMap coordinates. Exclude this.
     * @return The array of byte cost values in the form of {x11 x12 ... x1n, x21 x22 ... x2n}.
     */
    @SuppressWarnings("WeakerAccess")
    public byte[] getCostRegion(int xStart, int yStart, int xEnd, int yEnd) {
        return super.getCostRegion(xStart, yStart, xEnd, yEnd);
    }

    /**
     * Gets the entire CostMap data, by getting the region from the lower limit to the upper limit.
     *
     * @return The entire CostMap, starting at the lower limits.
     */
    public byte[] getFullCostRegion() {
        return super.getFullCostRegion();
    }

    /**
     * Returns a string of the CostMap description.
     *
     * @return CostMap description string.
     */
    @Override
    public String toString() {
        return "FixedGridCostMap(source=" + getSource() + ")";
    }
}
