package ai.cellbots.robot.driver.parallax;

import android.content.Context;
import android.util.Log;

import com.felhr.usbserial.UsbSerialInterface;

import java.util.Collections;
import java.util.HashSet;

import ai.cellbots.common.data.BatteryStatus;
import ai.cellbots.common.data.Teleop;
import ai.cellbots.robot.driver.RobotModel;
import ai.cellbots.robot.driver.UsbRobotDriver;

/**
 * Driver for Parallax robot bases.
 */
@SuppressWarnings("WeakerAccess")
public abstract class ParallaxDriver extends UsbRobotDriver {
    private static final String TAG = ParallaxDriver.class.getSimpleName();

    private static final double MIN_BATTERY_PERCENTAGE = 0.3;
    private static final double CRITICAL_BATTERY_PERCENTAGE = 0.25;
    private static final int MAX_PACKET_LENGTH = 16384; // characters
    // The number of ticks per second. Arlo originally recommends 127, we increase this value
    // for a faster speed. The hard limit of this value is 250.
    private static final int MAX_ENCODER_SPEED = 200;

    /**
     * Robot model for Parallax robots. Contains additional information.
     */
    @SuppressWarnings("WeakerAccess")
    protected static class ParallaxRobotModel extends RobotModel {
        private final double mMetersPerTick;
        private final double mWheelbase;
        private final int mFreeRange;
        private final int mMinBumperRange;

        /**
         * Initialize the robot model.
         *
         * @param wheelRadius            The wheel radius in meters.
         * @param ticksPerRevolution     The number of ticks per revolution of the wheel.
         * @param wheelbase              The distance between the wheels in meters.
         * @param maxAcceleration        The maximum robot linear acceleration, in meters/second^2.
         * @param maxAngularAcceleration The maximum robot linear acceleration, in radians/second^2.
         * @param width                  The width of the robot in meters.
         * @param length                 The length of the robot in meters.
         * @param height                 The height of the robot in meters.
         * @param deviceZ                The device height of the phone on the robot.
         * @param freeRange              The minimum distance to slow the robot, in ping units.
         * @param minBumperRange         The minimum distance to trip the bumper, in ping units.
         * @param batteryStatuses        The battery statuses.
         */
        @SuppressWarnings("WeakerAccess")
        protected ParallaxRobotModel(double wheelRadius, double ticksPerRevolution, double wheelbase,
                double maxAcceleration, double maxAngularAcceleration,
                double width, double length, double height, double deviceZ,
                int freeRange, int minBumperRange, BatteryStatus[] batteryStatuses) {
            super(MAX_ENCODER_SPEED * (wheelRadius * 2 * Math.PI / ticksPerRevolution), maxAcceleration,
                    MAX_ENCODER_SPEED * 2.0 * (wheelRadius * 2 * Math.PI / ticksPerRevolution) / wheelbase,
                    maxAngularAcceleration, width, length, height, deviceZ, batteryStatuses);
            mMetersPerTick = (wheelRadius * 2 * Math.PI / ticksPerRevolution);
            mWheelbase = wheelbase;
            mFreeRange = freeRange;
            mMinBumperRange = minBumperRange;
        }

        /**
         * Get the meters per tick.
         *
         * @return The meters per encoder tick.
         */
        private double getMetersPerTick() {
            return mMetersPerTick;
        }

        /**
         * Get the wheelbase in meters.
         *
         * @return The wheelbase in meters.
         */
        private double getWheelbase() {
            return mWheelbase;
        }

        /**
         * Get the free range.
         *
         * @return The free range in ping units.
         */
        private int getFreeRange() {
            return mFreeRange;
        }

        /**
         * Get the bumper range.
         *
         * @return The bumper range in ping units.
         */
        private int getMinBumperRange() {
            return mMinBumperRange;
        }
    }

