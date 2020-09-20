package ai.cellbots.common.data.event;

/**
 * Event called whenever the robot base is updated and sends new data to the UsbRobotDriver.
 * Used for determining if the connection between RobotApp and robot base is still active.
 */
public class RobotBaseConnectionStatusEvent {

    /**
     * Determines if the robot base is still connected to the RobotApp.
     * True if the robot base is connected and able to send data to the UsbRobotDriver class.
     */
    public final boolean mIsRobotBaseConnected;

    /**
     * Constructor for RobotBaseConnectionStatusEvent.
     *
     * @param isRobotBaseConnected True if the robot base is connected to the RobotApp and able
     *                             to send data to the UsbRobotDriver class.
     */
    public RobotBaseConnectionStatusEvent(boolean isRobotBaseConnected) {
        mIsRobotBaseConnected = isRobotBaseConnected;
    }
}
