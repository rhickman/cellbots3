package ai.cellbots.common.data;

import com.google.firebase.database.PropertyName;

import java.util.Map;

/**
 * Represents a Robot object in the database.
 */

public class Robot {
    // The robot is blocked
    public static final String STUCK_STATE = "stuck";
    // The robot is active and moving around
    public static final String ACTIVE_STATE = "active";
    // The robot is paused by the user
    public static final String PAUSED_STATE = "paused";

    // Current map loaded into the robot
    public String map;
    // Robot UUID
    public String uuid;
    // Robot version
    public String version;
    // Friendly / displayable robot name
    public String name;
    // State of the robot
    public String state;
    // Localization state of the robot
    public boolean localized;
    // Position of the robot
    public Transform tf;

    // Mapping run id, for saving the map
    @PropertyName("mapping_run_id")
    public String mMappingRunId;

    // List of batteries
    public Map<String, BatteryStatus> batteries;
    public Map<String, Transform> path;

    // List of log messages
    @PropertyName("log_messages")
    public Map<String, LogMessage> mLogMessages;

    // Timestamp for last update from firebase server
    @PropertyName("last_update_time")
    public long mLastUpdateTime;

    // Timestamp for last update from robot clock
    @PropertyName("local_time")
    public long mLocalTime;

    public Robot() {
    }

    /**
     * Helper method that returns the robot name or defaults to its UUID.
     */
    public String getName() {
        return name != null ? name : "Unnamed (" + uuid + ")";
    }

    @Override
    public String toString() {
        StringBuilder batteryString = new StringBuilder("[");
        if (batteries != null) {
            for (BatteryStatus battery : batteries.values()) {
                if (batteryString.toString().equals("[")) {
                    batteryString.append("[").append(battery);
                } else {
                    batteryString.append(", ").append(battery);
                }
            }
            batteryString.append("]");
        } else {
            batteryString = new StringBuilder("NULL");
        }
        return "Robot{" +
                "map='" + map + '\'' +
                ", uuid='" + uuid + '\'' +
                ", version='" + version + '\'' +
                ", name='" + name + '\'' +
                ", batteries=" + batteryString +
                '}';
    }
}
