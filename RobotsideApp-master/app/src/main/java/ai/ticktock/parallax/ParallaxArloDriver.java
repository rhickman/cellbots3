package ai.cellbots.parallax;

import android.content.Context;
import android.util.Log;

import com.felhr.usbserial.UsbSerialInterface;

import java.util.Locale;

import ai.cellbots.common.Transform;
import ai.cellbots.common.data.BatteryStatus;
import ai.cellbots.robotlib.RobotDriver;
import ai.cellbots.robotlib.UsbRobotDriver;

/**
 * Driver for the Parallax Arlo Propeller-Based Robot.
 */
public class ParallaxArloDriver extends UsbRobotDriver {
    private static final String TAG = ParallaxArloDriver.class.getSimpleName();
    private static final double MIN_BATTERY_PERCENTAGE = 0.3;
    private static final double CRITICAL_BATTERY_PERCENTAGE = 0.25;
    private static final double BODY_WIDTH = 18 * (2.54 / 100.0); // meters
    private static final double BODY_LENGTH = 18 * (2.54 / 100.0); // meters
    private static final double BODY_HEIGHT = 10 * (2.54 / 100.0); // meters
    private static final double DEVICE_Z = 28 * (2.54 / 100.0); // meters
    private static final double DEVICE_X = -7 * (2.54 / 100); // meters
    private static final double WHEELBASE = 15 * (2.54 / 100.0); // in meters
    private static final double TICKS_PER_REVOLUTION = 144.0;
    private static final double WHEEL_RADIUS = 3.0 * (2.54 / 100.0); // in meters
    private static final double METERS_PER_TICK = WHEEL_RADIUS * 2.0 * Math.PI / TICKS_PER_REVOLUTION;
    private static final int FREE_RANGE = 60; // cm from ping, above this there is no slowing
    private static final int MIN_BUMPER_RANGE = 20; // centimeters from ping
    private static final int MAX_PACKET_LENGTH = 10000; // characters
    private static final int MAX_ENCODER_SPEED = 127;
    private static final double MAX_VELOCITY = MAX_ENCODER_SPEED * METERS_PER_TICK; // meters/second
    private static final double MAX_ANGULAR_VELOCITY = MAX_ENCODER_SPEED * 2.0 * METERS_PER_TICK / WHEELBASE; // rad/second

    // A list of acceptable vendor ID, device ID values.
    private static final int[][] DEVICE_IDS = {
            {0x0403, 0x6015},
    };

    private int mBumper = RobotDriver.NO_BUMPER_ID;

    /**
     * Create the driver.
     *
     * @param parent    Android context parent.
     */
    public ParallaxArloDriver(Context parent) {
        super(parent, new RobotModel(MAX_VELOCITY, MAX_ANGULAR_VELOCITY, BODY_WIDTH, BODY_LENGTH,
                BODY_HEIGHT, DEVICE_Z, new BatteryStatus[] {
                new BatteryStatus("parallax", CRITICAL_BATTERY_PERCENTAGE, MIN_BATTERY_PERCENTAGE),
                new BatteryStatus("phone", CRITICAL_BATTERY_PERCENTAGE, MIN_BATTERY_PERCENTAGE),
        }), DEVICE_IDS, false,
                new UsbConfiguration(115200, UsbSerialInterface.DATA_BITS_8, 1,
                        UsbSerialInterface.PARITY_NONE, UsbSerialInterface.FLOW_CONTROL_OFF, true, true));
    }

    /**
     * Update the system always, before USB logic is handled.
     */
    @Override
    protected void onUpdateBeforeUsb() {
        setBatteryStatusToPhoneBattery(1);
    }

    /**
     * On update system when the USB is connected.
     */
    @Override
    protected void onUpdateAfterUsb() {
        if (getDataBuffer().length() > MAX_PACKET_LENGTH) {
            Log.w(TAG, "Very large data buffer - length: " + getDataBuffer().length());
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
                        if (left <= MIN_BUMPER_RANGE && (right > MIN_BUMPER_RANGE
                                || left < right)) {
                            if (center < MIN_BUMPER_RANGE) {
                                mBumper = CENTER_LEFT_BUMPER_ID;
                            } else {
                                mBumper = LEFT_BUMPER_ID;
                            }
                        } else if (right <= MIN_BUMPER_RANGE) {
                            if (center < MIN_BUMPER_RANGE) {
                                mBumper = CENTER_RIGHT_BUMPER_ID;
                            } else {
                                mBumper = RIGHT_BUMPER_ID;
                            }
                        } else if (center <= MIN_BUMPER_RANGE) {
                            if (right < left) {
                                mBumper = CENTER_RIGHT_BUMPER_ID;
                            } else {
                                mBumper = CENTER_LEFT_BUMPER_ID;
                            }
                        } else {
                            mBumper = NO_BUMPER_ID;
                        }

                        setStateString("ARLO SONAR: L:"
                                + String.format(Locale.US, "%05d", left)
                                + " C:" + String.format(Locale.US, "%05d", center)
                                + " L:" + String.format(Locale.US, "%05d", right));
                    } catch (NumberFormatException ex) {
                        Log.w(TAG, "Invalid ping packet number: '" + packet + "'\n", ex);
                    }
                } else {
                    Log.w(TAG, "Invalid ping packet: '" + packet + "'\n");
                }
            }
        }

        String serial = getUsbDeviceSerialNumber();
        if (serial == null) {
            Log.w(TAG, "Serial number is null");
            setUuid(null);
            return;
        }
        setUuid("PARALLAX_ARLO:" + serial);
        setVersionString("UNVERSIONED");

        double speed = getRobotSpeed() / METERS_PER_TICK;
        double aSpeed = (getRobotAng() * WHEELBASE / 2) / METERS_PER_TICK;
        double left = speed - aSpeed;
        double right = speed + aSpeed;
        left = Math.min(MAX_ENCODER_SPEED, Math.max(-MAX_ENCODER_SPEED, left));
        right = Math.min(MAX_ENCODER_SPEED, Math.max(-MAX_ENCODER_SPEED, right));
        writeToUsb("gospd " + ((int)left) + " " + ((int)right) + "\r\npen 7\r\npmask 0\r\n"
                + "safelim " + MAX_ENCODER_SPEED + " " + FREE_RANGE + " " + MIN_BUMPER_RANGE + "\r\n");
    }

    /**
     * Called when a USB session is terminated.
     */
    @Override
    protected void onTerminateSession() {
        mBumper = RobotDriver.NO_BUMPER_ID;
        setUuid(null);
        setVersionString(null);
    }

    /**
     * On the initialization.
     */
    @Override
    protected void onInit() {
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
     * Tango to base system.
     * @param tf The current world (ADF) transform.
     * @return Get the transform of the tango.
     */
    @Override
    public Transform tangoToBase(Transform tf) {
        return stablePhoneTransform(tf, DEVICE_X, getModel().getDeviceZ());
    }

    /**
     * Get the bumper status.
     * @return The bumper.
     */
    @Override
    public int getBumper() {
        return mBumper;
    }
}
