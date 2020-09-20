package ai.cellbots.robot.executive;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ServerValue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import ai.cellbots.common.cloud.CloudLog;
import ai.cellbots.common.cloud.CloudPath;

/**
 * Goal for the executive planner.
 */
public class Goal implements Comparable<Goal> {
    final static private String TAG = Goal.class.getSimpleName();

    private final String mUuid;
    private final String mType;
    private final String mVersion;
    private final long mTimestamp;
    private final long mSequence;
    private final String mRobotUuid;
    private final String mUserUuid;
    private final Map<String, Object> mParams;
    // TODO(playerone) Define level of priorities.
    private final long mPriority;
    // True if this goal is for local, not to be written in the cloud.
    private final boolean mIsLocal;

    /**
     * Check if equal.
     *
     * @param o The equal check object.
     * @return True if they are equal.
     */
    @Override
    public boolean equals(Object o) {
        return (o instanceof Goal) && ((Goal) o).getUuid().equals(getUuid());
    }

    /**
     * Get the value of the hash code.
     *
     * @return The hash code value.
     */
    @Override
    public int hashCode() {
        return mUuid.hashCode();
    }

    /**
     * Create the goal from Firebase.
     *
     * @param firebase The firebase object.
     * @param uuid The uuid.
     * @param type The type.
     * @param userUuid The user uuid.
     * @param robotUuid The robot uuid.
     */
    @SuppressWarnings({"unchecked", "WeakerAccess"})
    public Goal(Map<String, Object> firebase, String uuid, GoalType type,
            String userUuid, String robotUuid) {
        if ((!firebase.containsKey("name") || firebase.get("name") == null)
                && (!firebase.containsKey("type") || firebase.get("type") == null)) {
            throw new IllegalArgumentException("No name/type field for goal");
        }
        if (!firebase.containsKey("version") || firebase.get("version") == null) {
            throw new IllegalArgumentException("No version field for goal");
        }
        if (!firebase.containsKey("timestamp") || firebase.get("timestamp") == null) {
            throw new IllegalArgumentException("No timestamp field for goal");
        }
        mUuid = uuid;
        mType = firebase.containsKey("type")
                ? (String) firebase.get("type") : (String) firebase.get("name");
        mVersion = (String) firebase.get("version");
        mTimestamp = (long) Types.fromFirebase(new Types.VariableTypeLong(), firebase.get("timestamp"));
        mSequence = firebase.containsKey("sequence") ?
                (long) Types.fromFirebase(new Types.VariableTypeLong(), firebase.get("sequence")) : 0L;
        mPriority = firebase.containsKey("priority") ?
                (long) Types.fromFirebase(new Types.VariableTypeLong(), firebase.get("priority")) : 0L;
        Map<String, Object> nParams = new HashMap<>();
        Map<String, Object> s = firebase.containsKey("parameters")
                ? (Map<String, Object>) firebase.get("parameters") : null;
        if (s != null) {
            for (Map.Entry<String, Types.VariableType> p : type.getParameters().entrySet()) {
                if (!s.containsKey(p.getKey())) {
                    throw new IllegalArgumentException("Missing parameter: " + p);
                }
                nParams.put(p.getKey(), Types.fromFirebase(p.getValue(), s.get(p.getKey())));
            }
            for (String param : s.keySet()) {
                if (!type.getParameters().containsKey(param)) {
                    throw new IllegalArgumentException("Invalid parameter: " + param);
                }
            }
        }
        mParams = Collections.unmodifiableMap(nParams);
        mUserUuid = userUuid;
        mRobotUuid = robotUuid;
        mIsLocal = false;
    }

