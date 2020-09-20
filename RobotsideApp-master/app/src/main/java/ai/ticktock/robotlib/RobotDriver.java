package ai.cellbots.robotlib;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import ai.cellbots.common.Transform;
import ai.cellbots.common.data.BatteryStatus;
import geometry_msgs.Vector3;

/**
 * Driver to connect to a robot. The driver has several abstract functions that must be filled
 * out for each type of robot in order for it to work. See the documentation below.
 */

public abstract class RobotDriver implements TwistVelocityGenerator {
    private static final String TAG = RobotDriver.class.getSimpleName();
    private boolean mHasRunCorrectConstructor = false;
    private boolean mHasBeenInitialized = false;
    private RobotRunner mRobotRunner = null;
    private RobotRunner.Transmitter mTransmitter = null;
    private boolean mShutdown = false;
    private double mRobotSpeed = 0.0;
    private double mRobotAng = 0.0;
    private long mUpdateCounter = 0;
    private long mLastSpeedSet = 0;
    private long mLastActionSet = 0;
    private Context mParent = null;
    private final RobotModel mModel;
    private String mUuid = null;
    private String mVersionString = null;
    private boolean mAction1 = false;
    private boolean mAction2 = false;
    private String mStateString = null;

    private static final int BATTERY_LOW_TIMER_MAX = 100;
    private int mBatteryLowTimer;
    private static final int BATTERY_CRITICAL_TIMER_MAX = 100;
    private int mBatteryCriticalTimer;
    private final BatteryStatus[] mBatteryStatuses;

    public static final int NO_BUMPER_ID = 0;
    public static final int RIGHT_BUMPER_ID = 1;
    public static final int CENTER_LEFT_BUMPER_ID = 2;
    public static final int CENTER_RIGHT_BUMPER_ID= 3;
    public static final int LEFT_BUMPER_ID = 4;

    private static final double MAX_ALLOWED_LINEAR_VEL = 0.65;

    private double mDeviceHeight = 0;
    private boolean mDeviceHeightValid = false;

    // Bumper constants
    public static final float BUMPER_POSITION_Z = (float)-0.3;
    public static final float BUMPER_RADIO = (float)0.2;
    public static final float BUMPER_CENTER_SIDE_ANGLE = (float)(45.0 * Math.PI/180.0);

    /**
     * Set the state string of the robot.
     * @param stateString The new state string.
     */
    protected synchronized void setStateString(String stateString) {
        mStateString = stateString;
    }

    /**
     * Sets the action1 state.
     * @param action1 The action1 state.
     */
    synchronized void setAction1(boolean action1) {
        mAction1 = action1;
        mLastActionSet = mUpdateCounter;
    }

    /**
     * Gets the action1 state.
     * @return The action1 state.
     */
    protected boolean getAction1() {
        return mAction1;
    }

    /**
     * Sets the action2 state.
     * @param action2 The action2 state.
     */
    synchronized void setAction2(boolean action2) {
        mAction2 = action2;
        mLastActionSet = mUpdateCounter;
    }

    /**
     * Gets the action2 state.
     * @return The action2 state.
     */
    protected boolean getAction2() {
        return mAction2;
    }

    /**
     * The model of robot currently in operation.
     */
    public static class RobotModel {
        // mMaxSpeed is not final because it can be changed
        private double mMaxSpeed;
        private final double mMaxAng;
        private final double mWidth;
        private final double mLength;
        private final double mHeight;
        private final double mDeviceZ;
        private final BatteryStatus[] mBatteryStatuses;

        /**
         * Initialize the robot model.
         *
         * @param maxSpeed The maximum robot linear speed, in meters/second.
         * @param maxAng   The maximum robot angular speed, in radians/second.
         */
        public RobotModel(@SuppressWarnings("SameParameterValue") double maxSpeed,
                          @SuppressWarnings("SameParameterValue") double maxAng,
                          @SuppressWarnings("SameParameterValue") double width,
                          @SuppressWarnings("SameParameterValue") double length,
                          @SuppressWarnings("SameParameterValue") double height,
                          @SuppressWarnings("SameParameterValue") double deviceZ,
                BatteryStatus[] batteryStatuses) {
            mMaxSpeed = maxSpeed;
            mMaxAng = maxAng;
            mWidth = width;
            mLength = length;
            mHeight = height;
            mDeviceZ = deviceZ;
            mBatteryStatuses = batteryStatuses.clone();
        }

        /**
         * Get the maximum linear speed of this robot, which is different for each robot type.
         *
         * @return The maximum robot linear speed, in meters/second.
         */
        double getMaxSpeed() {
            return mMaxSpeed;
        }

