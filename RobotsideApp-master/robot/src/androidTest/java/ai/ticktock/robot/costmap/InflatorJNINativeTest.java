package ai.cellbots.robot.costmap;

import static org.junit.Assert.*;

import org.junit.Test;

public class InflatorJNINativeTest {
    private static final double GRID_RESOLUTION = 1.0;
    private final double ROBOT_RADIUS = 1.0;

    /**
     * Inflates a 5x5 cost map with a wall as obstacle on the left
     */
    @Test
    public void testInflateSimpleCostMap() throws Exception {
        byte[] originalCosts = {
                127, 0, 0, 0, 0,
                127, 0, 0, 0, 0,
                127, 0, 0, 0, 0,
                127, 0, 0, 0, 0,
                127, 0, 0, 0, 0};

        CostMap costMap = new FixedGridCostMap(null, GRID_RESOLUTION, 5, 5, 0, 0, originalCosts);
        byte[] costMapInflated = InflatorJNINative.inflate(costMap.getFullCostRegion(),
                ROBOT_RADIUS, costMap.getResolution(), new int[]{
                        costMap.getLowerXLimit(), costMap.getUpperXLimit(),
                        costMap.getLowerYLimit(), costMap.getUpperYLimit()
                });

        byte[] solution = {
                127, 127, 0, 0, 0,
                127, 127, 0, 0, 0,
                127, 127, 0, 0, 0,
                127, 127, 0, 0, 0,
                127, 127, 0, 0, 0};

        assertArrayEquals(solution, costMapInflated);
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

        CostMap costMap = new FixedGridCostMap(null, GRID_RESOLUTION, 5, 5, 0, 0, originalCosts);
        byte[] costMapInflated = InflatorJNINative.inflate(costMap.getFullCostRegion(),
                ROBOT_RADIUS, costMap.getResolution(), new int[]{
                        costMap.getLowerXLimit(), costMap.getUpperXLimit(),
                        costMap.getLowerYLimit(), costMap.getUpperYLimit()
                });

        byte[] solution = {
                127, 127, 127, 127, 127,
                127, 127, 127, 127, 127,
                127, 127, 100,  50,   0,
                127, 127,  50,   0,   0,
                127, 127,   0,   0,   0};

        assertArrayEquals(solution, costMapInflated);
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
        CostMap costMap = new FixedGridCostMap(null, GRID_RESOLUTION, 5, 5, 1, 3, originalCosts);
        byte[] costMapInflated = InflatorJNINative.inflate(costMap.getFullCostRegion(),
                ROBOT_RADIUS, costMap.getResolution(), new int[]{
                        costMap.getLowerXLimit(), costMap.getUpperXLimit(),
                        costMap.getLowerYLimit(), costMap.getUpperYLimit()
                });

        byte[] solution = {
                127, 127, 127, 127, 127,
                127, 127, 127, 127, 127,
                127, 127, 100,  50,   0,
                127, 127,  50,   0,   0,
                127, 127,   0,   0,   0};

        assertArrayEquals(solution, costMapInflated);
    }
}
