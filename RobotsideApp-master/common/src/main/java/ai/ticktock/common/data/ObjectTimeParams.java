package ai.cellbots.common.data;

/**
 * ObjectTimeParams data class. Describes parameters for a POI goal_type.
 * TODO: set parameters dynamically for goals according to the specifications in goal_types.
 */
public class ObjectTimeParams {
    // uuid of the target POI.
    public String target;
    // Time to execute the goal.
    public long time;

    public ObjectTimeParams() {
    }
}