        /**
         * Get the maximum angular speed of this robot, which is different for each robot type.
         *
         * @return The maximum robot angular speed, in radians/second.
         */
        double getMaxAngular() {
            return mMaxAng;
        }

        /**
         * Get width of the robot, which is different for each robot type.
         *
         * @return The width in meters.
         */
        double getWidth() {
            return mWidth;
        }

        /**
         * Get length of the robot, which is different for each robot type.
         *
         * @return The length in meters.
         */
        double getLength() {
            return mLength;
        }

        /**
         * Get height of the robot, which is different for each robot type.
         *
         * @return The height in meters.
         */
        double getHeight() {
            return mHeight;
        }

        /**
         * Get the Z height of the sensor on the robot
         *
         * @return The Z height in meters.
         */
        public double getDeviceZ() {
            return mDeviceZ;
        }

        /**
         * Get the battery statuses
         *
         * @return The battery statuses
         */
        public BatteryStatus[] getBatteryStatuses() {
            return mBatteryStatuses.clone();
        }

        /**
         * Sets the maximum linear velocity of the robot
         * @param maxSpeed Integer [0;100] represents a percentage value
         */
        public void changeMaxVelocity(double maxSpeed) {
            mMaxSpeed = maxSpeed;
        }

        public double getMaxAllowedLinearVelocity() {
            return MAX_ALLOWED_LINEAR_VEL;
        }
    }

    /**
     * Set the status of a battery.
     * @param number The number of the battery.
     * @param newStatus The new status.
     */
    protected void setBatteryStatus(int number, BatteryStatus newStatus) {
        mBatteryStatuses[number] = newStatus;
    }

    /**
     * Get the status of a battery.
     * @param number The number of the battery.
     * @return The battery status.
     */
    protected BatteryStatus getBatteryStatus(int number) {
        return mBatteryStatuses[number];
    }

    /**
     * Get the status of a batteries.
     * @return The battery statuses.
     */
    protected BatteryStatus[] getBatteryStatuses() {
        return mBatteryStatuses.clone();
    }