    /**
     * Compute the default battery statuses.
     *
     * @return The default battery statuses.
     */
    @SuppressWarnings("WeakerAccess")
    protected static BatteryStatus[] createDefaultBatteryStatuses() {
        return new BatteryStatus[] {
                new BatteryStatus("parallax", CRITICAL_BATTERY_PERCENTAGE, MIN_BATTERY_PERCENTAGE),
                new BatteryStatus("phone", CRITICAL_BATTERY_PERCENTAGE, MIN_BATTERY_PERCENTAGE)};
    }

    // A list of acceptable vendor ID, device ID values.
    private static final int[][] DEVICE_IDS = {
            {0x0403, 0x6015},
    };

    private final ParallaxRobotModel mRobotModel;
    private final String mRobotTag;

    private String mUuid = null;
    private String mVersionString = null;
    private BumperState mBumper = BumperState.NONE;

    private Teleop mTeleop = null;
    private String mTeleopUuid = null;

    /**
     * List all special serial numbers.
     * @return The set of all special serial numbers.
     */
    private static String[] allSpecialSerialNumbers() {
        HashSet<String> numbers = new HashSet<>();
        Collections.addAll(numbers, RubbermaidCartDriver.SERIAL_NUMBERS);
        return numbers.toArray(new String[0]);
    }

    /**
     * Create a new Parallax driver.
     * @param context The Android context for the driver.
     * @param name The name of the robot.
     * @param robotTag The database tag for the UUID, e.g. PARALLAX_ARLO.
     * @param acceptSerialNumbers A list of all serial numbers for this driver, or null to ignore.
     * @param robotModel The robot model.
     */
    @SuppressWarnings("WeakerAccess")
    protected ParallaxDriver(Context context, String name, String robotTag,
            String[] acceptSerialNumbers, ParallaxRobotModel robotModel) {
        super(name, context, 100, robotModel, DEVICE_IDS, false,
                acceptSerialNumbers, acceptSerialNumbers == null ? allSpecialSerialNumbers() : null,
                new UsbConfiguration(115200, UsbSerialInterface.DATA_BITS_8, 1,
                        UsbSerialInterface.PARITY_NONE, UsbSerialInterface.FLOW_CONTROL_OFF, true, true));
        mRobotModel = robotModel;
        mRobotTag = robotTag;
        Log.i(TAG, "Parallax Arlo driver created");
    }

    /**
     * Called before the USB update is executed. Stores the phone's battery status.
     */
    @Override
    protected synchronized void onUpdateBeforeUsb() {
        setBatteryStatusToPhoneBattery(1, mUuid);
    }

