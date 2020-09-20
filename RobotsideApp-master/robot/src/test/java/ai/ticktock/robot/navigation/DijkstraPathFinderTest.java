package ai.cellbots.robot.navigation;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.costmap.FixedGridCostMap;
import ai.cellbots.robot.costmap.CostMapPose;

public class DijkstraPathFinderTest {
    CostMapPose origin = new CostMapPose(0, 0);
    FixedGridCostMap map;
    DijkstraPathFinder subject;
    byte[] grid = {0, 0, 0, 0, 0,
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0};

    @Before
    public void setUp() {
        subject = new DijkstraPathFinder();
        map = new FixedGridCostMap(CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED, 1.0, 5, 5, 0, 0, grid);
        subject.setCostMap(map);
    }

    // FIND PATHS WITH NO OBSTACLES

    // Find a path when origin and target are on the exact same location.
    // Should return a path containing only the origin.
    @Test
    public void testComputePlanOriginEqualsTarget() {
        CostMapPose target = map.discretize(0.0, 0.0);
        assertEquals(subject.computePlan(origin, target), new Path(new CostMapPose(0, 0)));
    }

    // Find the paths between a cell and all its adjacent cells.
    @Test
    public void testComputePlanToAdjacent() {
        CostMapPose origin = new CostMapPose(1, 1);

        // Origin: (1,1)
        // Target: (1,2)
        // Should return: (1,1)*--*(1,2)
        CostMapPose target = map.discretize(1.0, 2.0);
        CostMapPose[] pathElements = {
                new CostMapPose(1, 1),
                new CostMapPose(1, 2)};
        assertEquals(subject.computePlan(origin, target), new Path(pathElements));

        // Origin: (1,1)
        // Target: (0,2)
        // Should return:        (0,2)
        //                         *
        //                        /
        //                  (1,1)*
        pathElements = new CostMapPose[]{new CostMapPose(1, 1), new CostMapPose(0, 2)};
        assertEquals(subject.computePlan(origin, new CostMapPose(0, 2)), new Path(pathElements));

        // Origin: (1,0)
        // Target: (0,1)
        // Should return:   (0,1)
        //                    *
        //                    |
        //                    *
        //                  (1,0)
        pathElements = new CostMapPose[]{new CostMapPose(1, 1), new CostMapPose(0, 1)};
        assertEquals(subject.computePlan(origin, new CostMapPose(0, 1)), new Path(pathElements));

        // Origin: (1,1)
        // Target: (0,0)
        // Should return:   (0,0)
        //                    *
        //                     \
        //                      *(1,1)
        pathElements = new CostMapPose[]{new CostMapPose(1, 1), new CostMapPose(0, 0)};
        assertEquals(subject.computePlan(origin, new CostMapPose(0, 0)), new Path(pathElements));

        // Origin: (1,1)
        // Target: (1,0)
        // Should return: (1,0)*--*(1,1)
        pathElements = new CostMapPose[]{new CostMapPose(1, 1), new CostMapPose(1, 0)};
        assertEquals(subject.computePlan(origin, new CostMapPose(1, 0)), new Path(pathElements));
    }

    // Find a path when origin and target are on the same vertical line.
    // Origin: (0,0)
    // Target: (4,0)
    // Expected output:   (0,0)*--*(1,0)*--*(2,0)*--*(3,0)*--*(4,0)
    @Test
    public void testComputePlanVertical() {
        CostMapPose origin = new CostMapPose(0, 0);

        CostMapPose[] pathElements = {
                new CostMapPose(0, 0),
                new CostMapPose(1, 0),
                new CostMapPose(2, 0),
                new CostMapPose(3, 0),
                new CostMapPose(4, 0)};
        assertEquals(subject.computePlan(origin, new CostMapPose(4, 0)),
                new Path(pathElements));
    }

