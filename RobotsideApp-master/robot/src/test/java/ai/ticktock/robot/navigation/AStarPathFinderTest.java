package ai.cellbots.robot.navigation;

import junit.framework.Assert;

import org.junit.Test;

import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.costmap.FixedGridCostMap;
import ai.cellbots.robot.costmap.CostMapPose;

/**
 * Tests the AStarPathFinder.
 */
public class AStarPathFinderTest {
    /**
     * Tests a simple map with no items in it.
     */
    @Test
    public void testEmptyMap() {
        byte[] grid = {};
        AStarPathFinder aStarPathFinder = new AStarPathFinder();
        aStarPathFinder.setCostMap(new FixedGridCostMap(CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED, 0.1, 0, 0, 0, 0, grid));
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(0, 0), new CostMapPose(0, 1)));
    }

    /**
     * Tests a simple map with no items in it.
     */
    @Test
    public void testSimpleMap() {
        byte[] grid = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        AStarPathFinder aStarPathFinder = new AStarPathFinder();
        aStarPathFinder.setCostMap(new FixedGridCostMap(CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED, 0.1, 4, 4, 0, 0, grid));

        // Out-of-bounds paths, so no results
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(-1, -1), new CostMapPose(0, 1)));
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(10, 10), new CostMapPose(0, 1)));

        // A path form (0, 0) to (0, 1)
        // Expected result: a straight path consisting of (0, 0) to (0, 1)
        Path path1 = aStarPathFinder.computePlan(new CostMapPose(0, 0), new CostMapPose(0, 1));
        Assert.assertNotNull(path1);
        Assert.assertEquals(path1.size(), 2);
        Assert.assertEquals(path1.get(0).getX(), 0);
        Assert.assertEquals(path1.get(0).getY(), 0);
        Assert.assertEquals(path1.get(1).getX(), 0);
        Assert.assertEquals(path1.get(1).getY(), 1);

        // A path from (0, 0) to (0, 3)
        // Expected result: a straight path consisting of (0, 0) -> (0, 1) -> (0, 2) -> (0, 3)
        Path path2 = aStarPathFinder.computePlan(new CostMapPose(0, 0), new CostMapPose(0, 3));
        Assert.assertNotNull(path2);
        Assert.assertEquals(path2.size(), 4);
        Assert.assertEquals(path2.get(0).getX(), 0);
        Assert.assertEquals(path2.get(0).getY(), 0);
        Assert.assertEquals(path2.get(1).getX(), 0);
        Assert.assertEquals(path2.get(1).getY(), 1);
        Assert.assertEquals(path2.get(2).getX(), 0);
        Assert.assertEquals(path2.get(2).getY(), 2);
        Assert.assertEquals(path2.get(3).getX(), 0);
        Assert.assertEquals(path2.get(3).getY(), 3);

        // Another out-of-bounds path that is ignored
        Path path3 = aStarPathFinder.computePlan(new CostMapPose(0, 0), new CostMapPose(0, 4));
        Assert.assertNull(path3);

        // A direct diagonal path from (0, 0) to (3, 3)
        // Expected result: a diagonal path consisting of (0, 0) -> (1, 1) -> (2, 2) -> (3, 3)
        Path path4 = aStarPathFinder.computePlan(new CostMapPose(0, 0), new CostMapPose(3, 3));
        Assert.assertNotNull(path4);
        Assert.assertEquals(path4.size(), 4);
        Assert.assertEquals(path4.get(0).getX(), 0);
        Assert.assertEquals(path4.get(0).getY(), 0);
        Assert.assertEquals(path4.get(1).getX(), 1);
        Assert.assertEquals(path4.get(1).getY(), 1);
        Assert.assertEquals(path4.get(2).getX(), 2);
        Assert.assertEquals(path4.get(2).getY(), 2);
        Assert.assertEquals(path4.get(3).getX(), 3);
        Assert.assertEquals(path4.get(3).getY(), 3);
    }

    /**
     * Test what happens if there is an offset.
     */
    @Test
    public void testSimpleMapOffset() {
        byte[] grid = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        AStarPathFinder aStarPathFinder = new AStarPathFinder();
        aStarPathFinder.setCostMap(
                new FixedGridCostMap(CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED, 0.1, 4, 4, -20, -10, grid));

        // Numerous out-of-bounds paths, should all be null.
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(-20, -10), new CostMapPose(0, 0)));
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(-20, -11), new CostMapPose(-17, -7)));
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(-21, -10), new CostMapPose(-17, -7)));
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(-20, -10), new CostMapPose(-16, -7)));
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(-20, -10), new CostMapPose(-17, -6)));

        // A path form (-20, -10) to (-20, -9), which in the grid is (0, 0) to (0, 1)
        // Expected result: a straight path consisting of (-20, -10) to (-20, -9)
        Path path1 = aStarPathFinder.computePlan(new CostMapPose(-20, -10), new CostMapPose(-20, -9));
        Assert.assertNotNull(path1);
        Assert.assertEquals(path1.size(), 2);
        Assert.assertEquals(path1.get(0).getX(), -20);
        Assert.assertEquals(path1.get(0).getY(), -10);
        Assert.assertEquals(path1.get(1).getX(), -20);
        Assert.assertEquals(path1.get(1).getY(), -9);

        // A path form (-20, -10) to (-19, -10), which in the grid is (0, 0) to (1, 0)
        // Expected result: a straight path consisting of (-20, -10) to (-19, -10)
        Path path2 = aStarPathFinder.computePlan(new CostMapPose(-20, -10), new CostMapPose(-19, -10));
        Assert.assertNotNull(path2);
        Assert.assertEquals(path2.size(), 2);
        Assert.assertEquals(path2.get(0).getX(), -20);
        Assert.assertEquals(path2.get(0).getY(), -10);
        Assert.assertEquals(path2.get(1).getX(), -19);
        Assert.assertEquals(path2.get(1).getY(), -10);

        // A path form (-20, -10) to (-17, -7), which in the grid is (0, 0) to (3, 3)
        // Expected result: a diagonal path consisting of (-20, -10) -> (-19, -9) -> (-18, -8) -> (-17, -7)
        Path path3 = aStarPathFinder.computePlan(new CostMapPose(-20, -10), new CostMapPose(-17, -7));
        Assert.assertNotNull(path3);
        Assert.assertEquals(path3.size(), 4);
        Assert.assertEquals(path3.get(0).getX(), -20);
        Assert.assertEquals(path3.get(0).getY(), -10);
        Assert.assertEquals(path3.get(1).getX(), -19);
        Assert.assertEquals(path3.get(1).getY(), -9);
        Assert.assertEquals(path3.get(2).getX(), -18);
        Assert.assertEquals(path3.get(2).getY(), -8);
        Assert.assertEquals(path3.get(3).getX(), -17);
        Assert.assertEquals(path3.get(3).getY(), -7);
    }

    /**
     * Test a map with a wall
     */
    @Test
    public void testWall() {
        byte[] grid1 = {
                0,   127,   0,   0,
                0,   127,   0,   0,
                127, 127, 127, 127,
                0,   127,   0,   0};
        byte[] grid2 = {
                0,   127,   0,   0,
                0,     0,   0,   0,
                127, 127, 127, 127,
                0,   127,   0,   0};

        AStarPathFinder aStarPathFinder = new AStarPathFinder();
        aStarPathFinder.setCostMap(new FixedGridCostMap(CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED, 0.1, 4, 4, 0, 0, grid1));

        // Test out-of-bounds, should all be null
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(-1, -1), new CostMapPose(0, 1)));
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(10, 10), new CostMapPose(0, 1)));

        // Test impossible path because the wall along (*, 2) blocks the path
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(0, 0), new CostMapPose(0, 3)));

        // Test with goal inside an obstacle since (1, 0) is blocked
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(0, 0), new CostMapPose(1, 0)));

        // Test with start inside an obstacle since (1, 0) is blocked
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(1, 0), new CostMapPose(0, 0)));

        // Test impossible path because the wall along (1, *) blocks the path
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(0, 0), new CostMapPose(2, 0)));

        // Test impossible path because the wall along (*, 2) blocks the path
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(2, 0), new CostMapPose(0, 0)));

        // Test impossible path because the wall along (*, 2) blocks the path
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(3, 1), new CostMapPose(0, 1)));

        // Punch a hole in the wall at (1, 1)
        aStarPathFinder.setCostMap(new FixedGridCostMap(CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED, 0.1, 4, 4, 0, 0, grid2));

        // Plan from (0, 0) to (2, 0). Expected result:
        // (0, 0)    [127]    (2, 0)      [0]
        //       \            /
        //         \        /
        //           \    /
        //   [0]    (1, 1)      [0]       [0]
        //
        //
        //
        //  [127]    [127]     [127]      [0]
        //
        //
        //
        //   [0]     [127]      [0]       [0]
        Path path1 = aStarPathFinder.computePlan(new CostMapPose(0, 0), new CostMapPose(2, 0));
        Assert.assertNotNull(path1);
        Assert.assertEquals(path1.size(), 3);
        Assert.assertEquals(path1.get(0).getX(), 0);
        Assert.assertEquals(path1.get(0).getY(), 0);
        Assert.assertEquals(path1.get(1).getX(), 1);
        Assert.assertEquals(path1.get(1).getY(), 1);
        Assert.assertEquals(path1.get(2).getX(), 2);
        Assert.assertEquals(path1.get(2).getY(), 0);

        // Plan from (2, 0) to (0, 0). Expected result:
        // (0, 0)    [127]    (2, 0)      [0]
        //       \            /
        //         \        /
        //           \    /
        //   [0]    (1, 1)      [0]       [0]
        //
        //
        //
        //  [127]    [127]     [127]      [0]
        //
        //
        //
        //   [0]     [127]      [0]       [0]
        Path path2 = aStarPathFinder.computePlan(new CostMapPose(2, 0), new CostMapPose(0, 0));
        Assert.assertNotNull(path2);
        Assert.assertEquals(path2.size(), 3);
        Assert.assertEquals(path2.get(0).getX(), 2);
        Assert.assertEquals(path2.get(0).getY(), 0);
        Assert.assertEquals(path2.get(1).getX(), 1);
        Assert.assertEquals(path2.get(1).getY(), 1);
        Assert.assertEquals(path2.get(2).getX(), 0);
        Assert.assertEquals(path2.get(2).getY(), 0);

        // Plan from (3, 1) to (0, 1). Expected result:
        //   [0]     [127]      [0]       [0]
        //
        //
        //
        // (0, 1)----(1, 1)----(2, 1)----(3, 1)
        //
        //
        //
        //  [127]    [127]     [127]      [0]
        //
        //
        //
        //   [0]     [127]      [0]       [0]
        Path path3 = aStarPathFinder.computePlan(new CostMapPose(3, 1), new CostMapPose(0, 1));
        Assert.assertNotNull(path3);
        Assert.assertEquals(path3.size(), 4);
        Assert.assertEquals(path3.get(0).getX(), 3);
        Assert.assertEquals(path3.get(0).getY(), 1);
        Assert.assertEquals(path3.get(1).getX(), 2);
        Assert.assertEquals(path3.get(1).getY(), 1);
        Assert.assertEquals(path3.get(2).getX(), 1);
        Assert.assertEquals(path3.get(2).getY(), 1);
        Assert.assertEquals(path3.get(3).getX(), 0);
        Assert.assertEquals(path3.get(3).getY(), 1);
    }


    /**
     * Test a map with high-cost region in the center leading to indirect paths
     */
    @Test
    public void testCostlyCenter() {
        byte[] grid = {
                0, 100, 100, 0,
                0, 100, 100, 0,
                0, 100, 100, 0,
                0, 0, 0, 0};

        AStarPathFinder aStarPathFinder = new AStarPathFinder();
        aStarPathFinder.setCostMap(new FixedGridCostMap(CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED, 0.1, 4, 4, 0, 0, grid));

        // Plan from (0, 0) to (3, 0). Expected result:
        //  (0, 0)   [100]    [100]     (3, 0)
        //    |                           |
        //    |                           |
        //    |                           |
        //  (0, 1)   [100]    [100]     (3, 1)
        //    |                           |
        //    |                           |
        //    |                           |
        //  (0, 2)   [100]    [100]     (3, 2)
        //     \                        /
        //       \                    /
        //         \                /
        //    [0]   (1, 3)----(2, 3)      [0]
        Path path1 = aStarPathFinder.computePlan(new CostMapPose(0, 0), new CostMapPose(3, 0));
        Assert.assertNotNull(path1);
        Assert.assertEquals(path1.size(), 8);
        Assert.assertEquals(path1.get(0).getX(), 0);
        Assert.assertEquals(path1.get(0).getY(), 0);
        Assert.assertEquals(path1.get(1).getX(), 0);
        Assert.assertEquals(path1.get(1).getY(), 1);
        Assert.assertEquals(path1.get(2).getX(), 0);
        Assert.assertEquals(path1.get(2).getY(), 2);
        Assert.assertEquals(path1.get(3).getX(), 1);
        Assert.assertEquals(path1.get(3).getY(), 3);
        Assert.assertEquals(path1.get(4).getX(), 2);
        Assert.assertEquals(path1.get(4).getY(), 3);
        Assert.assertEquals(path1.get(5).getX(), 3);
        Assert.assertEquals(path1.get(5).getY(), 2);
        Assert.assertEquals(path1.get(6).getX(), 3);
        Assert.assertEquals(path1.get(6).getY(), 1);
        Assert.assertEquals(path1.get(7).getX(), 3);
        Assert.assertEquals(path1.get(7).getY(), 0);

        // Plan from (0, 0) to (2, 0). Expected result:
        //  (0, 0)   [100]   (2, 0)-----(3, 0)
        //    |                           |
        //    |                           |
        //    |                           |
        //  (0, 1)   [100]    [100]     (3, 1)
        //    |                           |
        //    |                           |
        //    |                           |
        //  (0, 2)   [100]    [100]     (3, 2)
        //     \                        /
        //       \                    /
        //         \                /
        //    [0]   (1, 3)----(2, 3)      [0]
        Path path2 = aStarPathFinder.computePlan(new CostMapPose(0, 0), new CostMapPose(2, 0));
        Assert.assertNotNull(path2);
        Assert.assertEquals(path2.size(), 9);
        Assert.assertEquals(path2.get(0).getX(), 0);
        Assert.assertEquals(path2.get(0).getY(), 0);
        Assert.assertEquals(path2.get(1).getX(), 0);
        Assert.assertEquals(path2.get(1).getY(), 1);
        Assert.assertEquals(path2.get(2).getX(), 0);
        Assert.assertEquals(path2.get(2).getY(), 2);
        Assert.assertEquals(path2.get(3).getX(), 1);
        Assert.assertEquals(path2.get(3).getY(), 3);
        Assert.assertEquals(path2.get(4).getX(), 2);
        Assert.assertEquals(path2.get(4).getY(), 3);
        Assert.assertEquals(path2.get(5).getX(), 3);
        Assert.assertEquals(path2.get(5).getY(), 2);
        Assert.assertEquals(path2.get(6).getX(), 3);
        Assert.assertEquals(path2.get(6).getY(), 1);
        Assert.assertEquals(path2.get(7).getX(), 3);
        Assert.assertEquals(path2.get(7).getY(), 0);
        Assert.assertEquals(path2.get(8).getX(), 2);
        Assert.assertEquals(path2.get(8).getY(), 0);

        // Plan from (0, 0) to (2, 3). Expected result:
        //  (0, 0)   [100]    [100]       [0]
        //    |
        //    |
        //    |
        //  (0, 1)   [100]    [100]       [0]
        //    |
        //    |
        //    |
        //  (0, 2)   [100]    [100]       [0]
        //     \
        //       \
        //         \
        //    [0]   (1, 3)----(2, 3)      [0]
        Path path3 = aStarPathFinder.computePlan(new CostMapPose(0, 0), new CostMapPose(2, 3));
        Assert.assertNotNull(path3);
        Assert.assertEquals(path3.size(), 5);
        Assert.assertEquals(path3.get(0).getX(), 0);
        Assert.assertEquals(path3.get(0).getY(), 0);
        Assert.assertEquals(path3.get(1).getX(), 0);
        Assert.assertEquals(path3.get(1).getY(), 1);
        Assert.assertEquals(path3.get(2).getX(), 0);
        Assert.assertEquals(path3.get(2).getY(), 2);
        Assert.assertEquals(path3.get(3).getX(), 1);
        Assert.assertEquals(path3.get(3).getY(), 3);
        Assert.assertEquals(path3.get(4).getX(), 2);
        Assert.assertEquals(path3.get(4).getY(), 3);
    }


    /**
     * Test a map with a wall offset
     */
    @Test
    public void testWallOffset() {
        byte[] grid1 = {
                0,   127,   0,   0,
                0,   127,   0,   0,
                127, 127, 127, 127,
                0,   127,   0,   0};
        byte[] grid2 = {
                0,   127,   0,   0,
                0,     0,   0,   0,
                127, 127, 127, 127,
                0,   127,   0,   0};

        AStarPathFinder aStarPathFinder = new AStarPathFinder();
        aStarPathFinder.setCostMap(new FixedGridCostMap(CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED, 0.1, 4, 4, 20, 10, grid1));

        // Out-of-bounds points should return no path
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(-1, -1), new CostMapPose(20, 11)));
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(30, 20), new CostMapPose(20, 11)));

        // Paths blocked by wall at (*, 12)
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(20, 10), new CostMapPose(20, 13)));

        // Path ends in an obstacle and is rejected
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(20, 10), new CostMapPose(21, 10)));

        // Path starts in an obstacle and is rejected
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(21, 10), new CostMapPose(20, 10)));

        // Path rejected because it is blocked by wall at (21, *)
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(20, 10), new CostMapPose(22, 10)));

        // Paths blocked by wall at (*, 12)
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(22, 10), new CostMapPose(20, 10)));

        // Paths blocked by wall at (*, 12)
        Assert.assertNull(aStarPathFinder.computePlan(new CostMapPose(23, 11), new CostMapPose(20, 11)));

        // Punch a hole in the wall at (21, 11)
        aStarPathFinder.setCostMap(new FixedGridCostMap(CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED, 0.1, 4, 4, 20, 10, grid2));

        // Plan from (20, 10) to (22, 10). Expected result:
        // (20, 10)      [0]      [127]      [0]
        //       \
        //         \
        //           \
        //  [127]     (21, 11)   [127]       [0]
        //           /
        //         /
        //       /
        // (22, 10)     [0]      [127]       [0]
        //
        //
        //
        //   [0]        [0]      [127]       [0]
        Path path1 = aStarPathFinder.computePlan(new CostMapPose(20, 10), new CostMapPose(22, 10));
        Assert.assertNotNull(path1);
        Assert.assertEquals(path1.size(), 3);
        Assert.assertEquals(path1.get(0).getX(), 20);
        Assert.assertEquals(path1.get(0).getY(), 10);
        Assert.assertEquals(path1.get(1).getX(), 21);
        Assert.assertEquals(path1.get(1).getY(), 11);
        Assert.assertEquals(path1.get(2).getX(), 22);
        Assert.assertEquals(path1.get(2).getY(), 10);

        // Plan from (22, 10) to (20, 10). Expected result:
        // (20, 10)   [127]   (22, 10)      [0]
        //       \            /
        //         \        /
        //           \    /
        //   [0]    (21, 11)      [0]       [0]
        //
        //
        //
        //  [127]    [127]       [127]      [0]
        //
        //
        //
        //   [0]     [127]        [0]       [0]
        Path path2 = aStarPathFinder.computePlan(new CostMapPose(22, 10), new CostMapPose(20, 10));
        Assert.assertNotNull(path2);
        Assert.assertEquals(path2.size(), 3);
        Assert.assertEquals(path2.get(0).getX(), 22);
        Assert.assertEquals(path2.get(0).getY(), 10);
        Assert.assertEquals(path2.get(1).getX(), 21);
        Assert.assertEquals(path2.get(1).getY(), 11);
        Assert.assertEquals(path2.get(2).getX(), 20);
        Assert.assertEquals(path2.get(2).getY(), 10);

        // Plan from (23, 11) to (20, 11). Expected result:
        // (20, 10)   [127]   (22, 10)      [0]
        //       \            /
        //         \        /
        //           \    /
        //   [0]    (21, 11)      [0]       [0]
        //
        //
        //
        //  [127]    [127]       [127]      [0]
        //
        //
        //
        //   [0]     [127]        [0]       [0]
        Path path3 = aStarPathFinder.computePlan(new CostMapPose(23, 11), new CostMapPose(20, 11));
        Assert.assertNotNull(path3);
        Assert.assertEquals(path3.size(), 4);
        Assert.assertEquals(path3.get(0).getX(), 23);
        Assert.assertEquals(path3.get(0).getY(), 11);
        Assert.assertEquals(path3.get(1).getX(), 22);
        Assert.assertEquals(path3.get(1).getY(), 11);
        Assert.assertEquals(path3.get(2).getX(), 21);
        Assert.assertEquals(path3.get(2).getY(), 11);
        Assert.assertEquals(path3.get(3).getX(), 20);
        Assert.assertEquals(path3.get(3).getY(), 11);
    }

    /**
     * Test finding 100 paths for benchmarking purposes
     */
    @Test
    public void testBenchmark() {
        byte[] grid = new byte[100 * 100];
        for (int x = 0; x < 100; x++) {
            grid[50 * 100 + x] = 127;
        }

        AStarPathFinder aStarPathFinder = new AStarPathFinder();
        aStarPathFinder.setCostMap(new FixedGridCostMap(CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED, 0.1, 100, 100, 0, 0, grid));
        for (int i = 0; i < 100; i++) {
            // Path is along (*, 0) from (0, 0) to (99, 0)
            Path path1 = aStarPathFinder.computePlan(new CostMapPose(0, 0), new CostMapPose(99, 0));
            Assert.assertNotNull(path1);
            Assert.assertEquals(path1.size(), 100);
            for (int x = 0; x < 100; x++) {
                Assert.assertEquals(path1.get(x).getX(), x);
                Assert.assertEquals(path1.get(x).getY(), 0);
            }

            // Path crosses the wall along (*, 50)
            Path path2 = aStarPathFinder.computePlan(new CostMapPose(0, 0), new CostMapPose(99, 99));
            Assert.assertNull(path2);
        }
    }
}
