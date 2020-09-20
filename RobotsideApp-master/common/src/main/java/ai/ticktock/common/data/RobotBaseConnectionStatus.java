package ai.cellbots.common.data;

import android.util.Log;

/**
 * Connection Status between RobotApp and Robot base.
 */
public class RobotBaseConnectionStatus {
    private static final String TAG = RobotBaseConnectionStatus.class.getSimpleName();

    public enum Status {
        CONNECTED,  // Robot base is connected to the RobotApp.
        DISCONNECTED  // Robot base is NOT connected to the RobotApp.
    }

    // Current status of connection between RobotApp and Robot base.
    private Status mStatus;

    public RobotBaseConnectionStatus() {
        mStatus = Status.DISCONNECTED;  // Default status.
    }

    /**
     * Gets the current status for the connection between RobotApp and Robot base.
     *
     * @return The current connection status.
     */
    public Status getStatus() {
        return mStatus;
    }

    /**
     * Sets the current status for the connection between RobotApp and Robot base.
     *
     * @param status The new connection status.
     */
    public void setStatus(Status status) {
        Log.i(TAG, "Connection Status changed to: " + status);
        mStatus = status;
    }
}
