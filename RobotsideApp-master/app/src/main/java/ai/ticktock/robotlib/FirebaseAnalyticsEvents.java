package ai.cellbots.robotlib;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;

public class FirebaseAnalyticsEvents {
    private static final String TAG = FirebaseAnalyticsEvents.class.getSimpleName();

    private static final String ANALYTICS_EVENT_PARAM_ROBOT_UUID = "ROBOT_UUID";
    private static final String ANALYTICS_EVENT_PARAM_ROBOT_VERSION = "ROBOT_VERSION";
    private static final String ANALYTICS_EVENT_PARAM_WORLD_UUID = "WORLD_UUID";

    // Name of the events that will be notified to Firebase Analytics
    // BUTTONS EVENTS
    private static final String ANALYTICS_EVENT_BTN_SIGN_OUT = "BTN_SIGN_OUT";
    private static final String ANALYTICS_EVENT_BTN_STOP_OPERATION = "BTN_STOP_OPERATION";
    private static final String ANALYTICS_EVENT_BTN_START_SERVICE = "BTN_START_SERVICE";
    private static final String ANALYTICS_EVENT_BTN_STOP_SERVICE = "BTN_STOP_SERVICE";
    private static final String ANALYTICS_EVENT_BTN_SAVE_MAP = "BTN_SAVE_MAP";
    private static final String ANALYTICS_EVENT_BTN_START_MAPPING = "BTN_START_MAPPING";
    private static final String ANALYTICS_EVENT_BTN_CALIBRATE = "BTN_CALIBRATE";
    private static final String ANALYTICS_EVENT_BTN_SAVE_AS_FLEET = "BTN_SAVE_AS_FLEET";
    private static final String ANALYTICS_EVENT_BTN_SELECT_MAP = "BTN_SELECT_MAP";

    private static final String ANALYTICS_EVENT_ROBOT_ACTIVE = "ROBOT_ACTIVE";
    private static final String ANALYTICS_EVENT_ROBOT_BUMPER = "BUMPER";
    private static final String ANALYTICS_EVENT_ROBOT_COMMAND_RECEIVED = "COMMAND_RECEIVED";
    private static final String ANALYTICS_EVENT_ROBOT_DISCONNECTED = "ROBOT_DISCONNECTED";
    private static final String ANALYTICS_EVENT_ROBOT_DRIVE = "DRIVE";
    private static final String ANALYTICS_EVENT_ROBOT_DRIVE_TRAVERSED =
            FirebaseAnalytics.Param.VALUE;
    private static final String ANALYTICS_EVENT_ROBOT_GOAL_ADDED = "GOAL_ADDED";
    private static final String ANALYTICS_EVENT_ROBOT_GOAL_REACHED = "GOAL_REACHED";
    private static final String ANALYTICS_EVENT_ROBOT_GOAL_REJECTED = "GOAL_REJECTED";
    private static final String ANALYTICS_EVENT_NEW_ROBOT_CONNECTED = "NEW_ROBOT_CONNECTED";
    private static final String ANALYTICS_EVENT_ROBOT_NAVIGATION_SHUTDOWN =
            "ROBOT_NAVIGATION_SHUTDOWN";
    private static final String ANALYTICS_EVENT_ROBOT_PAUSED = "ROBOT_PAUSED";
    private static final String ANALYTICS_EVENT_ROBOT_PATH_BLOCKED = "PATH_BLOCKED";
    private static final String ANALYTICS_EVENT_ROBOT_STATUS = "STATUS";

    private static final String ANALYTICS_EVENT_USER_SAVE_MAP = "SAVE_MAP";
    private static final String ANALYTICS_EVENT_USER_START_MAPPING = "START_MAPPING";
    private static final String ANALYTICS_EVENT_USER_START_OPERATION = "START_OPERATION";

    private static final String ANALYTICS_EVENT_START_SERVICE = "START_SERVICE";
    private static final String ANALYTICS_EVENT_STOP_OPERATION = "STOP_OPERATION";

    private static final String ANALYTICS_EVENT_TANGO_CONNECTED = "TANGO_CONNECTED";
    private static final String ANALYTICS_EVENT_TANGO_DELOCALIZED = "TANGO_DELOCALIZED";
    private static final String ANALYTICS_EVENT_TANGO_ERROR = "TANGO_ERROR";
    private static final String ANALYTICS_EVENT_TANGO_LOCALIZED = "TANGO_LOCALIZED";

    private static final String ANALYTICS_EVENT_VALUE = FirebaseAnalytics.Param.VALUE;
    private static final String ANALYTICS_EVENT_DRIVE_TO_LOCATION_GOAL_ADDED =
            "DRIVE_TO_LOCATION_GOAL_ADDED";

