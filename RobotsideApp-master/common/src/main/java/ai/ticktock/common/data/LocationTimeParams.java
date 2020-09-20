package ai.cellbots.common.data;

/**
 * LocationTimeParams data class. Describes parameters for a drive goal_type.
 * TODO: set parameters dynamically for goals according to the specifications in goal_types.
 */
public class LocationTimeParams {
    // Target location as a transform.
    public Transform location;
    // Name of map to set the goal to.
    public String map;
    // Time to execute the action for.
    public long time;

    public LocationTimeParams() {
    }
}
