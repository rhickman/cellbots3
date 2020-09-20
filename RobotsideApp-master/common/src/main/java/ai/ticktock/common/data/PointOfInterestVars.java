package ai.cellbots.common.data;

import org.parceler.Parcel;

/**
 * PointOfInterestVars data class. Describes variables of a point of interest.
 */
@Parcel
public class PointOfInterestVars {

    // POI location as a transform.
    public Transform location;
    // Name of map to set the POI to.
    public String name;

    /**
     * Class constructor.
     */
    public PointOfInterestVars() {
    }
}
