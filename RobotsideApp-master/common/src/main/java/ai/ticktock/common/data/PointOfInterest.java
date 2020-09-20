package ai.cellbots.common.data;

import org.parceler.Parcel;

import java.util.UUID;

/**
 * PointOfInterest data class. Used to save data of a point of interest in a map.
 */
@Parcel
public class PointOfInterest {
    public static final String TYPE = "point_of_interest";

    // Name of point of interest.
    public String name;
    // Point of interest variables.
    public PointOfInterestVars variables = new PointOfInterestVars();
    // Point of interest UUID
    public String uuid;
    public String type;

    /**
     * Class constructor.
     */
    public PointOfInterest() {
    }

    /**
     * Class constructor.
     *
     * @param tf    transform of the POI.
     * @param label name of the POI.
     */
    public PointOfInterest(Transform tf, String label) {
        type = TYPE;
        uuid = UUID.randomUUID().toString();
        variables.location = tf;
        variables.name = label;
    }

    public Transform getTf() {
        return variables.location;
    }
}
