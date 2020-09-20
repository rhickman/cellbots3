package ai.cellbots.robot.executive;

import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An object in the world. Currently only for POIs.
 */
public class WorldObject {
    private final WorldObjectType mType;
    private final String mUuid;
    private final String mMapUuid;
    private final Map<String, Object> mVariables;

    /**
     * Create a new world object.
     * @param mapUuid The map uuid for the object.
     * @param uuid The uuid of the object.
     * @param type The type of the object.
     * @param values The values of the object's variables.
     */
    @SuppressWarnings({"unchecked", "WeakerAccess"})
    public WorldObject(String mapUuid, String uuid, WorldObjectType type, Map<String, Object> values) {
        mType = type;
        mMapUuid = mapUuid;
        mUuid = uuid;
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

    /**
     * Determine if the object is valid, in that it has all variables expected of its type.
     * @return True if the object is valid.
     */
    private boolean validate() {
        for (Map.Entry<String, Types.VariableType> var : mType.getVariableTypes()) {
            if (!mVariables.containsKey(var.getKey())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get a value from the object data.
     * @param variable The variable to get.
     * @return The value of the variable.
     */
    public Object getValue(@SuppressWarnings("SameParameterValue") String variable) {
        return mVariables.containsKey(variable) ? mVariables.get(variable) : null;
    }

    /**
     * Get the map uuid for this object.
     * @return The map uuid.
     */
    public String getMapUuid() {
        return mMapUuid;
    }

    /**
     * Get the uuid for this object.
     * @return The uuid.
     */
    public String getUuid() {
        return mUuid;
    }

    /**
     * Get the type for this object.
     * @return The type.
     */
    public WorldObjectType getType() {
        return mType;
    }

    /**
     * Determine if a firebase map is valid.
     * @param values The values for checking.
     * @param type The object type for checking.
     * @return True if firebase the map is valid.
     */
    @SuppressWarnings("unchecked")
    private static boolean isMapValid(Map<String, Object> values, WorldObjectType type) {
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

    /**
     * Convert from firebase.
     * @param dataSnapshot The database snapshot.
     * @param mapUuid The map uuid.
     * @param objectTypes The object types.
     * @return The resulting world object list.
     */
    static List<WorldObject> fromFirebase(DataSnapshot dataSnapshot, String mapUuid,
            Map<String, WorldObjectType> objectTypes) {
        ArrayList<WorldObject> result = new ArrayList<>();
        if (dataSnapshot != null) {
            for (DataSnapshot child : dataSnapshot.getChildren()) {
                if (!child.hasChild("type")) {
                    continue;
                }
                Object typeObject = child.child("type").getValue();
                if (typeObject == null) {
                    continue;
                }
                String type = typeObject.toString();
                if (!objectTypes.containsKey(type)) {
                    continue;
                }
                Object map = child.getValue();
                if (!(map instanceof Map)) {
                    continue;
                }
                //noinspection unchecked
                if (!WorldObject.isMapValid((Map<String, Object>) map, objectTypes.get(type))) {
                    continue;
                }
                //noinspection unchecked
                result.add(new WorldObject(mapUuid, child.getKey(), objectTypes.get(type),
                        (Map<String, Object>) map));
            }
        }
        return result;
    }
}
