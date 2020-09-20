package ai.cellbots.robot.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import ai.cellbots.robot.costmap.CostMapPose;

public class DijkstraNodeTest {
    DijkstraNode subject;

    @Before
    public void setUp() {
        subject = new DijkstraNode(new CostMapPose(0, 0));
    }

    // Calculate the absolute distance to another DijkstraNode.
    @Test
    public void testDistanceTo() {
        // Distances should be equal
        assertEquals(subject.distanceTo(subject), 0.0, 1e-10);
        assertEquals(subject.distanceTo(new DijkstraNode(new CostMapPose(0, 1))), 1.0, 1e-10);
        assertEquals(subject.distanceTo(new DijkstraNode(new CostMapPose(1, 1))), Math.sqrt(2),
                1e-10);
    }

    // Check if a node has a previous one.
    @Test
    public void testHasPrevious() {
        // Subject doesn't have any previous node yet.
        assertFalse(subject.hasPrevious());
        // Set a previous node and then check it.
        subject.setPrevious(new DijkstraNode(new CostMapPose(0, -1)));
        assertTrue(subject.hasPrevious());
    }

    // Compare two Dijkstra nodes, according to their distances.
    @Test
    public void testCompareTo() {
        subject.setScore(0.0);

        // Nodes should be equal.
        // Compare it to itself.
        //noinspection EqualsWithItself
        assertTrue(subject.compareTo(subject) == 0);

        // Compare it to another node with the same target and distance.
        DijkstraNode otherNode = new DijkstraNode(new CostMapPose(0, 0));
        otherNode.setScore(0.0);
        assertTrue(subject.compareTo(otherNode) == 0);

        // Compare it to another node with different target but same distance.
        otherNode = new DijkstraNode(new CostMapPose(3, 5));
        otherNode.setScore(0.0);
        assertTrue(subject.compareTo(otherNode) == 0);

        // Nodes should not be equal.
        // Make the distance of otherNode greater. Comparison result should be negative.
        otherNode.setScore(1.0);
        assertFalse(subject.compareTo(otherNode) == 0);
        assertTrue(subject.compareTo(otherNode) < 0);

        // Make the distance of subject greater than that of otherNode. Comparison result should
        // be positive.
        subject.setScore(2.0);
        assertTrue(subject.compareTo(otherNode) > 0);
    }
}
