package ai.cellbots.common;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Test cases for MathUtil.
 */
public class MathUtilTest {
    // Tests that ComputeFastInverseSqrt() returns valid values in an approximate sense.
    @Test
    public void testComputeFastInverseSqrt() {
        double value1 = 25.0;
        assertEquals(0.2, MathUtil.ComputeFastInverseSqrt(value1),1e-3);

        double value2 = 4.0;
        assertEquals(0.5, MathUtil.ComputeFastInverseSqrt(value2),1e-3);

        // For some small values.
        for (double value = 0.01; value < 5.0; value += 0.13) {
            double inverseSqrt = 1.0 / Math.sqrt(value);
            assertEquals(inverseSqrt, MathUtil.ComputeFastInverseSqrt(value), 2e-2);
        }

        // For some large values.
        for (double value = 127.0; value < 10000.0; value += 3.2) {
            double inverseSqrt = 1.0 / Math.sqrt(value);
            assertEquals(inverseSqrt, MathUtil.ComputeFastInverseSqrt(value), 1e-3);
        }
    }
}
