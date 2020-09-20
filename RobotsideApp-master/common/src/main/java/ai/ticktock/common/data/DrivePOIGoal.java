package ai.cellbots.common.data;

import java.util.UUID;

/**
 * DrivePOIGoal data class. Used to send a robot to a POI through a drivePOI goal_type.
 */
@SuppressWarnings("unused")
public class DrivePOIGoal extends PlannerGoal {
    public static final String NAME = "drivePOI";
    public static final String VERSION = "1.0.0";

    // Goal type name
    public String name;
    // Goal type parameters.
    public ObjectParams parameters = new ObjectParams();
    // Timestamp when the goal was created
    public long timestamp;
    // Goal version
    public String version;
    // Priority of the goal, default = 100
    public long priority;

    /**
     * Class constructor
     *
     * @param POIUuid uuid of POI to go to, saved as an object type.
     */
    public DrivePOIGoal(String POIUuid) {
        name = NAME;
        version = VERSION;
        uuid = UUID.randomUUID().toString();
        parameters.target = POIUuid;
        timestamp = System.currentTimeMillis();
        priority = 100;
    }
}
