package ai.cellbots.robot.costmap;

import java.util.Collection;

import ai.cellbots.robot.state.RobotSessionGlobals;

/**
 * Fuses together multiple CostMaps to produce a resulting CostMap.
 */
public abstract class CostMapFuser {
    private final RobotSessionGlobals mSession;
    private final double mResolution;

    /**
     * Creates the CostMapFuser
     *
     * @param session    The session variables.
     * @param resolution The resolution data.
     */
    protected CostMapFuser(RobotSessionGlobals session, double resolution) {
        mSession = session;
        mResolution = resolution;
    }

    /**
     * Get the session.
     *
     * @return The session.
     */
    @SuppressWarnings("unused")
    protected final RobotSessionGlobals getSession() {
        return mSession;
    }

    /**
     * Get the resolution.
     *
     * @return The resolution of the CostMap in meters/cell.
     */
    @SuppressWarnings("unused")
    protected final double getResolution() {
        return mResolution;
    }

    /**
     * Fuses together CostMaps by mutating the data provided.
     *
     * @param costMaps The CostMaps to be fused.
     * @param source   The source to set to the fused CostMap.
     */
    public abstract CostMap fuseCostMaps(Collection<CostMap> costMaps, CostMap.Source source);
}
