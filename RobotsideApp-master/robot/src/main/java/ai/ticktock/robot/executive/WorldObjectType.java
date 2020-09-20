package ai.cellbots.robot.executive;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * An object type of a world object. Currently the only supported type is POI. The world object type
 * stores the list of parameters and types for the object, as each object type an have a different
 * set of parameters and types.
 */
@SuppressWarnings("WeakerAccess")
public class WorldObjectType {
    private final String mName;
    private final Map<String, Types.VariableType> mVariables;

    /**
     * Create the hardcoded POI object type.
     * @return The hardcoded POI object.
     */
    private static WorldObjectType createPOIObjectType() {
        Map<String, Types.VariableType> params = new HashMap<>();
        params.put("location", new Types.VariableTypeTransform());
        params.put("name", new Types.VariableTypeString());
        return new WorldObjectType("point_of_interest", params);
    }

    /**
     * Create a map of hardcoded object types.
     * @return The hardcoded object types.
     */
    static Map<String, WorldObjectType> createHardcodedObjects() {
        Map<String, WorldObjectType> map = new HashMap<>();
        WorldObjectType poi = createPOIObjectType();
        map.put(poi.getName(), poi);
        return Collections.unmodifiableMap(map);
    }


    /**
     * Create an WorldObjectType()
     * @param name The name of the object.
     * @param variables The variables for the object.
     */
    public WorldObjectType(@SuppressWarnings("SameParameterValue") String name,
            Map<String, Types.VariableType> variables) {
        mName = name;
        mVariables = Collections.unmodifiableMap(new HashMap<>(variables));
    }

    /**
     * Get the name of the object.
     * @return The name of the object.
     */
    public String getName() {
        return mName;
    }

    /**
     * Get the type of a variable.
     * @param variable The name of the variable.
     * @return The type of the variable.
     */
    public Types.VariableType getVariableType(String variable) {
        return mVariables.get(variable);
    }

    /**
     * Iterate on variables.
     * @return The variables.
     */
    public Set<Map.Entry<String, Types.VariableType>> getVariableTypes() {
        return mVariables.entrySet();
    }
}