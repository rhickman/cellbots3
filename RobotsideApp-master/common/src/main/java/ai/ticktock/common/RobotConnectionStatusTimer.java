package ai.cellbots.common;

import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * Timer for checking if robot is disconnected by tracking the pause duration between
 * each new ping result. In other words, whenever a new ping time is received from the cloud,
 * this timer will start and count down from the specified duration.
 * If this timer finishes before being cancelled, this means that there are no more pings from
 * the cloud, hence the RobotApp is disconnected.
 *
 * Note: Depends on RobotConnectionStatus.
 */
public class RobotConnectionStatusTimer extends CountDownTimer {
    private static final String TAG = RobotConnectionStatusTimer.class.getSimpleName();

    // Parameters for setting up mRobotConnectionStatusTimer.
    private static final long DURATION = 3000;  // In milliseconds.
    private static final long COUNTDOWN_INTERVAL = 1000;  // In milliseconds.

    private RobotConnectionStatus mRobotConnectionStatus;

    /**
     * Constructor for the RobotConnectionStatusTimer with a given duration and countdown interval.
     *
     * @param status The status of the robot connection. Must not be null.
     */
    public RobotConnectionStatusTimer(@NonNull RobotConnectionStatus status) {
        super(DURATION, COUNTDOWN_INTERVAL);
        mRobotConnectionStatus = status;
    }

    /**
     * Called whenever the specified countDownInterval time passes.
     *
     * @param millisUntilFinished Amount of milliseconds until the timer finishes.
     */
    @Override
    public void onTick(long millisUntilFinished) {
        Log.v(TAG, "Tick: " + millisUntilFinished);
    }

    /**
     * Called when the timer has expired.
     */
    @Override
    public void onFinish() {
        // Change status in RobotConnectionStatus to DISCONNECTED.
        Log.i(TAG, "Timer has finished. Robot connection status updated to DISCONNECTED.");
        mRobotConnectionStatus.setStatus(RobotConnectionStatus.Status.DISCONNECTED);
    }

    /**
     * Restart this timer by cancelling the previous countdown (if running), then starting it again.
     */
    public void restart() {
        cancel();
        start();
    }
}