    private static FirebaseAnalyticsEvents sInstance;

    public synchronized static FirebaseAnalyticsEvents getInstance() {
        if (sInstance == null) {
            sInstance = new FirebaseAnalyticsEvents();
        }
        return sInstance;
    }

    /**
     * Log a robot event.
     *
     * @param robotUuid    The robot's UUID.
     * @param robotVersion The robot's version.
     * @param worldUuid    The world's UUID.
     * @param statusString A string representing the status
     */
    void logStatusEvent(Context context, String robotUuid, String robotVersion, String worldUuid,
            String statusString) {

        // Sanity checks
        if (robotUuid == null) {
            Log.w(TAG, "logStatus: null robotUuid");
        }
        if (robotVersion == null) {
            Log.w(TAG, "logStatus: null robotVersion");
        }
        if (statusString == null) {
            Log.w(TAG, "logStatus: null statusString");
        }
        if (worldUuid == null) {
            Log.w(TAG, "logStatus: null worldUuid");
        }

        // Build event
        Bundle statusEvent = new Bundle();
        statusEvent.putString(ANALYTICS_EVENT_PARAM_ROBOT_UUID, robotUuid);
        statusEvent.putString(ANALYTICS_EVENT_PARAM_ROBOT_VERSION, robotVersion);
        statusEvent.putString(ANALYTICS_EVENT_PARAM_WORLD_UUID, worldUuid);
        statusEvent.putString(ANALYTICS_EVENT_VALUE, statusString);

        // Upload to Firebase
        FirebaseAnalytics.getInstance(context).logEvent(ANALYTICS_EVENT_ROBOT_STATUS, statusEvent);
    }

    /**
     * Log a robot event.
     *
     * @param robotUuid         The robot's UUID.
     * @param robotVersion      The robot's version.
     * @param worldUuid         The world's UUID.
     * @param distanceTraversed The distance traversed by the robot.
     */
    void logDistanceEvent(Context context, String robotUuid, String robotVersion, String worldUuid,
            double distanceTraversed) {

        // Sanity checks
        if (robotUuid == null) {
            Log.w(TAG, "logDistanceEvent: null robotUuid");
        }
        if (robotVersion == null) {
            Log.w(TAG, "logDistanceEvent: null robotVersion");
        }
        if (worldUuid == null) {
            Log.w(TAG, "logDistanceEvent: null worldUuid");
        }

        // Build event
        Bundle distanceEvent = new Bundle();
        distanceEvent.putString(ANALYTICS_EVENT_PARAM_ROBOT_UUID, robotUuid);
        distanceEvent.putString(ANALYTICS_EVENT_PARAM_ROBOT_VERSION, robotVersion);
        distanceEvent.putString(ANALYTICS_EVENT_PARAM_WORLD_UUID, worldUuid);
        distanceEvent.putDouble(ANALYTICS_EVENT_ROBOT_DRIVE_TRAVERSED, distanceTraversed);

        // Upload to Firebase
        FirebaseAnalytics.getInstance(context).logEvent(ANALYTICS_EVENT_ROBOT_DRIVE, distanceEvent);
    }

    /**
     * Report events to Firebase Analytics. Send a message describing the event.
     *
     * @param eventType    The type of the event to log.
     * @param eventMessage A description of the event.
     */
    void reportEventWithDescriptionToFirebase(Context context, String eventType,
            String eventMessage) {
        // Sanity checks
        if (eventType == null) {
            Log.e(TAG, "reportEventToFirebase: null eventType");
            return;
        }

        // Build event
        Bundle statusEvent = new Bundle();
        if (eventMessage != null) {
            statusEvent.putString(ANALYTICS_EVENT_VALUE, eventMessage);
        }

        // Upload to Firebase
        FirebaseAnalytics.getInstance(context).logEvent(eventType, statusEvent);
    }

    /**
     * Report events to Firebase Analytics.
     *
     * @param eventType The type of the event to log.
     */
    void reportEventToFirebase(Context context, String eventType) {
        reportEventWithDescriptionToFirebase(context, eventType, null);
    }

