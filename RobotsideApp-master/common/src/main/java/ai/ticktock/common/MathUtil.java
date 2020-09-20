package ai.cellbots.common;

/**
 * Math utility class.
 */
public final class MathUtil {
    /**
     * Computes an inverse of square root. The returned value is approximate, may have an error
     * up to 2% of its original value for a small value like < 1.0. As the value increases,
     * the error decreases.
     *
     * @param value The value to compute
     * @return 1.0 / sqrt(value)
     */
    public static double ComputeFastInverseSqrt(double value) {
        double half = 0.5d * value;
        long i = Double.doubleToLongBits(value);
        i = 0x5fe6ec85e7de30daL - (i >> 1);
        value = Double.longBitsToDouble(i);
        value *= (1.5d - half * value * value);
        return value;
    }
}
