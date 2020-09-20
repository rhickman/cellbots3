package ai.cellbots.common.data;

import java.util.UUID;

/**
 * DriveGoal data class. Used to send a robot to a target through a drive goal_type.
 */
public class DriveGoal extends PlannerGoal {
    private static final String NAME = "drive";
    private static final String VERSION = "1.0.0";

    // Goal type name
    @SuppressWarnings({"unused", "PublicField"})
    public String name;
    // Goal type parameters.
    @SuppressWarnings({"unused", "PublicField"})
    public LocationParams parameters = new LocationParams();
    // Timestamp when the goal was created
    @SuppressWarnings({"unused", "PublicField"})
    public long timestamp;
    // The goal version
    @SuppressWarnings({"unused", "PublicField"})
    public String version;
    // Priority of the goal, default = 100
    @SuppressWarnings({"unused", "PublicField"})
    public long priority;

    /**
     * Creates DriveGoal object.
     *
     * @param tf  Goal pose.
     * @param map Map name.
     */
    public DriveGoal(Transform tf, String map) {
        name = NAME;
        version = VERSION;
        //noinspection AssignmentToSuperclassField
        super.uuid = UUID.randomUUID().toString();
        parameters.location = tf;
        parameters.map = map;
        timestamp = System.currentTimeMillis();
        priority = 100;
    }
}
