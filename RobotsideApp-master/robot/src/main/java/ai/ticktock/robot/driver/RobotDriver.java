package ai.cellbots.robot.driver;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import java.util.Collection;

import ai.cellbots.common.EventProcessor;
import ai.cellbots.common.MathUtil;
import ai.cellbots.common.Strings;
import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.TimedLoop;
import ai.cellbots.common.Transform;
import ai.cellbots.common.data.BatteryStatus;
import ai.cellbots.common.data.Teleop;
import ai.cellbots.robot.costmap.GeometryCostMap;
import ai.cellbots.robot.manager.SoundManager;

/**
 * An abstract implementation of a robot driver.
 */
public abstract class RobotDriver implements ThreadedShutdown {
    private static final String TAG = RobotDriver.class.getSimpleName();
    private final Context mParent;
    private final TimedLoop mTimedLoop;
    private final RobotModel mRobotModel;
    private final EventProcessor mBumperEventProcessor;
    private final EventProcessor mBatteryEventProcessor;
    private final BatteryStatus[] mBatteryStatuses;
    private final String[] mBatteryStatusUuids;
    private BumperCostMap mBumperCostMap;
    private String mSelectedRobotUuid;
    private Listener mListener;
    private String mBumperUuid;
    private BumperState mBumperState;

    // Sound manager for playing sound.
    private SoundManager mSoundManager = null;
    // Sound previously played.
    private SoundManager.Sound mSound = null;

    private boolean mHasRunCorrectConstructor = false;

    /**
     * Bumper state of the robot.
     */
    public enum BumperState {
        NONE, RIGHT, CENTER_RIGHT, CENTER, CENTER_LEFT, LEFT
    }

    /**
     * Robot state listener.
     */
    public interface Listener {
        /**
         * Called on an update to bumper state.
         *
         * @param uuid        The robot uuid updated.
         * @param bumperState The new bumper state.
         */
        void onBumperUpdate(String uuid, BumperState bumperState);

        /**
         * Called on battery state update.
         *
         * @param uuid            The uuid of the battery states.
         * @param batteryStatuses The battery states.
         * @param low             True if the battery state is low.
         * @param critical        True if the battery state is critical.
         */
        void onBatteryUpdate(String uuid, BatteryStatus[] batteryStatuses, boolean low,
                boolean critical);

        /**
         * Called when the robot's version is updated.
         *
         * @param uuid    The robot's version.
         * @param version The new robot version.
         */
        void onVersionUpdate(String uuid, String version);
    }

    /**
     * On the update of the version string.
     */
    final protected synchronized void onVersionUpdate() {
        Listener listener = mListener;
        if (listener != null) {
            listener.onVersionUpdate(getRobotUuid(), getVersionString());
        }
    }

    /**
     * Set the listener.
     *
     * @param listener The new listener to set.
     */
    final public void setListener(Listener listener) {
        mListener = listener;
        onVersionUpdate();
    }

    /**
     * On a bumper state update.
     *
     * @param uuid        The robot uuid updated.
     * @param bumperState The new bumper state.
     */
    protected synchronized void onBumperUpdate(String uuid, BumperState bumperState) {
        if (mBumperState != bumperState || !Strings.compare(uuid, mBumperUuid)) {
            Log.i(TAG, "Bumper state updated to " + bumperState);
            mBumperUuid = uuid;
            mBumperState = bumperState;
            mBumperEventProcessor.onEvent();
        }
    }

