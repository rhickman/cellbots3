package ai.cellbots.neato;

import android.content.Context;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import ai.cellbots.common.Transform;
import ai.cellbots.common.data.BatteryStatus;
import ai.cellbots.robotlib.RobotDriver;
import ai.cellbots.robotlib.UsbRobotDriver;

/**
 * The driver for the Neato system.
 */
public class NeatoDriver extends UsbRobotDriver {
    private static final String TAG = NeatoDriver.class.getSimpleName();

    private static final double MIN_BATTERY_PERCENTAGE = 0.3;
    private static final double CRITICAL_BATTERY_PERCENTAGE = 0.25;

    private static final double MAX_VELOCITY = 0.35;
    private static final double MAX_ANGULAR_VELOCITY = 1.0;
    private static final double BODY_WIDTH = 0.3302;
    private static final double BODY_LENGTH = 0.3175;
    private static final double BODY_HEIGHT = 0.420;
    private static final double DEVICE_Z = 0.1;

    // A list of acceptable vendor ID, device ID values.
    private static final int[][] DEVICE_IDS = {
            {0x2108, 0x780c},
    };

    private static final String SEP_STRING = new String(new byte[]{26});

    private int mBumper = RobotDriver.NO_BUMPER_ID;

    private int mReadUuidTimes = 0;

    // If we can't read the UUID this many times, then we terminate and re-connect
    private static final int MAX_READ_UUID_TIMES = 30;

    /**
     * Create the driver.
     *
     * @param parent Android context parent.
     */
    public NeatoDriver(Context parent) {
        super(parent, new RobotModel(MAX_VELOCITY, MAX_ANGULAR_VELOCITY, BODY_WIDTH, BODY_LENGTH,
                BODY_HEIGHT, DEVICE_Z, new BatteryStatus[] {
                new BatteryStatus("neato", CRITICAL_BATTERY_PERCENTAGE, MIN_BATTERY_PERCENTAGE),
                new BatteryStatus("phone", CRITICAL_BATTERY_PERCENTAGE, MIN_BATTERY_PERCENTAGE),
        }), DEVICE_IDS, false);
    }

    /**
     * On the initialization.
     */
    @Override
    protected void onInit() {
    }

    /**
     * Called when a USB session is terminated.
     */
    public synchronized void onTerminateSession() {
        mBumper = RobotDriver.NO_BUMPER_ID;
        mReadUuidTimes = 0;
        setUuid(null);
        setVersionString(null);
    }

    /**
     * Update the system always, before USB logic is handled.
     */
    protected void onUpdateBeforeUsb() {
        setBatteryStatusToPhoneBattery(1);
    }

    /**
     * On update system when the USB is connected.
     */
    @Override
    protected void onUpdateAfterUsb() {
        boolean testModeIssue = false;
        while (getDataBuffer().contains(SEP_STRING)) {
            int split = getDataBuffer().indexOf(SEP_STRING);
            String packet = getDataBuffer().substring(0, split);
            setDataBuffer(getDataBuffer().substring(split + 1));
            String[] packetData = packet.trim().split("\n");
            if (packet.contains("TestMode must be on to use this command.")) {
                testModeIssue = true;
            } else if (packetData.length == 0) {
                // Ignore empty packets
                Log.i(TAG, "Empty packet ignored");
            } else if (packetData[0].trim().equals("GetVersion")) {
                handleGetVersion(packetData);
            } else if (packetData[0].trim().startsWith("SetMotor ")) {
                handleSetMotor(packetData);
            } else if (packetData[0].trim().startsWith("GetDigitalSensors")) {
                handleDigitalSensor(packetData);
            } else if (packetData[0].trim().startsWith("GetCharger")) {
                handleGetCharger(packetData);
            } else {
                Log.w(TAG, "Get invalid packet type: '" + packetData[0].trim() + "'");
            }
        }

        if (getRobotUuid() == null || getVersionString() == null) {
            writeToUsb("GetVersion\n");
            if (mReadUuidTimes > MAX_READ_UUID_TIMES) {
                terminateSession();
            }
            mReadUuidTimes++;
        } else if (testModeIssue) {
            writeToUsb("TestMode On\n");
        } else {
            double leftSpeed = (-getRobotAng() * WHEELBASE / 2.0 + getRobotSpeed()) * 1000.0;
            double rightSpeed = (getRobotAng() * WHEELBASE / 2.0 + getRobotSpeed()) * 1000.0;
            double maxSpeed = Math.max(Math.abs(leftSpeed), Math.abs(rightSpeed));
            // This happens if we get told to turn while going at high velocity, the
            // extra speed from the additional angular term pushes us over top speed.
            // If this happens, the neato firmware will stop the robot.
            maxSpeed = Math.min(maxSpeed, MAX_VELOCITY * 1000.0);

            StringBuilder out = new StringBuilder("GetDigitalSensors\nGetCharger\n");
            out.append("SetMotor ");
            if (Math.abs(leftSpeed) >= MIN_WHEEL_SPEED) {
                out.append("LWheelEnable ");
            } else {
                out.append("LWheelDisable ");
            }
            if (Math.abs(rightSpeed) >= MIN_WHEEL_SPEED) {
                out.append("RWheelEnable\n");
            } else {
                out.append("RWheelDisable\n");
            }
            out.append("SetMotor LWheelDist ").append(leftSpeed)
                    .append(" RWheelDist ").append(rightSpeed)
                    .append(" Speed ").append(maxSpeed).append("\n");
            if (getAction1()) {
                out.append("SetMotor VacuumOn VacuumSpeed ")
                        .append(MAX_VACUUM_PERCENT).append("\n");
            } else {
                out.append("SetMotor VacuumOff\n");
            }
            if (getAction2()) {
                out.append("SetMotor BrushEnable\nSetMotor Brush RPM ")
                        .append(BRUSH_RPM).append("\n");
            } else {
                out.append("SetMotor BrushDisable\n");
            }
            writeToUsb(out.toString());
        }
    }

