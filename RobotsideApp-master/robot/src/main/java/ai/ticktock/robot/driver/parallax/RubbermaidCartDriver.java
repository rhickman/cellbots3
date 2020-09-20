package ai.cellbots.robot.driver.parallax;

import android.content.Context;

import ai.cellbots.common.Transform;

/**
 * Driver for the Rubbermaid cart.
 */
public class RubbermaidCartDriver extends ParallaxDriver {
    private final static double LINEAR_ACCELERATION_MAX = 0.2;  // in Meters / second^2
    private final static double ANGULAR_ACCELERATION_MAX = 0.2;  // in radians / second^2

    private static final double BODY_WIDTH = 18 * (2.54 / 100.0); // meters
    // TODO: we multiply body length by an extra factor of 2 since the center is at the back
    private static final double BODY_LENGTH = 39 * (2.54 / 100.0) * 2; // meters
    private static final double BODY_HEIGHT = 34 * (2.54 / 100.0); // meters
    private static final double DEVICE_Z = 28 * (2.54 / 100.0); // meters
    private static final double DEVICE_X = 32 * (2.54 / 100); // meters
    private static final double WHEELBASE = 15 * (2.54 / 100.0); // in meters
    private static final double TICKS_PER_REVOLUTION = 144.0;
    private static final double WHEEL_RADIUS = 3.0 * (2.54 / 100.0); // in meters

    private static final int FREE_RANGE = 60; // cm from ping, above this there is no slowing
    private static final int MIN_BUMPER_RANGE = 20; // centimeters from ping

    static final String[] SERIAL_NUMBERS = {"WX27NXQ"};

    /**
     * Create a new Rubbermaid cart driver driver.
     * @param context The Android context for the driver.
     */
    public RubbermaidCartDriver(Context context) {
        super(context, "RubbermaidCartDriver", "RUBBERMAID_CART", SERIAL_NUMBERS,
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
