package ai.cellbots.robot.executive;

import java.util.Map;

import ai.cellbots.common.Transform;
import ai.cellbots.common.data.ExecutiveStateCommand;

/**
 * Types for the executive planner
 */
@SuppressWarnings("UtilityClassCanBeEnum")
public final class Types {

    /**
     * Returns the variable type for a given string.
     * @param type The type name.
     * @return The VariableType object or null if invalid.
     */
    @SuppressWarnings("unused")
    public static VariableType variableTypeForName(String type) {
        if (type.equals("LONG")) {
            return new VariableTypeLong();
        }
        if (type.equals("DOUBLE")) {
            return new VariableTypeDouble();
        }
        if (type.equals("STRING")) {
            return new VariableTypeString();
        }
        if (type.equals("BOOLEAN")) {
            return new VariableTypeBoolean();
        }
        if (type.equals("TRANSFORM")) {
            return new VariableTypeTransform();
        }
        if (type.equals("MAP")) {
            return new VariableTypeMap();
        }
        if (type.equals("ANIMATION")) {
            return new VariableTypeAnimation();
        }
        if (type.equals("EXECUTIVE_STATE")) {
            return new VariableTypeExecutiveState();
        }
        if (type.startsWith("OBJECT:")) {
            return new VariableTypeObject(type.substring(7));
        }
        return null;
    }

    /**
     * Types of variables in expressions.
     */
    public abstract static class VariableType {
        /**
         * Tests if an object is instance of this type.
         *
         * @param o The object to test.
         * @return True if the object is an instance of this type.
         */
        public abstract boolean isInstanceOf(Object o);

        /**
         * Converts a firebase object to a valid object.
         *
         * @param object The object to convert.
         * @return The object converted from the firebase type.
         */
        public abstract Object fromFirebase(Object object);
    }

    /**
     * Variable type for doubles.
     */
    public static final class VariableTypeDouble extends VariableType {
        /**
         * Create the double variable type.
         */
        @SuppressWarnings("WeakerAccess")
        public VariableTypeDouble() {
        }

        @Override
        public int hashCode() {
            return "DOUBLE".hashCode();
        }

        @Override
        public String toString() {
            return "DOUBLE";
        }

        @Override
        public boolean equals(Object o) {
            return o != null && o instanceof VariableTypeDouble;
        }

        @Override
        public boolean isInstanceOf(Object o) {
            return o instanceof Double;
        }

