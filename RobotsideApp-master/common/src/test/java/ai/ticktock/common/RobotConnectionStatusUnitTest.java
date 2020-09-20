package ai.cellbots.common;

import org.junit.Before;
import org.junit.Test;

import ai.cellbots.common.RobotConnectionStatus.Status;

import static junit.framework.Assert.assertEquals;

/**
 * Unit test class for the ai.cellbots.common.RobotConnectionStatus class.
 */
public class RobotConnectionStatusUnitTest {

    /**
     * What to test:
     *
     * 1) Default status                    (status = disconnected)
     * 2) Positive Latency                  (status = excellent if latency <= 1000)
     *                                      (status = poor if latency > 1000)
     * 3) Zero Latency                      (status = excellent)
     * 4) Negative Latency                  (should not change status)
     * 5) Status change to DISCONNECTED if timer finishes
     */

    private RobotConnectionStatus mRobotConnectionStatus;
    private RobotConnectionStatusTimer mRobotConnectionStatusTimer;

    /**
     * Initializes the necessary variables before each test.
     */
    @Before
    public void setUp() {
        mRobotConnectionStatus = new RobotConnectionStatus(new RobotConnectionStatus.Listener() {
            @Override
            public void onStatusUpdate(Status status) {
                // Empty for testing purposes.
            }
        });
        mRobotConnectionStatusTimer = new RobotConnectionStatusTimer(mRobotConnectionStatus);
    }

    /**
     * Tests if the status is set to the default (Status.DISCONNECTED) after RobotConnectionStatus
     * is initialized.
     */
    @Test
    public void testDefaultStatus() {
        assertEquals(Status.DISCONNECTED, mRobotConnectionStatus.getStatus());
    }

    /**
     * Tests if the status is set to EXCELLENT (if latency is positive and less than or equal to 1000)
     * or POOR (if latency is positive and greater than 1000).
     */
    @Test
    public void testPositiveLatency() {
        long latency = 10;
        mRobotConnectionStatus.setStatusBasedOnLatency(latency);
        assertEquals(Status.EXCELLENT, mRobotConnectionStatus.getStatus());

        latency = 9000;
        mRobotConnectionStatus.setStatusBasedOnLatency(latency);
        assertEquals(Status.POOR, mRobotConnectionStatus.getStatus());

        latency = 150;
        mRobotConnectionStatus.setStatusBasedOnLatency(latency);
        assertEquals(Status.EXCELLENT, mRobotConnectionStatus.getStatus());
    }

    /**
     * Tests if the status is set to EXCELLENT if latency is 0.
     */
    @Test
    public void testZeroLatency() {
        long latency = 0;
        mRobotConnectionStatus.setStatusBasedOnLatency(latency);
        assertEquals(Status.EXCELLENT, mRobotConnectionStatus.getStatus());
    }

    /**
     * Tests if status remains UNCHANGED if latency is negative (< 0).
     * This is because RobotConnectionStatus will throw an exception if it received
     * a negative latency. 
     */
    @Test
    public void testNegativeLatency() {
        // Since giving a negative latency throws a wtf log, expect the status to not change.
        long latency = -1;
        mRobotConnectionStatus.setStatusBasedOnLatency(latency);  // Should not change status.
        assertEquals(Status.DISCONNECTED, mRobotConnectionStatus.getStatus());

        mRobotConnectionStatus.setStatus(Status.EXCELLENT);
        mRobotConnectionStatus.setStatusBasedOnLatency(latency);  // Should not change status.
        assertEquals(Status.EXCELLENT, mRobotConnectionStatus.getStatus());

        mRobotConnectionStatus.setStatus(Status.POOR);
        mRobotConnectionStatus.setStatusBasedOnLatency(latency);  // Should not change status.
        assertEquals(Status.POOR, mRobotConnectionStatus.getStatus());
    }

    /**
     * Tests if RobotConnectionStatus changes its status to DISCONNECTED if
     * RobotConnectionStatusTimer finishes.
     */
    @Test
    public void testStatusChangeToDisconnectedIfTimerFinishes() {
        mRobotConnectionStatusTimer.onFinish();
        assertEquals(Status.DISCONNECTED, mRobotConnectionStatus.getStatus());
    }
}
