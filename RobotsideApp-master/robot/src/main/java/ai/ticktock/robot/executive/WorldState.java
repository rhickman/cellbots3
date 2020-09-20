package ai.cellbots.robot.executive;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.Transform;
import ai.cellbots.common.data.ExecutiveStateCommand;
import ai.cellbots.common.data.RobotMetadata;

/**
 * Contains the state of the world for the executive.
 */
public class WorldState {
    private final Map<Types.WorldStateKey, Object> mKeys;
    private final Map<String, WorldObject> mWorldObjects;
    private final DetailedWorld mWorld;

    /**
     * Create the world state.
     * @param world The current detailed world.
     * @param metadata The robot metadata.
     * @param location The robot location.
     * @param worldObjects The list of world objects.
     * @param batteryLow True if the battery is low.
     * @param batteryCritical True if the battery is critical.
     * @param executiveState The state of the executive planner.
     */
    public WorldState(DetailedWorld world, RobotMetadata metadata, Transform location,
            Map<String, WorldObject> worldObjects, boolean batteryLow, boolean batteryCritical,
            ExecutiveStateCommand.ExecutiveState executiveState) {
        mWorld = world;
        Map<Types.WorldStateKey, Object> m = new HashMap<>();
        m.put(Types.WorldStateKey.ROBOT_LOCATION, location);
        m.put(Types.WorldStateKey.ROBOT_MAP, world.getUuid());
        m.put(Types.WorldStateKey.ROBOT_BATTERY_LOW, batteryLow);
        m.put(Types.WorldStateKey.ROBOT_BATTERY_CRITICAL, batteryCritical);
        m.put(Types.WorldStateKey.ROBOT_EXECUTIVE_STATE, executiveState);
        m.put(Types.WorldStateKey.ROBOT_NAME,
                (metadata != null && metadata.getRobotName() != null) ? metadata.getRobotName() : "");
        mWorldObjects = Collections.unmodifiableMap(new HashMap<>(worldObjects));
        mKeys = Collections.unmodifiableMap(m);
    }

    /**
     * Get the current detailed world.
     *
     * @return The current detailed world.
     */
    public DetailedWorld getWorld() {
        return mWorld;
    }

    /**
     * Get a world object.
     *
     * @param key The world object uuid.
     * @return The world object, or null if non-existent.
     */
    public WorldObject getWorldObject(String key) {
        if (mWorldObjects.containsKey(key)) {
            return mWorldObjects.get(key);
        }
        return null;
    }

    /**
     * Get world objects.
     *
     * @return Collection of world objects. Empty if there are no world objects.
     */
    public Collection<WorldObject> getWorldObjects() {
        return mWorldObjects.values();
    }

    /**
     * Get a state key.
     *
     * @param key The state key to get.
     * @return The state key value.
     */
    public Object getState(@SuppressWarnings("SameParameterValue") Types.WorldStateKey key) {
        return mKeys.get(key);
    }

}