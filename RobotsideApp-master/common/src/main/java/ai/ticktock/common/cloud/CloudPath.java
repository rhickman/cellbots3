package ai.cellbots.common.cloud;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Manage paths in the cloud storage system. These paths allow the access with certain set
 * variables, namely the robot uuid, user uuid, and entity uuid and serve as a method to pass
 * firebase paths throughout the system.
 */
public class CloudPath {
    private static final String TAG = CloudPath.class.getSimpleName();

    // The path to the robot's goals for the executive planner
    public static final CloudPath ROBOT_GOALS_PATH
            = new CloudPath(new Component[] {new StaticComponent("robot_goals"),
            new UserComponent(), new RobotComponent(), new StaticComponent("goals")});
    public static final CloudPath ROBOT_GOALS_PATH_ENTITY
            = new CloudPath(ROBOT_GOALS_PATH, new EntityComponent());
    // The path to the robot's old goals for the executive planner
    public static final CloudPath ROBOT_OLD_GOALS_PATH
            = new CloudPath(new Component[] {new StaticComponent("robot_goals"),
            new UserComponent(), new RobotComponent(), new StaticComponent("old_goals")});
    public static final CloudPath ROBOT_OLD_GOALS_PATH_ENTITY
            = new CloudPath(ROBOT_OLD_GOALS_PATH, new EntityComponent());
    // The path to the robot's goal types for the executive planner
    public static final CloudPath ROBOT_GOAL_TYPES_PATH
            = new CloudPath(new Component[] {new StaticComponent("robot_goals"),
            new UserComponent(), new RobotComponent(), new StaticComponent("goal_types")});
    // The path to the robot's metadata object, which sets the robot's name from the companion app
    public static final CloudPath ROBOT_METADATA_PATH
            = new CloudPath(new Component[] {new StaticComponent("robot_goals"),
            new UserComponent(), new RobotComponent(), new StaticComponent("metadata")});
    // The path to the robot's teleop object (data.Teleop) which stores the last teleop values
    public static final CloudPath ROBOT_TELEOP_PATH
            = new CloudPath(new Component[] {new StaticComponent("robot_goals"),
            new UserComponent(), new RobotComponent(), new StaticComponent("teleop")});
    // The path to the robot's navigation state (data.NavigationStateCommand) which stores which
    // map the robot should attempt to navigate upon or if the robot should start mapping
    public static final CloudPath ROBOT_NAVIGATION_STATE_PATH
            = new CloudPath(new Component[] {new StaticComponent("robot_goals"),
            new UserComponent(), new RobotComponent(), new StaticComponent("navigation_state")});
    // The path to the robot's speed state (data.SpeedStateCommand) which stores the speed at which
    // the robot should be moving (currently stopped or started)
    public static final CloudPath ROBOT_SPEED_STATE_PATH
            = new CloudPath(new Component[] {new StaticComponent("robot_goals"),
            new UserComponent(), new RobotComponent(), new StaticComponent("speed_state")});
    // The path to the robot's executive state (data.ExecutiveStateCommand) which stores which mode
    // the robot's executive planner should be operating in
    public static final CloudPath ROBOT_EXECUTIVE_STATE_PATH
            = new CloudPath(new Component[] {new StaticComponent("robot_goals"),
            new UserComponent(), new RobotComponent(), new StaticComponent("executive_state")});
    // The path that stores a dictionary for each robot of mapping run id -> map name. If a mapping
    // run appears here, then the robot should attempt to save that map as the map name value.
    public static final CloudPath ROBOT_SAVE_MAP_PATH
            = new CloudPath(new Component[] {new StaticComponent("robot_goals"),
            new UserComponent(), new RobotComponent(), new StaticComponent("save_map")});
    // Path stores the next animation queued up in for the robot animation board
    public static final CloudPath ROBOT_ANIMATION_QUEUE_PATH = new CloudPath(
            new Component[]{new StaticComponent("robot_goals"), new UserComponent(),
                    new RobotComponent(), new StaticComponent("animations")});
    // Path stores the next sound queued up in for the robot sound board
    public static final CloudPath ROBOT_SOUND_QUEUE_PATH = new CloudPath(
            new Component[]{new StaticComponent("robot_goals"), new UserComponent(),
                    new RobotComponent(), new StaticComponent("sound")});

    // Paths for storing the sounds in the local path sounds in database and storage
    public static final CloudPath LOCAL_SOUNDS_PATH
            = new CloudPath(new Component[]{new StaticComponent("sounds"), new UserComponent()});
    public static final CloudPath LOCAL_SOUNDS_STORAGE_PATH
            = new CloudPath(LOCAL_SOUNDS_PATH, new EntityComponent());

