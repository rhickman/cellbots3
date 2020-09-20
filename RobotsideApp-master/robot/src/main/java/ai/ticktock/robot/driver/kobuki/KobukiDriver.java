package ai.cellbots.robot.driver.kobuki;

import android.content.Context;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;

import ai.cellbots.common.Strings;
import ai.cellbots.common.Transform;
import ai.cellbots.common.data.BatteryStatus;
import ai.cellbots.common.data.Teleop;
import ai.cellbots.robot.driver.RobotModel;
import ai.cellbots.robot.driver.UsbRobotDriver;

/**
 * Driver for the Kobuki robot base.
 */
public class KobukiDriver extends UsbRobotDriver {
    private static final String TAG = KobukiDriver.class.getSimpleName();

    private static final int TIMESTAMP = 2;
    private static final int BUMPER = 4;
    private static final int WHEEL_DROP = 5;
    private static final int CLIFF = 6;
    private static final int BUTTON = 13;
    private static final int CHARGER = 14;
    private static final int BATTERY = 15;

    private static final int KOBUKI_NO_BUMPER_ID = 0;
    private static final int KOBUKI_RIGHT_BUMPER_ID = 1;
    private static final int KOBUKI_CENTER_RIGHT_BUMPER_ID = (1 | 2);
    private static final int KOBUKI_CENTER_BUMPER_ID = 2;
    private static final int KOBUKI_CENTER_LEFT_BUMPER_ID = (4 | 2);
    private static final int KOBUKI_LEFT_BUMPER_ID = 4;

    private ReadState mReadState = ReadState.SEARCH_START;

    private byte mChecksum = 0;
    private int mPacketLength = 0;
    private byte[] mNextPacket = null;
    private String mHardwareVersion = null;
    private String mFirmwareVersion = null;
    private String mUuid = null;
    private BaseStatus mBaseStatus = null;

    private static final double MAX_BATTERY_VOLTAGE = 16.7;
    private static final double LOW_BATTERY_VOLTAGE = 14.0;
    private static final double MIN_BATTERY_VOLTAGE = 13.2;
    private static final double MIN_BATTERY_PERCENTAGE = 0.3;
    private static final double CRITICAL_BATTERY_PERCENTAGE = 0.25;

    private static final double MAX_VELOCITY = 0.65;
    private static final double MAX_ANGULAR_VELOCITY = 2.5;
    private static final double BODY_WIDTH = 0.355;
    private static final double BODY_LENGTH = 0.355;
    private static final double BODY_HEIGHT = 0.420;
    private static final double DEVICE_Z = 0.1;

    private Teleop mTeleop = null;
    private String mTeleopUuid = null;