    /**
     * Create the goal from parameters with a new random UUID.
     *
     * @param type The type.
     * @param version The version of the goal.
     * @param params The parameters of the goal.
     * @param userUuid The user uuid.
     * @param robotUuid The robot uuid.
     * @param timestamp The goal start timestamp. If less than 1, the server is used.
     * @param sequence The goal sequence number.
     * @param priority The goal priority.
     */
    @SuppressWarnings("WeakerAccess")
    public Goal(@SuppressWarnings("SameParameterValue") String type, @SuppressWarnings
            ("SameParameterValue") String version, Map<String, Object> params,
            String userUuid, String robotUuid, @SuppressWarnings("SameParameterValue") long timestamp, long sequence, long priority) {
        mUuid = UUID.randomUUID().toString();
        mType = type;
        mVersion = version;
        mParams = Collections.unmodifiableMap(new HashMap<>(params));
        mTimestamp = timestamp;
        mSequence = sequence;
        mPriority = priority;
        mUserUuid = userUuid;
        mRobotUuid = robotUuid;
        mIsLocal = false;
    }

    /**
     * Create the goal from parameters with a fixed UUID. Goals with this type are local and are
     * not written to the database ever.
     *
     * @param uuid The uuid.
     * @param type The type.
     * @param version The version of the goal.
     * @param params The parameters of the goal.
     * @param userUuid The user uuid.
     * @param robotUuid The robot uuid.
     * @param timestamp The goal start timestamp. If less than 1, the server is used.
     * @param sequence The goal sequence number.
     * @param priority The goal priority.
     */
    @SuppressWarnings("WeakerAccess")
    public Goal(String uuid, String type, @SuppressWarnings("SameParameterValue") String version, Map<String, Object> params,
            String userUuid, String robotUuid, @SuppressWarnings("SameParameterValue") long timestamp, long sequence, long priority) {
        mUuid = uuid;
        mType = type;
        mVersion = version;
        mParams = Collections.unmodifiableMap(new HashMap<>(params));
        mTimestamp = timestamp;
        mSequence = sequence;
        mPriority = priority;
        mUserUuid = userUuid;
        mRobotUuid = robotUuid;
        mIsLocal = true;
    }

    /**
     * Get the goal uuid.
     * @return The goal uuid.
     */
    @SuppressWarnings("unused")
    public String getUuid() {
        return mUuid;
    }

    /**
     * Get the goal type.
     * @return The goal type.
     */
    public String getType() {
        return mType;
    }

    /**
     * Get the goal version.
     * @return The goal version.
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public String getVersion() {
        return mVersion;
    }

    /**
     * Get the parameters of the goal.
     * @return The parameters.
     */
    public Map<String, Object> getParameters() {
        return Collections.unmodifiableMap(mParams);
    }

    /**
     * Get the timestamp of the goal.
     * @return The firebase timestamp.
     */
    @SuppressWarnings("WeakerAccess")
    public long getTimestamp() {
        return mTimestamp;
    }

    /**
     * Get the user uuid for the goal.
     * @return The user uuid.
     */
    public String getUserUuid() {
        return mUserUuid;
    }

    /**
     * Get the robot uuid for the goal.
     * @return The robot uuid.
     */
    public String getRobotUuid() {
        return mRobotUuid;
    }

    /**
     * Get the priority of the goal. Higher is executed faster.
     * @return The goal priority.
     */
    public long getPriority() { return mPriority; }

    /**
     * Get if the goal is local and should not be written to the server.
     * @return True if the goal is local.
     */
    public boolean isLocal() { return mIsLocal; }

    /**
     * Get the sequence number of the goal, used to determine order when goals are created at the
     * same exact time.
     * @return The sequence number of the goal.
     */
    @SuppressWarnings("WeakerAccess")
    public long getSequence() { return mSequence; }

    /**
     * Convert to string.
     * @return The string representation.
     */
    public String toString() {
        StringBuilder toStr = new StringBuilder(mType);
        toStr.append(':');
        toStr.append(mVersion);
        toStr.append('(');
        boolean isFirst = true;
        for (Map.Entry<String, Object> param : mParams.entrySet()) {
            if (!isFirst) {
                toStr.append(',');
            }
            isFirst = false;
            toStr.append(param.getKey());
            toStr.append(':');
            toStr.append(param.getValue());
        }
        toStr.append(')');

        return toStr.toString();
    }

