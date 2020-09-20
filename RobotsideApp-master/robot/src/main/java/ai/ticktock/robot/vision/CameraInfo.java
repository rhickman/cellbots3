package ai.cellbots.robot.vision;

/**
 * Calibration and information of a camera.
 */
public class CameraInfo {
    /**
     * SensorType types.
     */
    public enum SensorType {
        COLOR,
        FISHEYE,
        DEPTH
    }

    private final SensorType mSensorType;
    private final double mCx;
    private final double mCy;
    private final double mFx;
    private final double mFy;

    /**
     * Create the sensorType calibration info.
     *
     * @param sensorType The sensorType.
     * @param cx The cx of the sensorType.
     * @param cy The cy of the sensorType.
     * @param fx The fx of the sensorType.
     * @param fy The fy of the sensorType.
     */
    public CameraInfo(SensorType sensorType, double cx, double cy, double fx, double fy) {
        mSensorType = sensorType;
        mCx = cx;
        mCy = cy;
        mFx = fx;
        mFy = fy;
    }

    /**
     * Create a sensorType info by scaling.
     *
     * @param sensorType The sensorType.
     * @param scale The scale factor.
     * @param cameraInfo The sensorType info to copy.
     */
    public CameraInfo(SensorType sensorType, double scale, CameraInfo cameraInfo) {
        mSensorType = sensorType;
        mCx = cameraInfo.getCx() * scale;
        mCy = cameraInfo.getCy() * scale;
        mFx = cameraInfo.getFx() * scale;
        mFy = cameraInfo.getFy() * scale;
    }

    /**
     * Get the camera for this camera info.
     *
     * @return The camera.
     */
    public SensorType getCamera() {
        return mSensorType;
    }

    /**
     * Get the cx for this camera info.
     *
     * @return The cx.
     */
    public double getCx() {
        return mCx;
    }


    /**
     * Get the cy for this camera info.
     *
     * @return The cy.
     */
    public double getCy() {
        return mCy;
    }


    /**
     * Get the fx for this camera info.
     *
     * @return The fx.
     */
    public double getFx() {
        return mFx;
    }


    /**
     * Get the fy for this camera info.
     *
     * @return The fy.
     */
    public double getFy() {
        return mFy;
    }

    /**
     * Convert a point from 3D to 2D in the X axis.
     *
     * @param x X position.
     * @param z Z position.
     * @return The pixel coordinate.
     */
    @SuppressWarnings("unused")
    public int pointToPixelX(double x, double z) {
        return  (int) ((mFx * (x / z)) + mCx);
    }

    /**
     * Convert a point from 3D to 2D in the X axis.
     *
     * @param y Y position.
     * @param z Z position.
     * @return The pixel coordinate.
     */
    @SuppressWarnings("unused")
    public int pointToPixelY(double y, double z) {
        return  (int) ((mFy * (y / z)) + mCy);
    }
}
