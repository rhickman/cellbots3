package ai.cellbots.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A polygon in 3D space.
 */
public class Polygon {
    private final List<Transform> mPoints;

    /**
     * Creates a polygon.
     *
     * @param points The points list.
     */
    public Polygon(List<Transform> points) {
        mPoints = Collections.unmodifiableList(new ArrayList<>(points));
    }

    /**
     * Creates a polygon.
     *
     * @param points The points array.
     */
    public Polygon(Transform[] points) {
        ArrayList<Transform> transforms = new ArrayList<>(points.length);
        Collections.addAll(transforms, points);
        mPoints = Collections.unmodifiableList(transforms);
    }

    /**
     * Gets the size of the polygon, as the number of points.
     *
     * @return The number of points in the polygon
     */
    public int getSize() {
        return mPoints.size();
    }

    /**
     * Gets the points of the polygon.
     *
     * @return The points list.
     */
    public List<Transform> getPoints() {
        return Collections.unmodifiableList(mPoints);
    }

    /**
     * Returns true if there are no points in the polygon.
     *
     * @return True if no points.
     */
    public boolean isEmpty() {
        return mPoints.isEmpty();
    }
}
