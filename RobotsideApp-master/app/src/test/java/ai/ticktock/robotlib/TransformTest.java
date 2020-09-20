package ai.cellbots.robotlib;

import org.junit.Test;

import static org.junit.Assert.*;

import ai.cellbots.common.Transform;

/**
 * Unit test for toXYTimestamp() method in Transform class. It is made to extract x, y and timestamp
 * data from a known transform and compare it to the expected output.
 */
public class TransformTest {

    @Test
    public void testToXYTimestamp() throws Exception {
        double[] testPosition = new double[]{1.0, 2.1, 3.0};
        double[] testRotation = new double[]{0.0, 0.0, 0.0, 1.0};
        double testTimestamp = 4.0;
        Transform transform = new Transform(testPosition, testRotation, testTimestamp);
        double[] expectedXYTimestamp = new double[]{1.0, 2.1, 4.0};
        assertEquals(expectedXYTimestamp[0], transform.toXYTimestamp()[0], 0.001);
    }

}