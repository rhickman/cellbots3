package ai.cellbots.robot.navigation;

import junit.framework.Assert;

import org.junit.Test;

import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.costmap.CostMapPose;
import ai.cellbots.robot.costmap.FixedGridCostMap;

/**
 * Tests the PathUtil class.
 */
public class PathUtilTest {
    /**
     * Tests when the cost map is null.
     */
    @Test
    public void testNullCostMap() {
        CostMapPose[] pathElements = {
                new CostMapPose(0, 0),
                new CostMapPose(1, 1),
                new CostMapPose(2, 2),
                new CostMapPose(3, 3),
                new CostMapPose(4, 4)};
        Path path = new Path(pathElements);
        CostMap costMap = null;
        boolean pathThroughObstcale = PathUtil.isPathThroughObstacle(path, costMap);
        Assert.assertFalse(pathThroughObstcale);
    }

    /**
     * Tests when the path is null.
     */
    @Test
    public void testNullPath() {
        Path path = null;
        byte[] costs = {
                127, 0, 0, 0, 0,
                127, 0, 0, 0, 0,
                127, 0, 0, 0, 0,
                127, 0, 0, 0, 0,
                127, 0, 0, 0, 0};
        CostMap costMap = new FixedGridCostMap(null, 0.1, 5, 5, 0, 0, costs);
        boolean pathThroughObstcale = PathUtil.isPathThroughObstacle(path, costMap);
        Assert.assertFalse(pathThroughObstcale);
    }

    /**
     * Tests various paths when the cost map contains diagonal obstacles.
     */
    @Test
    public void testDiagonalObstacles() {
        byte[] costs = {
                127,   0,   0,   0,   0,
                  0, 127,   0,   0,   0,
                  0,   0, 127,   0,   0,
                  0,   0,   0, 127,   0,
                  0,   0,   0,   0, 127};
        CostMap costMap = new FixedGridCostMap(null, 0.1, 5, 5, 0, 0, costs);

        // First Path is in obstacle at (0, 0).
        CostMapPose[] firstPathElements = {
                new CostMapPose(0, 0),
                new CostMapPose(1, 0),
                new CostMapPose(2, 0),
                new CostMapPose(3, 0),
                new CostMapPose(4, 0)};
        Path firstPath = new Path(firstPathElements);
        boolean pathThroughObstcale = PathUtil.isPathThroughObstacle(firstPath, costMap);
        Assert.assertTrue(pathThroughObstcale);

        // Second Path is in obstacle at (2, 2).
        CostMapPose[] secondPathElements = {
                new CostMapPose(0, 1),
                new CostMapPose(0, 2),
                new CostMapPose(2, 2)};
        Path secondPath = new Path(secondPathElements);
        pathThroughObstcale = PathUtil.isPathThroughObstacle(secondPath, costMap);
        Assert.assertTrue(pathThroughObstcale);

        // Third Path is not in an obstacle.
        CostMapPose[] thirdPathElements = {
                new CostMapPose(0, 1),
                new CostMapPose(0, 2),
                new CostMapPose(0, 3),
                new CostMapPose(1, 3)};
        Path thirdPath = new Path(thirdPathElements);
        pathThroughObstcale = PathUtil.isPathThroughObstacle(thirdPath, costMap);
        Assert.assertFalse(pathThroughObstcale);

        // Fourth Path is not in an obstacle.
        CostMapPose[] fourthPathElements = {
                new CostMapPose(1, 2),
                new CostMapPose(1, 3),
                new CostMapPose(2, 3),
                new CostMapPose(2, 4)};
        Path fourthPath = new Path(fourthPathElements);
        pathThroughObstcale = PathUtil.isPathThroughObstacle(fourthPath, costMap);
        Assert.assertFalse(pathThroughObstcale);

        // Fifth Path is in an obstacle.
        CostMapPose[] fifthPathElements = {
                new CostMapPose(3, 4),
                new CostMapPose(4, 4)};
        Path fifthPath = new Path(fifthPathElements);
        pathThroughObstcale = PathUtil.isPathThroughObstacle(fifthPath, costMap);
        Assert.assertTrue(pathThroughObstcale);
    }

