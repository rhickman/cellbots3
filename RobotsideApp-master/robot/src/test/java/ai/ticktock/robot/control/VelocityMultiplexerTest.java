package ai.cellbots.robot.control;

import android.test.mock.MockContext;

import org.junit.Assert;
import org.junit.Test;

import ai.cellbots.common.Transform;
import ai.cellbots.common.data.BatteryStatus;
import ai.cellbots.common.data.Teleop;
import ai.cellbots.robot.control.VelocityMultiplexer.MuxPriority;
import ai.cellbots.robot.driver.RobotDriver;
import ai.cellbots.robot.driver.RobotModel;

/**
 * The the VelocityMultiplexer class.
 */
public class VelocityMultiplexerTest {

    /**
     * Created a dummy TestRobotDriver class to be used for testing purposes
     */
    private final class TestRobotDriver extends RobotDriver {
        private Teleop mTeleop;
        /**
         * Create the TestRobotDriver.
         */
        private TestRobotDriver() {
            super("TestRobotDriver", new MockContext(), 5,
                    new RobotModel(0, 0, 0, 0, 1, 1, 1, 0, new BatteryStatus[0]));
        }

        @Override
        public Transform computeRobotBasePosition(Transform deviceTransform) {
            return null;
        }

        @Override
        public synchronized void setTeleop(Teleop teleop) {
            mTeleop = teleop;
        }

        /**
         * Get the current teleop value.
         *
         * @return The teleop value.
         */
        private synchronized Teleop getTeleop() {
            return mTeleop;
        }

        @Override
        public String getRobotUuid() {
            return null;
        }

        @Override
        protected void onUpdate() {

        }

        @Override
        protected void onShutdown() {

        }

        @Override
        public String getVersionString() {
            return null;
        }

        @Override
        public boolean isConnected() {
            return false;
        }
    }

    /**
     * Adds each a message at each priority and ensures it replaces lower priority ones.
     */
    @Test
    public void checkPriorityPositions() {
        TestRobotDriver testRobotDriver = new TestRobotDriver();
        VelocityMultiplexer velocityMultiplexer = new VelocityMultiplexer(testRobotDriver);

        // Enqueues velocities, all with different priorities, except for the third and forth
        // elements. To distinguish them, they will contain Teleop elements with different values
        velocityMultiplexer.enqueueVelocity(new Teleop(1, 0, 0, 0, 0, 0), MuxPriority.NAVIGATION);
        Assert.assertEquals(testRobotDriver.getTeleop().getVx(), 1.0, 0);

        velocityMultiplexer.enqueueVelocity(new Teleop(2, 0, 0, 0, 0, 0), MuxPriority.ANIMATION);
        Assert.assertEquals(testRobotDriver.getTeleop().getVx(), 2.0, 0);

        velocityMultiplexer.enqueueVelocity(new Teleop(3, 0, 0, 0, 0, 0), MuxPriority.TELEOP);
        Assert.assertEquals(testRobotDriver.getTeleop().getVx(), 3.0, 0);

        velocityMultiplexer.enqueueVelocity(new Teleop(4, 0, 0, 0, 0, 0), MuxPriority.POINT_CLOUD);
        Assert.assertEquals(testRobotDriver.getTeleop().getVx(), 4.0, 0);

        velocityMultiplexer.enqueueVelocity(new Teleop(5, 0, 0, 0, 0, 0), MuxPriority.BUMP);
        Assert.assertEquals(testRobotDriver.getTeleop().getVx(), 5.0, 0);

        velocityMultiplexer.enqueueVelocity(new Teleop(6, 0, 0, 0, 0, 0), MuxPriority.EMERGENCY);
        Assert.assertEquals(testRobotDriver.getTeleop().getVx(), 6.0, 0);

        testRobotDriver.shutdown();
        velocityMultiplexer.shutdown();
        testRobotDriver.waitShutdown();
        velocityMultiplexer.waitShutdown();
    }

    /**
     * Test that a new element at the same priority is set as the new element.
     */
    @Test
    public void checkSamePriority() {
        TestRobotDriver testRobotDriver = new TestRobotDriver();
        VelocityMultiplexer velocityMultiplexer = new VelocityMultiplexer(testRobotDriver);

        // Enqueue four elements with the same priority and check that are removed in the correct
        // order
        velocityMultiplexer.enqueueVelocity(new Teleop(2, 0, 0, 0, 0, 0), MuxPriority.ANIMATION);
        Assert.assertEquals(testRobotDriver.getTeleop().getVx(), 2.0, 0);

        velocityMultiplexer.enqueueVelocity(new Teleop(3, 0, 0, 0, 0, 0), MuxPriority.ANIMATION);
        Assert.assertEquals(testRobotDriver.getTeleop().getVx(), 3.0, 0);

        velocityMultiplexer.enqueueVelocity(new Teleop(4, 0, 0, 0, 0, 0), MuxPriority.POINT_CLOUD);
        Assert.assertEquals(testRobotDriver.getTeleop().getVx(), 4.0, 0);

        testRobotDriver.shutdown();
        velocityMultiplexer.shutdown();
        testRobotDriver.waitShutdown();
        velocityMultiplexer.waitShutdown();
    }

    /**
     * Insert and null a priority to ensure a null result.
     */
    @Test
    public void testNull() {
        TestRobotDriver testRobotDriver = new TestRobotDriver();
        VelocityMultiplexer velocityMultiplexer = new VelocityMultiplexer(testRobotDriver);

        velocityMultiplexer.enqueueVelocity(new Teleop(2, 0, 0, 0, 0, 0), MuxPriority.ANIMATION);
        Assert.assertEquals(testRobotDriver.getTeleop().getVx(), 2.0, 0);

        velocityMultiplexer.enqueueVelocity(null, MuxPriority.ANIMATION);
        Assert.assertNull(testRobotDriver.getTeleop());

        testRobotDriver.shutdown();
        velocityMultiplexer.shutdown();
        testRobotDriver.waitShutdown();
        velocityMultiplexer.waitShutdown();
    }

    /**
     * Tests if the previous element times out.
     *
     * @throws InterruptedException Exception if test is interrupted
     */
    @Test
    public void testTimeout() throws InterruptedException {
        TestRobotDriver testRobotDriver = new TestRobotDriver();
        VelocityMultiplexer velocityMultiplexer = new VelocityMultiplexer(testRobotDriver);

        // Similar test than checkPriorityPositions(), but with more elements.
        velocityMultiplexer.enqueueVelocity(new Teleop(1, 0, 0, 0, 0, 0), MuxPriority.NAVIGATION);
        velocityMultiplexer.enqueueVelocity(new Teleop(2, 0, 0, 0, 0, 0), MuxPriority.TELEOP);
        Assert.assertEquals(testRobotDriver.getTeleop().getVx(), 2.0, 0);

        Thread.sleep(Teleop.TIMEOUT * 2);

        velocityMultiplexer.enqueueVelocity(new Teleop(3, 0, 0, 0, 0, 0), MuxPriority.NAVIGATION);
        Assert.assertEquals(testRobotDriver.getTeleop().getVx(), 3.0, 0);

        Thread.sleep(Teleop.TIMEOUT * 2);
        Assert.assertNull(testRobotDriver.getTeleop());

        testRobotDriver.shutdown();
        velocityMultiplexer.shutdown();
        testRobotDriver.waitShutdown();
        velocityMultiplexer.waitShutdown();
    }
}