    private static final double MAX_VACUUM_PERCENT = 100.0;

    private static final double BRUSH_RPM = 1200.0;

    private static final double WHEELBASE = 0.244475; // Meters

    private static final double MIN_WHEEL_SPEED = 1.0; // Millimeters/second

    // Elements of the robot's version string extracted from GetVersion command.
    private static final String[][] VERSION_ELEMENTS = {
            {"Bootloader Version", "Bootloader:"},
            {"Locale", " Locale:"},
            {"Software", " Software:"},
            {"BaseID", " Base:"},
            /*{"BatteryType", " Bat:"},
            {"BlowerType", " Blower:"},
            {"BrushMotorType", " Brush:"},
            {"BrushSpeed", ":"},
            {"BrushSpeedEco", ":"},
            {"ChassisRev", " Chassis:"},
            {"DropSensorType", " DropSensor:"},
            {"LCD Panel", " LCD:"},
            {"LDS CPU", " LDS:"},
            {"LDS Software", ":"},
            {"LDSMotorType", ":"},
            {"MainBoard Serial Number", " Main:"},
            {"MainBoard Version", ":"},
            {"Model", ":"},
            {"SideBrushPower", " SideBrush:"},
            {"SideBrushType", ":"},
            {"UI Board Hardware", " UI:"},
            {"UI Board Software", ":"},
            {"UI Name", ":"},
            {"UI Version", ":"},
            {"VacuumPwr", " Vacuum:"},
            {"VacuumPwrEco", ":"},
            {"WallSensorType", " WallSensor:"},
            {"WheelPodType", " WheelPod:"},*/
    };

    /**
     * Handle a GetVersion response packet. Extracts UUID and version string.
     * @param packetData The data of the packet.
     */
    private synchronized void handleGetVersion(String[] packetData) {
        HashSet<String> versionElements = new HashSet<>();
        for (String[] s : VERSION_ELEMENTS) {
            versionElements.add(s[0]);
        }

        StringBuilder setSerial = null;
        HashMap<String, String> versionStore = new HashMap<>();
        for (String line : packetData) {
            String[] split = line.trim().split(",");
            if (split.length == 0) {
                continue;
            }
            if (split[0].equals("Serial Number") && split.length > 1) {
                setSerial = new StringBuilder(split[1]);
                for (int i = 2; i < split.length; i++) {
                    setSerial.append(",").append(split[i]);
                }
            } else if (versionElements.contains(split[0])) {
                Log.i(TAG, "Save: '" + split[0] + "'");
                StringBuilder verElement = new StringBuilder(split.length > 1 ? split[1] : "");
                for (int i = 2; i < split.length; i++) {
                    verElement.append(",").append(split[i]);
                }
                versionStore.put(split[0], verElement.toString().trim());
            }
        }

        if (setSerial == null) {
            Log.w(TAG, "Could not extract serial number from robot");
            return;
        }

        StringBuilder setVer = new StringBuilder();
        for (String[] s : VERSION_ELEMENTS) {
            if (!versionStore.containsKey(s[0])) {
                Log.w(TAG, "Could not extract version element '" + s[0] + "' from robot");
                return;
            }
            setVer.append(s[1]).append(versionStore.get(s[0]));
        }
        setUuid("NEATO:" + setSerial);
        setVersionString(setVer.toString());
    }

