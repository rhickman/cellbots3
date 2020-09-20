package ai.cellbots.common;

import android.support.annotation.NonNull;
import android.util.Log;

import java.util.Locale;

/**
 * Contains information about and sets the connection status of RobotApp with the cloud.
 * Used by Companion and ARCompanion apps for logging the robot's connection status.
 */
public class RobotConnectionStatus {
    private static final String TAG = RobotConnectionStatus.class.getSimpleName();

    // The maximum latency for robot connection to have status "excellent".
    // Surpassing this value will set the robot's connection status to "poor".
    private static final long CONNECTION_LATENCY_THRESHOLD = 1000;  // In milliseconds.

    /**
     * Connection status of RobotApp with the cloud.
     * Changes depending on latency, or the difference between current timestamp and
     * robot's last ping time (from the cloud).
     */
    public enum Status {
        EXCELLENT,
        POOR,
        DISCONNECTED
    }

    /**
     * Listener for sending updates of mStatus.
     */
    public interface Listener {
        /**
         * Called when the status changes in setStatus().
         *
         * @param status New status of the robot connection.
         */
        void onStatusUpdate(Status status);
    }

    private Listener mListener;

    // Current status of the connection between RobotApp and cloud.
    private Status mStatus;

    /**
     * Constructor with a listener for sending status updates.
     *
     * @param listener Listener for sending status updates. Must not be null.
     */
    public RobotConnectionStatus(@NonNull Listener listener) {
        mListener = listener;
        initialize();
    }

    /**
     * Initializes components of RobotConnectionStatus.
     */
    private void initialize() {
        setStatus(Status.DISCONNECTED);  // Default status.
    }

    /**
     * Checks if the latency value is excellent (<= 1000 ms) or poor (> 1000 ms), then stores and
     * logs the result.
     *
     * @param latency Latency, or difference between current timestamp and the received ping timestamp.
     */
    public void setStatusBasedOnLatency(long latency) {
        if (latency < 0) {
            String negativeLatencyLog =
                    String.format(Locale.US, "Latency was %s but expected nonnegative.", latency);
            Log.wtf(TAG, negativeLatencyLog);
            return;
        }
        if (latency <= CONNECTION_LATENCY_THRESHOLD) {
            setStatus(Status.EXCELLENT);
        } else {
            setStatus(Status.POOR);
        }
    }

    /**
     * Gets status of robot connection.
     *
     * @return State of robot connection.
     */
    public Status getStatus() {
        return mStatus;
    }

    /**
     * Sets status of robot connection.
     *
     * @param status State of robot connection.
     */
    public void setStatus(Status status) {
        Log.i(TAG, "Connection Status changed to: " + mStatus);
        mStatus = status;
        mListener.onStatusUpdate(status);
    }
}