package ai.cellbots.executive;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * An object in the world.
 */

public class WorldObject {
    private final ObjectType mType;
    private final String mUuid;
    private final String mMapUuid;
    private final Map<String, Object> mVariables;

    @SuppressWarnings("unchecked")
    public WorldObject(String mapUuid, ObjectType type, Map<String, Object> values) {
        mType = type;
        mMapUuid = mapUuid;
        mUuid = values.get("uuid").toString();
        Map<String, Object> variables = new HashMap<>();
        if (values.containsKey("variables")) {
            Map<String, Object> rawVariables = (Map<String, Object>) values.get("variables");
            for (Map.Entry<String, Object> var : rawVariables.entrySet()) {
                if (type.getVariableType(var.getKey()) != null) {
                    variables.put(var.getKey(),
                            type.getVariableType(var.getKey()).fromFirebase(var.getValue()));
                }
            }
        }
        mVariables = Collections.unmodifiableMap(variables);
        if (!validate()) {
            throw new IllegalArgumentException("Failed to validate object");
        }
    }

    private boolean validate() {
        for (Map.Entry<String, Types.VariableType> var : mType.getVariableTypes()) {
            if (!mVariables.containsKey(var.getKey())) {
                return false;
            }
        }
        return true;
    }

    public Object getValue(String variable) {
        return mVariables.containsKey(variable) ? mVariables.get(variable) : null;
    }

    public String getMapUuid() {
        return mMapUuid;
    }

    public static boolean isMapValid(Map<String, Object> values) {
        return values.containsKey("uuid")
                && values.containsKey("type");
    }

    @SuppressWarnings("unchecked")
    public static boolean isMapValid(Map<String, Object> values, ObjectType type) {
        if (!isMapValid(values)) {
            return false;
        }
        if (type == null) {
            return false;
        }
        Map<String, Object> variables = new HashMap<>();
        if (values.containsKey("variables")) {
            Object variablesObj = values.get("variables");
            if (variablesObj == null || !(variablesObj instanceof Map)) {
                return false;
            }
            variables = (Map<String, Object>) variablesObj;
        }


        for (Map.Entry<String, Types.VariableType> var : type.getVariableTypes()) {
            if (!variables.containsKey(var.getKey())) {
                return false;
            }
        }
        return true;
    }
}
