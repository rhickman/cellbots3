package ai.cellbots.robot.executive;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A goal type for the executive planner.
 */
public class GoalType {
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
    public Map<String, Object> toMap() {
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
    public GoalType(String name, boolean userVisible, String version,
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
     * @param userVisible   If true, then the user should have the option to create the goal.
     */
    @SuppressWarnings("SameParameterValue")
    public GoalType(String name, boolean userVisible, String version,
            Map<String, Types.VariableType> variableTypes, Set<String> parameters) {
        this(name, userVisible, version, variableTypes, parameters, false);
    }

    /**
     * Get the name of the type.
     *
     * @return The type.
     */
    public String getName() {
        return mName;
    }

    /**
     * Get the version string of the goal type.
     *
     * @return The version string.
     */
    public String getVersion() {
        return mVersion;
    }

    /**
     * If true, then the goal should run a query before starting execution.
     *
     * @return True if the goal should query.
     */
    public boolean isQuery() {
        return mQuery;
    }

    /**
     * Determine if a goal is valid for this type.
     *
     * @param goal The goal to validate.
     * @return True if it is valid.
     */
    @SuppressWarnings({"SameReturnValue", "UnusedParameters"})
    public boolean goalValid(Goal goal) {
        // TODO implement this
        return true;
    }

}