    /**
     * Tests when the cost map has offsets.
     * In all the tests below the same element is repeated in the path
     * so that the path contains more than one element and is valid.
     */
    @Test
    public void testCostMapOffset() {
        byte[] costs = {
                127, 0,   0,
                127, 0,   0,
                127, 0, 127};
        CostMap costMap = new FixedGridCostMap(null, 0.1, 3, 3, 1, 2, costs);

        // First Path is in an obstacle, will be at (0, 0) if the offset is removed.
        CostMapPose[] firstPathElements = {
                new CostMapPose(1, 2),
                new CostMapPose(1, 2)};
        Path firstPath = new Path(firstPathElements);
        boolean pathThroughObstcale = PathUtil.isPathThroughObstacle(firstPath, costMap);
        Assert.assertTrue(pathThroughObstcale);

        // Second Path is outside the cost map so getCost function returns it as an obstacle.
        CostMapPose[] secondPathElements = {
                new CostMapPose(0, 0),
                new CostMapPose(0, 0)};
        Path secondPath = new Path(secondPathElements);
        pathThroughObstcale = PathUtil.isPathThroughObstacle(secondPath, costMap);
        Assert.assertTrue(pathThroughObstcale);

        // Third Path is in an obstacle, will be at (2, 2) if the offset is removed.
        CostMapPose[] thirdPathElements = {
                new CostMapPose(3, 4),
                new CostMapPose(3, 4)};
        Path thirdPath = new Path(thirdPathElements);
        pathThroughObstcale = PathUtil.isPathThroughObstacle(thirdPath, costMap);
        Assert.assertTrue(pathThroughObstcale);

        // Fourth Path is not in an obstacle, will be at (1, 1) if the offset is removed.
        CostMapPose[] fourthPathElements = {
                new CostMapPose(2, 4),
                new CostMapPose(2, 4)};
        Path fourthPath = new Path(fourthPathElements);
        pathThroughObstcale = PathUtil.isPathThroughObstacle(fourthPath, costMap);
        Assert.assertFalse(pathThroughObstcale);
    }

    /**
     * Tests a single cell costmap and a single element path.
     * Path is invalid with a single element.
     */
    @Test
    public void testSingleCellSingleElement() {
        byte[] cost = {127};
        CostMap costMap = new FixedGridCostMap(null, 0.1, 1, 1, 0, 0, cost);
        CostMapPose[] pathElement = {new CostMapPose(0, 0)};
        Path path = new Path(pathElement);
        boolean pathThroughObstcale = PathUtil.isPathThroughObstacle(path, costMap);
        Assert.assertFalse(pathThroughObstcale);
    }

    /**
     * Tests a single cell costmap and a path with two elements.
     */
    @Test
    public void testSingleCellTwoElements() {
        byte[] cost = {127};
        CostMap costMap = new FixedGridCostMap(null, 0.1, 1, 1, 0, 0, cost);
        CostMapPose[] pathElement = {
                new CostMapPose(0, 0),
                new CostMapPose(1, 1)};
        Path path = new Path(pathElement);
        boolean pathThroughObstcale = PathUtil.isPathThroughObstacle(path, costMap);
        Assert.assertTrue(pathThroughObstcale);
    }

    /**
     * Tests when the costmap and path contain the same elements, and the costmap is full of obstacles.
     */
    @Test
    public void testFullObstaclesPath() {
        byte[] costs = {
                127, 127,
                127, 127};
        CostMap costMap = new FixedGridCostMap(null, 0.5, 2, 2, 0, 0, costs);
        CostMapPose[] pathElements = {
                new CostMapPose(0, 0),
                new CostMapPose(1, 0),
                new CostMapPose(0, 1),
                new CostMapPose(1, 1)};
        Path path = new Path(pathElements);
        boolean pathThroughObstcale = PathUtil.isPathThroughObstacle(path, costMap);
        Assert.assertTrue(pathThroughObstcale);
    }


    /**
     * Tests when the costmap and path contain the same elements, and the costmap is clear.
     */
    @Test
    public void testClearObstaclesPath() {
        byte[] costs = {
                0, 0,
                0, 0};
        CostMap costMap = new FixedGridCostMap(null, 0.5, 2, 2, 0, 0, costs);
        CostMapPose[] pathElements = {
                new CostMapPose(0, 0),
                new CostMapPose(1, 0),
                new CostMapPose(0, 1),
                new CostMapPose(1, 1)};
        Path path = new Path(pathElements);
        boolean pathThroughObstcale = PathUtil.isPathThroughObstacle(path, costMap);
        Assert.assertFalse(pathThroughObstcale);
    }
}
