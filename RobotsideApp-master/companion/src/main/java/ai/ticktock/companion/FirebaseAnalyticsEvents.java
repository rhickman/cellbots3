package ai.cellbots.companion;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;

public class FirebaseAnalyticsEvents {

    private static final String TAG = FirebaseAnalyticsEvents.class.getSimpleName();

    // Events to report to Firebase Analytics
    public static final String ANALYTICS_EVENT_ADD_PATROL_GOAL_REQUEST = "ADD_PATROL_GOAL_REQUEST";
    public static final String ANALYTICS_EVENT_ADD_POI_REQUEST = "ADD_POI_REQUEST";
    public static final String ANALYTICS_EVENT_ADD_SPOT_CLEAN_GOAL_REQUEST =
            "ADD_SPOT_CLEAN_GOAL_REQUEST";
    public static final String ANALYTICS_EVENT_DELETE_POI = "DELETE_POI";
    public static final String ANALYTICS_EVENT_ADD_DRIVE_GOAL = "ADD_DRIVE_GOAL";
    public static final String ANALYTICS_EVENT_DRIVE_TO_POI = "DRIVE_TO_POI";
    public static final String ANALYTICS_EVENT_FLOORPLAN_LONG_PRESS = "FLOORPLAN_LONG_PRESS";
    public static final String ANALYTICS_EVENT_MAP_SELECTED = "MAP_SELECTED";
    public static final String ANALYTICS_EVENT_PATROL_GOAL_ADDED = "PATROL_GOAL_ADDED";
    public static final String ANALYTICS_EVENT_PATROL_POI = "PATROL_POI";
    public static final String ANALYTICS_EVENT_PHONE_BTN_BACK = "PHONE_BTN_BACK";
    public static final String ANALYTICS_EVENT_POI_ADDED = "POI_ADDED";
    public static final String ANALYTICS_EVENT_ROBOT_RENAMED = "ROBOT_RENAMED";
    public static final String ANALYTICS_EVENT_ROBOT_SELECTED = "ROBOT_SELECTED";
    public static final String ANALYTICS_EVENT_SPOT_CLEAN_GOAL_ADDED = "SPOT_CLEAN_GOAL_ADDED";
    public static final String ANALYTICS_EVENT_USER_SIGN_IN = "USER_SIGN_IN";

    // ACTIONS SELECTED
    public static final String ANALYTICS_EVENT_ACTION_ABOUT = "ACTION_ABOUT";
    public static final String ANALYTICS_EVENT_ACTION_CANCEL_ACTIVE_GOALS =
            "ACTION_CANCEL_ACTIVE_GOALS";
    public static final String ANALYTICS_EVENT_ACTION_MANAGE_MAPS = "ACTION_MANAGE_MAPS";
    public static final String ANALYTICS_EVENT_ACTION_RENAME_ROBOT = "ACTION_RENAME_ROBOT";
    public static final String ANALYTICS_EVENT_ACTION_RESET_ZOOM = "ACTION_RESET_ZOOM";
    public static final String ANALYTICS_EVENT_ACTION_SELECT_ROBOT = "ACTION_SELECT_ROBOT";
    public static final String ANALYTICS_EVENT_ACTION_SETTINGS = "ACTION_SETTINGS";
    public static final String ANALYTICS_EVENT_ACTION_USER_SIGN_OUT = "ACTION_USER_SIGN_OUT";
    public static final String ANALYTICS_EVENT_ACTION_VOICE_COMMAND = "ACTION_VOICE_COMMAND";
    public static final String ANALYTICS_EVENT_ACTION_START_POI_CONTROLLER = "ACTION_START_POI_CONTROLLER";

    // BUTTONS PRESSED
    public static final String ANALYTICS_EVENT_BTN_ACTION_1 = "BTN_ACTION_1";
    public static final String ANALYTICS_EVENT_BTN_ACTION_2 = "BTN_ACTION_2";
    public static final String ANALYTICS_EVENT_BTN_ANIMATIONS = "BTN_ANIMATIONS";
    public static final String ANALYTICS_EVENT_BTN_EXECUTIVE_RANDOM_MODE =
            "BTN_EXECUTIVE_RANDOM_MODE";
    public static final String ANALYTICS_EVENT_BTN_EXECUTIVE_STOP_MODE =
            "BTN_EXECUTIVE_STOP_MODE";
    public static final String ANALYTICS_EVENT_BTN_LOG = "BTN_LOG";
    public static final String ANALYTICS_EVENT_BTN_PAUSE = "BTN_PAUSE";
    public static final String ANALYTICS_EVENT_BTN_POIS = "BTN_POIS";
    public static final String ANALYTICS_EVENT_BTN_PLAY = "BTN_PLAY";
    public static final String ANALYTICS_EVENT_BTN_SAVE_MAP = "BTN_SAVE_MAP";
    public static final String ANALYTICS_EVENT_BTN_SAVE_AS_FLEET = "BTN_SAVE_AS_FLEET";
    public static final String ANALYTICS_EVENT_BTN_SOUNDS = "BTN_SOUNDS";
    public static final String ANALYTICS_EVENT_BTN_START_MAPPING = "BTN_START_MAPPING";
    public static final String ANALYTICS_EVENT_BTN_TELEOP = "BTN_TELEOP";
    public static final String ANALYTICS_EVENT_BTN_VIDEO = "BTN_VIDEO";

    private static FirebaseAnalyticsEvents sInstance;