    /**
     * Handle a SetMotor response packet. Sets the version string and uuid.
     * @param packetData The data of the packet.
     */
    private synchronized void handleSetMotor(String[] packetData) {
        Set<String> acceptableResponses = new HashSet<>();
        acceptableResponses.add("Stop Vacuum Motor");
        acceptableResponses.add("No recognizable parameters");
        acceptableResponses.add("Enable/Disable Commands used in this command Ignoring all other");
        acceptableResponses.add("SetMotor LWheelDist  RWheelDist  Speed");
        acceptableResponses.add("SetMotor VacuumOff");
        acceptableResponses.add("SetMotor BrushDisable");
        acceptableResponses.add("SetMotor LWheelDisable RWheelDisable");
        acceptableResponses.add("SetMotor LWheelEnable RWheelDisable");
        acceptableResponses.add("SetMotor LWheelDisable RWheelEnable");
        acceptableResponses.add("SetMotor LWheelEnable RWheelEnable");
        acceptableResponses.add("Run Vacuum Motor @ %");
        acceptableResponses.add("Run Brush Motor @  RPM");
        acceptableResponses.add("SetMotor VacuumOn VacuumSpeed");
        acceptableResponses.add("SetMotor BrushEnable");
        acceptableResponses.add("SetMotor Brush RPM");

        for (String line : packetData) {
            String clean = line.trim().replaceAll("\\.", "").replaceAll("-", "");
            for (int i = 0; i < 10; i++) {
                clean = clean.replaceAll("" + i, "");
            }
            clean = clean.trim();
            if (!acceptableResponses.contains(clean)) {
                Log.w(TAG, "Set Motor Error: '" + clean + "'");
            }
        }
    }

    /**
     * Handle a DigitalSensor response, which sets the bumper state.
     * @param packetData The data of the packet.
     */
    private synchronized void handleDigitalSensor(String[] packetData) {
        HashMap<String, Boolean> states = new HashMap<>();
        for (String ll : packetData) {
            String lc = ll.trim();
            String[] ls = lc.split(",");
            if (ls.length >= 2) {
                states.put(ls[0].trim(), ls[1].trim().equals("1"));
            }
        }
        String[] required = {"LSIDEBIT", "RSIDEBIT", "LFRONTBIT", "RFRONTBIT"};
        for (String req : required) {
            if (!states.containsKey(req)) {
                Log.w(TAG, "Missing required sensor: " + req);
                return;
            }
        }
        mBumper = RobotDriver.NO_BUMPER_ID;
        if (states.get("LSIDEBIT")) {
            mBumper = RobotDriver.LEFT_BUMPER_ID;
        }
        if (states.get("RSIDEBIT")) {
            mBumper = RobotDriver.RIGHT_BUMPER_ID;
        }
        if (states.get("RFRONTBIT")) {
            mBumper = RobotDriver.CENTER_RIGHT_BUMPER_ID;
        }
        if (states.get("LFRONTBIT")) {
            mBumper = RobotDriver.CENTER_LEFT_BUMPER_ID;
        }
    }

    /**
     * Handle a GetCharger packet, which updates battery status.
     * @param packetData The GetCharger packet.
     */
    private synchronized void handleGetCharger(String[] packetData) {
        boolean charging = false;
        double percent = 0.0;

        for (String l : packetData) {
            String[] lc = l.trim().split(",");
            if (lc.length >= 2) {
                if (lc[0].equals("FuelPercent")) {
                    percent = Double.valueOf(lc[1].trim()) / 100.0;
                }
                if (lc[0].equals("ChargingEnabled")) {
                    charging = lc[1].trim().equals("1");
                }
            }
        }

        setBatteryStatus(0, new BatteryStatus(getBatteryStatus(0), charging, percent));
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
        final double PHONE_OFFSET_F = -0.05;
        final double PHONE_OFFSET_Z = getModel().getDeviceZ();
        return stablePhoneTransform(tf, PHONE_OFFSET_F, PHONE_OFFSET_Z);
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