    /**
     * Called after the USB update is executed. Processes the USB packets.
     */
    @Override
    protected synchronized void onUpdateAfterUsb() {
        String serial = getUsbDeviceSerialNumber();
        if (serial == null) {
            Log.e(TAG, "Serial number is null");
            mUuid = null;
            return;
        }
        if (getDataBuffer().length() > MAX_PACKET_LENGTH) {
            Log.e(TAG, "Very large data buffer - length: " + getDataBuffer().length());
            setDataBuffer(getDataBuffer().substring(getDataBuffer().length() - MAX_PACKET_LENGTH));
        }
        while (getDataBuffer().contains("\n")) {
            int split = getDataBuffer().indexOf("\n");
            String packet = getDataBuffer().substring(0, split).trim();
            setDataBuffer(getDataBuffer().substring(split + 1));
            if (packet.startsWith("ping:")) {
                String[] data = packet.split(" ");
                if (data.length == 4) {
                    try {
                        int left = Integer.valueOf(data[1]);
                        int center = Integer.valueOf(data[2]);
                        int right = Integer.valueOf(data[3]);
                        if (left <= mRobotModel.getMinBumperRange()
                                && (right > mRobotModel.getMinBumperRange() || left < right)) {
                            if (center < mRobotModel.getMinBumperRange()) {
                                mBumper = BumperState.CENTER_LEFT;
                            } else {
                                mBumper = BumperState.LEFT;
                            }
                        } else if (right <= mRobotModel.getMinBumperRange()) {
                            if (center < mRobotModel.getMinBumperRange()) {
                                mBumper = BumperState.CENTER_RIGHT;
                            } else {
                                mBumper = BumperState.RIGHT;
                            }
                        } else if (center <= mRobotModel.getMinBumperRange()) {
                            if (right < left) {
                                mBumper = BumperState.CENTER_RIGHT;
                            } else {
                                mBumper = BumperState.CENTER_LEFT;
                            }
                        } else {
                            mBumper = BumperState.NONE;
                        }
                    } catch (NumberFormatException ex) {
                        Log.w(TAG, "Invalid ping packet number: '" + packet + "'\n", ex);
                    }
                } else {
                    Log.w(TAG, "Invalid ping packet: '" + packet + "'\n");
                }
            }
        }
        onBumperUpdate(mUuid, mBumper);
        mUuid = mRobotTag + ":" + serial;
        mVersionString = "UNVERSIONED";
        onVersionUpdate();
        double teleopLinearSpeed = mTeleop == null ? 0.0 : mTeleop.getVx();
        double teleopAngularSpeed = mTeleop == null ? 0.0 : mTeleop.getRz();
        // Clear speed if we are not the selected robot.
        if (mUuid == null || !mUuid.equals(getSelectedRobotUuid()) || !mUuid.equals(mTeleopUuid)) {
            teleopLinearSpeed = 0.0;
            teleopAngularSpeed = 0.0;
        }
        double linearSpeed = Math.min(mRobotModel.getMaxLinearSpeed(),
                Math.max(teleopLinearSpeed, -mRobotModel.getMaxLinearSpeed()))
                    / mRobotModel.getMetersPerTick();
        double angularSpeed = (Math.min(mRobotModel.getMaxAngularSpeed(),
                Math.max(-mRobotModel.getMaxAngularSpeed(), teleopAngularSpeed))
                    * mRobotModel.getWheelbase() / 2) / mRobotModel.getMetersPerTick();
        double left = linearSpeed - angularSpeed;
        double right = linearSpeed + angularSpeed;
        left = Math.min(MAX_ENCODER_SPEED, Math.max(-MAX_ENCODER_SPEED, left));
        right = Math.min(MAX_ENCODER_SPEED, Math.max(-MAX_ENCODER_SPEED, right));
        Log.v(TAG, "Left: " + left + " right: " + right + " speed: " + linearSpeed +
                " ang: " + angularSpeed);
        writeToUsb("gospd " + ((int)left) + " " + ((int)right) + "\r\npen 7\r\npmask 0\r\n"
                + "safelim " + MAX_ENCODER_SPEED + " " + mRobotModel.getFreeRange()
                + " " + mRobotModel.getMinBumperRange() + "\r\n");
    }

    /**
     * Get the version string of the system.
     * @return The current version string.
     */
    @Override
    public String getVersionString() {
        return mVersionString;
    }

    /**
     * Called after the USB session terminates. Nulls out the stored versions.
     */
    @Override
    protected void onTerminateSession() {
        Log.i(TAG, "Terminating");
        mBumper = BumperState.NONE;
        mUuid = null;
        mVersionString = null;
        mTeleop = null;
        mTeleopUuid = null;
        clearStatuses();
    }

    /**
     * Get the currently connected robot uuid.
     * @return The robot uuid.
     */
    @Override
    public String getRobotUuid() {
        return mUuid;
    }

    /**
     * True if the system is connected.
     * @return True if connected.
     */
    @Override
    public synchronized boolean isConnected() {
        return getRobotUuid() != null && isUsbConnected() && getVersionString() != null;
    }

    /**
     * Set the teleop of the robot.
     * @param teleop The teleop state of the robot, if any.
     */
    @Override
    public synchronized void setTeleop(Teleop teleop) {
        super.setTeleop(teleop);
        mTeleop = teleop;
        mTeleopUuid = getSelectedRobotUuid();
    }
}
