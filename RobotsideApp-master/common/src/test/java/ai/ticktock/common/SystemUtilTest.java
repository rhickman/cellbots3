package ai.cellbots.common;

import org.junit.Test;

import static ai.cellbots.common.SystemUtil.GenerateMemoryUsageString;
import static org.junit.Assert.assertTrue;

/**
 * Test case for SystemUtil.
 */
public class SystemUtilTest {
    /**
     * Tests that GenerateMemoryUsageString() generates a string containing several memory
     * configuration.
     */
    @Test
    public void testGenerateMemoryUsageString() {
        String memoryUsage = GenerateMemoryUsageString();
        assertTrue(memoryUsage.contains("max="));
        assertTrue(memoryUsage.contains("total="));
        assertTrue(memoryUsage.contains("free="));
    }
}