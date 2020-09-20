package ai.cellbots.robot.costmap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ai.cellbots.common.Transform;

public class PriorTrajectoryCostMapTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testConstructorFromListNoPadding() {
        // Test building a prior trajectory cost map from a list of transforms (path).

        /* Path made of list of transforms

         (0.0, 0.0) -- (0.4, 0.0) --(0.8, 0.4) -- (1.0, 0.8) -- (0.6, 1.2) -- (0.2, 1.4)

         As this example has all points exactly on the boundaries of the grid,
         the calculated costmap is shown below, and it might be one cell different than
         what one assumes, that is due to loss of precision.
         For example:
         double X = 1.2;
         double resolution = 0.2;
         (int) Math.floor(X / resolution) is 5 not 6

                                   | Cost map made of lists of transforms
                                   | (the discretized transforms are marked with an X)

        (-2,-2) (-2,-1) (-2,+0) (-2,+1) (-2,+2) (-2,+3) (-2,+4) (-2,+5) (-2,+6) (-2,+7) (-2,+8)

        (-1,-2) (-1,-1) (-1,+0) (-1,+1) (-1,+2) (-1,+3) (-1,+4) (-1,+5) (-1,+6) (-1,+7) (-1,+8)

        (+0,-2) (+0,-1)    X    (+0,+1) (+0,+2) (+0,+3) (+0,+4) (+0,+5) (+0,+6) (+0,+7) (+0,+8)

        (+1,-2) (+1,-1) (+1,+0) (+1,+1) (+1,+2) (+1,+3) (+1,+4) (+1,+5)    X    (+1,+7) (+1,+8)

        (+2,-2) (+2,-1)    X    (+2,+1) (+2,+2) (+2,+3) (+2,+4)    X    (+2,+6) (+2,+7) (+2,+8)

        (+3,-2) (+3,-1) (+3,+0) (+3,+1) (+3,+2) (+3,+3) (+3,+4) (+3,+5) (+3,+6) (+3,+7) (+3,+8)

        (+4,-2) (+4,-1) (+4,+0) (+4,+1)    X    (+4,+3) (+4,+4) (+4,+5) (+4,+6) (+4,+7) (+4,+8)

        (+5,-2) (+5,-1) (+5,+0) (+5,+1) (+5,+2) (+5,+3)    X    (+5,+5) (+5,+6) (+5,+7) (+5,+8)

        (+6,-2) (+6,-1) (+6,+0) (+6,+1) (+6,+2) (+6,+3) (+6,+4) (+6,+5) (+6,+6) (+6,+7) (+6,+8)

                                   |
                                   | Inflated CostMap (Prior trajectory cost map)
                                   | (0 cost is for free space and B for background)

           B       B       B       B       B       B       B       B       B       B       B

           B       B       o       B       B       B       B       B       B       B       B

           B       o       o       o       B       B       B       B       o       B       B

           B       B       o       B       B       B       B       o       o       o       B

           B       o       o       o       B       B       o       o       o       B       B

           B       B       o       B       o       B       B       o       B       B       B

           B       B       B       o       o       o       o       B       B       B       B

           B       B       B       B       o       o       o       o       B       B       B

           B       B       B       B       B       B       o       B       B       B       B
        */
        final byte B = PriorTrajectoryCostMap.BACKGROUND_COST;
        final byte o = CostMap.MIN_COST;
        List<Transform> path = new ArrayList<>(Arrays.asList(
                new Transform[]{
                        new Transform(0.0, 0.0, 0.0, 0.0),
                        new Transform(0.0, 0.4, 0.0, 0.0),
                        new Transform(0.4, 0.8, 0.0, 0.0),
                        new Transform(0.8, 1.0, 0.0, 0.0),
                        new Transform(1.2, 0.6, 0.0, 0.0),
                        new Transform(1.4, 0.2, 0.0, 0.0)}));

