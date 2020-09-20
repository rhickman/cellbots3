package ai.cellbots.robot.navigation;

import static org.junit.Assert.*;
import org.junit.Test;

import ai.cellbots.robot.costmap.CostMapPose;

public class PathTest {

    @Test
    public void testEquals() {
        // Single element path.
        Path subject = new Path(new CostMapPose(1,4));

        // Objects should be equal.
        assertEquals(subject, subject);
        assertEquals(subject, new Path(new CostMapPose(1,4)));

        // Objects should not be equal.
        assertNotEquals(subject, new Object());
        assertNotEquals(subject, new CostMapPose(50, 2));

        // Multiple elements path.
        subject = new Path(new CostMapPose[]{
                new CostMapPose(1,2),
                new CostMapPose(3,4),
                new CostMapPose(5,6)});

        // Objects should be equal.
        assertEquals(subject, subject);

        Path pattern = new Path(new CostMapPose[]{
                new CostMapPose(1,2),
                new CostMapPose(3,4),
                new CostMapPose(5,6)});
        assertEquals(subject, pattern);

        // Objects should not be equal.
        assertNotEquals(subject, new Object());

        pattern = new Path(new CostMapPose[]{
                new CostMapPose(1,2),
                new CostMapPose(5,6),
                new CostMapPose(3,4)});
        assertNotEquals(subject, pattern);
    }

    @Test
    public void testLast() {
        Path subject = new Path(new CostMapPose[]{
                new CostMapPose(1,2),
                new CostMapPose(3,4),
                new CostMapPose(5,6)});
        assertEquals(subject.last(), new CostMapPose(5,6));
    }
}
