package ai.cellbots.arcompanion.utils;

import java.text.DecimalFormat;

import ai.cellbots.arcompanion.R;

/**
 * A utility class for formatting floating points (floats, doubles) to a specified number
 * of decimal places.
 *
 * Created by playerthree on 8/22/17.
 */

public final class FloatingPointUtils {

    // Format for converting values to three decimal places
    private static final DecimalFormat FORMAT_THREE_DECIMAL = new DecimalFormat("0.000");

    private FloatingPointUtils() {
        throw new AssertionError(R.string.assertion_utility_class_never_instantiated);
    }

    public static String formatToThreeDecimals(double num) {
        return FORMAT_THREE_DECIMAL.format(num);
    }
}
