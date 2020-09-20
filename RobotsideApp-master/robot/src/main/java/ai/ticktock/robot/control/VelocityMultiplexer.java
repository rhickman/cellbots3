package ai.cellbots.robot.control;

import android.util.Log;

import java.util.HashMap;

import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.TimedLoop;
import ai.cellbots.common.cloud.TimestampManager;
import ai.cellbots.common.data.Teleop;
import ai.cellbots.robot.driver.RobotDriver;

/**
 * Receives velocities using enqueueVelocity() and stores them in a PriorityQueue.
 * Then, at certain rate, it de-queues these values and sends them to the connected robot base,
 * specified in RobotDriver argument.
 */
public class VelocityMultiplexer implements ThreadedShutdown {
    private static final String TAG = VelocityMultiplexer.class.getSimpleName();
    // Send the latest teleop every 100 ms.
    private static final long LOOP_UPDATE = 100;
    // Loop for sending the teleop message to the driver.
    private final TimedLoop mTimedLoop;
    // Whether to enable the velocity output or not.
    private boolean mIsEnabled;
    // This class sends velocities commands directly to the connected robot base.
    private final RobotDriver mRobotDriver;
    // Stores the latest velocities of the robot.
    private final HashMap<MuxPriority, Teleop> mLatestTeleop = new HashMap<>();

    /**
     * We can send velocities to this multiplexer given the following priorities
     */
    public enum MuxPriority {
        EMERGENCY,  // Reserved for real emergency
        BUMP,
        POINT_CLOUD,
        RTC_TELEOP,
        TELEOP,
        ANIMATION,
        NAVIGATION, // default
        RESERVED    // Reserved for future usage (LOWEST PRIORITY)
    }

    /**
     * Constructor
     *
     * @param robotDriver The robot driver
     * @param start True if the multiplexer starts automatically
     */
    private VelocityMultiplexer(RobotDriver robotDriver, boolean start) {
        mIsEnabled = start;
        mRobotDriver = robotDriver;
        mTimedLoop = new TimedLoop(VelocityMultiplexer.class.getSimpleName(),
                new TimedLoop.Looped() {
                    @Override
                    public boolean update() {
                        sendBestTeleopToDriver();
                        return true;
                    }

                    @Override
                    public void shutdown() {
                        onShutdown();
                    }
                }, LOOP_UPDATE);
    }

    /**
     * Constructor with start always true.
     *
     * @param robotDriver The robot driver
     */
    public VelocityMultiplexer(RobotDriver robotDriver) {
        // Enabled automatically at start-up
        this(robotDriver, true);
    }

    /**
     * Enqueues velocities into a priority queue.
     *
     * @param velocity Teleop parameter.
     * @param priority MuxPriorities priority.
     */
    @SuppressWarnings("WeakerAccess")
    public synchronized void enqueueVelocity(Teleop velocity, MuxPriority priority) {
        mLatestTeleop.put(priority, velocity);
        sendBestTeleopToDriver();
    }

    /**
     * Sets the current robot driver state
     */
    private synchronized void sendBestTeleopToDriver() {
        if (!mIsEnabled) {
            mRobotDriver.setTeleop(null);
            return;
        }
        // Find the best teleop.
        for (MuxPriority priority : MuxPriority.values()) {
            if (mLatestTeleop.containsKey(priority)) {
                Teleop teleop = mLatestTeleop.get(priority);
                if (teleop != null && teleop.getTimestamp() +
                        Teleop.TIMEOUT > TimestampManager.getCurrentTimestamp()) {
                    Log.v(TAG, "Sending teleop to driver: " + teleop);
                    mRobotDriver.setTeleop(teleop);
                    return;
                }
            }
        }
        // If we do not have a good teleop, null it out.
        mRobotDriver.setTeleop(null);
    }
    /**
     * Shutdown the system
     */
    private synchronized void onShutdown() {
        disable();
        mRobotDriver.setTeleop(null);
    }

    /**
     * Disable the velocity multiplexer (can't send velocities to the robot base)
     */
    public void disable() {
        mIsEnabled = false;
    }

    /**
     * Enable the velocity multiplexer (can send velocities to the robot base)
     */
    public void enable() {
        mIsEnabled = true;
    }

    /**
     * Shutdown the system.
     */
    @Override
    public void shutdown() {
        mTimedLoop.shutdown();
    }

    /**
     * Wait for the system to shutdown.
     */
    @Override
    public void waitShutdown() {
        shutdown();
        mTimedLoop.waitShutdown();
    }
}