    // Paths for storing the global sounds in the user and database paths
    public static final CloudPath GLOBAL_SOUNDS_PATH
            = new CloudPath(new Component[]{new StaticComponent("global_sounds")});
    public static final CloudPath GLOBAL_SOUNDS_STORAGE_PATH
            = new CloudPath(GLOBAL_SOUNDS_PATH, new EntityComponent());

    // Path for the local animation files
    public static final CloudPath LOCAL_ANIMATIONS_PATH = new CloudPath(
            new Component[]{new StaticComponent("animations"), new UserComponent()});
    public static final CloudPath LOCAL_ANIMATIONS_STORAGE_PATH
            = new CloudPath(LOCAL_ANIMATIONS_PATH, new CloudPath.EntityComponent());

    // Path for the global animation files
    public static final CloudPath GLOBAL_ANIMATIONS_PATH = new CloudPath(
            new Component[]{new StaticComponent("global_animations")});
    public static final CloudPath GLOBAL_ANIMATIONS_STORAGE_PATH
            = new CloudPath(GLOBAL_ANIMATIONS_PATH, new CloudPath.EntityComponent());

    // Path for the objects within a map. Entity = Map Uuid.
    public static final CloudPath MAP_OBJECTS_PATH = new CloudPath(new Component[]{
            new StaticComponent("objects"), new UserComponent(), new EntityComponent()});

    private static final String COMPONENT_TAG_STATIC = "STATIC";
    private static final String COMPONENT_TAG_ROBOT = "ROBOT";
    private static final String COMPONENT_TAG_USER = "USER";
    private static final String COMPONENT_TAG_ENTITY = "ENTITY";

    /**
     * A component of a path.
     */
    public abstract static class Component {
        /**
         * Gets the tag of this class.
         * @return The tag, e.g. ROBOT, FIXED, or USER.
         */
        @SuppressWarnings("unused")
        public abstract String getTag();

        /**
         * Converts this class to string.
         * @return The string representation.
         */
        @Override
        public abstract String toString();

        /**
         * Converts the class to JSON.
         * @return The JSON object, or null if conversion failed.
         */
        public abstract JSONObject toJSONObject();

        /**
         * Equals checker.
         * @param other The object to check.
         * @return True if the objects are equal.
         */
        @Override
        public abstract boolean equals(Object other);

        /**
         * Compute the hash code of this object.
         * @return The hash code.
         */
        @Override
        public abstract int hashCode();
    }

    /**
     * A fixed string component.
     */
    public static final class StaticComponent extends Component {
        private final String mValue;

        /**
         * The value.
         * @param value The value of this path element.
         */
        public StaticComponent(@NonNull String value) {
            Objects.requireNonNull(value);
            mValue = value;
        }

        /**
         * Create an element from JSON.
         * @param jsonObject The JSONObject.
         * @throws JSONException If the object is invalid.
         */
        private StaticComponent(@NonNull JSONObject jsonObject) throws JSONException {
            Objects.requireNonNull(jsonObject);
            if (!getTag().equals(jsonObject.getString("type"))) {
                throw new JSONException("Invalid element for type: " + getTag()
                        + ": " + jsonObject.getString("type"));
            }
            mValue = jsonObject.getString("value");
        }

        /**
         * Get the value of the static component.
         * @return The static path element.
         */
        public String getValue() {
            return mValue;
        }

        /**
         * Gets the tag of this class.
         * @return The tag, FIXED.
         */
        @Override
        public String getTag() {
            return COMPONENT_TAG_STATIC;
        }

        /**
         * Converts this class to string.
         * @return The string representation.
         */
        @Override
        public String toString() {
            return getTag() + "(" + mValue + ")";
        }

        /**
         * Converts the class to JSON.
         * @return The JSON object, or null if conversion failed.
         */
        @Override
        public JSONObject toJSONObject() {
            try {
                JSONObject out = new JSONObject();
                out.put("type", getTag());
                out.put("value", mValue);
                return out;
            } catch (JSONException e) {
                Log.e(TAG, "Could not convert component to json", e);
            }
            return null;
        }

        /**
         * Equals checker.
         * @param other The object to check.
         * @return True if the objects are equal.
         */
        @Override
        public boolean equals(Object other) {
            return (other instanceof StaticComponent)
                    && ((StaticComponent) other).getValue().equals(getValue());
        }

        /**
         * Compute the hash code of this object.
         * @return The hash code.
         */
        @Override
        public int hashCode() {
            return (getTag() + getValue()).hashCode();
        }
    }

