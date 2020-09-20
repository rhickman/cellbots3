package ai.cellbots.executive;

import android.util.Log;

import com.google.firebase.database.ServerValue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Goals for the system
 */
public class MacroGoal {
    final static private String TAG = MacroGoal.class.getSimpleName();

    private final String mUuid;
    private final String mMacro;
    private final String mVersion;
    private final Double mTimestamp;
    private final long mSequence;
    private final String mRobotUuid;
    private final String mUserUuid;
    private final Map<String, Object> mParams;
    private final long mPriority;
    private final boolean mLocal;

    @Override
    public boolean equals(Object o) {
        return (o instanceof MacroGoal) && ((MacroGoal) o).getUuid().equals(getUuid());
    }

    @Override
    public int hashCode() {
        return mUuid.hashCode();
    }


    @SuppressWarnings("unchecked")
    public MacroGoal(Map<String, Object> firebase, Macro macro,
            String userUuid, String robotUuid) {
        mUuid = (String) firebase.get("uuid");
        mMacro = (String) firebase.get("name");
        mVersion = (String) firebase.get("version");
        mTimestamp = (double) Types.fromFirebase(new Types.VariableTypeDouble(),
                firebase.get("timestamp"));
        mSequence = firebase.containsKey("sequence") ?
                (long) Types.fromFirebase(new Types.VariableTypeLong(), firebase.get("sequence")) : 0L;
        mPriority = firebase.containsKey("priority") ?
                (long) Types.fromFirebase(new Types.VariableTypeLong(), firebase.get("priority")) : 0L;
        Map<String, Object> nParams = new HashMap<>();
        Map<String, Object> s = (Map<String, Object>) firebase.get("parameters");
        if (s != null) {
            for (Map.Entry<String, Types.VariableType> p : macro.getParameters().entrySet()) {
                nParams.put(p.getKey(), Types.fromFirebase(p.getValue(), s.get(p.getKey())));
            }
        } else {
            // TODO: We need to be sure that invalid goals are rejected.
            // See issue https://github.com/cellbotsai/RobotsideApp/issues/252
            Log.v(TAG, "No parameters recovered from goal.");
        }
        mParams = Collections.unmodifiableMap(nParams);
        mUserUuid = userUuid;
        mRobotUuid = robotUuid;
        mLocal = false;
    }

    public MacroGoal(String macro, String version, Map<String, Object> params,
            String userUuid, String robotUuid, double timestamp, long sequence, long priority) {
        mUuid = UUID.randomUUID().toString();
        mMacro = macro;
        mVersion = version;
        mParams = Collections.unmodifiableMap(new HashMap<>(params));
        mTimestamp = timestamp;
        mSequence = sequence;
        mPriority = priority;
        mUserUuid = userUuid;
        mRobotUuid = robotUuid;
        mLocal = false;
    }

    public MacroGoal(String uuid, String macro, String version, Map<String, Object> params,
            String userUuid, String robotUuid, double timestamp, long sequence, long priority) {
        mUuid = uuid;
        mMacro = macro;
        mVersion = version;
        mParams = Collections.unmodifiableMap(new HashMap<>(params));
        mTimestamp = timestamp;
        mSequence = sequence;
        mPriority = priority;
        mUserUuid = userUuid;
        mRobotUuid = robotUuid;
        mLocal = true;
    }

    @SuppressWarnings("unused")
    public String getUuid() {
        return mUuid;
    }

    public String getMacro() {
        return mMacro;
    }

    @SuppressWarnings("unused")
    public String getVersion() {
        return mVersion;
    }

    public Map<String, Object> getParameters() {
        return Collections.unmodifiableMap(mParams);
    }

    public double getTimestamp() {
        return mTimestamp;
    }

    public String getUserUuid() {
        return mUserUuid;
    }

    public String getRobotUuid() {
        return mRobotUuid;
    }

    public long getPriority() { return mPriority; }

    public boolean getIsLocal() { return mLocal; }

    public long getSequence() { return mSequence; }

    public String toString() {
        StringBuilder toStr = new StringBuilder(mMacro);
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

    public Map<String, Object> toMap() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("uuid", getUuid());
        map.put("name", getMacro());
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
}