    /**
     * Set a battery status using android.
     * @param number The status number.
     */
    protected void setBatteryStatusToPhoneBattery(int number) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = mParent.registerReceiver(null, filter);
        if (batteryStatus == null) {
            return;
        }
        Log.d(TAG, "Battery status value: " + batteryStatus);
        if (batteryStatus.getExtras() != null) {
            Log.d(TAG, "Battery level: " + batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 0));
            Log.d(TAG, "Battery scale: " + batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 1));
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            Log.d(TAG, "Battery status: " + batteryStatus);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            if (scale == 0) {
                scale++;
            }
            double percentage = ((double)batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)) / ((double)scale);
            if (batteryStatus.hasExtra(BatteryManager.EXTRA_VOLTAGE)) {
                double voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000.0;
                Log.d(TAG, "Battery voltage: " + voltage);
                setBatteryStatus(number,
                        new BatteryStatus(getBatteryStatus(number), isCharging, percentage, voltage));
            } else {
                setBatteryStatus(number,
                        new BatteryStatus(getBatteryStatus(number), isCharging, percentage));
            }
        }
    }

    /**
     * Get the state string.
     * @return The state string.
     */
    public String getStateString() {
        return mStateString;
    }

    /**
     * Get if a battery is low.
     * @return True if we're almost empty
     */
    public boolean getBatteryLow() {
        for (int i = 0; i < mBatteryStatuses.length; i++) {
            BatteryStatus batteryStatus = mBatteryStatuses[i];
            if (batteryStatus.getPercentage() < batteryStatus.getLowPercentage()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get if a battery is critical.
     * @return True if we're almost empty
     */
    public boolean getBatteryCritical() {
        for (int i = 0; i < mBatteryStatuses.length; i++) {
            BatteryStatus batteryStatus = mBatteryStatuses[i];
            if (batteryStatus.getPercentage() < batteryStatus.getCriticalPercentage()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get if a battery is low debounced.
     * @return True if we're almost empty
     */
    public boolean getBatteryLowDebounced() {
        return mBatteryLowTimer >= BATTERY_LOW_TIMER_MAX;
    }

    /**
     * Get if a battery is critical debounced.
     * @return True if we're almost empty
     */
    public boolean getBatteryCriticalDebounced() {
        return mBatteryCriticalTimer >= BATTERY_LOW_TIMER_MAX;
    }


    /**
     * Robot constructor that must be called.
     *
     * @param parent The parent android context, needed so that the system can execute accesses.
     */
    protected RobotDriver(Context parent, RobotModel model) {
        mHasRunCorrectConstructor = true;
        mParent = parent;
        mModel = model;
        mBatteryStatuses = mModel.getBatteryStatuses().clone();
        if (parent == null) {
            throw new Error("Parent cannot be null");
        }
        //noinspection ConstantConditions
        if (model == null) {
            throw new Error("Model cannot be null");
        }
    }

    /**
     * Get the robot runner associated with this driver.
     * @return The robot runner.
     */
    private RobotRunner getRobotRunner() {
        return mRobotRunner;
    }

    /**
     * Get the parent android context, used for system access calls.
     * @return The context.
     */
    protected Context getParent() {
        return mParent;
    }

    /**
     * Internal initialization function, called by RobotRunner only.
     * @param robotRunner The RobotRunner to associate the driver with.
     * @param transmitter The RobotRunner's Transmitter.
     */
    final void initDriver(RobotRunner robotRunner, RobotRunner.Transmitter transmitter) {
        if (!mHasRunCorrectConstructor) {
            throw new Error("RobotDriver did not have correct constructor called.");
        }
        mRobotRunner = robotRunner;
        mTransmitter = transmitter;

        if (mHasBeenInitialized) {
            transmitter.reportError(RobotRunner.Error.robot_driver_invalid);
        }

        mHasBeenInitialized = true;

        onInit();
    }

    /**
     * Report an error to the RobotRunner.
     * @param error The RobotRunner error.
     */
    @SuppressWarnings("unused")
    protected void reportError(RobotRunner.Error error) {
        mTransmitter.reportError(error);
    }

    /**
     * Report an exception to the RobotRunner.
     * @param error The RobotRunner error.
     * @param e The exception.
     */
    protected void reportException(@SuppressWarnings("SameParameterValue") RobotRunner.Error error, Exception e) {
        mTransmitter.reportException(error, e);
    }

    /**
     * Update the state of the RobotRunner monitors. Should be called when the connection state
     * changes.
     */
    protected void updateMonitorsState() {
        mTransmitter.updateMonitorsState();
    }

    /**
     * Called onInit. Should be overwritten to do setup commands.
     */
    protected abstract void onInit();

    /**
     * Called when the controller update occurs (around 10Hz).
     */
    protected abstract void onUpdate();

    /**
     * Called to determine if the robot is connected.
     * @return If true, then the robot is in a state where it should work if commanded.
     */
    public abstract boolean isConnected();

    /**
     * Called when the driver is shutdown.
     */
    protected abstract void onShutdown();

    /**
     * Shutdown the driver, stopping all threads.
     */
    void shutdown() {
        mShutdown = true;
    }

    /**
     * Wait for the driver to fully shutdown.
     */
    void waitShutdown() {
        onShutdown();
        mRobotRunner = null;
        mParent = null;
    }

    /**
     * Update the Driver. This function ensures there is a command waiting for the robot, and
     * zeros the speed and angular velocity if it has been a long time waiting.
     */
    void update() {
        synchronized (this) {
            mUpdateCounter++;
            if (mLastSpeedSet < (mUpdateCounter - 10)) {
                mRobotSpeed = 0.0;
                mRobotAng = 0;
            }
            if (mLastActionSet < (mUpdateCounter - 10)) {
                mAction1 = false;
                mAction2 = false;
            }
        }
        onUpdate();

        if (getBatteryLow()) {
            if (mBatteryLowTimer < BATTERY_LOW_TIMER_MAX) {
                mBatteryLowTimer++;
            }
        } else {
            mBatteryLowTimer = 0;
        }
        if (getBatteryCritical()) {
            if (mBatteryCriticalTimer < BATTERY_CRITICAL_TIMER_MAX) {
                mBatteryCriticalTimer++;
            }
        } else {
            mBatteryCriticalTimer = 0;
        }
    }

    /**
     * Get the UUID of the robot - unique for each model, prefixed with a identified for the
     * robot type - e.g. "KOBUKI:uuid"
     *
     * @return The uuid.
     */
    protected String getRobotUuid() {
        return mUuid;
    }

    /**
     * Get the version string of the robot.
     *
     * @return The robot uuid.
     */
    protected String getVersionString() {
        return mVersionString;
    }

    /**
     * Set the UUID of the robot - unique for each model, prefixed with a identified for the
     * robot type - e.g. "KOBUKI:uuid"
     *
     * @param uuid The new uuid.
     */
    protected void setUuid(String uuid) {
        mUuid = uuid;
    }

    /**
     * Set the version string of the robot.
     *
     * @param versionString The new version string.
     */
    protected void setVersionString(String versionString) {
        mVersionString = versionString;
    }

    /**
     * Compute the transform to the base center from the tango device, which depends on the
     * physical properties of each robot.
     * @param tf The current world (ADF) transform.
     * @return The transform of the robot, in world (ADF) coordinates.
     */
    public abstract Transform tangoToBase(Transform tf);

    /**
     * Gets if the robot should shutdown.
     * @return True if we should shutdown.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected boolean isShutdown() {
        RobotRunner runner = getRobotRunner();
        return mShutdown || (runner == null) || (runner.getState() == RobotRunner.State.SHUTDOWN);
    }

    /**
     * Get the robot model for this specific robot.
     * @return The robot model.
     */
    @SuppressWarnings("WeakerAccess")
    public RobotModel getModel() {
        return mModel;
    }

    /**
     * Get the current robot linear speed setting.
     * @return The robot linear speed setting, in meters/second.
     */
    protected synchronized double getRobotSpeed() {
        return mRobotSpeed;
    }

    /**
     * Get the current robot angular speed setting.
     * @return The robot angular speed setting, in radian/second.
     */
    protected synchronized double getRobotAng() {
        return mRobotAng;
    }

    /**
     * Get bumper status
     *
     * @return bumper status
     */
    public abstract int getBumper();

    /**
     * Set the motion of the robot. If this function stops being called for 10 update cycles, the
     * robot will be stopped by the driver automatically.
     * @param speed The robot linear speed setting, in meters/second.
     * @param ang The robot linear speed setting, in meters/second.
     */
    @SuppressWarnings("unused")
    void setMotion(double speed, double ang) {
        if (speed > getModel().getMaxSpeed()) {
            speed = getModel().getMaxSpeed();
        }
        if (speed < -getModel().getMaxSpeed()) {
            speed = -getModel().getMaxSpeed();
        }

        if (ang > getModel().getMaxAngular()) {
            ang = getModel().getMaxAngular();
        }
        if (ang < -getModel().getMaxAngular()) {
            ang = -getModel().getMaxAngular();
        }

        synchronized (this) {
            mRobotSpeed = speed;
            mRobotAng = ang;
            mLastSpeedSet = mUpdateCounter;
        }
    }

    /**
     * Sets the angular velocity for ROS messages
     * @param angular angular velocity
     */
    @Override
    public synchronized void fillAngular(Vector3 angular) {
        angular.setZ(mRobotAng);
    }

    /**
     * Sets the linear velocity for ROS messages
     * @param linear linear velocity
     */
    @Override
    public synchronized void fillLinear(Vector3 linear) {
        linear.setX(mRobotSpeed);
    }

    /**
     * This computes the location of the robot using its current position. Currently, we assume
     * that the phone is placed in the rotating mount on the front of the robot. Since the mount
     * is unstable, the phone rotates up and down, and thus we have to compute the position of
     * the robot by stripping out all rotation except Z axis rotation. We do this by computing
     * the position of the transform as we normally would, finding the vector in the XY-plane
     * (stripping the Z component) and then placing the base_link at that point.
     * @param tf The tango TF offset.
     * @param phoneOffsetF The forwards offset of the phone from the center of the robot.
     * @param phoneOffsetZ The phone offset height from the ground.
     */
    protected Transform stablePhoneTransform(Transform tf, double phoneOffsetF, double phoneOffsetZ) {
        Transform offset = new Transform(tf, new Transform(0.0, 0.0, 1.0, 0.0));
        double[] v = offset.getPosition();
        double len = 0.0;
        v[2] = 0;
        for (int i = 0; i < 2; i++) {
            v[i] -= tf.getPosition(i);
            len += v[i] * v[i];
        }
        double angle = Math.atan2(v[1], v[0]) + Math.PI;
        len = Math.sqrt(len);
        // If something weird happens (e.g. pointing directly up), avoid a divide by zero.
        if (len < 0.00000001) {
            len = 1.0;
        }
        for (int i = 0; i < 3; i++) {
            v[i] = tf.getPosition(i) + ((v[i] * phoneOffsetF) / len);
        }

        if (mDeviceHeightValid) {
            v[2] -= mDeviceHeight;
        } else {
            v[2] -= phoneOffsetZ;
        }

        return new Transform(v, new double[]{0, 0, Math.sin(angle / 2), Math.cos(angle / 2)},
                tf.getTimestamp());
    }

    /**
     * Sets the device height.
     * @param height The device height.
     * @param deviceHeightValid True if the device height is valid.
     */
    void setTangoDeviceHeight(double height, boolean deviceHeightValid) {
        mDeviceHeight = height;
        mDeviceHeightValid = deviceHeightValid;
    }

}
