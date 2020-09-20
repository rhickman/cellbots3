package ai.cellbots.robot.driver;

import android.content.Context;

import ai.cellbots.common.NetworkUtil;
import ai.cellbots.common.Transform;
import ai.cellbots.common.data.BatteryStatus;
import ai.cellbots.common.data.Teleop;
import ai.cellbots.robot.navigation.NavigationManager;

/**
 * Creates a RobotDriver that is always connected with the phone as the uuid.
 */
public class TestRobotDriver extends RobotDriver {
    private static final String TAG = TestRobotDriver.class.getSimpleName();
    private static final double MIN_BATTERY_PERCENTAGE = 0.3;
    private static final double CRITICAL_BATTERY_PERCENTAGE = 0.25;
    private static final int UPDATE_TIME_MILLISECOND = 100;

    /**
     * Create the robot driver.
     * @param parent     The parent robot driver.
     */
    public TestRobotDriver(Context parent) {
        super(TAG, parent, UPDATE_TIME_MILLISECOND,
                new RobotModel(1, 1, 1, 1, 0.1, 0.2, 0.1, 0.01,
                        new BatteryStatus[]{new BatteryStatus("phone",
                                CRITICAL_BATTERY_PERCENTAGE, MIN_BATTERY_PERCENTAGE)}));
    }

    /**
     * Computes the position of the robot from the phone transform.
     *
     * @param cameraPose The pose of the camera.
     * @return The robot base pose.
     */
    @Override
    public Transform computeRobotBasePosition(Transform cameraPose) {
        return computeRobotPositionFromCameraPose(cameraPose, 0, 0);
    }

    /**
     * Sets the teleop, which is ignored.
     *
     * @param teleop The teleop state of the robot, if any.
     */
    @Override
    public void setTeleop(Teleop teleop) {
        super.setTeleop(teleop);
    }

    /**
     * Gets the robot uuid.
     *
     * @return The robot uuid.
     */
    @Override
    public String getRobotUuid() {
        return "PHONE:" + NetworkUtil.getMacAddress();
    }

    /**
     * Updates the robot system.
     */
    @Override
    protected void onUpdate() {
        setBatteryStatusToPhoneBattery(0, getRobotUuid());
    }

    /**
     * Determines if the robot is connected.
     *
     * @return True if the robot is connected.
     */
    @Override
    public boolean isConnected() {
        return true;
    }

    /**
     * Shuts down the driver.
     */
    @Override
    protected void onShutdown() {
        // Do nothing
    }

    /**
     * Gets the version string of the robot.
     *
     * @return The robot version string.
     */
    @Override
    public String getVersionString() {
        return "UNVERSIONED";
    }
}
