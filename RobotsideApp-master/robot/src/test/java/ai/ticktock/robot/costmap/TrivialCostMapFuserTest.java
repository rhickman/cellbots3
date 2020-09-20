package ai.cellbots.robot.costmap;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class TrivialCostMapFuserTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testFuseEqualSizeCostMaps() {
        TrivialCostMapFuser subject = new TrivialCostMapFuser(null, 1.0);

        // TEST: fusing two identical cost maps, with all costs 0.
        /* Expected output

         floorplanCostMap             octoMapCostMap                  Output

        0   0   0   0   0           0   0   0   0   0           0   0   0   0   0

        0   0   0   0   0           0   0   0   0   0           0   0   0   0   0

        0   0   0   0   0     +     0   0   0   0   0     =     0   0   0   0   0

        0   0   0   0   0           0   0   0   0   0           0   0   0   0   0

        0   0   0   0   0           0   0   0   0   0           0   0   0   0   0

         */

        FixedGridCostMap floorplanCostMap = new FixedGridCostMap(CostMap.Source.FLOORPLAN, 1.0,
                5, 5, 0, 0, new byte[25]);
        FixedGridCostMap octoMapCostMap = new FixedGridCostMap(CostMap.Source.OCTOMAP, 1.0,
                5, 5, 0, 0, new byte[25]);

        // Create a collection of cost maps to fuse
        Collection<CostMap> costMaps = new ArrayList<>();
        costMaps.add(floorplanCostMap);
        costMaps.add(octoMapCostMap);

        assertArrayEquals(
                (subject.fuseCostMaps(costMaps, CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED)).getFullCostRegion(),
                new byte[25]);

        // TEST: fusing two identical cost maps, with all values set to COST_MAX.
        /* Expected output

            floorplanCostMap                   octoMapCostMap                       Output

        127  127  127  127  127           127  127  127  127  127           127  127  127  127  127

        127  127  127  127  127           127  127  127  127  127           127  127  127  127  127

        127  127  127  127  127     +     127  127  127  127  127     =     127  127  127  127  127

        127  127  127  127  127           127  127  127  127  127           127  127  127  127  127

        127  127  127  127  127           127  127  127  127  127           127  127  127  127  127

         */

        byte[] grid = new byte[]{
                127, 127, 127, 127, 127,
                127, 127, 127, 127, 127,
                127, 127, 127, 127, 127,
                127, 127, 127, 127, 127,
                127, 127, 127, 127, 127};

        floorplanCostMap = new FixedGridCostMap(CostMap.Source.FLOORPLAN, 1.0, 5, 5, 0, 0, grid);
        octoMapCostMap = new FixedGridCostMap(CostMap.Source.OCTOMAP, 1.0, 5, 5, 0, 0, grid);

        costMaps = new ArrayList<>();
        costMaps.add(floorplanCostMap);
        costMaps.add(octoMapCostMap);

        assertArrayEquals(
                (subject.fuseCostMaps(costMaps, CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED)).getFullCostRegion(),
                grid);

        // TEST: fusing two cost maps, both containing MIN_COST and MAX_COST.
        // Expected output : a cost map where only the cells that are not occupied on both of
        // the input maps have cost 0, and the rest have 127 (both cells were occupied or there was
        // a superposition).
        /*
            floorplanCostMap                  octoMapCostMap                       Output

        127  127   0   127  127            0    0    0    0    0          127  127   0   127  127

        127   0   127   0   127           127   0   127   0  127          127   0   127   0   127

         0   127   0   127   0      +      0  127  127  127   0     =       0  127  127  127   0

        127   0   127   0   127           127   0  127   0  127           127   0   127   0  127

         0    0    0    0    0            127  127   0  127  127          127  127   0  127  127

         */
        byte[] grid1 = new byte[]{
                127, 127, 0, 127, 127,
                127, 0, 127, 0, 127,
                0, 127, 0, 127, 0,
                127, 0, 127, 0, 127,
                0, 0, 0, 0, 0};
        floorplanCostMap = new FixedGridCostMap(CostMap.Source.FLOORPLAN, 1.0, 5, 5, 0, 0, grid1);

        byte[] grid2 = new byte[]{
                0, 0, 0, 0, 0,
                127, 0, 127, 0, 127,
                0, 127, 127, 127, 0,
                127, 0, 127, 0, 127,
                127, 127, 0, 127, 127};
        octoMapCostMap = new FixedGridCostMap(CostMap.Source.OCTOMAP, 1.0, 5, 5, 0, 0, grid2);

        costMaps = new ArrayList<>();
        costMaps.add(floorplanCostMap);
        costMaps.add(octoMapCostMap);

        byte[] expectedCosts = new byte[]{
                127, 127, 0, 127, 127,
                127, 0, 127, 0, 127,
                0, 127, 127, 127, 0,
                127, 0, 127, 0, 127,
                127, 127, 0, 127, 127};
        assertArrayEquals(
                (subject.fuseCostMaps(costMaps, CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED)).getFullCostRegion(),
                expectedCosts);
    }

    @Test
    public void testFuseDifferentSizeCostMaps() {
        TrivialCostMapFuser subject = new TrivialCostMapFuser(null, 1.0);
        // TEST: fusing two cost maps with all costs 0, same offset but different size (5x5 and 2x2)
        // EXPECTED OUTPUT: a cost map where the data of those cells that coincide between the two
        // inputs are the result of adding their costs, and the rest of the cells remain with the
        // same value as the bigger grid.
        /*
            floorplanCostMap                      octoMapCostMap                       Output

        0   0   0   0   0           127  127  *   *   *           127  127  0   0   0

        0   0   0   0   0           127  127  *   *   *           127  127  0   0   0

        0   0   0   0   0     +      *    *   *   *   *     =      0    0   0   0   0

        0   0   0   0   0            *    *   *   *   *            0    0   0   0   0

        0   0   0   0   0            *    *   *   *   *            0    0   0   0   0

         */
        FixedGridCostMap floorplanCostMap = new FixedGridCostMap(CostMap.Source.FLOORPLAN, 1.0,
                5, 5, 0, 0, new byte[25]);
        byte[] octoMapGrid = new byte[4];
        Arrays.fill(octoMapGrid, CostMap.MAX_COST);
        FixedGridCostMap octoMapCostMap = new FixedGridCostMap(CostMap.Source.OCTOMAP, 1.0, 2, 2,
                0, 0, octoMapGrid);

        Collection<CostMap> costMaps = new ArrayList<>();
        costMaps.add(floorplanCostMap);
        costMaps.add(octoMapCostMap);

        byte[] expectedCosts = new byte[]{
                127, 127, 0, 0, 0,
                127, 127, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0};

        assertArrayEquals(
                (subject.fuseCostMaps(costMaps, CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED)).getFullCostRegion(),
                expectedCosts);

        // TEST: fusing two cost maps with all costs 0, different offset and different size.
        // EXPECTED OUTPUT: a cost map where the data of those cells that coincide between the two
        // inputs are the result of adding their costs, and the rest of the cells remain with the
        // same value as the bigger grid.
        /*
            floorplanCostMap                      octoMapCostMap                       Output

        0   0   0   0   0            *   *   *    *   *            0   0    0   0    0

        0   0   0   0   0            *   *   *    *   *            0   0    0   0    0

        0   0   0   0   0     +      *   *  127  127  *     =      0   0   127  127  0

        0   0   0   0   0            *   *  127  127  *            0   0   127  127  0

        0   0   0   0   0            *   *   *    *   *            0   0    0   0    0

         */
        floorplanCostMap = new FixedGridCostMap(CostMap.Source.FLOORPLAN, 1.0, 5, 5,
                0, 0, new byte[25]);
        octoMapGrid = new byte[4];
        Arrays.fill(octoMapGrid, CostMap.MAX_COST);
        octoMapCostMap = new FixedGridCostMap(CostMap.Source.OCTOMAP, 1.0, 2, 2, 2, 2, octoMapGrid);

        costMaps = new ArrayList<>();
        costMaps.add(floorplanCostMap);
        costMaps.add(octoMapCostMap);

        expectedCosts = new byte[]{
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 127, 127, 0,
                0, 0, 127, 127, 0,
                0, 0, 0, 0, 0};

        assertArrayEquals(
                (subject.fuseCostMaps(costMaps, CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED)).getFullCostRegion(),
                expectedCosts);

        // TEST: fusing two cost maps with all costs 0, different offset and different size.
        /* Expected output

            floorplanCostMap              octoMapCostMap                    Output

        64   64   64   *   *            *   *   *   *   *            64   64   64   0    0

        64   64   64   *   *            *   *   *   *   *            64   64   64   0    0

        64   64   64   *   *      +     *   *   *   *   *     =      64   64   64   0    0

        *    *    *    *   *            *   *   *  127  127           0    0    0  127  127

        *    *    *    *   *            *   *   *  127  127           0    0    0  127  127

         */
        byte[] floorPlanGrid = new byte[9];
        Arrays.fill(floorPlanGrid, (byte) 64);
        floorplanCostMap = new FixedGridCostMap(CostMap.Source.FLOORPLAN, 1.0, 3, 3,
                0, 0, floorPlanGrid);

        octoMapGrid = new byte[4];
        Arrays.fill(octoMapGrid, CostMap.MAX_COST);
        octoMapCostMap = new FixedGridCostMap(CostMap.Source.OCTOMAP, 1.0, 2, 2, 3, 3,
                octoMapGrid);

        costMaps = new ArrayList<>();
        costMaps.add(floorplanCostMap);
        costMaps.add(octoMapCostMap);

        expectedCosts = new byte[]{
                64, 64, 64, 0, 0,
                64, 64, 64, 0, 0,
                64, 64, 64, 0, 0,
                0, 0, 0, 127, 127,
                0, 0, 0, 127, 127};
        assertArrayEquals(
                (subject.fuseCostMaps(costMaps, CostMap.Source.OUTPUT_COSTMAP_FULLY_INFLATED)).getFullCostRegion(),
                expectedCosts);
    }
}