        @Override
        public Object fromFirebase(Object object) {
            if (object == null) {
                return 0.0;
            } else if (object instanceof Double) {
                return object;
            } else if (object instanceof Float) {
                try {
                    return Double.valueOf((Float) object);
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            } else {
                try {
                    return Double.valueOf(object.toString());
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            }
        }
    }

    /**
     * Variable type for longs.
     */
    public static final class VariableTypeLong extends VariableType {
        /**
         * Create the long variable type.
         */
        public VariableTypeLong() {
        }

        @Override
        public int hashCode() {
            return "LONG".hashCode();
        }

        @Override
        public String toString() {
            return "LONG";
        }

        @Override
        public boolean equals(Object o) {
            return o != null && o instanceof VariableTypeLong;
        }

        @Override
        public boolean isInstanceOf(Object o) {
            return o instanceof Long;
        }

        @Override
        public Object fromFirebase(Object object) {
            if (object == null) {
                return 0L;
            } else if (object instanceof Long) {
                return object;
            } else if (object instanceof Integer) {
                try {
                    return Long.valueOf((Integer) object);
                } catch (NumberFormatException e) {
                    return 0L;
                }
            } else {
                try {
                    return Long.valueOf(object.toString());
                } catch (NumberFormatException e) {
                    return 0L;
                }
            }
        }
    }

    /**
     * Variable type for strings.
     */
    public static final class VariableTypeString extends VariableType {
        /**
         * Create the string variable type.
         */
        public VariableTypeString() {
        }

        @Override
        public int hashCode() {
            return "STRING".hashCode();
        }

        @Override
        public String toString() {
            return "STRING";
        }

        @Override
        public boolean equals(Object o) {
            return o != null && o instanceof VariableTypeString;
        }

        @Override
        public boolean isInstanceOf(Object o) {
            return o instanceof String;
        }

        @Override
        public Object fromFirebase(Object object) {
            if (object == null) {
                return "";
            } else {
                return object.toString();
            }
        }
    }

    /**
     * Variable type for booleans.
     */
    public static final class VariableTypeBoolean extends VariableType {
        /**
         * Create the boolean variable type.
         */
        @SuppressWarnings("WeakerAccess")
        public VariableTypeBoolean() {
        }

        @Override
        public int hashCode() {
            return "BOOLEAN".hashCode();
        }

        @Override
        public String toString() {
            return "BOOLEAN";
        }

        @Override
        public boolean equals(Object o) {
            return o != null && o instanceof VariableTypeBoolean;
        }

        @Override
        public boolean isInstanceOf(Object o) {
            return o instanceof Boolean;
        }

        @Override
        public Object fromFirebase(Object object) {
            if (object == null) {
                return false;
            } else if (object instanceof Boolean) {
                return object;
            } else {
                return object.toString().toLowerCase().equals("true");
            }
        }
    }

    /**
     * Variable type for transforms.
     */
    public static final class VariableTypeTransform extends VariableType {
        /**
         * Create the transform variable type.
         */
        public VariableTypeTransform() {
        }

        @Override
        public int hashCode() {
            return "TRANSFORM".hashCode();
        }

        @Override
        public String toString() {
            return "TRANSFORM";
        }

        @Override
        public boolean equals(Object o) {
            return o != null && o instanceof VariableTypeTransform;
        }

        @Override
        public boolean isInstanceOf(Object o) {
            return o instanceof Transform;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object fromFirebase(Object object) {
            return new Transform((Map<String, Object>) object);
        }
    }

    /**
     * Variable type for world uuids.
     */
    public static final class VariableTypeMap extends VariableType {
        /**
         * Create the map variable type.
         */
        public VariableTypeMap() {
        }

        @Override
        public int hashCode() {
            return "MAP".hashCode();
        }

        @Override
        public String toString() {
            return "MAP";
        }

        @Override
        public boolean equals(Object o) {
            return o != null && o instanceof VariableTypeMap;
        }

        @Override
        public boolean isInstanceOf(Object o) {
            return o instanceof String;
        }

        @Override
        public Object fromFirebase(Object object) {
            if (object == null) {
                return "";
            } else {
                return object.toString();
            }
        }
    }

    /**
     * Variable type for animation names.
     */
    public static final class VariableTypeAnimation extends VariableType {
        /**
         * Create the animation variable type.
         */
        public VariableTypeAnimation() {
        }

        @Override
        public int hashCode() {
            return "ANIMATION".hashCode();
        }

        @Override
        public String toString() {
            return "ANIMATION";
        }

        @Override
        public boolean equals(Object o) {
            return o != null && o instanceof VariableTypeAnimation;
        }

        @Override
        public boolean isInstanceOf(Object o) {
            return o instanceof String;
        }

        @Override
        public Object fromFirebase(Object object) {
            if (object == null) {
                return "";
            } else {
                return object.toString();
            }
        }
    }

    /**
     * Variable type for executive state enum.
     */
    public static final class VariableTypeExecutiveState extends VariableType {
        /**
         * Create the executive state type.
         */
        @SuppressWarnings("WeakerAccess")
        public VariableTypeExecutiveState() {
        }

        @Override
        public int hashCode() {
            return "EXECUTIVE_STATE".hashCode();
        }

        @Override
        public String toString() {
            return "EXECUTIVE_STATE";
        }

        @Override
        public boolean equals(Object o) {
            return o != null && o instanceof VariableTypeExecutiveState;
        }

        @Override
        public boolean isInstanceOf(Object o) {
            return o instanceof ExecutiveStateCommand.ExecutiveState;
        }

        @Override
        public Object fromFirebase(Object object) {
            if (object == null) {
                return ExecutiveStateCommand.DEFAULT_EXECUTIVE_MODE;
            } else {
                try {
                    return ExecutiveStateCommand.ExecutiveState.valueOf(object.toString());
                } catch (Exception ex) {
                    return ExecutiveStateCommand.DEFAULT_EXECUTIVE_MODE;
                }
            }
        }
    }

    /**
     * Variable type for world objects.
     */
    public static final class VariableTypeObject extends VariableType {
        private final String mObjectType;
        private final String mToString;
        private final int mHashCode;

        /**
         * Create the world object variable type.
         *
         * @param objectType The world object type.
         */
        public VariableTypeObject(String objectType) {
            mObjectType = objectType;
            mToString = "OBJECT:" + mObjectType;
            mHashCode = mToString.hashCode();
        }

        @Override
        public int hashCode() {
            return mHashCode;
        }

        @Override
        public String toString() {
            return mToString;
        }

        @Override
        public boolean equals(Object o) {
            return o != null && o instanceof VariableTypeMap;
        }

        @Override
        public boolean isInstanceOf(Object o) {
            return o instanceof String;
        }

        @Override
        public Object fromFirebase(Object object) {
            if (object == null) {
                return "";
            } else {
                return object.toString();
            }
        }
    }

    /**
     * A list of keys in the world state.
     */
    public enum WorldStateKey {
        ROBOT_LOCATION, // The location of the robot.
        ROBOT_MAP, // The robot map uuid.
        ROBOT_BATTERY_LOW, // True if the battery is low.
        ROBOT_BATTERY_CRITICAL, // True if the battery is critical.
        ROBOT_NAME, // The string robot name.
        ROBOT_EXECUTIVE_STATE, // The executive mode.
    }

    /**
     * Constructor should never be called.
     */
    private Types() {
        throw new Error("Types should never be instantiated.");
    }

    /**
     * Get the type associated with a WorldStateKey value.
     *
     * @param key The WorldStateKey to start.
     * @return The VariableType associated.
     */
    @SuppressWarnings("unused")
    public static VariableType getWorldStateKeyType(WorldStateKey key) {
        if (key == null) {
            throw new Error("Key is null");
        }
        switch (key) {
            case ROBOT_LOCATION:
                return new VariableTypeTransform();
            case ROBOT_MAP:
                return new VariableTypeMap();
            case ROBOT_BATTERY_CRITICAL:
                return new VariableTypeBoolean();
            case ROBOT_BATTERY_LOW:
                return new VariableTypeBoolean();
            case ROBOT_NAME:
                return new VariableTypeString();
            case ROBOT_EXECUTIVE_STATE:
                return new VariableTypeExecutiveState();
            default:
                throw new Error("Key is not registered for type: " + key);
        }
    }

    /**
     * Ensures an object is the correct type.
     * @param type The VariableType for verification.
     * @param value The value to be tested.
     * @return True if the object value is a member of the type.
     */
    @SuppressWarnings({"BooleanMethodIsAlwaysInverted", "unused"})
    public static boolean isInstanceOf(VariableType type, Object value) {
        if (type == null) {
            throw new Error("Type is null");
        }
        return type.isInstanceOf(value);
    }

    /**
     * Creates the Java object from the firebase object.
     *
     * @param type The variable type.
     * @param value The firebase object.
     * @return The java object.
     */
    public static Object fromFirebase(VariableType type, Object value) {
        if (type == null) {
            throw new Error("Type is null");
        }
        return type.fromFirebase(value);
    }

    /**
     * Create a firebase-writable object from a Java object.
     *
     * @param value The Java object.
     * @return The firebase object.
     */
    public static Object toFirebase(Object value) {
        if (value instanceof Transform) {
            return ((Transform)value).toMap();
        }
        return value;
    }
}
