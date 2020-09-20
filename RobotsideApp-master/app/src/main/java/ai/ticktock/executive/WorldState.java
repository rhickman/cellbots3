package ai.cellbots.executive;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import ai.cellbots.common.Transform;
import ai.cellbots.common.data.ExecutiveStateCommand;
import ai.cellbots.common.data.RobotMetadata;

/**
 * Contains the state of the world for the executive.
 */
public class WorldState {
    private final Map<Types.WorldStateKey, Object> mKeys;
    private final Map<String, WorldObject> mWorldObjects;

    public WorldState(RobotMetadata metadata, Transform location,
            String map, double distanceTolerance,
            double angularTolerance, Map<String, WorldObject> worldObjects,
            boolean batteryLow, boolean batteryCritical,
            ExecutiveStateCommand.ExecutiveState executiveState) {
        Map<Types.WorldStateKey, Object> m = new HashMap<>();
        m.put(Types.WorldStateKey.ROBOT_LOCATION, location);
        m.put(Types.WorldStateKey.ROBOT_ANGULAR_TOLERANCE, angularTolerance);
        m.put(Types.WorldStateKey.ROBOT_DISTANCE_TOLERANCE, distanceTolerance);
        m.put(Types.WorldStateKey.ROBOT_MAP, map);
        m.put(Types.WorldStateKey.ROBOT_BATTERY_LOW, batteryLow);
        m.put(Types.WorldStateKey.ROBOT_BATTERY_CRITICAL, batteryCritical);
        m.put(Types.WorldStateKey.ROBOT_EXECUTIVE_STATE, executiveState);
        m.put(Types.WorldStateKey.ROBOT_NAME,
                (metadata != null && metadata.getRobotName() != null) ? metadata.getRobotName() : "");
        mWorldObjects = Collections.unmodifiableMap(new HashMap<>(worldObjects));
        mKeys = Collections.unmodifiableMap(m);
    }


    public WorldObject getWorldObject(String key) {
        if (mWorldObjects.containsKey(key)) {
            return mWorldObjects.get(key);
        }
        return null;
    }

    public Object getState(Types.WorldStateKey key) {
        return mKeys.get(key);
    }

}
