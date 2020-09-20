package ai.cellbots.arcompanion.model;

import android.support.annotation.NonNull;

/**
 * Data class containing info about a clicked point in AR view.
 * Contains time stamp of point creation and its depth in relation to the user's device.
 *
 * Created by playerthree on 8/10/17.
 */

public class ARViewPoint {

    private final double mTimestamp;

    // x, y, z coordinates of point recorded from the user's device
    private final double[] mCoordinates;

    public ARViewPoint(double timestamp, @NonNull double[] coordinates) {
        mTimestamp = timestamp;
        mCoordinates = new double[coordinates.length];
        System.arraycopy(coordinates, 0, mCoordinates, 0, coordinates.length);
    }

    public double getTimestamp() {
        return mTimestamp;
    }

    public double getCoordinate(int position) {
        return mCoordinates[position];
    }

    public double[] getCoordinates() {
        return mCoordinates;
    }
}