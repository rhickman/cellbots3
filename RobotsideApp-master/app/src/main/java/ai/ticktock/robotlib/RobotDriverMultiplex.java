package ai.cellbots.robotlib;


import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ai.cellbots.common.Transform;
import ai.cellbots.common.data.BatteryStatus;
import geometry_msgs.Vector3;

/**
 * Multiplexes robot drivers, so that the system can switch robot types automatically.
 */
public class RobotDriverMultiplex implements TwistVelocityGenerator {
    private final List<RobotDriver> mDrivers; // The list of drivers
    private RobotDriver mDriver = null; // The current driver

    /**
     * Create a Driver multiplex.
     * @param drivers A list of drivers to multiplex.
     */
    public RobotDriverMultiplex(@NonNull List<RobotDriver> drivers) {
        if (drivers.isEmpty()) {
            throw new IllegalArgumentException("Drivers must be non-empty");
        }
        mDrivers = Collections.unmodifiableList(new ArrayList<>(drivers));
    }

    /**
     * Internal initialization function, called by RobotRunner only.
     * @param robotRunner The RobotRunner to associate the driver with.
     * @param transmitter The RobotRunner's Transmitter.
     */
    void initDriver(RobotRunner robotRunner, RobotRunner.Transmitter transmitter) {
        for (RobotDriver driver : mDrivers) {
            driver.initDriver(robotRunner, transmitter);
        }
    }

    /**
     * Set the motion of the robot. If this function stops being called for 10 update cycles, the
     * robot will be stopped by the driver automatically.
     * @param speed The robot linear speed setting, in meters/second.
     * @param ang The robot linear speed setting, in meters/second.
     */
    void setMotion(double speed, double ang) {
        for (RobotDriver driver : mDrivers) {
            driver.setMotion(speed, ang);
        }
    }


    /**
     * Get the state string.
     * @return The state string.
     */
    public String getStateString() {
        RobotDriver driver = mDriver;
        if (driver != null) {
            return driver.getStateString();
        }
        return null;
    }

    /**
     * Sets the action1 state.
     * @param action1 The action1 state.
     */
    void setAction1(boolean action1) {
        for (RobotDriver driver : mDrivers) {
            driver.setAction1(action1);
        }
    }

    /**
     * Sets the action2 state.
     * @param action2 The action2 state.
     */
    void setAction2(boolean action2) {
        for (RobotDriver driver : mDrivers) {
            driver.setAction2(action2);
        }
    }

    /**
     * Sets the device height.
     * @param height The device height.
     * @param deviceHeightValid True if the device height is valid.
     */
    void setTangoDeviceHeight(double height, boolean deviceHeightValid) {
        for (RobotDriver driver : mDrivers) {
            driver.setTangoDeviceHeight(height, deviceHeightValid);
        }
    }

    /**
     * Update the Driver. This function ensures there is a command waiting for the robot, and
     * zeros the speed and angular velocity if it has been a long time waiting.
     */
    void update() {
        // Store the highest priority connected driver
        RobotDriver firstConn = null;
        for (RobotDriver driver : mDrivers) {
            driver.update();
            if (driver.isConnected() && firstConn == null) {
                firstConn = driver;
            }
        }
        // If the we are connected, then start the driver
        if (firstConn != null) {
            mDriver = firstConn;
        }
    }

    /**
     * Shutdown the driver, stopping all threads.
     */
    void shutdown() {
        for (RobotDriver driver : mDrivers) {
            driver.shutdown();
        }
    }

    /**
     * Wait for the driver to fully shutdown.
     */
    void waitShutdown() {
        for (RobotDriver driver : mDrivers) {
            driver.waitShutdown();
        }
    }

    /**
     * Gets the action1 state.
     * @return The action1 state.
     */
    boolean getAction1() {
        RobotDriver driver = mDriver;
        return driver != null && driver.getAction1();
    }

    /**
     * Gets the action2 state.
     * @return The action2 state.
     */
    boolean getAction2() {
        RobotDriver driver = mDriver;
        return driver != null && driver.getAction2();
    }

    /**
     * Get the UUID of the robot - unique for each model, prefixed with a identified for the
     * robot type - e.g. "KOBUKI:uuid"
     *
     * @return The uuid.
     */
    String getRobotUuid() {
        RobotDriver driver = mDriver;
        if (driver != null) {
            return driver.getRobotUuid();
        }
        return null;
    }

    /**
     * Get the version string of the robot.
     *
     * @return The robot uuid.
     */
    String getVersionString() {
        RobotDriver driver = mDriver;
        if (driver != null) {
            return driver.getVersionString();
        }
        return null;
    }

    /**
     * Get bumper status
     *
     * @return bumper status
     */
    int getBumper() {
        RobotDriver driver = mDriver;
        if (driver != null) {
            return driver.getBumper();
        }
        return RobotDriver.NO_BUMPER_ID;
    }

    /**
     * Get if a battery is low debounced.
     * @return True if we're almost empty
     */
    boolean getBatteryLowDebounced() {
        RobotDriver driver = mDriver;
        return driver != null && driver.getBatteryLowDebounced();
    }

    /**
     * Get if a battery is critical debounced.
     * @return True if we're almost empty
     */
    boolean getBatteryCriticalDebounced() {
        RobotDriver driver = mDriver;
        return driver != null && driver.getBatteryCriticalDebounced();
    }

    /**
     * Get the robot model for this specific robot.
     * @return The robot model.
     */
    RobotDriver.RobotModel getModel() {
        RobotDriver driver = mDriver;
        if (driver != null) {
            return driver.getModel();
        }
        return null;
    }

    /**
     * Compute the transform to the base center from the tango device, which depends on the
     * physical properties of each robot.
     * @param tf The current world (ADF) transform.
     * @return The transform of the robot, in world (ADF) coordinates.
     */
    Transform tangoToBase(Transform tf) {
        RobotDriver driver = mDriver;
        if (driver != null) {
            return driver.tangoToBase(tf);
        }
        return null;
    }

    /**
     * Get the battery statuses
     *
     * @return The battery statuses
     */
    BatteryStatus[] getBatteryStatuses() {
        RobotDriver driver = mDriver;
        if (driver != null) {
            return driver.getBatteryStatuses();
        }
        return null;
    }

    /**
     * Called to determine if the robot is connected.
     * @return If true, then the robot is in a state where it should work if commanded.
     */
    boolean isConnected() {
        RobotDriver driver = mDriver;
        return driver != null && driver.isConnected();
    }

    /**
     * Sets the angular velocity for ROS messages
     * @param angular angular velocity
     */
    @Override
    public void fillAngular(Vector3 angular) {
        RobotDriver driver = mDriver;
        if (driver != null) {
            driver.fillAngular(angular);
        }
    }

    /**
     * Sets the linear velocity for ROS messages
     * @param linear linear velocity
     */
    @Override
    public void fillLinear(Vector3 linear) {
        RobotDriver driver = mDriver;
        if (driver != null) {
            driver.fillLinear(linear);
        }
    }
}
