package ai.cellbots.robot.costmap;

import org.junit.Test;
import static org.junit.Assert.*;

public class GridCostMapPoseTest {
    @Test
    public void testHorizontalDistanceTo() {
        CostMapPose subject = new CostMapPose(0, 0);

        // Distances should be equal
        assertEquals(subject.horizontalDistanceTo(new CostMapPose(5, 0)), 5);
        assertEquals(subject.horizontalDistanceTo(subject), 0);
        assertEquals(subject.horizontalDistanceTo(new CostMapPose(-5, 0)), -5);
    }

    @Test
    public void testVerticalDistanceTo() {
        CostMapPose subject = new CostMapPose(0, 0);

        // Distances should be equal
        assertEquals(subject.verticalDistanceTo(new CostMapPose(0, 5)), 5);
        assertEquals(subject.verticalDistanceTo(subject), 0);
        assertEquals(subject.verticalDistanceTo(new CostMapPose(0, -5)), -5);
    }

    @Test
    public void testSquaredDistanceTo() {
        CostMapPose subject = new CostMapPose(0, 0);

        // Distances should be equal
        assertEquals(subject.squaredDistanceTo(subject), 0.0, 1e-10);
        assertEquals(subject.squaredDistanceTo(new CostMapPose(0, 1)), 1.0, 1e-10);
        assertEquals(subject.squaredDistanceTo(new CostMapPose(1, 1)), 2, 1e-10);
    }

    @Test
    public void testHorizontalOffsetBy() {
        CostMapPose subject = new CostMapPose(0, 0);

        // Objects should be equal
        assertEquals(subject.horizontalOffsetBy(5), new CostMapPose(5, 0));
        assertEquals(subject.horizontalOffsetBy(-5), new CostMapPose(-5, 0));
        assertEquals(subject.horizontalOffsetBy(0), subject);
    }

    @Test
    public void testVerticalOffsetBy() {
        CostMapPose subject = new CostMapPose(0, 0);

        // Objects should be equal
        assertEquals(subject.verticalOffsetBy(5), new CostMapPose(0, 5));
        assertEquals(subject.verticalOffsetBy(-5), new CostMapPose(0, -5));
        assertEquals(subject.verticalOffsetBy(0), subject);
    }

    @Test
    public void testEquals() {
        CostMapPose subject = new CostMapPose(1, 2);

        // Objects should be equal
        assertEquals(subject, subject);
        assertEquals(subject, new CostMapPose(1, 2));

        // Objects should not be equal
        assertFalse(subject.equals(new Object()));
        assertFalse(subject.equals(new CostMapPose(50, 2)));
        assertFalse(subject.equals(new CostMapPose(1, 50)));
    }

    @Test
    public void testHashCode() {
        CostMapPose subject = new CostMapPose(1, 2);

        // Objects should have the same hashCode. First guarantee that equality is satisfied.
        // Test the subject with itself.
        assertEquals(subject, subject);
        assertEquals(subject.hashCode(), subject.hashCode());

        // Test the subject with a copy of itself.
        CostMapPose copyOfSubject = new CostMapPose(1, 2);
        assertEquals(subject, copyOfSubject);
        assertEquals(subject.hashCode(), copyOfSubject.hashCode());
    }
}
