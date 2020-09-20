package ai.cellbots.robot.vision;

import android.support.annotation.NonNull;

import java.util.Objects;

/**
 * The point cloud.
 */
public class PointCloud {
    /**
     * Format of the PointCloud.
     */
    public enum Format {
        X_Y_Z_I, // 4 floating point values
    }

    private final double mTimestamp;
    private final Format mFormat;
    private final float[] mPoints;

    /**
     * Create the point cloud.
     *
     * @param timestamp The timestamp.
     * @param format    The format of this image.
     * @param points    The points to load. Must not be mutated after creation.
     */
    public PointCloud(double timestamp, @NonNull Format format, float[] points) {
        Objects.requireNonNull(format);
        Objects.requireNonNull(points);
        mTimestamp = timestamp;
        mFormat = format;
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        mPoints = points;
        if (format == Format.X_Y_Z_I) {
            if (points.length % 4 != 0) {
                throw new Error("Points length must be a multiple of 4: " + points.length);
            }
        }
    }

    /**
     * Get the points data.
     *
     * @return The floating point array. Mutation may cause unexpected behavior.
     */
    public float[] getPoints() {
        //noinspection ReturnOfCollectionOrArrayField
        return mPoints;
    }

    /**
     * Get the format of this point cloud.
     *
     * @return The format of the point cloud.
     */
    public Format getFormat() {
        return mFormat;
    }

    /**
     * Get the timestamp of the point cloud.
     *
     * @return The timestamp, in milliseconds.
     */
    public double getTimestamp() {
        return mTimestamp;
    }

    /**
     * Get the number of points.
     *
     * @return The number of points.
     */
    public int getPointCount() {
        if (mFormat == Format.X_Y_Z_I) {
            return mPoints.length / 4;
        }
        return 0;
    }

    /**
     * Get a point's coordinate.
     *
     * @param i  The index of the point.
     * @param pt The position array. Must be at least 3 long or it will crash.
     */
    public void getPoint(int i, @NonNull double[] pt) {
        if (mFormat == Format.X_Y_Z_I) {
            pt[2] = mPoints[i * 4 + 2];
            pt[1] = mPoints[i * 4 + 1];
            pt[0] = mPoints[i * 4];
        } else {
            throw new Error("Invalid format: " + getFormat());
        }
    }
}
