package ai.cellbots.robot.driver.parallax;

import android.content.Context;

import ai.cellbots.common.Transform;

/**
 * Driver for the Arlo base.
 */
public class ParallaxArloDriver extends ParallaxDriver {
    // As you increase MAX_ENCODER_SPEED, increase the max accelerations, too.
    private final static double LINEAR_ACCELERATION_MAX = 0.2;  // in Meters / second^2
    private final static double ANGULAR_ACCELERATION_MAX = 1.0;  // in radians / second^2

    private static final double BODY_WIDTH = 0.4572; // meters
    private static final double BODY_LENGTH = 0.4572; // meters
    private static final double BODY_HEIGHT = 1.524; // meters
    private static final double DEVICE_Z = 0.810; // meters
    private static final double DEVICE_X = 0.185; // meters
    private static final double WHEELBASE = 0.388; // in meters
    private static final double WHEEL_RADIUS = 0.0762; // in meters
    private static final double TICKS_PER_REVOLUTION = 144.0;

    private static final int FREE_RANGE = 60; // cm from ping, above this there is no slowing
    private static final int MIN_BUMPER_RANGE = 20; // centimeters from ping

    /**
     * Creates a new Parallax driver.
     *
     * @param context The Android context for the driver.
     */
    public ParallaxArloDriver(Context context) {
        super(context, "ParallaxArloDriver", "PARALLAX_ARLO", null,
                new ParallaxRobotModel(WHEEL_RADIUS, TICKS_PER_REVOLUTION, WHEELBASE,
                        LINEAR_ACCELERATION_MAX, ANGULAR_ACCELERATION_MAX, BODY_WIDTH, BODY_LENGTH,
                        BODY_HEIGHT, DEVICE_Z, FREE_RANGE, MIN_BUMPER_RANGE,
                        createDefaultBatteryStatuses()));
    }

    /**
     * Computes the position of the robot from the phone transform.
     *
     * @param cameraPose The pose of the camera.
     * @return The robot base pose.
     */
    @Override
    public Transform computeRobotBasePosition(Transform cameraPose) {
        return computeRobotPositionFromCameraPose(cameraPose, DEVICE_X, DEVICE_Z);
    }
}