        // Set map boundaries as the path's x and y limits.
        double[][] boundaries = {{0.0, 0.0}, {1.4, 1.0}};

        GridCostMap subject = new PriorTrajectoryCostMap(path, boundaries, 0.2, 0.0, 0.0);

        byte[] expectedCosts = {
                B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   o,   B,   B,   B,   B,   B,   B,   B,   B,
                B,   o,   o,   o,   B,   B,   B,   B,   o,   B,   B,
                B,   B,   o,   B,   B,   B,   B,   o,   o,   o,   B,
                B,   o,   o,   o,   B,   B,   o,   o,   o,   B,   B,
                B,   B,   o,   B,   o,   B,   B,   o,   B,   B,   B,
                B,   B,   B,   o,   o,   o,   o,   B,   B,   B,   B,
                B,   B,   B,   B,   o,   o,   o,   o,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   o,   B,   B,   B,   B};
        assertArrayEquals(subject.getFullCostRegion(), expectedCosts);

        // The prior trajectory CostMap should not be inflated.
        assertEquals(false, subject.requiresInflation());
    }

    @Test
    public void testConstructorFromListWithPadding() {
        // Test building a prior trajectory cost map from a list of transforms (path) and adding
        // a padding of 1.0m to the borders.

        /* Path made of list of transforms

         (0.0, 0.0) -- (0.4, 0.0) --(0.8, 0.4) -- (1.0, 0.8) -- (0.6, 1.2) -- (0.2, 1.4)

         As this example has all points exactly on the boundaries of the grid,
         the calculated costmap is shown below, and it might be one cell different than
         what one assumes, that is due to loss of precision.
         For example:
         double X = 1.2;
         double resolution = 0.2;
         (int) Math.floor(X / resolution) is 5 not 6

                                   | Cost map made of lists of transforms
                                   | (the discretized transforms are marked with an X)

     (-7,-7) <---> (-7,-2) <---------------------------------------------------------------------> (-7,+8) <---> (-7,14)
        ^             ^                                                                               ^             ^
        |             |                                                                               |             |
        v             v    <--------------------------------------------------------------------->    v             v
     (-2,-7) <---> (-2,-2) (-2,-1) (-2,+0) (-2,+1) (-2,+2) (-2,+3) (-2,+4) (-2,+5) (-2,+6) (-2,+7) (-2,+8) <---> (-2,14)

     (-1,-7) <---> (-1,-2) (-1,-1) (-1,+0) (-1,+1) (-1,+2) (-1,+3) (-1,+4) (-1,+5) (-1,+6) (-1,+7) (-1,+8) <---> (-1,14)

     (+0,-7) <---> (+0,-2) (+0,-1)    X    (+0,+1) (+0,+2) (+0,+3) (+0,+4) (+0,+5) (+0,+6) (+0,+7) (+0,+8) <---> (+0,14)

     (+1,-7) <---> (+1,-2) (+1,-1) (+1,+0) (+1,+1) (+1,+2) (+1,+3) (+1,+4) (+1,+5) (+1,+6)    X    (+1,+8) <---> (+1,14)

     (+2,-7) <---> (+2,-2) (+2,-1)    X    (+2,+1) (+2,+2) (+2,+3) (+2,+4) (+2,+5) (+2,+6) (+2,+7) (+2,+8) <---> (+2,14)

     (+3,-7) <---> (+3,-2) (+3,-1) (+3,+0) (+3,+1) (+3,+2) (+3,+3) (+3,+4) (+3,+5)    X    (+3,+7) (+3,+8) <---> (+3,14)

     (+4,-7) <---> (+4,-2) (+4,-1) (+4,+0) (+4,+1)    X    (+4,+3) (+4,+4) (+4,+5) (+4,+6) (+4,+7) (+4,+8) <---> (+4,14)

     (+5,-7) <---> (+5,-2) (+5,-1) (+5,+0) (+5,+1) (+5,+2) (+5,+3)    X    (+5,+5) (+5,+6) (+5,+7) (+5,+8) <---> (+5,14)

     (+6,-7) <---> (+6,-2) (+6,-1) (+6,+0) (+6,+1) (+6,+2) (+6,+3) (+6,+4) (+6,+5) (+6,+6) (+6,+7) (+6,+8) <---> (+6,14)

     (+7,-7) <---> (+7,-2) (+7,-1) (+7,+0) (+7,+1) (+7,+2) (+7,+3) (+7,+4) (+7,+5) (+7,+6) (+7,+7) (+7,+8) <---> (+7,14)
        ^             ^     <------------------------------------------------------------------->     ^             ^
        |             |                                                                               |             |
        v             v                                                                               v             v
     (+12,-7) <--> (+12,-2) <-------------------------------------------------------------------> (+12,+8) <--> (+12,14)

                                   |
                                   | Inflated CostMap (Prior trajectory cost map)
                                   | (0 cost is for free space and B for background)

        B    <--->    B    <--------------------------------------------------------------------->    B    <--->    B
        ^             ^                                                                               ^             ^
        |             |                                                                               |             |
        v             v    <--------------------------------------------------------------------->    v             v
        B    <--->    B       B       B       B       B       B       B       B       B       B       B    <--->    B

        B    <--->    B       B       o       B       B       B       B       B       B       B       B    <--->    B

        B    <--->    B       o       o       o       B       B       B       B       o       B       B    <--->    B

        B    <--->    B       B       o       B       B       B       B       o       o       o       B    <--->    B

        B    <--->    B       o       o       o       B       B       o       o       o       B       B    <--->    B

        B    <--->    B       B       o       B       o       B       B       o       B       B       B    <--->    B

        B    <--->    B       B       B       o       o       o       o       B       B       B       B    <--->    B

        B    <--->    B       B       B       B       o       o       o       o       B       B       B    <--->    B

        B    <--->    B       B       B       B       B       B       o       B       B       B       B    <--->    B

        B    <--->    B       B       B       B       B       B       o       B       B       B       B    <--->    B
        ^             ^    <------------------------------------------------------------------->      ^             ^
        |             |                                                                               |             |
        v             v                                                                               v             v
        B    <--->    B    <--------------------------------------------------------------------->    B    <--->    B
        */

        final byte B = PriorTrajectoryCostMap.BACKGROUND_COST;
        final byte o = CostMap.MIN_COST;

        List<Transform> path = new ArrayList<>(Arrays.asList(
                new Transform[]{
                        new Transform(0.0, 0.0, 0.0, 0.0),
                        new Transform(0.0, 0.4, 0.0, 0.0),
                        new Transform(0.4, 0.8, 0.0, 0.0),
                        new Transform(0.8, 1.0, 0.0, 0.0),
                        new Transform(1.2, 0.6, 0.0, 0.0),
                        new Transform(1.4, 0.2, 0.0, 0.0)}));

        // Set map boundaries as the path's x and y limits.
        double[][] boundaries = {{0.0, 0.0}, {1.4, 1.0}};

        GridCostMap subject = new PriorTrajectoryCostMap(path, boundaries, 0.2, 1.0, 1.0);

        byte[] expectedCosts = {
                B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   B,   o,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   o,   o,   o,   B,   B,   B,   B,   o,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   B,   o,   B,   B,   B,   B,   o,   o,   o,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   o,   o,   o,   B,   B,   o,   o,   o,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   B,   o,   B,   o,   B,   B,   o,   B,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   B,   B,   o,   o,   o,   o,   B,   B,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   B,   B,   B,   o,   o,   o,   o,   B,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   o,   B,   B,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,
                B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B,   B};
        assertArrayEquals(expectedCosts, subject.getFullCostRegion());

        // The prior trajectory CostMap should not be inflated.
        assertEquals(false, subject.requiresInflation());
    }
}
