package ai.cellbots.robot.costmap;

import org.junit.Assert;
import org.junit.Test;

import ai.cellbots.common.data.BatteryStatus;
import ai.cellbots.robot.driver.RobotModel;
import ai.cellbots.robot.state.RobotSessionGlobals;

public class SimpleCostMapInflatorTest {

    private static final double COSTMAP_RESOLUTION = 1; // Meters/cell
    // The robot width and height were chosen as 0.7 to obtain an "inscribedRadius" of 1.
    private static final double ROBOT_RADIUS = Math.sqrt(0.7 * 0.7 + 0.7 * 0.7);

    private final SimpleCostMapInflator mCostMapInflator =
            new SimpleCostMapInflator(COSTMAP_RESOLUTION, ROBOT_RADIUS, 1);

    /**
     * Inflates a 5x5 cost map with a wall as obstacle on the left
     */
    @Test
    public void testInflateSimpleCostMap() {
        byte[] originalCosts = {
                127, 0, 0, 0, 0,
                127, 0, 0, 0, 0,
                127, 0, 0, 0, 0,
                127, 0, 0, 0, 0,
                127, 0, 0, 0, 0};
        CostMap costMap = new FixedGridCostMap(null, COSTMAP_RESOLUTION, 5, 5, 0, 0, originalCosts);
        byte[] costMapInflated = mCostMapInflator.inflateCostMapFullRadius(costMap);

        byte[] solution = {
                127, 127, 0, 0, 0,
                127, 127, 0, 0, 0,
                127, 127, 0, 0, 0,
                127, 127, 0, 0, 0,
                127, 127, 0, 0, 0};

        Assert.assertArrayEquals(solution, costMapInflated);
    }

    /**
     * Inflates a 5x5 cost map with L-form as obstacle on the upper left corner
     */
    @Test
    public void testInflateLObstacleCostMap() {
        byte[] originalCosts = {
                127, 127, 127, 127, 127,
                127, 100,  50,   0,   0,
                127,  50,   0,   0,   0,
                127,   0,   0,   0,   0,
                127,   0,   0,   0,   0};
        CostMap costMap = new FixedGridCostMap(null, COSTMAP_RESOLUTION, 5, 5, 0, 0, originalCosts);
        byte[] costMapInflated = mCostMapInflator.inflateCostMapFullRadius(costMap);

        byte[] solution = {
                127, 127, 127, 127, 127,
                127, 127, 127, 127, 127,
                127, 127, 100,  50,   0,
                127, 127,  50,   0,   0,
                127, 127,   0,   0,   0};

        Assert.assertArrayEquals(solution, costMapInflated);
    }

    /**
     * Inflates a 5x5 cost map with L-form as obstacle on the upper left corner with the map offset
     */
    @Test
    public void testInflateLObstacleCostMapOffset() {
        byte[] originalCosts = {
                127, 127, 127, 127, 127,
                127, 100,  50,   0,   0,
                127,  50,   0,   0,   0,
                127,   0,   0,   0,   0,
                127,   0,   0,   0,   0};
        CostMap costMap = new FixedGridCostMap(null, COSTMAP_RESOLUTION, 5, 5, 1, 3, originalCosts);
        byte[] costMapInflated = mCostMapInflator.inflateCostMapFullRadius(costMap);

        byte[] solution = {
                127, 127, 127, 127, 127,
                127, 127, 127, 127, 127,
                127, 127, 100,  50,   0,
                127, 127,  50,   0,   0,
                127, 127,   0,   0,   0};

        Assert.assertArrayEquals(solution, costMapInflated);
    }
}
