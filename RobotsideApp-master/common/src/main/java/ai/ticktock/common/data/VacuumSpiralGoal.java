package ai.cellbots.common.data;

import java.util.UUID;

/**
 * Vacuum spiral goal. Used to create a spiral of vacuuming.
 */
@SuppressWarnings("unused")
public class VacuumSpiralGoal extends PlannerGoal {
    public static final String NAME = "vacuumSpiral";
    public static final String VERSION = "1.0.0";

    // Goal type name
    public String name;
    // Goal type parameters.
    public LocationTimeParams parameters = new LocationTimeParams();
    // Timestamp when the goal was created
    public long timestamp;
    // The version of the goal
    public String version;
    // Priority of the goal, default = 100
    public long priority;

    public VacuumSpiralGoal(Transform tf, String map, long time) {
        name = NAME;
        version = VERSION;
        uuid = UUID.randomUUID().toString();
        parameters.location = tf;
        parameters.map = map;
        parameters.time = time;
        timestamp = System.currentTimeMillis();
        priority = 100;
    }

    public VacuumSpiralGoal() {
    }
}
