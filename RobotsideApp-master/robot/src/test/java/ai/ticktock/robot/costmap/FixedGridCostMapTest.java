package ai.cellbots.robot.costmap;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ai.cellbots.common.Transform;

public class FixedGridCostMapTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testMakeGrid() {
        GridCostMap subject;
        double[][] positions;
        double resolution;
        int dx;
        int dy;

        /* General test */
        resolution = 0.2;
        subject = new FixedGridCostMap(null, resolution, 0, 0, 0, 0, new byte[0]);
        // Check that the cost map has no poses yet.
        assertTrue((subject.getAllCostMapPoses()).isEmpty());
        // Make a grid with initial costs of 10
        positions = new double[][]{{0, 0}, {1.17, 2.51}};
        subject.makeGrid(positions, (byte) 10);
        // Calculate number of rows and columns
        dx = (int) Math.ceil(1.17 / resolution);
        dy = (int) Math.ceil(2.51 / resolution);
        // Check that the grid was created with the correct size
        assertEquals(subject.getUpperXLimit(), dx);
        assertEquals(subject.getUpperYLimit(), dy);
        // Check that all cells have been initialized with the desired value
        for (int i = 0; i <= dx - 1; i++) {
            for (int j = 0; j <= dy - 1; j++) {
                assertEquals(subject.getCost(new CostMapPose(i, j)), (byte) 10);
            }
        }
        // Ensure that the CostMap requires inflation.
        assertTrue(subject.requiresInflation());

        /* Test when 2 vertices are in the same cell */
        resolution = 0.2;
        subject = new FixedGridCostMap(null, resolution, 0, 0, 0, 0, new byte[0]);
        // Check that the cost map has no poses yet.
        assertTrue((subject.getAllCostMapPoses()).isEmpty());
        // Make a grid with initial costs of 20
        positions = new double[][]{{0, 0}, {0.19, 0.19}};
        subject.makeGrid(positions, (byte) 20);
        dx = 1;
        dy = 1;
        // Check that the grid was created with the correct size
        assertEquals(subject.getUpperXLimit(), dx);
        assertEquals(subject.getUpperYLimit(), dy);
        // Check that all cells have been initialized with the desired value
        for (int i = 0; i <= dx - 1; i++) {
            for (int j = 0; j <= dy - 1; j++) {
                assertEquals(subject.getCost(new CostMapPose(i, j)), (byte) 20);
            }
        }
        // Ensure that the CostMap requires inflation.
        assertTrue(subject.requiresInflation());

        /* Test when flooring operation gives you a point exactly on the edge */
        resolution = 1;
        subject = new FixedGridCostMap(null, resolution, 0, 0, 0, 0, new byte[0]);
        // Check that the cost map has no poses yet.
        assertTrue((subject.getAllCostMapPoses()).isEmpty());
        // Make a grid with initial costs of 10
        positions = new double[][]{{0, 0}, {3, 3}};
        subject.makeGrid(positions, (byte) 50);
        // location 3 divided by resolution 1 is exactly 3, so we should get 4
        dx = 4;
        dy = 4;
        // Check that the grid was created with the correct size
        assertEquals(subject.getUpperXLimit(), dx);
        assertEquals(subject.getUpperYLimit(), dy);
        // Check that all cells have been initialized with the desired value
        for (int i = 0; i <= dx - 1; i++) {
            for (int j = 0; j <= dy - 1; j++) {
                assertEquals(subject.getCost(new CostMapPose(i, j)), (byte) 50);
            }
        }
        // Ensure that the CostMap requires inflation.
        assertTrue(subject.requiresInflation());

