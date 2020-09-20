package ai.cellbots.common.data;

import java.util.UUID;

/**
 * DriveGoal data class. Used to send a robot to a target through a drive goal_type.
 */
@SuppressWarnings("unused")
public class DrivePointGoal extends PlannerGoal {
    public static final String NAME = "drivePoint";
    public static final String VERSION = "1.0.0";

    // Goal type name
    public String name;
    // Goal type parameters.
    public LocationParams parameters = new LocationParams();
    // Timestamp when the goal was created
    public long timestamp;
    // The goal version
    public String version;
    // Priority of the goal, default = 100
    public long priority;

    public DrivePointGoal(Transform tf, String map) {
        name = NAME;
        version = VERSION;
        uuid = UUID.randomUUID().toString();
        parameters.location = tf;
        parameters.map = map;
        timestamp = System.currentTimeMillis();
        priority = 100;
    }

    public DrivePointGoal() {
    }
}