    // Find a path when origin and target are on the same horizontal line.
    // Origin: (0,0)
    // Target: (0,4)
    // Expected output:   (0,0)
    //                      *
    //                      |
    //                      *
    //                    (0,1)
    //                      *
    //                      |
    //                      *
    //                    (0,2)
    //                      *
    //                      |
    //                      *
    //                    (0,3)
    //                      *
    //                      |
    //                      *
    //                    (0,4)
    @Test
    public void testComputePlanHorizontal() {
        CostMapPose origin = new CostMapPose(0, 0);

        CostMapPose[] pathElements = {
                new CostMapPose(0, 0),
                new CostMapPose(0, 1),
                new CostMapPose(0, 2),
                new CostMapPose(0, 3),
                new CostMapPose(0, 4)};
        assertEquals(subject.computePlan(origin, new CostMapPose(0, 4)),
                new Path(pathElements));
    }

    // Find a path when origin and target are on opposite corners of the map.
    // Origin: (0,0)
    // Target: (4,4)
    // Expected output:      (0,0)
    //                          *
    //                           \
    //                            *
    //                            (1,1)
    //                              *
    //                               \
    //                                *
    //                                (2,2)
    //                                  *
    //                                   \
    //                                    *
    //                                    (3,3)
    //                                      *
    //                                       \
    //                                        *
    //                                        (4,4)
    @Test
    public void testComputePlanDiagonal() {
        CostMapPose origin = new CostMapPose(0, 0);

        CostMapPose[] pathElements = {
                new CostMapPose(0, 0),
                new CostMapPose(1, 1),
                new CostMapPose(2, 2),
                new CostMapPose(3, 3),
                new CostMapPose(4, 4)};
        assertEquals(subject.computePlan(origin, new CostMapPose(4, 4)),
                new Path(pathElements));
    }

    // Origin: (0,0)
    // Target: (2,0)
    // Obstacle at: (1,0)
    // Should return:
    //
    //        (0,0)...X..(2,0)
    //          .  *     *
    //          .   \   /
    //          .    * *
    //          X   (1,1)
    //
    //
    //
    @Test
    public void testFindPathWithObstacles45Deg() {
        CostMapPose origin = new CostMapPose(0, 0);

        byte[] occupancyGrid = {
                0, 127, 0,
                0,   0, 0,
                0,   0, 0};

        map = new FixedGridCostMap(CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED, 1.0, 3, 3, 0, 0, occupancyGrid);
        subject.setCostMap(map);

        CostMapPose[] pathElements = {
                new CostMapPose(0, 0),
                new CostMapPose(1, 1),
                new CostMapPose(2, 0)};
        assertEquals(subject.computePlan(origin, new CostMapPose(2, 0)),
                new Path(pathElements));
    }


    // Origin: (4,3)
    // Target: (4,1)
    // Obstacle wall at: (1,2)--(2,2)--(3,2)--(4,2)
    // Should return:
    //                    (0,2)
    //                      *
    //                     / \
    //                    * . *
    //                (1,1) . (1,3)
    //                  *   .   *
    //                  |   .   |
    //                  *   .   *
    //                (2,1) . (2,3)
    //                  *   .   *
    //                  |   .   |
    //                  *   .   *
    //                (3,1) . (3,3)
    //                  *   .   *
    //                  |   .   |
    //                  *   .   *
    //                (4,1) . (4,3)
    @Test
    public void testFindPathWithVerticalWall() {
        CostMapPose origin = new CostMapPose(3, 4);

        byte[] occupancyGrid = {
                0, 0, 0, 0, 0,
                0, 0, 127, 0, 0,
                0, 0, 127, 0, 0,
                0, 0, 127, 0, 0,
                0, 0, 127, 0, 0};

        map = new FixedGridCostMap(CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED, 1.0, 5, 5, 0, 0, occupancyGrid);
        subject.setCostMap(map);

        CostMapPose[] pathElements = {
                new CostMapPose(3, 4),
                new CostMapPose(3, 3),
                new CostMapPose(3, 2),
                new CostMapPose(3, 1),
                new CostMapPose(2, 0),
                new CostMapPose(1, 1),
                new CostMapPose(1, 2),
                new CostMapPose(1, 3),
                new CostMapPose(1, 4)};
        assertEquals(subject.computePlan(origin, new CostMapPose(1, 4)),
                new Path(pathElements));
    }