    /**
     * Convert to a map of goal properties, which include name, version, version, timestamp,
     * priority, and other parameters.
     *
     * @return The map representation.
     */
    public Map<String, Object> toMap() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("name", getType());
        map.put("version", getVersion());
        if (getTimestamp() < 1.0) {
            map.put("timestamp", ServerValue.TIMESTAMP);
        } else {
            map.put("timestamp", getTimestamp());
        }
        map.put("priority", getPriority());
        HashMap<String, Object> params = new HashMap<>();
        for (Map.Entry<String, Object> param : mParams.entrySet()) {
            params.put(param.getKey(), Types.toFirebase(param.getValue()));
        }
        map.put("parameters", params);
        return map;
    }

    /**
     * Creates a goal from cloud data.
     *
     * @param parent The parent context.
     * @param dataSnapshot The data snapshot.
     * @param goalTypes The goal types.
     * @param userUuid The user uuid.
     * @param robotUuid The robot uuid.
     * @return The goal or null if invalid.
     */
    static Goal fromFirebase(Context parent, DataSnapshot dataSnapshot,
            Map<String, GoalType> goalTypes, String userUuid, String robotUuid) {
        if (dataSnapshot == null) {
            CloudLog.reportFirebaseFormattingError(parent,
                    CloudPath.ROBOT_GOALS_PATH.getDatabaseReference(userUuid, robotUuid),
                    "Null data snapshot");
            return null;
        }
        if (dataSnapshot.getKey() == null) {
            CloudLog.reportFirebaseFormattingError(parent,
                    CloudPath.ROBOT_GOALS_PATH.getDatabaseReference(userUuid, robotUuid),
                    "Null data snapshot key");
            return null;
        }
        Object value = dataSnapshot.getValue();
        if (value == null || !(value instanceof Map)) {
            CloudLog.reportFirebaseFormattingError(parent, dataSnapshot,
                    "Goal must be a map but is: " + value);
            return null;
        }
        if (!dataSnapshot.hasChild("type") && !dataSnapshot.hasChild("name")) {
            CloudLog.reportFirebaseFormattingError(parent, dataSnapshot,
                    "Goal must have type or name");
            return null;
        }
        if (!dataSnapshot.hasChild("version")) {
            CloudLog.reportFirebaseFormattingError(parent, dataSnapshot,
                    "Goal must have version");
            return null;
        }
        Object typeName = dataSnapshot.hasChild("type")
                ? dataSnapshot.child("type").getValue()
                : dataSnapshot.child("name").getValue();
        if (typeName == null) {
            CloudLog.reportFirebaseFormattingError(parent, dataSnapshot,
                    "Goal must have type or name be non-null");
            return null;
        }
        if (!goalTypes.containsKey(typeName.toString())) {
            return null;
        }
        Object version = dataSnapshot.child("version").getValue();
        if (version == null) {
            CloudLog.reportFirebaseFormattingError(parent, dataSnapshot,
                    "Goal must have version non-null");
            return null;
        }
        GoalType gt = goalTypes.get(typeName.toString());
        if (!version.equals(gt.getVersion())) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked") Goal g = new Goal(
                    (Map<String, Object>) value, dataSnapshot.getKey(), gt,
                    userUuid, robotUuid);
            if (!gt.goalValid(g)) {
                return null;
            }
            return g;
        } catch (IllegalArgumentException ex) {
            CloudLog.reportFirebaseFormattingError(parent, dataSnapshot,
                    "Format error: " + ex);
            Log.w(TAG, "Invalid firebase format:", ex);
        }
        return null;
    }

    /**
     * Sorts the list so that high priority goals go first, followed by oldest goals, followed by
     * lowest sequence number. The robot should execute the first goal in the list that has an
     * acceptable query() result.
     *
     * @param goal The comparison goal.
     * @return The compare result.
     */
    @Override
    public int compareTo(@NonNull Goal goal) {
        if (Long.compare(getPriority(), goal.getPriority()) == 0) {
            if (Long.compare(getTimestamp(), goal.getTimestamp()) == 0) {
                return Long.compare(getSequence(), goal.getSequence());
            }
            return Long.compare(getTimestamp(), goal.getTimestamp());
        }
        return -Long.compare(getPriority(), goal.getPriority());
    }
}