    /**
     * Report a bumper event to Firebase Analytics.
     */
    public void reportBumperFirebaseEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ROBOT_BUMPER);
    }

    /**
     * Report a "Command Received" event to Firebase Analytics.
     */
    @SuppressWarnings("unused")
    public void reportCommandReceivedFirebaseEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ROBOT_COMMAND_RECEIVED);
    }

    /**
     * Report a "Drive-to-Location Goal Added" event to Firebase Analytics.
     */
    public void reportDriveToLocationGoalAddedFirebaseEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_DRIVE_TO_LOCATION_GOAL_ADDED);
    }

    /**
     * Report a "Goal Added" event to Firebase Analytics.
     */
    public void reportGoalAddedFirebaseEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ROBOT_GOAL_ADDED);
    }

    /**
     * Report a "Goal Reached" event to Firebase Analytics.
     */
    public void reportGoalReachedFirebaseEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ROBOT_GOAL_REACHED);
    }

    /**
     * Report a "Goal Rejected" event to Firebase Analytics.
     *
     * @param rejectionReason The reason why the goal was rejected.
     */
    public void reportGoalRejectedFirebaseEvent(Context context, String rejectionReason) {
        reportEventWithDescriptionToFirebase(context, ANALYTICS_EVENT_ROBOT_GOAL_REJECTED,
                rejectionReason);
    }

    /**
     * Report a "New Robot Connected" event to Firebase Analytics.
     */
    public void reportNewRobotConnectedEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_NEW_ROBOT_CONNECTED);
    }

    /**
     * Report a "Path Blocked" event to Firebase Analytics.
     */
    public void reportPathBlockedFirebaseEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ROBOT_PATH_BLOCKED);
    }

    /**
     * Report a "Robot Active" event to Firebase Analytics.
     */
    public void reportRobotActiveEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ROBOT_ACTIVE);
    }

    /**
     * Report a "Robot Disconnected" event to Firebase Analytics.
     */
    public void reportRobotDisconnectedEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ROBOT_DISCONNECTED);
    }

    /**
     * Report a "Robot Runner Shutdown" event to Firebase Analytics.
     */
    public void reportRobotNavigationShutdownEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ROBOT_NAVIGATION_SHUTDOWN);
    }

    /**
     * Report a "Robot Paused" event to Firebase Analytics.
     */
    public void reportRobotPausedEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ROBOT_PAUSED);
    }

    /**
     * Report a "Save Map" event to Firebase Analytics.
     */
    public void reportSaveMapEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_USER_SAVE_MAP);
    }

    /**
     * Report a "Start Mapping" event to Firebase Analytics.
     */
    public void reportStartMappingEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_USER_START_MAPPING);
    }

    /**
     * Report a "Start Operation" event to Firebase Analytics.
     */
    public void reportStartOperationEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_USER_START_OPERATION);
    }

    /**
     * Report a "Start Service" event to Firebase Analytics.
     */
    void reportStartServiceEventToFirebase(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_START_SERVICE);
    }

    /**
     * Report a "Stop Operations" event to Firebase Analytics.
     */
    void reportStopOperationsEventToFirebase(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_STOP_OPERATION);
    }

    /**
     * Report a "Tango Connected" event to Firebase Analytics.
     */
    public void reportTangoConnectedEventToFirebase(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_TANGO_CONNECTED);
    }

    /**
     * Report a "Tango Delocalized" event to Firebase Analytics.
     */
    public void reportTangoDelocalizedEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_TANGO_DELOCALIZED);
    }

    /**
     * Report a "Tango Error" event to Firebase Analytics.
     */
    public void reportTangoErrorEvent(Context context, String error) {
        reportEventWithDescriptionToFirebase(context, ANALYTICS_EVENT_TANGO_ERROR, error);
    }

    /**
     * Report a "Tango Localized" event to Firebase Analytics.
     */
    public void reportTangoLocalizedEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_TANGO_LOCALIZED);
    }

    /**
     * Report "Calibrate" button click events to Firebase Analytics.
     */
    public void reportCalibrateButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_CALIBRATE);
    }

    /**
     * Report "Save Map" button click events to Firebase Analytics.
     */
    public void reportSaveMapButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_SAVE_MAP);
    }

    /**
     * Report "Save As Fleet" button click events to Firebase Analytics.
     */
    public void reportSaveAsFleetButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_SAVE_AS_FLEET);
    }

    /**
     * Report "Select Map" button click events to Firebase Analytics.
     */
    public void reportSelectMapButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_SELECT_MAP);
    }

    /**
     * Report "Sign Out" button click events to Firebase Analytics.
     */
    public void reportSignOutButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_SIGN_OUT);
    }

    /**
     * Report "Start Mapping" button click events to Firebase Analytics.
     */
    public void reportStartMappingButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_START_MAPPING);
    }

    /**
     * Report "Start Service" button click events to Firebase Analytics.
     */
    public void reportStartServiceButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_START_SERVICE);
    }

    /**
     * Report "Stop Operation" button click events to Firebase Analytics.
     */
    public void reportStopOperationButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_STOP_OPERATION);
    }

    /**
     * Report "Stop Service" button click events to Firebase Analytics.
     */
    public void reportStopServiceButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_STOP_SERVICE);
    }
}