        /* Test when flooring operation gives you a point exactly on the edge */
        resolution = 0.2;
        subject = new FixedGridCostMap(null, resolution, 0, 0, 0, 0, new byte[0]);
        // Check that the cost map has no poses yet.
        assertTrue((subject.getAllCostMapPoses()).isEmpty());
        // Make a grid with initial costs of 10
        positions = new double[][]{{0, 0}, {1.4, 2.8}};
        subject.makeGrid(positions, (byte) 127);
        // location 1.4 divided by resolution 0.2 is exactly 7 so we should get endX = 8
        // location 2.8 divided by resolution 0.2 is exactly 14 so we should get endY = 15
        dx = 8;
        dy = 15;
        // Check that the grid was created with the correct size
        assertEquals(subject.getUpperXLimit(), dx);
        assertEquals(subject.getUpperYLimit(), dy);
        // Check that all cells have been initialized with the desired value
        for (int i = 0; i <= dx - 1; i++) {
            for (int j = 0; j <= dy - 1; j++) {
                assertEquals(subject.getCost(new CostMapPose(i, j)), (byte) 127);
            }
        }
        // Ensure that the CostMap requires inflation.
        assertTrue(subject.requiresInflation());
    }

    @Test
    public void testInflateCost() {
        // Test inflating costs for cell with a radius equal to the resolution. Should inflate only
        // in vertical and horizontal directions from center, but not diagonal.
        /* Center at (1,1)

        (0,0) (0,1) (0,2)              0   54  0
             \  |  /                    \  |  /
              \ | /          Costs       \ | /
        (1,0)---X---(1,2)    ---->    54---X---54
              / | \                      / | \
             /  |  \                    /  |  \
        (2,0) (2,1) (2,2)              0  54   0
        */
        GridCostMap subject = new FixedGridCostMap(null, 0.2, 3, 3, 0, 0, new byte[9]);
        byte[] expectedCosts = new byte[]{
                 0, 54,  0,
                54, 54, 54,
                 0, 54,  0};
        subject.inflateCost(new CostMapPose(1, 1), 0.2, (byte) 54);
        assertArrayEquals(expectedCosts, subject.getFullCostRegion());

        // Test inflating costs for cell with a bigger radius, which covers both of the grid's
        // dimensions.
        /* Center at (2,2)

        (0,0) (0,1) (0,2) (0,3) (0,4)              0    0   54   0   0

        (1,0) (1,1) (1,2) (1,3) (1,4)              0   54   54   54  0
                                         Costs
        (2,0) (2,1) (2,2) (2,3) (2,4)    ---->     54  54   54   54  54

        (3,0) (3,1) (3,2) (3,3) (3,4)              0   54   54   54  0

        (4,0) (4,1) (4,2) (4,3) (4,4)              0    0   54   0   0
        */
        subject = new FixedGridCostMap(null, 1.0, 5, 5, 0, 0, new byte[25]);
        expectedCosts = new byte[]{
                 0,  0, 54,  0,  0,
                 0, 54, 54, 54,  0,
                54, 54, 54, 54, 54,
                 0, 54, 54, 54,  0,
                 0,  0, 54,  0,  0};
        subject.inflateCost(new CostMapPose(2, 2), 1.5, (byte) 54);
        assertArrayEquals(subject.getFullCostRegion(), expectedCosts);

        // Test inflating costs for a cell with a given value and then inflating a cell which is
        // next to it, having an overlap. The last cost should replace the first one in the
        // overlapping cells.
        /* Center at (2,2)

        (0,0) (0,1) (0,2) (0,3) (0,4)             0    0   0   0   0           0   0    0    0   0

        (1,0) (1,1) (1,2) (1,3) (1,4)             0   54   0   0   0           0   54   0   17   0
                                       1st Costs                     2nd Costs
        (2,0) (2,1) (2,2) (2,3) (2,4)    ---->    54  54   54  0   0   ---->   54  54  17   17  17

        (3,0) (3,1) (3,2) (3,3) (3,4)             0   54   0   0   0           0   54   0   17   0

        (4,0) (4,1) (4,2) (4,3) (4,4)             0    0   0   0   0           0   0    0    0   0
        */
        subject = new FixedGridCostMap(null, 1.0, 5, 5, 0, 0, new byte[25]);
        expectedCosts = new byte[]{
                 0,  0,  0,  0,  0,
                 0, 54,  0, 17,  0,
                54, 54, 17, 17, 17,
                 0, 54,  0, 17,  0,
                 0,  0,  0,  0,  0};
        subject.inflateCost(new CostMapPose(1, 2), 1.0, (byte) 54);
        subject.inflateCost(new CostMapPose(3, 2), 1.0, (byte) 17);
        assertArrayEquals(subject.getFullCostRegion(), expectedCosts);

        // Test inflating costs for a cell which lies on the map's boundaries. Should set costs for
        // those cells inside of the map and not attempt to inflate those that fall out of it.
        /* Origin of the map (0,0)
                             Costs
          X---(0,1)          ---->    X---54
          | \                         | \
          |  \                        |  \
        (1,0) (1,1)                  54   0
        */
        // Make a new 2x2 grid
        subject = new FixedGridCostMap(null, 0.2, 2, 2, 0, 0, new byte[4]);
        expectedCosts = new byte[]{
                54, 54,
                54, 0};
        subject.inflateCost(new CostMapPose(0, 0), 0.2, (byte) 54);
        assertArrayEquals(subject.getFullCostRegion(), expectedCosts);

        // Ensure that the CostMap requires inflation.
        assertTrue(subject.requiresInflation());
    }

    @Test
    public void testSetInflatedCosts() {
        // Test inflating costs for cells which are next to each other. A path of connected costs
        // should be formed. Center cells to inflate are (2,2) and (3,3). A resolution of 1 is
        // chosen for an easier mapping.
        GridCostMap subject = new FixedGridCostMap(null, 1.0, 5, 5, 0, 0, new byte[25]);
        List<Transform> path = new ArrayList<>(Arrays.asList(
                new Transform[]{
                        new Transform(2.0, 2.0, 0.0, 0.0),
                        new Transform(3.0, 3.0, 0.0, 0.0)}));

        subject.setInflatedCosts(path, 1.0, (byte) 54);
        byte[] expectedCosts = new byte[]{
                0,  0,  0,  0,  0,
                0,  0, 54,  0,  0,
                0, 54, 54, 54,  0,
                0,  0, 54, 54, 54,
                0,  0,  0, 54,  0};
        assertArrayEquals(subject.getFullCostRegion(), expectedCosts);

        // Test inflating costs for cells which are on opposite corners of the map. No path of
        // connected costs should be formed. Center cells to inflate are (0,0) and (4,4).
        // A resolution of 1 is chosen for an easier mapping.
        subject = new FixedGridCostMap(null, 1.0, 5, 5, 0, 0, new byte[25]);
        path = new ArrayList<>(Arrays.asList(
                new Transform[]{
                        new Transform(0.0, 0.0, 0.0, 0.0),
                        new Transform(4.0, 4.0, 0.0, 0.0)}));

        subject.setInflatedCosts(path, 1.0, (byte) 54);
        expectedCosts = new byte[]{
                54, 54, 0,  0,  0,
                54,  0, 0,  0,  0,
                 0,  0, 0,  0,  0,
                 0,  0, 0,  0, 54,
                 0,  0, 0, 54, 54};
        assertArrayEquals(subject.getFullCostRegion(), expectedCosts);

        // Ensure that the CostMap requires inflation.
        assertTrue(subject.requiresInflation());
    }

    @Test
    public void testDiscretize() {
        // RESOLUTION = 1.0
        // Make a new 3x3 map, with a resolution of 1.0 and offset by (0.0, 0.0)
        GridCostMap subject = new FixedGridCostMap(null, 1.0, 3, 3, 0, 0, new byte[9]);

        // Check for boundaries of the coordinate (0,0)
        // Center
        assertEquals(subject.discretize(0.0, 0.0), new CostMapPose(0, 0));
        // Right
        assertEquals(subject.discretize(0.49, 0.0), new CostMapPose(0, 0));
        // Down - right
        assertEquals(subject.discretize(0.49, -0.5), new CostMapPose(0, -1));
        // Down
        assertEquals(subject.discretize(0.0, -0.5), new CostMapPose(0, -1));
        // Down - left
        assertEquals(subject.discretize(-0.5, -0.5), new CostMapPose(-1, -1));
        // Left
        assertEquals(subject.discretize(-0.5, 0.0), new CostMapPose(-1, 0));
        // Up - left
        assertEquals(subject.discretize(-0.5, 0.49), new CostMapPose(-1, 0));
        // Up
        assertEquals(subject.discretize(0.0, 0.49), new CostMapPose(0, 0));
        // Up - right
        assertEquals(subject.discretize(0.49, 0.49), new CostMapPose(0, 0));

        // Now check for transitions (Cannot test further transitions as they fall out of the map)
        // 0°
        assertEquals(subject.discretize(0.5, 0.0), new CostMapPose(0, 0));
        // 45°
        assertEquals(subject.discretize(0.5, 0.5), new CostMapPose(0, 0));
        // 90°
        assertEquals(subject.discretize(0.0, 0.5), new CostMapPose(0, 0));

        // Test for out of bounds
        assertEquals(subject.discretize(-0.51, 0.0), new CostMapPose(-1, 0));
        assertEquals(subject.discretize(0.0, -0.5), new CostMapPose(0, -1));

        // RESOLUTION = 0.2
        // Make a new 3x3 map, with a resolution of 0.2 and offset by (0.0, 0.0)
        subject = new FixedGridCostMap(null, 0.2, 3, 3, 0, 0, new byte[9]);

        // Check for boundaries of the coordinate (0,0)
        // Center
        assertEquals(subject.discretize(0.0, 0.0), new CostMapPose(0, 0));
        // Right
        assertEquals(subject.discretize(0.09, 0.0), new CostMapPose(0, 0));
        // Down - right
        assertEquals(subject.discretize(0.09, -0.1), new CostMapPose(0, -1));
        // Down
        assertEquals(subject.discretize(0.0, -0.1), new CostMapPose(0, -1));
        // Down - left
        assertEquals(subject.discretize(-0.1, -0.1), new CostMapPose(-1, -1));
        // Left
        assertEquals(subject.discretize(-0.1, 0.0), new CostMapPose(-1, 0));
        // Up - left
        assertEquals(subject.discretize(-0.1, 0.09), new CostMapPose(-1, 0));
        // Up
        assertEquals(subject.discretize(0.0, 0.09), new CostMapPose(0, 0));
        // Up - right
        assertEquals(subject.discretize(0.09, 0.09), new CostMapPose(0, 0));

        // Now check for transitions (Cannot test further transitions as they fall out of the map)
        // 0°
        assertEquals(subject.discretize(0.1, 0.0), new CostMapPose(0, 0));
        // 45°
        assertEquals(subject.discretize(0.1, 0.1), new CostMapPose(0, 0));
        // 90°
        assertEquals(subject.discretize(0.0, 0.1), new CostMapPose(0, 0));

        // Ensure that the CostMap requires inflation.
        assertTrue(subject.requiresInflation());
    }

    @Test
    public void testNeighborsFor() {
        // Make a new 3x3 map, with a resolution 1 and offset by (0, 0)
        GridCostMap subject = new FixedGridCostMap(null, 1.0, 3, 3, 0, 0, new byte[9]);
        Set<CostMapPose> neighbors;

        // Test central cell of the map. Find all 8 neighbors.
        /* Center of the map (1,1)

           (2,0) (2,1) (2,2)
                \  |  /
                 \ | /
           (1,0)---X---(1,2)
                 / | \
                /  |  \
           (0,0) (0,1) (0,2)
        */
        neighbors = new HashSet<>(Arrays.asList(
                new CostMapPose[]{
                        new CostMapPose(1, 2),
                        new CostMapPose(2, 2),
                        new CostMapPose(2, 1),
                        new CostMapPose(2, 0),
                        new CostMapPose(1, 0),
                        new CostMapPose(0, 0),
                        new CostMapPose(0, 1),
                        new CostMapPose(0, 2)}));
        assertTrue(neighbors.equals(
                new HashSet<>(subject.neighborsFor(new CostMapPose(1, 1), Byte.MAX_VALUE))));

        // Test map limits
        /* Lower center (0,1)

            (1,0) (1,1) (1,2)
                 \  |  /
                  \ | /
            (0,0)---X---(0,2)
        */
        neighbors = new HashSet<>(Arrays.asList(
                new CostMapPose[]{
                        new CostMapPose(0, 2),
                        new CostMapPose(1, 2),
                        new CostMapPose(1, 1),
                        new CostMapPose(1, 0),
                        new CostMapPose(0, 0)}));

        assertTrue(neighbors.equals(
                new HashSet<>(subject.neighborsFor(new CostMapPose(0, 1), Byte.MAX_VALUE))));

        /* Upper limit (2,1)

            (2,0)---X---(2,2)
                  / | \
                 /  |  \
            (1,0) (1,1) (1,2)
        */
        neighbors = new HashSet<>(Arrays.asList(
                new CostMapPose[]{
                        new CostMapPose(2, 2),
                        new CostMapPose(2, 0),
                        new CostMapPose(1, 0),
                        new CostMapPose(1, 1),
                        new CostMapPose(1, 2)}));

        assertTrue(neighbors.equals(
                new HashSet<>(subject.neighborsFor(new CostMapPose(2, 1), Byte.MAX_VALUE))));

        /* Lower-left corner (0,0)

            (1,0) (1,1)
              |  /
              | /
              X---(0,1)
        */
        neighbors = new HashSet<>(Arrays.asList(
                new CostMapPose[]{
                        new CostMapPose(0, 1),
                        new CostMapPose(1, 1),
                        new CostMapPose(1, 0)}));

        assertTrue(neighbors.equals(
                new HashSet<>(subject.neighborsFor(new CostMapPose(0, 0), Byte.MAX_VALUE))));

        /* Upper-right corner (2,2)

           (2,1)---X
                 / |
                /  |
           (1,1) (1,2)
        */
        neighbors = new HashSet<>(Arrays.asList(
                new CostMapPose[]{
                        new CostMapPose(2, 1),
                        new CostMapPose(1, 1),
                        new CostMapPose(1, 2)}));

        assertTrue(neighbors.equals(
                new HashSet<>(subject.neighborsFor(new CostMapPose(2, 2), Byte.MAX_VALUE))));

        // Ensure that the CostMap requires inflation.
        assertTrue(subject.requiresInflation());
    }

    @Test
    public void testNeighborsForWithCosts() {
        // Test central cell of the map, when cell (1,0) is occupied.
        // Find all 7 neighbors whose cost is below 100.
        /* Center of the map (1,1)

           (0,0)  xxx (2,0)
                \  |  /
                 \ | /
           (0,1)---X---(2,1)
                 / | \
                /  |  \
           (0,2) (1,2) (2,2)
        */
        // Make a new 3x3 map, with a resolution of 1 and offset by (0, 0).
        // Set (1, 0) as occupied.
        byte[] neighborsGrid = new byte[]{
                0, 127, 0,
                0,   0, 0,
                0,   0, 0};
        GridCostMap subject = new FixedGridCostMap(null, 1.0, 3, 3, 0, 0, neighborsGrid, false);

        Set<CostMapPose> neighbors;
        neighbors = new HashSet<>(Arrays.asList(
                new CostMapPose[]{
                        new CostMapPose(0, 0),
                        new CostMapPose(2, 0),
                        new CostMapPose(0, 1),
                        new CostMapPose(2, 1),
                        new CostMapPose(0, 2),
                        new CostMapPose(1, 2),
                        new CostMapPose(2, 2)}));
        assertTrue(neighbors.equals(
                new HashSet<>(subject.neighborsFor(new CostMapPose(1, 1), (byte) 100))));

        // Test central cell of the map, when cells (1,0), (0,2), (1,2) and (2,2) have a cost
        // equal to the max cost limit. Other cells have a value of maxCost.
        // Find all 4 free neighbors.
        /* Center of the map (1,1)

           (0,0)  xxx (0,2)
                \  |  /
                 \ | /
           (1,0)---X---(1,2)
                 / | \
                /  |  \
             xxx  xxx  xxx
        */
        neighborsGrid = new byte[]{
                100, 101, 100,
                100,   0, 100,
                101, 101, 101};
        subject = new FixedGridCostMap(null, 1.0, 3, 3, 0, 0, neighborsGrid);

        neighbors = new HashSet<>(Arrays.asList(
                new CostMapPose[]{
                        new CostMapPose(0, 0),
                        new CostMapPose(2, 0),
                        new CostMapPose(0, 1),
                        new CostMapPose(2, 1)}));
        assertTrue(neighbors.equals(
                new HashSet<>(subject.neighborsFor(new CostMapPose(1, 1), (byte) 100))));

        // Test central cell of the map. All cells are occupied, so there must be no free neighbors.
        /* Center of the map (1,1)

             xxx  xxx  xxx
                \  |  /
                 \ | /
             xxx---X---xxx
                 / | \
                /  |  \
             xxx  xxx  xxx
        */
        neighborsGrid = new byte[]{
                127, 127, 127,
                127,   0, 127,
                127, 127, 127};

        subject = new FixedGridCostMap(null, 1.0, 3, 3, 0, 0, neighborsGrid);
        List<CostMapPose> neighborsList = subject.neighborsFor(new CostMapPose(1, 1),
                (byte) 100);

        assertTrue(neighborsList.isEmpty());

        // Test central cell of the map, when adjacent cells have costs other than 0 or 127.
        // Find all neighbors whose cost is below 50.
        /* Center of the map (1,1)

           (0,0) (0,1) (0,2)           [13] [56]  [87]
                \  |  /                   \   |   /
                 \ | /         Costs       \  |  /
           (1,0)---X---(1,2)    --->  [111]---X---[43]
                 / | \                     /  |  \
                /  |  \                   /   |   \
           (2,0) (2,1) (2,2)           [25] [49]  [52]
        */
        neighborsGrid = new byte[]{
                 13, 56, 87,
                111,  0, 43,
                 25, 49, 52};
        subject = new FixedGridCostMap(null, 1.0, 3, 3, 0, 0, neighborsGrid);

        neighbors = new HashSet<>(Arrays.asList(
                new CostMapPose[]{
                        new CostMapPose(0, 0),
                        new CostMapPose(2, 1),
                        new CostMapPose(0, 2),
                        new CostMapPose(1, 2)}));
        assertTrue(neighbors.equals(
                new HashSet<>(subject.neighborsFor(new CostMapPose(1, 1), (byte) 50))));

        // Ensure that the CostMap requires inflation.
        assertTrue(subject.requiresInflation());
    }

    @Test
    public void testGetLocations() {
        // Make a new 1x1 map, with a discretization step of 1 and offset by (0, 0)
        GridCostMap subject = new FixedGridCostMap(null, 1.0, 1, 1, 0, 0, new byte[1], true);

        Set<CostMapPose> mapCoordinates = new HashSet<>();
        mapCoordinates.add(new CostMapPose(0, 0));
        assertTrue(mapCoordinates.equals(new HashSet<>(subject.getAllCostMapPoses())));

        // Ensure that the CostMap does not require inflation
        assertEquals(false, subject.requiresInflation());

        // Make a new 3x3 map, with a resolution of 1.0 and offset by (0, 0)
        subject = new FixedGridCostMap(null, 1.0, 3, 3, 0, 0, new byte[9], true);
        mapCoordinates = new HashSet<>(Arrays.asList(
                new CostMapPose(0, 0),
                new CostMapPose(0, 1),
                new CostMapPose(0, 2),
                new CostMapPose(1, 0),
                new CostMapPose(1, 1),
                new CostMapPose(1, 2),
                new CostMapPose(2, 0),
                new CostMapPose(2, 1),
                new CostMapPose(2, 2)));

        assertTrue(mapCoordinates.equals(new HashSet<>(subject.getAllCostMapPoses())));

        // Ensure that the CostMap requires inflation.
        assertEquals(false, subject.requiresInflation());
    }

    /* Draw a rectangular wall on the grid's boundaries, given by the vertices shown on the left.

        |---|---|---|                     |---|---|---|
        | * | * | * |                     |127|127|127|
        |---|---|---|                     |---|---|---|
        | * |   | * |   Expected output   |127| 0 |127|
        |---|---|---|       ----->        |---|---|---|
        | * |   | * |                     |127| 0 |127|
        |---|---|---|                     |---|---|---|
        | * | * | * |                     |127|127|127|
        |---|---|---|                     |---|---|---|
    */
    @Test
    public void testDrawPolygonOnGridBoundaryWall() {
        int[][] vertices = new int[][]{{0, 0}, {2, 0}, {2, 3}, {0, 3}};

        // Make a new 3x4 map, with a discretization step of 1 and offset by (0, 0)
        GridCostMap subject = new FixedGridCostMap(null, 1.0, 3, 4, 0, 0, new byte[12]);
        subject.drawPolygonOnGrid(vertices);

        byte[] expectedGrid = new byte[]{
                127, 127, 127,
                127,   0, 127,
                127,   0, 127,
                127, 127, 127};

        assertArrayEquals(expectedGrid, subject.getFullCostRegion());
    }

    /* Draw a triangle on the grid, given by the vertices shown on the left.

        |---|---|---|                     |---|---|---|
        | * |   |   |                     |127| 0 | 0 |
        |---|---|---|                     |---|---|---|
        |   |   |   |   Expected output   |127|127| 0 |
        |---|---|---|       ----->        |---|---|---|
        |   |   |   |                     |127|127|127|
        |---|---|---|                     |---|---|---|
        | * |   | * |                     |127|127|127|
        |---|---|---|                     |---|---|---|
    */
    @Test
    public void testDrawPolygonOnGridTriangle() {
        int[][] vertices = new int[][]{{0, 0}, {2, 3}, {0, 3}};

        // Make a new 3x4 map, with a discretization step of 1 and offset by (0, 0)
        GridCostMap subject = new FixedGridCostMap(null, 1.0, 3, 4, 0, 0, new byte[12]);
        subject.drawPolygonOnGrid(vertices);

        byte[] expectedGrid = new byte[]{
                127,   0,   0,
                127, 127,   0,
                127, 127, 127,
                127, 127, 127};

        assertArrayEquals(expectedGrid, subject.getFullCostRegion());
    }


    /* Try to draw a rectangular wall which exceeds the grid's dimensions, so only draw what falls
       inside of it.
       Case 2) Polygon has two vertices whose x coordinates are below startX value for the grid.

           |---|---|---|                       |---|---|---|
         * | * | * | * |                       |127|127|127|
           |---|---|---|                       |---|---|---|
         * |   |   | * |    Expected output    | 0 | 0 |127|
           |---|---|---|         ----->        |---|---|---|
         * |   |   | * |                       | 0 | 0 |127|
           |---|---|---|                       |---|---|---|
         * | * | * | * |                       |127|127|127|
           |---|---|---|                       |---|---|---|
   */
    @Test
    public void testDrawPolygonOnGridBelowStartX() {
        int[][] vertices = new int[][]{{-1, 0}, {2, 0}, {2, 3}, {-1, 3}};

        // Make a new 3x4 map, with a discretization step of 1 and offset by (0, 0)
        GridCostMap subject = new FixedGridCostMap(null, 1.0, 3, 4, 0, 0, new byte[12]);
        subject.drawPolygonOnGrid(vertices);

        byte[] expectedGrid = new byte[]{
                127, 127, 127,
                  0,   0, 127,
                  0,   0, 127,
                127, 127, 127};

        assertArrayEquals(expectedGrid, subject.getFullCostRegion());
    }

    /* Try to draw a rectangular wall which exceeds the grid's dimensions, so only draw what falls
       inside of it.
       Case 1) Polygon has two vertices whose x coordinates exceed endX value for the grid.

        |---|---|---|                       |---|---|---|
        | * | * | * | *                     |127|127|127|
        |---|---|---|                       |---|---|---|
        | * |   |   | *   Expected output   |127| 0 | 0 |
        |---|---|---|         ----->        |---|---|---|
        | * |   |   | *                     |127| 0 | 0 |
        |---|---|---|                       |---|---|---|
        | * | * |   | *                     |127|127|127|
        |---|---|---|                       |---|---|---|
    */
    @Test
    public void testDrawPolygonOnGridExceedsEndX() {
        int[][] vertices = new int[][]{{0, 0}, {0, 3}, {3, 3}, {3, 0}};

        // Make a new 3x4 map, with a discretization step of 1 and offset by (0, 0)
        GridCostMap subject = new FixedGridCostMap(null, 1.0, 3, 4, 0, 0, new byte[12]);
        subject.drawPolygonOnGrid(vertices);

        byte[] expectedGrid = new byte[]{
                127, 127, 127,
                127,   0,   0,
                127,   0,   0,
                127, 127, 127};

        assertArrayEquals(expectedGrid, subject.getFullCostRegion());
    }

    /* Try to draw a rectangular wall which exceeds the grid's dimensions, so only draw what falls
       inside of it.
       Case 3) Polygon has two vertices whose y coordinates are below startY value for the grid.

          *   *   *
        |---|---|---|                       |---|---|---|
        | * |   | * |                       |127| 0 |127|
        |---|---|---|                       |---|---|---|
        | * |   | * |    Expected output    |127| 0 |127|
        |---|---|---|         ----->        |---|---|---|
        | * |   | * |                       |127| 0 |127|
        |---|---|---|                       |---|---|---|
        | * | * | * |                       |127|127|127|
        |---|---|---|                       |---|---|---|
    */
    @Test
    public void testDrawPolygonOnGridBelowStartY() {
        int[][] vertices = new int[][]{{0, -1}, {2, -1}, {2, 3}, {0, 3}};

        // Make a new 3x4 map, with a discretization step of 1 and offset by (0, 0)
        GridCostMap subject = new FixedGridCostMap(null, 1.0, 3, 4, 0, 0, new byte[12]);
        subject.drawPolygonOnGrid(vertices);

        byte[] expectedGrid = new byte[]{
                127,   0, 127,
                127,   0, 127,
                127,   0, 127,
                127, 127, 127};

        assertArrayEquals(expectedGrid, subject.getFullCostRegion());
    }

    /* Try to draw a rectangular wall which exceeds the grid's dimensions, so only draw what falls
       inside of it.
       Case 4) Polygon has two vertices whose y coordinates exceed endY value for the grid.

        |---|---|---|                       |---|---|---|
        | * | * | * |                       |127|127|127|
        |---|---|---|                       |---|---|---|
        | * |   | * |    Expected output    |127| 0 |127|
        |---|---|---|         ----->        |---|---|---|
        | * |   | * |                       |127| 0 |127|
        |---|---|---|                       |---|---|---|
        | * |   | * |                       |127| 0 |127|
        |---|---|---|                       |---|---|---|
          *   *   *
    */
    @Test
    public void testDrawPolygonOnGridExceedsEndY() {
        int[][] vertices = new int[][]{{0, 0}, {2, 0}, {2, 4}, {0, 4}};

        // Make a new 3x4 map, with a discretization step of 1 and offset by (0, 0)
        GridCostMap subject = new FixedGridCostMap(null, 1.0, 3, 4, 0, 0, new byte[12]);
        subject.drawPolygonOnGrid(vertices);

        byte[] expectedGrid = new byte[]{
                127, 127, 127,
                127,   0, 127,
                127,   0, 127,
                127,   0, 127};

        assertArrayEquals(expectedGrid, subject.getFullCostRegion());
    }

    @Test
    public void testGetSingleCostRegion() {
        // Get central cell of the map, which has a different cost from the rest.
        /* Center of the map (1,1)
            |---|---|---|
            | * | * | * |
            |---|---|---|    Expected output     |---|
            | * | 8 | * |         ----->         | 8 |
            |---|---|---|                        |---|
            | * | * | * |
            |---|---|---|
        */
        byte[] costGrid = new byte[]{
                64, 64, 64,
                64, 8, 64,
                64, 64, 64};
        // Make a new 3x3 map, with a resolution of 1 and offset by (0, 0).
        GridCostMap subject = new FixedGridCostMap(null, 1.0, 3, 3, 0, 0, costGrid, false);

        byte[] expectedRegion = new byte[]{8};

        assertArrayEquals(expectedRegion, subject.getCostRegion(1, 1, 2, 2));

        // Try to get a single element from the map, but with wrong input arguments such as
        // xStart = xEnd. The method should return an empty grid.
        costGrid = new byte[]{
                64, 64,
                64, 64};
        subject = new FixedGridCostMap(null, 1.0, 2, 2, 0, 0, costGrid, false);

        assertArrayEquals(new byte[0], subject.getCostRegion(0, 0, 0, 1));

        // Try to get a single element from the map, but with wrong input arguments such as
        // yStart = yEnd. The method should return an empty grid.
        assertArrayEquals(new byte[0], subject.getCostRegion(0, 0, 1, 0));
    }

    @Test
    public void testGetSquaredCostRegion() {
        // Get a 2x2 grid from the map when xStart and yStart are other than (0,0).
        /*
            |---|---|---|                        |---|---|
            | 8 | 8 | * |                        | 8 | 8 |
            |---|---|---|    Expected output     |---|---|
            | 8 | 8 | * |         ----->         | 8 | 8 |
            |---|---|---|                        |---|---|
            | * | * | * |
            |---|---|---|
        */
        byte[] costGrid = new byte[]{
                8, 8, 64,
                8, 8, 64,
                64, 64, 64};
        CostMap subject = new FixedGridCostMap(null, 1.0, 3, 3, -5, -7, costGrid, false);

        byte[] expectedRegion = new byte[]{
                8, 8,
                8, 8};

        assertArrayEquals(expectedRegion, subject.getCostRegion(-5, -7, -3, -5));
    }

    @Test
    public void testGetRectangularCostRegion() {
        // Get a column from the map.
        /*
            |---|---|---|                        |---|
            | * | 8 | * |                        | 8 |
            |---|---|---|    Expected output     |---|
            | * | 8 | * |         ----->         | 8 |
            |---|---|---|                        |---|
            | * | 8 | * |                        | 8 |
            |---|---|---|                        |---|
        */

        // Get a row from the map.
        /*
            |---|---|---|
            | * | * | * |
            |---|---|---|    Expected output     |---|---|---|
            | 8 | 8 | 8 |         ----->         | 8 | 8 | 8 |
            |---|---|---|                        |---|---|---|
            | * | * | * |
            |---|---|---|
        */
        byte[] costGrid = new byte[]{
                64, 64, 64,
                8, 8, 8,
                64, 64, 64};
        GridCostMap subject = new FixedGridCostMap(null, 1.0, 3, 3, 0, 0, costGrid, false);

        byte[] expectedRegion = new byte[]{8, 8, 8};

        assertArrayEquals(expectedRegion, subject.getCostRegion(0, 1, 3, 2));
    }

    @Test
    public void testGetCostRegionOutOfBounds() {
        // Get a cost region whose end limits fall out of map's bounds. For those cells which are
        // out of the grid, return max cost = 127.
        /*
            |---|---|---|
            | * | * | * |
            |---|---|---|    Expected output     |---|---|
            | * | * | * |         ----->         | 8 |127|
            |---|---|---|                        |---|---|
            | * | * | 8 | ?                      |127|127|
            |---|---|---|                        |---|---|
                      ?   ?
        */
        byte[] costGrid = new byte[]{
                64, 64, 64,
                64, 64, 64,
                64, 64, 8};
        CostMap subject = new FixedGridCostMap(null, 1.0, 3, 3, 0, 0, costGrid, false);

        byte[] expectedRegion = new byte[]{
                8, 127,
                127, 127};

        assertArrayEquals(expectedRegion, subject.getCostRegion(2, 2, 4, 4));
    }

    @Test
    public void testGetCostRegionThrowsIllegalArgumentException() {
        byte[] costGrid = new byte[]{
                0, 0,
                0, 0};
        CostMap subject = new FixedGridCostMap(null, 1.0, 2, 2, 0, 0, costGrid, false);

        // Ask for a cost region with xStart > xEnd. This should throw an IllegalArgumentException.
        exception.expect(IllegalArgumentException.class);
        subject.getCostRegion(1, 0, 0, 0);

        // Ask for a cost region with yStart > yEnd. This should throw an IllegalArgumentException.
        exception.expect(IllegalArgumentException.class);
        subject.getCostRegion(0, 1, 0, 0);
    }

    @Test
    public void testGetHighestCostInRegion() {
        // Get highest cost in a 1x1 grid.
        /*
            |----|      Expected output
            | 64 |          ---->         64
            |----|
        */
        byte[] costGrid = new byte[]{64};
        CostMap subject = new FixedGridCostMap(null, 1.0, 1, 1, 0, 0, costGrid, false);

        assertEquals((byte) 64, subject.getHighestCostInRegion(0, 0, 1, 1));

        // Get highest cost of squared grid.
        /*
            |---|---|---|
            | 0 | 0 | 0 |
            |---|---|---|     Expected output
            | 0 | 8 | 0 |         ----->        8
            |---|---|---|
            | 0 | 0 | 0 |
            |---|---|---|
        */
        costGrid = new byte[]{
                0, 0, 0,
                0, 8, 0,
                0, 0, 0};
        subject = new FixedGridCostMap(null, 1.0, 3, 3, 0, 0, costGrid, false);

        assertEquals((byte) 8, subject.getHighestCostInRegion(0, 0, 3, 3));

        // Get highest cost on a squared grid where the max value is repeated in different cells.
        /*
            |---|---|---|
            | 0 | 0 | 0 |
            |---|---|---|     Expected output
            | 8 | 8 | 8 |         ----->        8
            |---|---|---|
            | 0 | 0 | 0 |
            |---|---|---|
        */
        costGrid = new byte[]{
                0, 0, 0,
                8, 8, 8,
                0, 0, 0};
        subject = new FixedGridCostMap(null, 1.0, 3, 3, 0, 0, costGrid, false);

        assertEquals((byte) 8, subject.getHighestCostInRegion(0, 0, 3, 3));

        // Get highest cost of a region which falls out of the grid's boundaries. As the output cost
        // for a cell out of the map is considered 127, then this will be the highest value returned
        /*
            |---|---|
            | 0 | 0 |
            |---|---|    Expected output
            | 0 | 0 |         ----->        127
            |---|---|
            | 0 | 0 |
            |---|---|
        */
        costGrid = new byte[]{
                0, 0,
                0, 0,
                0, 0};
        subject = new FixedGridCostMap(null, 1.0, 2, 3, 0, 0, costGrid, false);

        assertEquals(CostMap.MAX_COST, subject.getHighestCostInRegion(2, 0, 3, 3));

        // Try to query the highest cost in a region indicated with xStart = xEnd. The method should
        // return MAX_COST.
        costGrid = new byte[]{
                64, 64,
                64, 64};
        subject = new FixedGridCostMap(null, 1.0, 2, 2, 0, 0, costGrid, false);

        assertEquals(CostMap.MAX_COST, subject.getHighestCostInRegion(2, 0, 3, 3));

        // Try to query the highest cost in a region indicated with yStart = yEnd. The method should
        // return MAX_COST.
        assertEquals(CostMap.MAX_COST, subject.getHighestCostInRegion(2, 0, 3, 3));
    }

    @Test
    public void testHighestGetCostInRegionWithEqualBoundaryLimits() {
        // Ask for the highest cost in a 1x1 grid when indicated x or y span is 0 (xStart = xEnd or
        // yStart = yEnd). This should return MIN_COST.
        /*
            |----|      Expected output
            | 64 |          ---->         MIN_COST (0)
            |----|
        */
        byte[] costGrid = new byte[]{64};
        CostMap subject = new FixedGridCostMap(null, 1.0, 1, 1, 0, 0, costGrid, false);

        // Ask for the highest cost when xStart = xEnd. This should return MIN_COST.
        assertEquals(CostMap.MIN_COST, subject.getHighestCostInRegion(0, 0, 0, 1));

        // Ask for the highest cost when xStart = xEnd. This should return MIN_COST.
        assertEquals(CostMap.MIN_COST, subject.getHighestCostInRegion(0, 0, 1, 0));
    }

    @Test
    public void testHighestGetCostInRegionThrowsIllegalArgumentException() {
        byte[] costGrid = new byte[]{
                0, 0,
                0, 0};
        CostMap subject = new FixedGridCostMap(null, 1.0, 2, 2, 0, 0, costGrid, false);

        // Ask for a cost region with xStart > xEnd. This should throw an IllegalArgumentException.
        exception.expect(IllegalArgumentException.class);
        subject.getHighestCostInRegion(1, 0, 0, 1);

        // Ask for a cost region with yStart > yEnd. This should throw an IllegalArgumentException.
        exception.expect(IllegalArgumentException.class);
        subject.getHighestCostInRegion(0, 1, 1, 0);
    }

    @Test
    public void testIsObstacle() {
        /* Test if a cell has a cost which is considered an obstacle or not.
                         is obstacle?
            MAX_COST        ------>     true
            MAX_COST - 1    ------>     false

        */
        assertTrue(CostMap.isObstacle(CostMap.MAX_COST));
        assertFalse(CostMap.isObstacle((byte) (CostMap.MAX_COST - 1)));
    }

    @Test
    public void testGetCostAtPosition() {
        // Make a 3x2 grid with a resolution of 1.0 and offset (1, -1). Try to get the cost at
        // (2.0, 0.0).
        /*
            |---|---|---|
            | 0 | 0 | 0 |
            |---|---|---|     Expected output
            | 0 | 8 | 0 |         ----->        8
            |---|---|---|
        */
        byte[] costGrid = new byte[]{
                0, 0, 0,
                0, 8, 0};
        CostMap subject = new FixedGridCostMap(null, 1.0, 3, 2, 1, -1, costGrid, false);

        assertEquals((byte) 8, subject.getCostAtPosition(2.0, 0.0));

        // Make a 3x2 grid with a resolution of 0.2 and offset (0, 0). Try to get the cost at
        // (0.48, 0.15) which should be discretized to (2, 0).
        /*
            |---|---|---|
            | 0 | 0 | 8 |
            |---|---|---|     Expected output
            | 0 | 0 | 0 |         ----->        8
            |---|---|---|
        */
        costGrid = new byte[]{
                0, 0, 8,
                0, 0, 0};
        subject = new FixedGridCostMap(null, 0.2, 3, 2, 0, 0, costGrid, false);

        assertEquals((byte) 8, subject.getCostAtPosition(0.48, 0.15));

        // Make a 3x2 grid with a resolution of 0.2 and offset (0, 0). Try to get the cost of a pose
        // which falls out of the map first in row and then in column index.
        /*
            |---|---|---|
            | 0 | 0 | 0 |
            |---|---|---|     Expected output
            | 0 | 0 | 0 |         ----->        MAX_COST
            |---|---|---|
        */
        costGrid = new byte[]{
                0, 0, 0,
                0, 0, 0};
        subject = new FixedGridCostMap(null, 0.2, 3, 2, 0, 0, costGrid, false);

        assertEquals(CostMap.MAX_COST, subject.getCostAtPosition(0.8, 0.0));
        assertEquals(CostMap.MAX_COST, subject.getCostAtPosition(0.6, 0.4));
    }

    @Test
    public void testGetCostFromIntegerIndexes() {
        // Make a 3x2 grid with a resolution of 1.0 and offset x = -1, y = -1. Try to get the cost
        // at x = 1, y = 0.
        /*
            |---|---|---|
            | 0 | 0 | 0 |
            |---|---|---|     Expected output
            | 0 | 0 | 8 |         ----->        8
            |---|---|---|
        */
        byte[] costGrid = new byte[]{
                0, 0, 0,
                0, 0, 8};
        CostMap subject = new FixedGridCostMap(null, 1.0, 3, 2, -1, -1, costGrid, false);

        assertEquals((byte) 8, subject.getCost(1, 0));

        // Get cost when index falls out of grid
        subject = new FixedGridCostMap(null, 1.0, 3, 2, 0, 0, costGrid, false);

        assertEquals((byte) 127, subject.getCost(3, 0));
        assertEquals((byte) 127, subject.getCost(2, 2));
    }

    @Test
    public void testGetCostFromCostMapPose() {
        // Make a 3x2 grid with a resolution of 1.0 and offset x = -1, y = -1. Try to get the cost
        // at CostMapPose(1, 0).
        /*
            |---|---|---|
            | 0 | 0 | 0 |
            |---|---|---|     Expected output
            | 0 | 0 | 8 |         ----->        8
            |---|---|---|
        */
        byte[] costGrid = new byte[]{
                0, 0, 0,
                0, 0, 8};
        CostMap subject = new FixedGridCostMap(null, 1.0, 3, 2, -1, -1, costGrid, false);

        assertEquals((byte) 8, subject.getCost(new CostMapPose(1, 0)));

        // Get cost when index falls out of grid
        subject = new FixedGridCostMap(null, 1.0, 3, 2, 0, 0, costGrid, false);

        assertEquals((byte) 127, subject.getCost(new CostMapPose(3, 0)));
        assertEquals((byte) 127, subject.getCost(new CostMapPose(2, 2)));
    }
}