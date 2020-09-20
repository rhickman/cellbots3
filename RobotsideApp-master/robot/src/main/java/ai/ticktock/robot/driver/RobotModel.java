package ai.cellbots.robot.driver;


import ai.cellbots.common.data.BatteryStatus;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The model of robot currently in operation. All elements of this class and subclasses should
 * remain final to avoid race conditions and unexpected behavior.
 */
public class RobotModel {
    // Maximum linear speed in meters / second.
    private final double mMaxLinearSpeed;
    // Maximum linear acceleration in meters / second^2.
    private final double mMaxLinearAcceleration;
    // Maximum angular speed in radians / second.
    private final double mMaxAngularSpeed;
    // Maximum angular acceleration in radians / second^2.
    private final double mMaxAngularAcceleration;
    // Robot width.
    private final double mWidth;
    // Robot length.
    private final double mLength;
    // Robot height.
    private final double mHeight;
    // Z height of the sensor on the robot.
    private final double mDeviceZ;
    // Battery statuses.
    private final BatteryStatus[] mBatteryStatuses;

    /**
     * Initializes the robot model.
     *
     * @param maxLinearSpeed         The maximum robot linear speed, in meters/second.
     * @param maxLinearAcceleration  The maximum robot linear acceleration, in meters/second^2.
     * @param maxAngularSpeed        The maximum robot angular speed, in radians/second.
     * @param maxAngularAcceleration The maximum robot linear acceleration, in radians/second^2.
     * @param width                  The width of the robot in meters.
     * @param length                 The length of the robot in meters.
     * @param height                 The height of the robot in meters.
     * @param deviceZ                The device height of the phone on the robot.
     * @param batteryStatuses        The battery statuses.
     */
    public RobotModel(double maxLinearSpeed, double maxLinearAcceleration, double maxAngularSpeed,
            double maxAngularAcceleration, double width, double length, double height,
            double deviceZ, BatteryStatus[] batteryStatuses) {
        checkArgument(width > 0, "Expect positive width, but %s", width);
        checkArgument(length > 0, "Expect positive length, but %s", length);
        checkArgument(height > 0, "Expect positive height, but %s", height);
        mMaxLinearSpeed = maxLinearSpeed;
        mMaxLinearAcceleration = maxLinearAcceleration;
        mMaxAngularSpeed = maxAngularSpeed;
        mMaxAngularAcceleration = maxAngularAcceleration;
        mWidth = width;
        mLength = length;
        mHeight = height;
        mDeviceZ = deviceZ;
        mBatteryStatuses = checkNotNull(batteryStatuses, "batteryStatus is null").clone();
    }

    /**
     * Gets the maximum linear speed.
     *
     * @return The maximum robot linear speed, in meters/second.
     */
    public double getMaxLinearSpeed() {
        return mMaxLinearSpeed;
    }

    /**
     * Gets the maximum linear acceleration.
     *
     * @return The maximum robot linear acceleration, in meters/second^2.
     */
    public double getMaxLinearAcceleration() {
        return mMaxLinearAcceleration;
    }

    /**
     * Gets the maximum angular speed.
     *
     * @return The maximum robot angular speed, in radians/second.
     */
    public double getMaxAngularSpeed() {
        return mMaxAngularSpeed;
    }

    /**
     * Gets the maximum angular acceleration.
     *
     * @return The maximum robot angular acceleration, in radians/second^2.
     */
    public double getMaxAngularAcceleration() {
        return mMaxAngularAcceleration;
    }

    /**
     * Gets the width of the robot.
     *
     * @return The width in meters.
     */
    public double getWidth() {
        return mWidth;
    }

    /**
     * Gets the length of the robot.
     *
     * @return The length in meters.
     */
    public double getLength() {
        return mLength;
    }

    /**
     * Gets the height of the robot.
     *
     * @return The height in meters.
     */
    public double getHeight() {
        return mHeight;
    }

    /**
     * Gets the Z height of the sensor on the robot.
     *
     * @return The Z height in meters.
     */
    public double getDeviceZ() {
        return mDeviceZ;
    }

    /**
     * Gets the battery statuses.
     *
     * @return The battery statuses.
     */
    public BatteryStatus[] getBatteryStatuses() {
        return mBatteryStatuses.clone();
    }
}