    /**
     * Set a battery status using android.
     *
     * @param number The status number.
     * @param uuid   The uuid of the robot.
     */
    protected void setBatteryStatusToPhoneBattery(int number, String uuid) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = mParent.registerReceiver(null, filter);
        if (batteryStatus == null) {
            return;
        }
        if (batteryStatus.getExtras() != null) {
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            if (scale == 0) {
                scale++;
            }
            double percentage = ((double) batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 0))
                    / ((double) scale);
            if (batteryStatus.hasExtra(BatteryManager.EXTRA_VOLTAGE)) {
                double voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                        / 1000.0;
                setBatteryStatus(number,
                        new BatteryStatus(getBatteryStatus(number), isCharging, percentage,
                                voltage), uuid);
            } else {
                setBatteryStatus(number,
                        new BatteryStatus(getBatteryStatus(number), isCharging, percentage), uuid);
            }
        }
    }

    /**
     * Set the status of a battery.
     *
     * @param number    The number of the battery.
     * @param newStatus The new status.
     * @param uuid      The uuid of the robot.
     */
    protected synchronized void setBatteryStatus(int number, BatteryStatus newStatus, String uuid) {
        mBatteryStatuses[number] = newStatus;
        mBatteryStatusUuids[number] = uuid;
        mBatteryEventProcessor.onEvent();
    }

    /**
     * Get the status of a battery.
     *
     * @param number The number of the battery.
     * @return The battery status.
     */
    protected BatteryStatus getBatteryStatus(int number) {
        return mBatteryStatuses[number];
    }

    /**
     * Clear the battery and bumper statuses
     */
    protected void clearStatuses() {
        mBumperUuid = null;
        for (int i = 0; i < mBatteryStatusUuids.length; i++) {
            mBatteryStatusUuids[i] = null;
        }
    }

    /**
     * Create the robot driver.
     *
     * @param name       The name of the driver.
     * @param parent     The parent robot driver.
     * @param updateTime The milliseconds between updates.
     * @param model      The robot model.
     */
    protected RobotDriver(String name, Context parent, long updateTime, RobotModel model) {
        if (parent == null) {
            throw new Error("Parent cannot be null");
        }
        if (model == null) {
            throw new Error("Model cannot be null");
        }

        mParent = parent;
        mRobotModel = model;
        mHasRunCorrectConstructor = true;

        mBatteryStatuses = model.getBatteryStatuses().clone();
        mBatteryStatusUuids = new String[mBatteryStatuses.length];

        mBumperEventProcessor = new EventProcessor(name + "Bumper",
                new EventProcessor.Processor() {
                    @Override
                    public boolean update() {
                        Listener listener;
                        String uuid;
                        BumperState bumperState;
                        synchronized (RobotDriver.this) {
                            listener = mListener;
                            uuid = mBumperUuid;
                            bumperState = mBumperState;
                        }
                        if (listener != null) {
                            listener.onBumperUpdate(uuid, bumperState);
                        }
                        return true;
                    }

                    @Override
                    public void shutdown() {
                    }
                });

        mBatteryEventProcessor = new EventProcessor(name + "Battery",
                new EventProcessor.Processor() {
                    @Override
                    public boolean update() {
                        Listener listener;
                        String[] uuids;
                        BatteryStatus[] batteryStatuses;
                        synchronized (RobotDriver.this) {
                            listener = mListener;
                            uuids = mBatteryStatusUuids.clone();
                            batteryStatuses = mBatteryStatuses.clone();
                        }
                        if (uuids.length == 0 || listener == null) {
                            return true;
                        }
                        String uuid = uuids[0];
                        for (String cmp : uuids) {
                            if (!Strings.compare(cmp, uuid)) {
                                return true;
                            }
                        }
                        boolean low = false, critical = false;
                        for (BatteryStatus batteryStatus : batteryStatuses) {
                            low |= batteryStatus.getPercentage() < batteryStatus.getLowPercentage();
                            critical |=
                                    batteryStatus.getPercentage()
                                            < batteryStatus.getCriticalPercentage();
                        }
                        listener.onBatteryUpdate(uuid, batteryStatuses, low, critical);
                        return true;
                    }

                    @Override
                    public void shutdown() {
                    }
                });

        mTimedLoop = new TimedLoop(name, new TimedLoop.Looped() {
            @Override
            public boolean update() {
                onUpdate();
                return true;
            }

            @Override
            public void shutdown() {
                onShutdown();
            }
        }, updateTime);
    }

    /**
     * Sets the selected robot uuid. Data such as bumper updates and robot uuid update should
     * only be exchanged with a robot that has this uuid. If no selected robot uuid is set, then
     * no data should be sent except getRobotUuid() should return a robot if connected.
     *
     * @param selectedRobotUuid The selected robot uuid.
     */
    final public synchronized void setSelectedRobotUuid(String selectedRobotUuid) {
        mSelectedRobotUuid = selectedRobotUuid;
    }

    /**
     * Gets the selected robot uuid. Data such as bumper updates and robot uuid update should
     * only be exchanged with a robot that has this uuid. If no selected robot uuid is set, then
     * no data should be sent except getRobotUuid() should return a robot if connected.
     *
     * @return The selected robot uuid.
     */
    final protected synchronized String getSelectedRobotUuid() {
        return mSelectedRobotUuid;
    }

    /**
     * Set the bumper CostMap. Called only from RobotManager.
     */
    final public synchronized void setBumperCostMap(BumperCostMap costMap) {
        mBumperCostMap = costMap;
    }

    /**
     * Sends a new set of geometry events to the bumper CostMap.
     *
     * @param robotUuid The uuid of the robot generating the update.
     * @param list      The list of geometry to send through.
     */
    final protected synchronized void updateBumper(String robotUuid,
                Collection<GeometryCostMap.Geometry> list) {
        BumperCostMap bc = mBumperCostMap;
        if (bc != null && getSelectedRobotUuid() != null && getSelectedRobotUuid().equals(
                robotUuid)) {
            bc.addBumperGeometry(list);
        }
    }

    /**
     * Sets the sound manager for playing sound.
     *
     * @param soundManager  The sound manager
     */
    public final synchronized void setSoundManager(SoundManager soundManager) {
        mSoundManager = soundManager;
    }

    /**
     * Computes the robot base position from the phone position.
     *
     * @param deviceTransform The device transform.
     * @return The device transform.
     */
    abstract public Transform computeRobotBasePosition(Transform deviceTransform);

    /**
     * Shuts down the driver.
     */
    @Override
    final public void shutdown() {
        BumperCostMap bc = mBumperCostMap;
        if (bc != null) {
            bc.shutdown();
        }
        mBumperEventProcessor.shutdown();
        mBatteryEventProcessor.shutdown();
        mTimedLoop.shutdown();
    }

    /**
     * Waits for the driver to shutdown.
     */
    @Override
    final public void waitShutdown() {
        shutdown();
        BumperCostMap bc = mBumperCostMap;
        if (bc != null) {
            bc.waitShutdown();
        }
        mBumperEventProcessor.waitShutdown();
        mBatteryEventProcessor.waitShutdown();
        mTimedLoop.waitShutdown();
    }

    /**
     * Updates the driver with a new teleop state. If sound manager is available and teleop is not
     * trivial, play a teleop sound. Must be synchronized.
     *
     * @param teleop The teleop state of the robot, if any.
     */
    public void setTeleop(Teleop teleop) {
        if (mSoundManager != null && teleop != null && teleop.getVx() != 0 && teleop.getRz() != 0) {
            if (mSound == null || !mSound.isPlaying()) {
                mSound = mSoundManager.playSound(
                        "STATIC_" + SoundManager.SoundLevel.ANIMATION + "_" + "TELEOP",
                        SoundManager.SoundLevel.AUTOGEN);
            }
        }
    }

    /**
     * Sends a Teleop command with all-zero values to stop the robot base.
     */
    public void stopRobot() {
        setTeleop(new Teleop(0, 0, 0, 0, 0, 0));
    }

    /**
     * Gets the robot uuid of the robot.
     *
     * @return The robot uuid, or null if the robot is not connected.
     */
    public abstract String getRobotUuid();

    /**
     * Called to update the driver.
     */
    protected abstract void onUpdate();

    /**
     * Called to determine if the robot is connected.
     *
     * @return If true, then the robot is in a state where it should work if commanded.
     */
    public abstract boolean isConnected();

    /**
     * Called when the driver is shutdown.
     */
    protected abstract void onShutdown();

    /**
     * Gets the parent context.
     *
     * @return Parent the parent context.
     */
    final protected Context getParent() {
        return mParent;
    }

    /**
     * Gets the robot model.
     *
     * @return The robot model.
     */
    final public RobotModel getRobotModel() {
        return mRobotModel;
    }

    /**
     * Get the version string of the robot.
     *
     * @return The robot version string.
     */
    abstract public String getVersionString();

    /**
     * Internal initialization function, called by RobotRunner only.
     */
    public final void initDriver() {
        if (!mHasRunCorrectConstructor) {
            throw new Error("RobotDriver did not have correct constructor called.");
        }
    }

    /**
     * Computes the location of the robot using its current position. Currently, we assume
     * that the phone is placed in the rotating mount on the front of the robot. Since the mount
     * is unstable, the phone rotates up and down, and thus we have to compute the position of
     * the robot by stripping out all rotation except Z axis rotation. We do this by computing
     * the position of the transform as we normally would, finding the vector in the XY-plane
     * (stripping the Z component) and then placing the base_link at that point.
     *
     * @param cameraPose   The pose of the camera
     * @param offsetForward The forwards offset of the phone from the center of the robot.
     * @param offsetZ The phone offset height from the ground.
     * @return The pose of the robot base.
     */
    public static Transform computeRobotPositionFromCameraPose(
            Transform cameraPose, double offsetForward, double offsetZ) {
        if (cameraPose == null) {
            Log.w(TAG, "Camera pose is null. Cannot compute robot's position.");
            return new Transform(0, 0, 0, 1);
        }
        Transform offset = new Transform(cameraPose, new Transform(0.0, 0.0, 1.0, 0.0));
        double[] position = offset.getPosition();
        double offsetLengthSquared = 0.0;
        position[2] = 0;
        for (int i = 0; i < 2; i++) {
            position[i] -= cameraPose.getPosition(i);
            offsetLengthSquared += position[i] * position[i];
        }
        double angle = Math.atan2(position[1], position[0]) + Math.PI;
        // If something weird happens (e.g. pointing directly up), avoid a divide by zero.
        if (offsetLengthSquared < 0.00000001) {
            offsetLengthSquared = 1.0;
        }
        for (int i = 0; i < 3; i++) {
            position[i] = cameraPose.getPosition(i) +
                    (position[i] * offsetForward) *
                            MathUtil.ComputeFastInverseSqrt(offsetLengthSquared);
        }
        position[2] -= offsetZ;

        return new Transform(position, new double[]{0, 0, Math.sin(angle / 2), Math.cos(angle / 2)},
                cameraPose.getTimestamp());
    }
}
