package ai.cellbots.robot.driver;


import java.util.Collection;
import java.util.Collections;

import ai.cellbots.common.Transform;
import ai.cellbots.robot.costmap.GeometryCostMap;

/**
 * Stores impacts from the robot's bumpers
 */
public class BumperCostMap extends GeometryCostMap {
    private Transform mTransform = null;

    /**
     * Creates the CostMap.
     *
     * @param robotRadius The physical radius of the robot (in meters).
     * @param resolution  The resolution of the CostMap, being the width of a square in meters.
     */
    @SuppressWarnings("unused")
    public BumperCostMap(double resolution, double robotRadius) {
        super(Source.BUMPER, resolution, robotRadius);
    }

    /**
     * Updates the bumper CostMap geometry.
     * @param list The geometry.
     */
    public synchronized void addBumperGeometry(Collection<Geometry> list) {
        if (mTransform != null) {
            // TODO get proper timestamp.
            long timestamp = 0;
            // TODO transform the geometry with the robot position.
            update(timestamp, Collections.<Geometry>emptyList());
        }
    }

    /**
     * Updates the transform of the bumper.
     */
    public synchronized void setTransform(Transform transform) {
        mTransform = transform;
        // TODO get proper timestamp.
        long timestamp = 0;
        update(timestamp, Collections.<Geometry>emptyList());
    }
}
