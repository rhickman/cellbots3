package ai.cellbots.robot.costmap;

import ai.cellbots.robot.state.RobotSessionGlobals;

/**
 * Inflates a CostMap so that the robot is taken in to consideration.
 */
public abstract class CostMapInflator {
    /**
     * Represents the different types of inflation.
     */
    public enum Type {
        FULL_RADIUS,     // Inflation using the full robot radius.
        FACTOR_RADIUS    // Inflation using the robot radius multiplied by a factor.
                         // If the factor is less than 1 then the inflation will be partial.
    }
    private final double mResolution;
    private final double mRobotRadius;
    private final double mRadiusFactor;

    /**
     * Creates the CostMapInflator.
     *
     * @param resolution   The resolution of the CostMap, being the width of a square in meters.
     * @param robotRadius  The physical radius of the robot (in meters).
     * @param radiusFactor The factor by which the robot radius is multiplied.
     */
    protected CostMapInflator(double resolution, double robotRadius, double radiusFactor) {
        mResolution = resolution;
        mRobotRadius = robotRadius;
        mRadiusFactor = radiusFactor;
    }

    /**
     * Gets the robot radius.
     *
     * @return The robot radius.
     */
    final protected double getRobotRadius() {
        return mRobotRadius;
    }

    /**
     * Gets the resolution.
     *
     * @return The resolution of the CostMap in meters/cell.
     */
    final protected double getResolution() {
        return mResolution;
    }

    /**
     * Gets the robot radius factor.
     *
     * @return The robot radius factor.
     */
    final protected double getRadiusFactor() {
        return mRadiusFactor;
    }

    /**
     * Inflates a CostMap by the robot radius.
     *
     * @param costMapData The CostMap data.
     * @return Inflated CostMap.
     */
    public abstract byte[] inflateCostMapFullRadius(CostMap costMapData);

    /**
     * Inflates a CostMap by a factor of the robot radius.
     *
     * @param costMapData The CostMap data.
     * @return Inflated CostMap.
     */
    public abstract byte[] inflateCostMapFactorRadius(CostMap costMapData);
}
