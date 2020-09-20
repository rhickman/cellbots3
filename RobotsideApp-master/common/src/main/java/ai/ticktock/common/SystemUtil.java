package ai.cellbots.common;

/**
 * Utility class for system.
 */
@SuppressWarnings("UtilityClassCanBeEnum")
public final class SystemUtil {
    /**
     * Constructor, but should not construct because this is a utility class.
     */
    private SystemUtil() {
        throw new UnsupportedOperationException(
                "This is a utility class and cannot be instantiated");
    }

    /**
     * Generates a string message containing the maximum/total/free memory in MB.
     *
     * @return Memory usage string
     */
    public static String GenerateMemoryUsageString() {
        Runtime runTime = Runtime.getRuntime();
        long maxMemory = runTime.maxMemory();
        long totalMemory = runTime.totalMemory();
        long freeMemory = runTime.freeMemory();
        final long megaInByte = 1048576;
        return "max=" + maxMemory / megaInByte + "MB, total=" + totalMemory / megaInByte +
                "MB, free=" + freeMemory / megaInByte +"MB";
    }
}