    // Origin: (0,0)
    // Target: (4,4)
    // Map cells are mostly occupied. There is just a single path between origin and target,
    // which is not the shortest one.
    // Expected output:       (0,0)*--*(1,0)*--*(2,0)*--*(3,0)
    //                          .        .        .        *
    //                          .        .        .        .  \
    //                          .        .        .        .    \
    //                          .................................*(2,1)
    //                          .        .        .        .        *
    //                          .        .        .        .        |
    //                          .        .        .        .        *
    //                          ................................. (3,2)
    //                          .        .        .        .        *
    //                          .        .        .        .        |
    //                          .        .        .        .        *
    //                          ................................. (4,2)
    //                          .        .        .        .        *
    //                          .        .        .        .        |
    //                          .        .        .        .        *
    //                          ................................. (4,4)
    //
    @Test
    public void testFindPathShapeLFreePath() {
        CostMapPose origin = new CostMapPose(0, 0);

        byte[] occupancyGrid = {
                0,   0,  0,  0, 0,
                15, 15, 15, 15, 0,
                15, 15, 15, 15, 0,
                15, 15, 15, 15, 0,
                15, 15, 15, 15, 0};

        map = new FixedGridCostMap(CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED, 1.0, 5, 5, 0, 0, occupancyGrid);
        subject.setCostMap(map);

        CostMapPose[] pathElements = {
                new CostMapPose(0, 0),
                new CostMapPose(1, 0),
                new CostMapPose(2, 0),
                new CostMapPose(3, 0),
                new CostMapPose(4, 1),
                new CostMapPose(4, 2),
                new CostMapPose(4, 3),
                new CostMapPose(4, 4)};
        assertEquals(subject.computePlan(origin, new CostMapPose(4, 4)),
                new Path(pathElements));
    }

    // Origin: (0,0)
    // Target: (4,4)
    // Map cells have costs in a range from MIN_COST to MAX COST. The algorithm should find the
    // path with the lowest cost from origin to target, which is not the shortest one.
    // Expected output:       (0,0) .....................
    //                          . *   .     .     .     .
    //                          .  \  .     .     .     .
    //                          .   * .     .     .     .
    //                          ....(1,1)................
    //                          .     *     .     .     .
    //                          .     |     .     .     .
    //                          .     *     .     .     .
    //                          ....(1,2)................
    //                          .     . *   .     .     .
    //                          .     .  \  .     .     .
    //                          .     .   * .     .     .
    //                          ..........(2,3) .........
    //                          .     .     . *   .     .
    //                          .     .     .  \  .     .
    //                          .     .     .   * .     .
    //                          ...............(3,4)*-*(4,4)
    //
    @Test
    public void testFindPathMultipleCosts() {
        CostMapPose origin = new CostMapPose(0, 0);

        byte[] occupancyGrid = {
                5, 10, 15, 15, 15,
                15, 5, 15, 18, 15,
                8, 6, 75, 15, 0,
                13, 15, 5, 111, 0,
                1, 34, 78, 7, 0};

        map = new FixedGridCostMap(CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED, 1.0, 5, 5, 0, 0, occupancyGrid);
        subject.setCostMap(map);

        CostMapPose[] pathElements = {
                new CostMapPose(0, 0),
                new CostMapPose(1, 1),
                new CostMapPose(1, 2),
                new CostMapPose(2, 3),
                new CostMapPose(3, 4),
                new CostMapPose(4, 4)};
        assertEquals(subject.computePlan(origin, new CostMapPose(4, 4)),
                new Path(pathElements));
    }
}