    /**
     * The user uuid as a component.
     */
    public static final class UserComponent extends Component {
        /**
         * Create the element.
         */
        public UserComponent() {
        }

        /**
         * Create an element from JSON.
         * @param jsonObject The JSONObject.
         * @throws JSONException If the object is invalid.
         */
        private UserComponent(@NonNull JSONObject jsonObject) throws JSONException {
            Objects.requireNonNull(jsonObject);
            if (!getTag().equals(jsonObject.getString("type"))) {
                throw new JSONException("Invalid element for type: " + getTag()
                        + ": " + jsonObject.getString("type"));
            }
        }

        /**
         * Gets the tag of this class.
         * @return The tag, USER.
         */
        @Override
        public String getTag() {
            return COMPONENT_TAG_USER;
        }

        /**
         * Converts this class to string.
         * @return The string representation.
         */
        @Override
        public String toString() {
            return getTag() + "()";
        }

        /**
         * Converts the class to JSON.
         * @return The JSON object, or null if conversion failed.
         */
        @Override
        public JSONObject toJSONObject() {
            try {
                JSONObject out = new JSONObject();
                out.put("type", getTag());
                return out;
            } catch (JSONException e) {
                Log.e(TAG, "Could not convert component to json", e);
            }
            return null;
        }

        /**
         * Equals checker.
         * @param other The object to check.
         * @return True if the objects are equal.
         */
        @Override
        public boolean equals(Object other) {
            return (other instanceof UserComponent);
        }

        /**
         * Compute the hash code of this object.
         * @return The hash code.
         */
        @Override
        public int hashCode() {
            return getTag().hashCode();
        }
    }

    /**
     * The robot uuid as a component.
     */
    public static final class RobotComponent extends Component {
        /**
         * Create the element.
         */
        public RobotComponent() {
        }

        /**
         * Create an element from JSON.
         * @param jsonObject The JSONObject.
         * @throws JSONException If the object is invalid.
         */
        private RobotComponent(@NonNull JSONObject jsonObject) throws JSONException {
            Objects.requireNonNull(jsonObject);
            if (!getTag().equals(jsonObject.getString("type"))) {
                throw new JSONException("Invalid element for type: " + getTag()
                        + ": " + jsonObject.getString("type"));
            }
        }

        /**
         * Gets the tag of this class.
         * @return The tag, ROBOT.
         */
        @Override
        public String getTag() {
            return COMPONENT_TAG_ROBOT;
        }

        /**
         * Converts this class to string.
         * @return The string representation.
         */
        @Override
        public String toString() {
            return getTag() + "()";
        }

        /**
         * Converts the class to JSON.
         * @return The JSON object, or null if conversion failed.
         */
        @Override
        public JSONObject toJSONObject() {
            try {
                JSONObject out = new JSONObject();
                out.put("type", getTag());
                return out;
            } catch (JSONException e) {
                Log.e(TAG, "Could not convert component to json", e);
            }
            return null;
        }

        /**
         * Equals checker.
         * @param other The object to check.
         * @return True if the objects are equal.
         */
        @Override
        public boolean equals(Object other) {
            return (other instanceof RobotComponent);
        }

        /**
         * Compute the hash code of this object.
         * @return The hash code.
         */
        @Override
        public int hashCode() {
            return getTag().hashCode();
        }
    }

    /**
     * The entity name as a component.
     */
    public static final class EntityComponent extends Component {
        /**
         * Create the element.
         */
        public EntityComponent() {
        }

        /**
         * Create an element from JSON.
         * @param jsonObject The JSONObject.
         * @throws JSONException If the object is invalid.
         */
        private EntityComponent(@NonNull JSONObject jsonObject) throws JSONException {
            Objects.requireNonNull(jsonObject);
            if (!getTag().equals(jsonObject.getString("type"))) {
                throw new JSONException("Invalid element for type: " + getTag()
                        + ": " + jsonObject.getString("type"));
            }
        }

        /**
         * Gets the tag of this class.
         * @return The tag, ROBOT.
         */
        @Override
        public String getTag() {
            return COMPONENT_TAG_ENTITY;
        }

        /**
         * Converts this class to string.
         * @return The string representation.
         */
        @Override
        public String toString() {
            return getTag() + "()";
        }

        /**
         * Converts the class to JSON.
         * @return The JSON object, or null if conversion failed.
         */
        @Override
        public JSONObject toJSONObject() {
            try {
                JSONObject out = new JSONObject();
                out.put("type", getTag());
                return out;
            } catch (JSONException e) {
                Log.e(TAG, "Could not convert component to json", e);
            }
            return null;
        }