    public synchronized static FirebaseAnalyticsEvents getInstance() {
        if (sInstance == null) {
            sInstance = new FirebaseAnalyticsEvents();
        }
        return sInstance;
    }

    /**
     * Report events to Firebase Analytics.
     *
     * @param event   to log
     * @param context the Android context where the event occurred
     */
    public void reportEventToFirebase(Context context, String event) {
        // Sanity checks
        if (event == null) {
            Log.e(TAG, "reportEventToFirebase: null event");
            return;
        }

        // Upload to Firebase
        FirebaseAnalytics.getInstance(context).logEvent(event, new Bundle());
    }

    /**
     * Report an "Add Patrol Goal Request" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportAddPatrolGoalRequest(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ADD_PATROL_GOAL_REQUEST);
    }

    /**
     * Report an "Add POI Request" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportAddPOIRequest(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ADD_POI_REQUEST);
    }

    /**
     * Report an "Add Spot Clean Request" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportAddSpotCleanRequest(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ADD_SPOT_CLEAN_GOAL_REQUEST);
    }

    /**
     * Report a "Delete POI" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportDeletePOIEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_DELETE_POI);
    }

    /**
     * Report an "Add Drive Goal" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportAddDriveGoalEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ADD_DRIVE_GOAL);
    }

    /**
     * Report a "Drive To POI" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportDriveToPOIEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_DRIVE_TO_POI);
    }

    /**
     * Report a "Floorplan Long Press" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportFloorplanLongPressEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_FLOORPLAN_LONG_PRESS);
    }

    /**
     * Report a "Map Selected" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportMapSelectedEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_MAP_SELECTED);
    }

    /**
     * Report a "Patrol Goal Added" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportPatrolGoalAddedEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_PATROL_GOAL_ADDED);
    }

    /**
     * Report a "Patrol POI" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportPatrolPOIEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_PATROL_POI);
    }

    /**
     * Report a "Phone Back Button" click event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportPhoneBackButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_PHONE_BTN_BACK);
    }

    /**
     * Report a "POI Added" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportPOIAddedEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_POI_ADDED);
    }

    /**
     * Report a "Robot Renamed" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportRobotRenamedEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ROBOT_RENAMED);
    }

    /**
     * Report a "Robot Selected" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportRobotSelectedEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ROBOT_SELECTED);
    }

    /**
     * Report a "Spot Clean Goal Added" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportSpotCleanGoalAddedEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_SPOT_CLEAN_GOAL_ADDED);
    }

    /**
     * Report a "Sign In" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportSignInEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_USER_SIGN_IN);
    }

    /**
     * Report an "Action About" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportActionAboutEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ACTION_ABOUT);
    }

    /**
     * Report an "Action Cancel Active Goals" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportActionCancelActiveGoalsEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ACTION_CANCEL_ACTIVE_GOALS);
    }

    /**
     * Report an "Action Manage Maps" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportActionManageMapsEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ACTION_MANAGE_MAPS);
    }

    /**
     * Report an "Action Rename Robot" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportActionRenameRobotEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ACTION_RENAME_ROBOT);
    }

    /**
     * Report an "Action Reset Zoom" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportActionResetZoomEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ACTION_RESET_ZOOM);
    }

    public void reportActionStartPoiController(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ACTION_START_POI_CONTROLLER);
    }

    /**
     * Report an "Action Select Robot" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportActionSelectRobotEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ACTION_SELECT_ROBOT);
    }

    /**
     * Report an "Action Settings" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportActionSettingsEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ACTION_SETTINGS);
    }

    /**
     * Report an "Action Sign Out" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportActionSignOutEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ACTION_USER_SIGN_OUT);
    }

    /**
     * Report an "Action Voice Command" event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportActionVoiceCommandEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_ACTION_VOICE_COMMAND);
    }

    /**
     * Report an "Action 1" button click event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportAction1ButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_ACTION_1);
    }

    /**
     * Report an "Action 2" button click event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportAction2ButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_ACTION_2);
    }

    /**
     * Report an "Animations" button click event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportAnimationsButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_ANIMATIONS);
    }

    /**
     * Report an "Executive Mode Random" button click event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportExecutiveModeRandomButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_EXECUTIVE_RANDOM_MODE);
    }

    /**
     * Report an "Executive Mode Stop" button click event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportExecutiveModeStopButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_EXECUTIVE_STOP_MODE);
    }

    /**
     * Report a "Log" button click event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportLogButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_LOG);
    }

    /**
     * Report a "Pause" button click event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportPauseButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_PAUSE);
    }

    /**
     * Report a "Play" button click event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportPlayButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_PLAY);
    }

    /**
     * Report a "POIs" button click event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportPOIsButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_POIS);
    }

    /**
     * Report a "Save Map" button click event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportSaveMapButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_SAVE_MAP);
    }

    /**
     * Report a "Save As Fleet" button click event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportSaveAsFleetButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_SAVE_AS_FLEET);
    }

    /**
     * Report a "Sounds" button click event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportSoundsButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_SOUNDS);
    }

    /**
     * Report a "Start Mapping" button click event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportStartMappingButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_START_MAPPING);
    }

    /**
     * Report a "Teleop" button click event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportTeleopButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_TELEOP);
    }

    /**
     * Report a "Video" button click event to Firebase Analytics.
     *
     * @param context The Android context where the event occurred.
     */
    public void reportVideoButtonClickEvent(Context context) {
        reportEventToFirebase(context, ANALYTICS_EVENT_BTN_VIDEO);
    }
}
