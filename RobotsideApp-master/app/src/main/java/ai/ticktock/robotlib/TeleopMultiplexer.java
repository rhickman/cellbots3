package ai.cellbots.robotlib;

import android.support.annotation.NonNull;

import java.util.Objects;

import ai.cellbots.common.cloud.CloudPath;
import ai.cellbots.common.cloud.RTCDatabaseChannel;
import ai.cellbots.common.cloud.Synchronizer;
import ai.cellbots.common.data.Teleop;

/**
 * Multiplexes together teleop and controller channels to get the right robot motion and
 * accessory commands for the driver. The rule is that there is a list of sources, (ordered
 * from ROS, to the Firebase Cloud, to the SafetyController to the Controller) and a timeout.
 * The highest-priority source that has not timed out and has relevant component is selected.
 * For driving, the highest-priority non-timed out source with a non-zero velocity is selected.
 * In the event that all sources have timed out, then the robot will stop. For accessories,
 * the highest-priority source with the accessory on is selected. If no sources are available,
 * the accessory will turn off.
 */
public class TeleopMultiplexer {
    private static final String SOURCE_ROS = "ROS";
    private static final String SOURCE_CLOUD = "CLOUD";
    private static final String SOURCE_SAFETY_CONTROLLER = "SAFETY_CONTROLLER";
    private static final String SOURCE_CONTROLLER = "CONTROLLER";
    private static final String[] SOURCES = {SOURCE_ROS, SOURCE_CLOUD,
            SOURCE_SAFETY_CONTROLLER, SOURCE_CONTROLLER};
    private static final long TIMEOUT = 1000; // Milliseconds

    private final Synchronizer<Teleop> mSynchronizer =
            new Synchronizer<>(SOURCES, TIMEOUT);

    private SafetyController.BumperManeuver mBumperManeuver;
    private final RobotDriverMultiplex mDriver;
    private final RTCDatabaseChannel mDatabaseChannel;

    /**
     * Create the teleop multiplexer.
     * @param driver The robot driver multiplex.
     * @param databaseChannel The database channel.
     */
    public TeleopMultiplexer(@NonNull RobotDriverMultiplex driver,
            @NonNull RTCDatabaseChannel databaseChannel) {
        mDriver = driver;
        mDatabaseChannel = databaseChannel;
        Objects.requireNonNull(driver);
        Objects.requireNonNull(databaseChannel);
    }

    /**
     * Update the CONTROLLER source from the Controller.
     * @param controller The Controller object.
     */
    public synchronized void updateFromController(@NonNull Controller controller) {
        Objects.requireNonNull(controller);
        if (controller.haveMotion()) {
            mSynchronizer.setValue(SOURCE_CONTROLLER,
                    new Teleop(
                            new Teleop(controller.getSpeed(), 0.0, 0.0, 0.0, 0.0,
                                    controller.getAngular()),
                            controller.getAction1(), controller.getAction2()));
        }
    }

    /**
     * Update the SAFETY_CONTROLLER source from the SafetyController.
     * @param safetyController The SafetyController object.
     * @return True if the state of the SafetyController has changed.
     */
    public synchronized boolean updateFromSafetyController(@NonNull SafetyController safetyController) {
        Objects.requireNonNull(safetyController);
        // Check if there is a bumper hit, and if so execute the functions.
        if (safetyController.updateBumper(mDriver.getBumper())) {
            mSynchronizer.setValue(SOURCE_SAFETY_CONTROLLER,
                    new Teleop(safetyController.getSafeLinearSpeed(), 0.0, 0.0, 0.0, 0.0,
                            safetyController.getSafeAngularSpeed()));
            // Clear the controller, so we will not move if the safety controller returns zero.
            mSynchronizer.setValue(SOURCE_CONTROLLER, null);
        } else {
            mSynchronizer.setValue(SOURCE_SAFETY_CONTROLLER, null);
        }
        SafetyController.BumperManeuver newManeuver = safetyController.getBumperManeuver();
        if (newManeuver != mBumperManeuver) {
            mBumperManeuver = newManeuver;
            return true;
        }
        return false;
    }

    /**
     * Update the ROS source from the ROSNode.
     * @param rosNode The ROSNode object.
     */
    public synchronized void updateFromROS(@NonNull ROSNode rosNode) {
        Objects.requireNonNull(rosNode);
        if (rosNode.getRobotUpdate()) {
            mSynchronizer.setValue(SOURCE_ROS,
                    new Teleop(rosNode.getRobotSpeed(), 0.0, 0.0, 0.0, 0.0, rosNode.getRobotAngular()));
        }

    }

    /**
     * Update the CLOUD source from the CloudConnector.
     * @param cloudConnector The CloudConnector.
     */
    public synchronized void updateFromCloud(@NonNull CloudConnector cloudConnector,
            String user, String robot) {
        Objects.requireNonNull(cloudConnector);
        Teleop firebaseValue = cloudConnector.getTeleop();
        if (firebaseValue != null) {
            RobotDriver.RobotModel model = mDriver.getModel();
            if (model != null) {
                firebaseValue = new Teleop(firebaseValue, model.getMaxSpeed(), model.getMaxAngular());
            } else {
                firebaseValue = null;
            }
        }
        Teleop rtcValue = (Teleop) mDatabaseChannel.getObject(CloudPath.ROBOT_TELEOP_PATH, user, robot);
        if (rtcValue != null) {
            RobotDriver.RobotModel model = mDriver.getModel();
            if (model != null) {
                rtcValue = new Teleop(rtcValue, model.getMaxSpeed(), model.getMaxAngular());
            } else {
                rtcValue = null;
            }
        }
        mSynchronizer.setValues(SOURCE_CLOUD, new Teleop[]{rtcValue, firebaseValue});
    }

    /**
     * Update the driver.
     * @return True if the accessory state changed, and thus we need to display that.
     */
    public synchronized boolean updateDriver() {
        // If we have a valid robot and user, and teleop is not null, then do update.
        Teleop driveTeleop = mSynchronizer.getFirstValue(
                new Synchronizer.Criteria<Teleop>() {
                    @Override
                    public boolean isAcceptable(@NonNull Teleop teleop) {
                        return teleop.getVx() != 0 || teleop.getRz() != 0;
                    }
                });
        if (driveTeleop != null) {
            mDriver.setMotion(driveTeleop.getVx(), driveTeleop.getRz());
        } else {
            mDriver.setMotion(0, 0);
        }
        boolean action1 = null != mSynchronizer.getFirstValue(
                new Synchronizer.Criteria<Teleop>() {
                    @Override
                    public boolean isAcceptable(@NonNull Teleop teleop) {
                        return teleop.getAction1();
                    }
                });
        boolean action2 = null != mSynchronizer.getFirstValue(
                new Synchronizer.Criteria<Teleop>() {
                    @Override
                    public boolean isAcceptable(@NonNull Teleop teleop) {
                        return teleop.getAction2();
                    }
                });
        boolean result = (action1 != mDriver.getAction1()) || (action2 != mDriver.getAction2());
        mDriver.setAction1(action1);
        mDriver.setAction2(action2);
        return result;
    }
}
