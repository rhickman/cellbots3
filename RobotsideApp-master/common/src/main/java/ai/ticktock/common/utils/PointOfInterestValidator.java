package ai.cellbots.common.utils;

import android.util.Log;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Pattern;

import ai.cellbots.common.R;
import ai.cellbots.common.data.PointOfInterest;

/**
 * A utility class containing functions for validating a PointOfInterest.
 */

public final class PointOfInterestValidator {

    private static final String TAG = PointOfInterestValidator.class.getSimpleName();

    private PointOfInterestValidator() {
        throw new AssertionError(R.string.assertion_utility_class_never_instantiated);
    }

    /**
     * Determines whether or not a given Object obj has valid fields (non-null, non-empty, wrong type/value).
     * Uses the breadth-first search algorithm to check all fields.
     *
     * @param obj A generic object with fields we want to validate.
     * @return
     */
    public static boolean areAllFieldsValid(Object obj) {
        if (obj == null) {
            return false;
        }

        Field[] fields = obj.getClass().getDeclaredFields();
        Queue<Field> queue = new LinkedList<>(Arrays.asList(fields));

        while (queue.size() > 0) {
            Field field = queue.poll();
            field.setAccessible(true);

            try {
                String key = field.getName();
                Object value = field.get(obj);
                // Cases to ignore fields:
                //      1) Current object is PointOfInterest and key is "name"
                //          - Value is always null since there is no "name" key on firebase
                //      2) Key is "$change"
                //          - Value is always null and unused
                //      3) Key is "serialVersionUID"
                //          - Value has type Long and is unused
                //
                // Cases for invalid fields:
                //      1) Value is null
                //      2) Value is empty (length 0)
                //      3) Key is "type" but value is not "point_of_interest"
                //      4) Key is "uuid" but value is not a valid UUID
                boolean ignoredCaseOne = (obj instanceof PointOfInterest && key.equals("name"));
                boolean ignoredCaseTwo = (key.equals("$change"));
                boolean ignoredCaseThree = (key.equals("serialVersionUID"));

                // Check cases for ignoring fields
                if (ignoredCaseOne || ignoredCaseTwo || ignoredCaseThree) {
                    continue;
                }
                // Check cases for invalid fields
                if (isCaseOneValid(obj, key, value) || isCaseTwoValid(obj, key, value)) {
                    return false;
                }
                if (obj instanceof PointOfInterest) {
                    if (isCaseThreeValid(obj, key, value) || isCaseFourValid(obj, key, value)) {
                        return false;
                    }
                }
                // If value is a custom class from the ai.cellbots package, recursively check and
                // validate this object's fields.
                if (value.getClass().getPackage().getName().contains("ai.cellbots")) {
                    if (!areAllFieldsValid(value)) {
                        return false;
                    }
                }
            } catch (IllegalAccessException e) {
                Log.e(TAG, "An IllegalAccessException was caught for the object " + obj.toString() + ".");
                e.printStackTrace();
            }
        }
        return true;
    }

    /**
     * Checks case one: Is value null?
     *
     * @param obj An Object with a map of key-value pairs that we're checking the above case with.
     * @param key
     * @param value
     * @return True if value is null, else false.
     */
    private static boolean isCaseOneValid(Object obj, String key, Object value) {
        boolean invalidCaseOne = (value == null);

        if (invalidCaseOne) {
            Log.w(TAG, "Value was null for object " + obj.toString() + ".");
            return true;
        }
        return false;
    }

    /**
     * Checks case two: Is the value object empty or has length 0?
     *
     * @param obj An Object with a map of key-value pairs that we're checking the above case with.
     * @param key
     * @param value
     * @return True if value is empty/has length 0, else false.
     */
    private static boolean isCaseTwoValid(Object obj, String key, Object value) {
        boolean invalidCaseTwo = (value.toString().length() == 0);

        if (invalidCaseTwo) {
            Log.w(TAG, "Value was empty for object " + obj.toString() + ".");
            return true;
        }
        return false;
    }

    /**
     * Checks case three: If key has name "type", does its value have value "point_of_interest"?
     *
     * @param obj An Object with a map of key-value pairs that we're checking the above case with.
     * @param key
     * @param value
     * @return True if key is "type" but value is not "point_of_interest".
     */
    private static boolean isCaseThreeValid(Object obj, String key, Object value) {
        String type = value.toString();
        boolean invalidCaseThree = (key.equals("type") && !type.equals("point_of_interest"));

        if (invalidCaseThree) {
            Log.w(TAG, "The object " + obj.toString() + " is a POI but its type was not point_of_interest.");
            return true;
        }
        return false;
    }

    /**
     * Checks case four: If key has name "uuid", is its value a valid UUID?
     *
     * @param obj An Object with a map of key-value pairs that we're checking the above case with.
     * @param key
     * @param value
     * @return True if key is "uuid" but its value is NOT a valid UUID.
     */
    private static boolean isCaseFourValid(Object obj, String key, Object value) {
        String uuid = value.toString();
        boolean invalidCaseFour = (key.equals("uuid") && !isValidUuid(uuid));

        if (invalidCaseFour) {
            Log.w(TAG, "The object " + obj.toString() + " has a UUID but the UUID value is invalid.");
            return true;
        }
        return false;
    }

    /**
     * Checks if a String uuid is a valid UUID object using RegEx.
     *
     * Reference: https://stackoverflow.com/a/18399081
     */
    private static boolean isValidUuid(String uuid) {
        if(!Pattern.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}", uuid)) {
            return false;
        }
        return true;
    }
}
