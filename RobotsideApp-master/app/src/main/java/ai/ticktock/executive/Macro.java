package ai.cellbots.executive;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Create a macro
 */
public class Macro {
    private static final String TAG = "Executive.Macro";
    private final String mName;
    private final String mVersion;
    private final Map<String, Types.VariableType> mVariableTypes;
    private final Set<String> mParameters;
    private final boolean mQuery;
    private final boolean mUserVisible;

    /**
     * Return the map for cloud upload
     *
     * @return A map of the parameters
     */
    public Map<String, Object> getMaps() {
        Map<String, Object> map = new HashMap<>();
        Map<String, Object> parameters = new HashMap<>();

        for (String param : mParameters) {
            parameters.put(param, mVariableTypes.get(param).toString());
        }

        map.put("name", mName);
        map.put("version", mVersion);
        map.put("parameters", parameters);
        map.put("userVisible", mUserVisible);
        return map;
    }

    /**
     * Get the types of the parameters.
     *
     * @return The value of the parameters.
     */
    Map<String, Types.VariableType> getParameters() {
        HashMap<String, Types.VariableType> m = new HashMap<>();
        for (String param : mParameters) {
            m.put(param, mVariableTypes.get(param));
        }
        return m;
    }

    /**
     * Create a macro.
     *
     * @param name          The name of the macro.
     * @param userVisible   Available to users of the web app.
     * @param version       The version of the macro.
     * @param variableTypes The variable types.
     * @param parameters    The variables that are parameters.
     * @param query         The goal should be queried before execution starts.
     */
    @SuppressWarnings("SameParameterValue")
    public Macro(String name, boolean userVisible, String version,
            Map<String, Types.VariableType> variableTypes, Set<String> parameters, boolean query) {
        mName = name;
        mVersion = version;
        mVariableTypes = new HashMap<>(variableTypes);
        mParameters = new HashSet<>(parameters);
        mQuery = query;
        mUserVisible = userVisible;
    }


    /**
     * Create a macro.
     *
     * @param name          The name of the macro.
     * @param version       The version of the macro.
     * @param variableTypes The variable types.
     * @param parameters    The variables that are parameters.
     */
    @SuppressWarnings("SameParameterValue")
    public Macro(String name, boolean userVisible, String version,
            Map<String, Types.VariableType> variableTypes, Set<String> parameters) {
        this(name, userVisible, version, variableTypes, parameters, false);
    }

    public String getName() {
        return mName;
    }

    public String getVersion() {
        return mVersion;
    }

    public boolean isQuery() {
        return mQuery;
    }

    @SuppressWarnings({"SameReturnValue", "UnusedParameters"})
    public boolean goalValid(MacroGoal goal) {
        // TODO
        return true;
    }

}