    private final static double LINEAR_ACCELERATION_MAX = 1.0;  // in Meters / second^2
    private final static double ANGULAR_ACCELERATION_MAX = 2.0;  // in radians / second^2

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
        while (hasNextByte()) {
            onByteReceived(getNextByte());
        }
        sendUpdatePackets();
    }

    /**
     * Called after the USB session terminates. Nulls out the stored versions.
     */
    @Override
    protected void onTerminateSession() {
        mHardwareVersion = null;
        mFirmwareVersion = null;
        mUuid = null;
        mTeleop = null;
        mTeleopUuid = null;
        clearStatuses();
    }

    /**
     * The current reading state of the driver. The driver goes through four steps:
     * * Reading the initial 0xAA byte (SEARCH_START)
     * * Reading the secondary 0x55 byte (SEARCH_HEADER)
     * * Reading the length byte (GET_LENGTH)
     * * Reading the packet bytes (READ_PACKET)
     */
    private enum ReadState {
        SEARCH_START, //Search for byte = 0xAA
        SEARCH_HEADER, //Search for byte = 0x55
        GET_LENGTH, //Read the header length byte
        READ_PACKET, //Read in the bytes of the packet
    }

    // A list of acceptable vendor ID, device ID values.
    private static final int[][] DEVICE_IDS = {
            {0x403, 0x6001},
    };

    /**
     * Create a new Kobuki driver.
     * @param context The Android context for the driver.
     */
    public KobukiDriver(Context context) {
        super("KobukiDriver", context, 100,
                new RobotModel(MAX_VELOCITY, LINEAR_ACCELERATION_MAX,
                        MAX_ANGULAR_VELOCITY, ANGULAR_ACCELERATION_MAX, BODY_WIDTH, BODY_LENGTH,
                        BODY_HEIGHT, DEVICE_Z, new BatteryStatus[] {
                        new BatteryStatus("kobuki", CRITICAL_BATTERY_PERCENTAGE,
                                MIN_BATTERY_PERCENTAGE, MIN_BATTERY_VOLTAGE, LOW_BATTERY_VOLTAGE,
                                MAX_BATTERY_VOLTAGE),
                        new BatteryStatus("phone", CRITICAL_BATTERY_PERCENTAGE,
                                MIN_BATTERY_PERCENTAGE),
        }), DEVICE_IDS, true);
    }

    /**
     * Sets the teleop of the robot.
     * 
     * @param teleop The teleop state of the robot, if any.
     */
    @Override
    public synchronized void setTeleop(Teleop teleop) {
        mTeleop = teleop;
        mTeleopUuid = getSelectedRobotUuid();
    }

    /**
     * Computes the position of the robot from the phone transform.
     * 
     * @param cameraPose The pose of the camera.
     * @return The robot base pose.
     */
    @Override
    public Transform computeRobotBasePosition(Transform cameraPose) {
        final double PHONE_OFFSET_F = 0.2;
        return computeRobotPositionFromCameraPose(cameraPose, PHONE_OFFSET_F, DEVICE_Z);
    }

    /**
     * Gets the currently connected robot uuid.
     * 
     * @return The robot uuid.
     */
    @Override
    public String getRobotUuid() {
        return mUuid;
    }

    /**
     * Determines if the robot is connected.
     * 
     * @return True if the robot is connected.
     */
    @Override
    public boolean isConnected() {
        return getRobotUuid() != null && getVersionString() != null && isUsbConnected();
    }

    /**
     * Called when a byte is received.
     * @param next The next byte read.
     */
    private synchronized void onByteReceived(byte next) {
        Log.v(TAG, "Byte: " + next);
        if (mReadState == ReadState.SEARCH_START) {
            if (next == (byte) 0xAA) {
                mReadState = ReadState.SEARCH_HEADER;
            } else if (next != 0) {
                Log.w(TAG, "Warning: invalid byte: " + Strings.byteToHexString(next));
            }
        } else if (mReadState == ReadState.SEARCH_HEADER) {
            if (next == (byte) 0xAA) {
                // Sometimes the Kobuki doubles up on 0xAA bytes. When this occurs, we just wait
                // for the next 0x55. E.g., we get 0xAA 0xAA 0x55
                mReadState = ReadState.SEARCH_HEADER;
            } else if (next == (byte) 0x55) {
                mReadState = ReadState.GET_LENGTH;
            } else {
                mReadState = ReadState.SEARCH_START;
                Log.w(TAG, "Warning: invalid after starter (0xAA) byte: " + Strings.byteToHexString(next));
            }
        } else if (mReadState == ReadState.GET_LENGTH) {
            if (next != 0) {
                mChecksum = next;
                mPacketLength = next;
                if (mPacketLength < 0) {
                    mPacketLength += 256;
                }
                mNextPacket = new byte[mPacketLength];
                mReadState = ReadState.READ_PACKET;
            } else {
                mReadState = ReadState.SEARCH_START;
                Log.w(TAG, "Warning: invalid packet length: " + next);
            }
        } else if (mReadState == ReadState.READ_PACKET) {
            if (mPacketLength > 0) {
                mNextPacket[mNextPacket.length - mPacketLength] = next;
                mChecksum ^= next;
                mPacketLength--;
            } else {
                if (mChecksum == next) {
                    Log.v(TAG, "Got a packet of length: " + mNextPacket.length);

                    // Parse through all the sub-payloads of a single packet payload
                    int p = 0;
                    while (true) {
                        if ((p + 1) >= mNextPacket.length) {
                            break;
                        }
                        int l = mNextPacket[p + 1];
                        if (l < 0) {
                            l += 256;
                        }
                        l += 2; // Also copy in the headers
                        if (l == 0) {
                            Log.e(TAG, "Zero length packet");
                            break;
                        }
                        if ((l + p) > mNextPacket.length) {
                            Log.e(TAG, "Packet is too big, at " + p + " len = " + l);
                            break;
                        }
                        byte[] nextPacket = new byte[l];
                        System.arraycopy(mNextPacket, p, nextPacket, 0, l);

                        p += l;
                        onPayloadReceived(nextPacket);
                    }
                } else {
                    Log.e(TAG, "Packet failed checksum with length: "
                            + mNextPacket.length + ' ' + mChecksum + " != " + next);
                }
                mReadState = ReadState.SEARCH_START;
            }
        } else {
            Log.e(TAG, "Invalid read state: " + mReadState);
            mReadState = ReadState.SEARCH_START;
        }
    }

    /**
     * Called when a payload is received.
     * @param payload The specific packet payload.
     */
    private synchronized void onPayloadReceived(byte[] payload) {
        if (payload.length < 1) {
            Log.e(TAG, "Empty packet");
            return;
        }
        Log.v(TAG, "Payload: " + payload[0] + " " + payload.length);
        switch (payload[0]) {
            case (byte) 1:
                Log.v(TAG, "Got basic sensor data, length: " + payload.length);
                onBasicSensors(payload);
                break;
            case (byte) 3:
                Log.v(TAG, "Got docking IR, length: " + payload.length);
                break;
            case (byte) 4:
                Log.v(TAG, "Got inertial sensor, length: " + payload.length);
                break;
            case (byte) 5:
                Log.v(TAG, "Got cliff, length: " + payload.length);
                break;
            case (byte) 6:
                Log.v(TAG, "Got current, length: " + payload.length);
                break;
            case (byte) 10:
                Log.v(TAG, "Got hardware version, length: " + payload.length);
                onHardwareVersion(payload);
                break;
            case (byte) 11:
                Log.v(TAG, "Got firmware version, length: " + payload.length);
                onFirmwareVersion(payload);
                break;
            case (byte) 13:
                Log.v(TAG, "Raw gyro ADC, length: " + payload.length);
                break;
            case (byte) 16:
                Log.v(TAG, "GPIO data, length: " + payload.length);
                break;
            case (byte) 19:
                Log.v(TAG, "Robot UUID, length: " + payload.length);
                onUDIDReceived(payload);
                break;
            case (byte) 21:
                Log.v(TAG, "PID gain values of wheels, length: " + payload.length);
                break;
            default:
                Log.e(TAG, "Packet with invalid frame id: " + payload[0]);
                break;
        }
    }

    /**
     * Called when a UDID packet is received.
     *
     * @param payload The specific packet payload.
     */
    private synchronized void onUDIDReceived(byte[] payload) {
        if (payload.length != 14) {
            Log.e(TAG, "UDID version length invalid: " + payload.length);
            return;
        }
        StringBuilder idBuilder = new StringBuilder("KOBUKI:");
        for (int i = 2; i < payload.length; i++) {
            idBuilder.append(Strings.byteToHexString(payload[i]));
        }
        mUuid = idBuilder.toString();
    }

    /**
     * Sends packets to the base through the serial port.
     */
    private synchronized void sendUpdatePackets() {
        List<byte[]> payloads = new LinkedList<>();

        if ((getRobotUuid() == null) || (getVersionString() == null)) {
            // Request the robot id.
            Log.i(TAG, "Getting robot uuid");
            payloads.add(new byte[]{0x9, 0x0, 0x01 | 0x02 | 0x08, 0x08 | 0x02 | 0x01});
        } else {
            payloads.add(computeSpeedPacket());
            payloads.add(computeGPIOPacket());
        }

        // Todo: if we have a payloads list of more than 255 or so, we need multiple packets
        int totalLength = 4;
        for (final byte[] payload : payloads) {
            totalLength += payload.length;
        }
        byte[] write = new byte[totalLength];
        write[0] = (byte) 0xAA;
        write[1] = (byte) 0x55;
        write[2] = (byte) (totalLength - 4);
        write[totalLength - 1] = write[2];
        int p = 3;
        for (final byte[] payload : payloads) {
            payload[1] = (byte) (payload.length - 2);
            for (final byte payloadByte : payload) {
                write[p] = payloadByte;
                write[totalLength - 1] ^= payloadByte;
                p++;
            }
        }
        writeToUsb(write);
    }

    /**
     * Gets the version string of the system.
     *
     * @return The current version string.
     */
    @Override
    public String getVersionString() {
        String hardware = mHardwareVersion, firmware = mFirmwareVersion;
        if ((hardware != null) && (firmware != null)) {
            return "KOBUKI: HW:" + hardware + " SW:" + firmware;
        }
        return null;
    }

    /**
     * Called when basic sensor data packet is received.
     *
     * @param payload The specific packet payload.
     */
    private synchronized void onBasicSensors(byte[] payload) {
        if (payload.length < 17) {
            Log.e(TAG, "Basic sensors length invalid: " + payload.length);
            return;
        }
        mBaseStatus = new BaseStatus();
        mBaseStatus.setBumper(payload[BUMPER]);

        mBaseStatus.setTimeStamp((short) (payload[TIMESTAMP + 1] << 8 | payload[TIMESTAMP]));
        mBaseStatus.setWheelDrop(payload[WHEEL_DROP]);
        mBaseStatus.setCliff(payload[CLIFF]);
        mBaseStatus.setButton(payload[BUTTON]);
        mBaseStatus.setCharger(payload[CHARGER]);
        mBaseStatus.setBattery(payload[BATTERY]);

        onBumperUpdate(mUuid, getBumperState());

        int batteryValue = (int)payload[BATTERY];
        if (batteryValue < 0) {
            batteryValue += 256;
        }
        double voltage = batteryValue * 0.1;
        double percent = (voltage - MIN_BATTERY_VOLTAGE)
                / (MAX_BATTERY_VOLTAGE - MIN_BATTERY_VOLTAGE);
        setBatteryStatus(0, new BatteryStatus(
                getBatteryStatus(0), payload[CHARGER] != 0, percent, voltage), mUuid);
    }

    /**
     * Gets bumper state.
     *
     * @return bumper state.
     */
    private BumperState getBumperState() {
        switch(mBaseStatus.getBumper()) {
            case KOBUKI_LEFT_BUMPER_ID:
                return BumperState.LEFT;
            case KOBUKI_CENTER_LEFT_BUMPER_ID:
                return BumperState.CENTER_LEFT;
            case KOBUKI_CENTER_RIGHT_BUMPER_ID:
                return BumperState.CENTER_RIGHT;
            case KOBUKI_RIGHT_BUMPER_ID:
                return BumperState.RIGHT;
            case KOBUKI_CENTER_BUMPER_ID:
                return BumperState.CENTER;
            case KOBUKI_NO_BUMPER_ID:
                return BumperState.NONE;
            default:
                return BumperState.CENTER_RIGHT;
        }
    }

    /**
     * Called when a hardware version packet is received.
     *
     * @param payload The specific packet payload.
     */
    private synchronized void onHardwareVersion(byte[] payload) {
        if (payload.length <= 4) {
            Log.e(TAG, "Hardware version length invalid: " + payload.length);
            return;
        }
        String newVersion = versionToString(payload);
        if (!Strings.compare(newVersion, mHardwareVersion)) {
            Log.i(TAG, "New hardware version: " + newVersion);
            mHardwareVersion = newVersion;
            onVersionUpdate();
        }
    }

    /**
     * Called when a firmware version packet is received.
     *
     * @param payload The specific packet payload.
     */
    private synchronized void onFirmwareVersion(byte[] payload) {
        if (payload.length <= 4) {
            Log.e(TAG, "Firmware version length invalid: " + payload.length);
            return;
        }
        String newVersion = versionToString(payload);
        if (!Strings.compare(newVersion, mFirmwareVersion)) {
            Log.i(TAG, "New firmware version: " + newVersion);
            mFirmwareVersion = newVersion;
            onVersionUpdate();
        }
    }

    /**
     * Computes the value of the speed packet.
     * @return The speed packet payload byte array.
     */
    private synchronized byte[] computeSpeedPacket() {
        // Note: a lot of strange math has to be done here, since robotTurn is actually the radius
        // of the circle the base will turn instead of the angular velocity, unlike how a normal
        // person would design a linear + angular velocity controller.
        // From: https://github.com/yujinrobot/kobuki_core/blob/
        //    461a75274c9788a163e8e3be360e3c19030c5606/kobuki_driver/src/driver/diff_drive.cpp#L143

        final double epsilon = 0.0001;

        double radius;
        double speed;

        double linearSpeed = mTeleop == null ? 0.0 : mTeleop.getVx();
        double angularSpeed = mTeleop == null ? 0.0 : mTeleop.getRz();

        // Special Case #1 : Straight Run
        if ((angularSpeed < epsilon) && (angularSpeed > -epsilon)) {
            radius = 0.0;
            speed = 1000.0 * linearSpeed;
        } else {
            final double bias = 0.230;
            speed = linearSpeed * 1000.0;
            radius = (linearSpeed * 1000.0) / angularSpeed;
            // Special Case #2 : Pure Rotation or Radius is less than or equal to 1.0 mm
            if (((linearSpeed < epsilon) && (linearSpeed > -epsilon))
                    || ((radius <= 1.0) && (radius >= -1.0))) {
                speed = (1000.0 * bias * angularSpeed) / (2.0);
                radius = 1.0;
            } else if (radius > 0) {
                speed += (1000.0  * bias * angularSpeed) / (2.0);
            } else if (radius < 0) {
                speed -= (1000.0  * bias * angularSpeed) / (2.0);
            }
        }

        int robotSpeed = (int) speed;
        int robotTurn = (int) radius;

        // Clear speed if we are not the selected robot.
        if (mUuid == null || !mUuid.equals(getSelectedRobotUuid()) || !mUuid.equals(mTeleopUuid)) {
            robotSpeed = 0;
            robotTurn = 0;
        }
        Log.d(TAG, "Robot speed: " + robotSpeed + ", radius: " + radius);

        // Set the speed of the robot to be the m/s control
        return new byte[]{1, 0,
                (byte) (robotSpeed & 0xFF), (byte) ((robotSpeed >> 8) & 0xFF),
                (byte) (robotTurn & 0xFF), (byte) ((robotTurn >> 8) & 0xFF)};
    }

    /**
     * Computes the value of the GPIO packet.
     * @return The GPIO packet payload byte array.
     */
    @SuppressWarnings({"unused", "UnusedAssignment"})
    private synchronized byte[] computeGPIOPacket() {
        final short DIGITAL_OUT_0 = 0x0001;
        final short DIGITAL_OUT_1 = 0x0002;
        final short DIGITAL_OUT_2 = 0x0004;
        final short DIGITAL_OUT_3 = 0x0008;

        final short POWER_3_3V   = 0x0010;
        final short POWER_5V     = 0x0020;
        final short POWER_12V_1A = 0x0040;
        final short POWER_12V_5A = 0x0080;

        final short LED1_RED   = 0x0100;
        final short LED1_GREEN = 0x0200;
        final short LED2_RED   = 0x0400;
        final short LED2_GREEN = 0x0800;

        short value = POWER_3_3V | POWER_5V | POWER_12V_1A | POWER_12V_5A;

        // If we are selected, then set the correct action states
        if (mUuid != null && mUuid.equals(getSelectedRobotUuid())
                && mUuid.equals(mTeleopUuid) && mTeleop != null) {
            if (mTeleop.getAction1()) {
                value |= DIGITAL_OUT_3;
                value |= LED1_GREEN;
            }
            if (mTeleop.getAction2()) {
                value |= DIGITAL_OUT_2;
                value |= LED2_RED;
            }
        }

        return new byte[] { 12, 0, (byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF), };
    }

    /**
     * Converts a version to a string.
     *
     * @param version Version bytes
     * @return Version string (major.minor.patch)
     */
    private static String versionToString(byte[] version) {
        String out = byteToStringAbs(version[4]);
        out += '.' + byteToStringAbs(version[3]);
        out += '.' + byteToStringAbs(version[2]);
        return out;
    }

    private static final int BYTE_MAX = 0xFF;

    /**
     * Converts byte to string, absolute value.
     *
     * @param inputByte Input byte.
     * @return Absolute value string.
     */
    private static String byteToStringAbs(byte inputByte) {
        int value = (int) inputByte;
        value &= KobukiDriver.BYTE_MAX;
        return Integer.toString(value);
    }
}
