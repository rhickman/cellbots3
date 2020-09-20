package ai.cellbots.common.data;

import java.util.UUID;

/**
 * PatrolDriverPOIGoal data class. Used to send a robot to a POI and patrol around it.
 */
public class PatrolDriverPOIGoal extends PlannerGoal {
    public static final String NAME = "patrolDriverPOI";
    public static final String VERSION = "1.0.0";

    // Goal type name
    public String name;
    // Goal type parameters.
    public ObjectTimeParams parameters = new ObjectTimeParams();
    // Timestamp when the goal was created
    public long timestamp;
    // The goal version
    public String version;
    // Priority of the goal, default = 100
    public long priority;

    public PatrolDriverPOIGoal(String POIUuid, long time)  {
        name = NAME;
        version = VERSION;
        uuid = UUID.randomUUID().toString();
        parameters.target = POIUuid;
        parameters.time = time;
        timestamp = System.currentTimeMillis();
        priority = 100;
    }

    public PatrolDriverPOIGoal() {
    }
}
