package ai.cellbots.executive;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * An object type.
 */
public class ObjectType {
    private final String mName;
    private final Map<String, Types.VariableType> mVariables;
    private final Map<String, Map<String, String>> mDisplay;

    /**
     * Create an WorldObjectType()
     * @param name The name of the object.
     * @param variables The variables for the object.
     * @param display The display information.
     */
    public ObjectType(String name, Map<String, Types.VariableType> variables,
            Map<String, Map<String, String>> display) {
        mName = name;
        mVariables = Collections.unmodifiableMap(new HashMap<>(variables));
        Map<String, Map<String, String>> nDisplay = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> displayItem : display.entrySet()) {
            nDisplay.put(displayItem.getKey(), Collections.unmodifiableMap(
                    new HashMap<>(displayItem.getValue())));
        }
        mDisplay = Collections.unmodifiableMap(nDisplay);
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

    /**
     * Return a map of the WorldObjectType.
     * @return Firebase maps of the WorldObjectType.
     */
    public Map<String, Object> getMaps() {
        Map<String, Object> outerMap = new HashMap<>();
        outerMap.put("name", mName);
        Map<String, Object> varMap = new HashMap<>(mVariables.size());
        for (Map.Entry<String, Types.VariableType> var : mVariables.entrySet()) {
            varMap.put(var.getKey(), var.getValue().toString());
        }
        outerMap.put("variables", Collections.unmodifiableMap(varMap));
        outerMap.put("display", Collections.unmodifiableMap(mDisplay));
        return Collections.unmodifiableMap(outerMap);
    }
}