        /**
         * Equals checker.
         * @param other The object to check.
         * @return True if the objects are equal.
         */
        @Override
        public boolean equals(Object other) {
            return (other instanceof EntityComponent);
        }

        /**
         * Compute the hash code of this object.
         * @return The hash code.
         */
        @Override
        public int hashCode() {
            return getTag().hashCode();
        }
    }

    private final List<Component> mComponents;
    private final boolean mHasUser;
    private final boolean mHasRobot;
    private final boolean mHasEntity;

    /**
     * Perform equals checking.
     * @param other The object to test for equals.
     * @return True if the same path.
     */
    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other instanceof CloudPath) {
            CloudPath otherPath = (CloudPath)other;
            return otherPath.getComponents().equals(mComponents);
        }
        return false;
    }

    /**
     * Computes the hash code of this object.
     *
     * @return The hash code.
     */
    @Override
    public int hashCode() {
        return mComponents.hashCode();
    }

    /**
     * Gets the components of a path.
     *
     * @return a list of path components.
     */
    public List<Component> getComponents() {
        return Collections.unmodifiableList(mComponents);
    }

    /**
     * Creates a path.
     *
     * @param components The array of components.
     */
    public CloudPath(Component[] components) {
        this(toList(components));
    }

    /**
     * Creates a path.
     *
     * @param path The path.
     * @param components The array of components.
     */
    public CloudPath(CloudPath path, Component[] components) {
        this(sumLists(path.getComponents(), toList(components)));
    }

    /**
     * Creates a path.
     *
     * @param path The path.
     * @param components The array of components.
     */
    public CloudPath(CloudPath path, List<Component> components) {
        this(sumLists(path.getComponents(), components));
    }

    /**
     * Creates a path.
     *
     * @param path The path.
     * @param component The next component.
     */
    public CloudPath(CloudPath path, Component component) {
        this(sumLists(path.getComponents(), toList(new Component[]{component})));
    }

    /**
     * Sums up two lists.
     * @param l1 The first list.
     * @param l2 The second list.
     * @return The sum of the lists.
     */
    static private List<Component> sumLists(List<Component> l1, List<Component> l2) {
        ArrayList<Component> sum = new ArrayList<>(l1);
        sum.addAll(l2);
        return sum;
    }

    /**
     * Creates a path.
     *
     * @param components The iterable of of components.
     */
    public CloudPath(Collection<Component> components) {
        mComponents = Collections.unmodifiableList(new ArrayList<>(components));
        boolean hasUser = false;
        boolean hasRobot = false;
        boolean hasEntity = false;
        for (Component c : mComponents) {
            if (c instanceof UserComponent) {
                hasUser = true;
            }
            if (c instanceof RobotComponent) {
                hasRobot = true;
            }
            if (c instanceof EntityComponent) {
                hasEntity = true;
            }
        }
        mHasUser = hasUser;
        mHasRobot = hasRobot;
        mHasEntity = hasEntity;
    }

    /**
     * Creates a path.
     *
     * @param jsonArray The JSONArray to use for components.
     */
    public CloudPath(JSONArray jsonArray) throws JSONException {
        this(JSONArrayToList(jsonArray));
    }

    /**
     * Converts a JSONArray to a list of path components.
     *
     * @param jsonArray The json array input.
     * @return The list of path components.
     * @throws JSONException The exception to be thrown by the JSON.
     */
    private static List<CloudPath.Component> JSONArrayToList(JSONArray jsonArray) throws JSONException {
        ArrayList<Component> listPath = new ArrayList<>(jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject object = jsonArray.getJSONObject(i);
            String type = object.getString("type");
            if (type == null) {
                throw new JSONException("Type is null for component");
            }
            switch (type) {
                case COMPONENT_TAG_USER:
                    listPath.add(new UserComponent(object));
                    break;
                case COMPONENT_TAG_ROBOT:
                    listPath.add(new RobotComponent(object));
                    break;
                case COMPONENT_TAG_ENTITY:
                    listPath.add(new EntityComponent(object));
                    break;
                case COMPONENT_TAG_STATIC:
                    listPath.add(new StaticComponent(object));
                    break;
                default:
                    throw new JSONException("Invalid type for component: " + type);
            }
        }
        return listPath;
    }

    /**
     * Converts the path component to the list.
     *
     * @param path The path to be converted.
     * @return The path as a list.
     */
    private static List<CloudPath.Component> toList(Component[] path) {
        ArrayList<Component> listPath = new ArrayList<>(path.length);
        for (Component c : path) {
            if (!(c instanceof UserComponent)
                    && !(c instanceof RobotComponent)
                    && !(c instanceof StaticComponent)
                    && !(c instanceof EntityComponent)) {
                throw new Error("Invalid path component: " + c);
            }
        }
        Collections.addAll(listPath, path);
        return Collections.unmodifiableList(listPath);
    }

    /**
     * Gets if a path includes a reference to the robot.
     * @return True if the robot is included.
     */
    public boolean hasRobotPath() {
        return mHasRobot;
    }

    /**
     * Gets if a path includes a reference to the user.
     * @return True if the user is included.
     */
    public boolean hasUserPath() {
        return mHasUser;
    }

    /**
     * Gets if a path includes a reference to the entity.
     * @return True if the entity is included.
     */
    public boolean hasEntityPath() {
        return mHasEntity;
    }

    /**
     * Converts a path component to a string.
     *
     * @param component The path component.
     * @param userUuid The user uuid.
     * @param robotUuid The robot uuid.
     * @return The String reference.
     */
    private static String convertComponent(Component component, String userUuid, String robotUuid) {
        if (component instanceof StaticComponent) {
            return ((StaticComponent) component).getValue();
        } else if (component instanceof UserComponent) {
            return userUuid;
        } else if (component instanceof RobotComponent) {
            return robotUuid;
        } else {
            throw new Error("Invalid path component: " + component);
        }
    }

    /**
     * Gets the reference to a database key from a path.
     *
     * @param userUuid The user uuid.
     * @param robotUuid The robot uuid.
     * @return The DatabaseReference of the target.
     */
    public DatabaseReference getDatabaseReference(String userUuid, String robotUuid) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference();
        for (Component c : mComponents) {
            ref = ref.child(convertComponent(c, userUuid, robotUuid));
        }
        return ref;
    }

    /**
     * Gets the reference to a storage key from a path.
     *
     * @param userUuid The user uuid.
     * @param robotUuid The robot uuid.
     * @return The StorageReference of the target.
     */
    @SuppressWarnings("unused")
    public StorageReference getStorageReference(String userUuid, String robotUuid) {
        StorageReference ref = FirebaseStorage.getInstance().getReference();
        for (Component c : mComponents) {
            ref = ref.child(convertComponent(c, userUuid, robotUuid));
        }
        return ref;
    }

    /**
     * Converts a path component to a string.
     *
     * @param component The path component.
     * @param userUuid The user uuid.
     * @param robotUuid The robot uuid.
     * @param entity The folder entity.
     * @return The String reference.
     */
    private static String convertComponent(Component component, String userUuid, String robotUuid, String entity) {
        if (component instanceof StaticComponent) {
            return ((StaticComponent) component).getValue();
        } else if (component instanceof UserComponent) {
            return userUuid;
        } else if (component instanceof RobotComponent) {
            return robotUuid;
        } else if (component instanceof EntityComponent) {
            return entity;
        } else {
            throw new Error("Invalid path component: " + component);
        }
    }

    /**
     * Gets the reference to a database key from a path.
     *
     * @param userUuid The user uuid.
     * @param robotUuid The robot uuid.
     * @param entity The folder entity.
     * @return The DatabaseReference of the target.
     */
    public DatabaseReference getDatabaseReference(String userUuid, String robotUuid, String entity) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference();
        for (Component c : mComponents) {
            ref = ref.child(convertComponent(c, userUuid, robotUuid, entity));
        }
        return ref;
    }

    /**
     * Gets the reference to a storage key from a path.
     *
     * @param userUuid The user uuid.
     * @param robotUuid The robot uuid.
     * @param entity The folder entity.
     * @return The StorageReference of the target.
     */
    public StorageReference getStorageReference(String userUuid, String robotUuid, String entity) {
        StorageReference ref = FirebaseStorage.getInstance().getReference();
        for (Component c : mComponents) {
            ref = ref.child(convertComponent(c, userUuid, robotUuid, entity));
        }
        return ref;
    }

    /**
     * Converts the path to a JSONArray
     *
     * @return The path JSONArray or null if it could not be created.
     */
    public JSONArray toJSONArray() {
        JSONArray out = new JSONArray();
        for (Component c : mComponents) {
            JSONObject object = c.toJSONObject();
            if (object == null) {
                Log.e(TAG, "Could not convert path to JSON");
                return null;
            }
            out.put(object);
        }
        return out;
    }

    /**
     * Converts object to string.
     *
     * @return String representation.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CloudPath(");
        boolean first = true;
        for (Component c : mComponents) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(c);
        }
        sb.append(")");
        return sb.toString();
    }
}
