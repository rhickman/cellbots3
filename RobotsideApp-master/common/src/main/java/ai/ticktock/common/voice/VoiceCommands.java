package ai.cellbots.common.voice;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.cellbots.common.CloudHelper;
import ai.cellbots.common.R;

import ai.api.AIListener;
import ai.api.android.AIConfiguration;
import ai.api.android.AIService;
import ai.api.model.AIError;
import ai.api.model.AIResponse;
import ai.api.model.Result;
import ai.cellbots.common.data.*;

/**
 * Command via voice.
 */
public class VoiceCommands implements AIListener {

    private static final String TAG = VoiceCommands.class.getName();

    private static final String VOICE_COMMAND_START_FEEDBACK = " started";
    private static final String VOICE_COMMAND_STOP_FEEDBACK = " stopped";
    private static final String VOICE_COMMAND_GO_TO_FEEDBACK = " is going to POI: ";
    private static final String VOICE_COMMAND_COME_HERE_FEEDBACK
            = " is going to Google Home's location";
    @SuppressWarnings("unused")
    private static final String VOICE_COMMAND_ACTION_1_FEEDBACK = " executed action 1";
    @SuppressWarnings("unused")
    private static final String VOICE_COMMAND_ACTION_2_FEEDBACK = " executed action 2";
    private static final String VOICE_COMMAND_ANIMATION_FEEDBACK = " is doing an animation";

    private static final String VOICE_COMMAND_MISMATCH =
            "Sorry I did not understand you, I heard: ";
    private static final String VOICE_COMMAND_MISSING_POI = "POI not found: ";
    private static final String VOICE_COMMAND_MISSING_ANIMATION = "Animation not found: ";
    private static final String VOICE_COMMAND_MISSING_GOOGLE_HOME = "Google Home not found";

    // These actions are defined in the DialogFlow intents
    private static final String VOICE_ACTION_START = "Robot.START";
    private static final String VOICE_ACTION_STOP = "Robot.STOP";
    private static final String VOICE_ACTION_GO_TO = "Robot.GOTO";
    private static final String VOICE_ACTION_COME_HERE = "Robot.COME_HERE";
    private static final String VOICE_ACTION_1 = "Robot.ACTION_1";
    private static final String VOICE_ACTION_2 = "Robot.ACTION_2";
    private static final String VOICE_ACTION_ANIMATION = "Robot.ANIMATION";

    private static final String VOICE_PARAMETER_ROBOT_GIVEN_NAME = "RobotGivenName";
    private static final String VOICE_PARAMETER_ROBOT_LAST_NAME = "RobotLastName";
    private static final String VOICE_PARAMETER_PLACE = "Place";
    @SuppressWarnings("unused")
    private static final String VOICE_PARAMETER_DURATION = "duration";
    private static final String VOICE_PARAMETER_ANIMATION_NAME = "AnimationName";

    private static final String CURRENT_ROBOT_NULL = "Robot is not set";
    private static final String CURRENT_POI_NULL = "POI is not set";
    private static final String CURRENT_ANIMATION_NULL = "Animation is not set";

    private static final boolean SHOW_TOAST_ERROR_MESSAGE = true;

    //***********************************************************************
    // To be able to edit the DialogFlow agent, edit this token
    // If you create a new DialogFlow agent, the new token will be on the settings section
    // https://console.dialogflow.com/api-client/#/agent/a51e9618-9256-4ce7-ad42-c99e69c799ef/
    //
    private static final String CLIENT_ACCESS_TOKEN = "584a697e46d8434492c6cb7f97623d52";
    //***********************************************************************

    /**
     * Multiple actions via voice.
     */
    public enum VoiceAction {
        START,
        STOP,
        GOTO,
        COME_HERE,
        ACTION1,
        ACTION2,
        ANIMATION
    }

    private final Context mActivityContext;
    private final CloudHelper mCloudHelper;
    private final AIService mAiService;

    // Listener to send VoiceAction message to the Main Activity
    private OnVoiceDetectedListener mOnVoiceDetectedListener;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private VoiceAction mVoiceAction;

    private Robot mCurrentRobot = null;
    private final Map<String, PointOfInterest> mKeyedPoi = new HashMap<>();
    private final List<String> mAnimations = new ArrayList<>();

    private boolean mIsListening = false;

    /**
     * Creates VoiceCommands object.
     *
     * @param context Context
     */
    public VoiceCommands(Context context) {
        mActivityContext = context;
        mCloudHelper = CloudHelper.getInstance();
        // Set up configuration for using system speech recognition
        final AIConfiguration config = new AIConfiguration(
                CLIENT_ACCESS_TOKEN,
                AIConfiguration.SupportedLanguages.English,
                AIConfiguration.RecognitionEngine.System);

        // Initialize the AI service and add this instance as the listener to handle query requests.
        mAiService = AIService.getService(context, config);
        mAiService.setListener(this);
    }

    /**
     * Sets the listener used to send a voice action back to MainActivity.
     * @param listener Listener
     */
    public void setOnVoiceDetectedListener(OnVoiceDetectedListener listener) {
        mOnVoiceDetectedListener = listener;
    }

    /**
     * Voice command action, starts voice recognition interaction.
     *
     * @param currentRobot Robot
     * @param keyedPoi POI keyed by their keys.
     * @param animations  Animations
     */
    public void startVoiceRecognition(Robot currentRobot,
                                      Map<String, PointOfInterest> keyedPoi,
                                      List<String> animations) {
        if (mIsListening) {
            // The microphone is already listening. We interrupt the listening service.
            mAiService.cancel();
            mIsListening = false;
        } else {
            // We start the listening service
            mCurrentRobot = currentRobot;
            mKeyedPoi.clear();
            mKeyedPoi.putAll(keyedPoi);
            mAnimations.clear();
            mAnimations.addAll(animations);
            mAiService.startListening();
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////// API.AI Service Listener Methods //////////////////////
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void onListeningStarted() {
        mIsListening = true;
    }

    @Override
    public void onListeningCanceled() {
        mIsListening = false;
    }

    @Override
    public void onListeningFinished() {
        mIsListening = false;
    }

    @Override
    public void onError(AIError error) {
        // TODO(Emiliano): Add analytics for error message error.toString()
        Toast.makeText(mActivityContext, error.toString(), Toast.LENGTH_SHORT).show();
        Log.e(TAG, error.toString());
    }

    @Override
    public void onAudioLevel(float level) {

    }

    /**
     * Gets the processed result from the DialogFlow agent.
     *
     * @param response in JSON format
     */
    @Override
    public void onResult(final AIResponse response) {
        if (!hasRobot()) {
            return;
        }

        Result voiceResult = response.getResult();
        String action = voiceResult.getAction();
        HashMap<String, JsonElement> parameters = voiceResult.getParameters();

        if (action.equals("input.unknown")) {
            Log.d(TAG, mActivityContext.getString(R.string.voice_error_input_unknown));
            Toast.makeText(mActivityContext, R.string.voice_error_input_unknown,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // If a name was given in the response, build and compare it to the user's current robot.
        if (parameters != null && parameters.containsKey(VOICE_PARAMETER_ROBOT_GIVEN_NAME)) {
            String givenName = buildRobotName(parameters);
            Log.d(TAG, "Given robot name = '" + givenName + "'");

            if (!givenName.equalsIgnoreCase(mCurrentRobot.getName())) {
                // A different robot other than mCurrentRobot was called.
                Toast.makeText(mActivityContext, givenName + " is not the same robot. Cannot execute action.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }
        executeAction(voiceResult, action);
    }

    /**
     * Builds the response's given name and compares to the user's current robot's name.
     * Two Cases:
     *      1) RobotGivenName
     *      2) RobotGivenName RobotLastName
     *
     * @param parameters map of parameter fields from the voice result
     * @return Robot name string.
     */
    @NonNull
    private String buildRobotName(HashMap<String, JsonElement> parameters) {
        String firstName = parameters.get(VOICE_PARAMETER_ROBOT_GIVEN_NAME).getAsString();
        StringBuilder robotNameBuilder = new StringBuilder(firstName);

        if (parameters.containsKey(VOICE_PARAMETER_ROBOT_LAST_NAME)) {
            String lastName = parameters.get(VOICE_PARAMETER_ROBOT_LAST_NAME).getAsString();
            robotNameBuilder.append(" ").append(lastName);
        }

        return robotNameBuilder.toString();
    }

    /**
     * Executes the given action.
     *
     * @param voiceResult Voice parsing result.
     * @param action Action name.
     */
    private void executeAction(Result voiceResult, String action) {
        switch (action) {
            case VOICE_ACTION_START:
                startAction();
                break;
            case VOICE_ACTION_STOP:
                stopAction();
                break;
            case VOICE_ACTION_GO_TO:
                goToAction(voiceResult);
                break;
            case VOICE_ACTION_COME_HERE:
                comeHereAction();
                break;
            case VOICE_ACTION_1:
                break;
            case VOICE_ACTION_2:
                break;
            case VOICE_ACTION_ANIMATION:
                animateAction(voiceResult);
                break;
            default:
                // Something went wrong.
                Toast.makeText(mActivityContext, VOICE_COMMAND_MISMATCH +
                        voiceResult.getResolvedQuery(), Toast.LENGTH_SHORT).show();
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////// Voice Actions for Controlling Robot /////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Executes the "Start" action intent.
     */
    private void startAction() {
        setVoiceAction(VoiceAction.START);
        Toast.makeText(mActivityContext, mCurrentRobot.getName() +
                VOICE_COMMAND_START_FEEDBACK, Toast.LENGTH_SHORT).show();
    }

    /**
     * Executes the "Stop" action intent.
     */
    private void stopAction() {
        setVoiceAction(VoiceAction.STOP);
        Toast.makeText(mActivityContext, mCurrentRobot.getName() +
                VOICE_COMMAND_STOP_FEEDBACK, Toast.LENGTH_SHORT).show();
    }

    /**
     * Executes the "Go To" action intent
     *
     * @param aiResult in JSON format to print in case of errors
     */
    private void goToAction(Result aiResult) {
        String place = getPlaceFromVoiceRecognition(aiResult);
        if (isPlaceNull(aiResult, place)) {
            return;
        }

        String poiUuid = getUuidFromPoi(place);
        if (isPOIUuidNull(place, poiUuid)) {
            return;
        }

        // Send GOTO action to MainActivity
        setVoiceAction(VoiceAction.GOTO);
        Toast.makeText(mActivityContext, mCurrentRobot.getName()
                + VOICE_COMMAND_GO_TO_FEEDBACK + place, Toast.LENGTH_SHORT).show();
        // Send the "Go To" command to Firebase
        mCloudHelper.sendPlannerGoalToRobot(new DrivePOIGoal(poiUuid), mCurrentRobot);
    }

    /**
     * Executes the "Come Here" action intent.
     * Sends robot to Google Home's location if location is available as a POI.
     */
    private void comeHereAction() {
        String poiUuid = getUuidFromPoi("google home");
        if (isGoogleHomeUuidNull(poiUuid)) {
            return;
        }

        // Send COME_HERE action to MainActivity
        Toast.makeText(mActivityContext, mCurrentRobot.getName()
                + VOICE_COMMAND_COME_HERE_FEEDBACK, Toast.LENGTH_SHORT).show();
        // Send the "Come Here/Go To" command to Firebase
        mCloudHelper.sendPlannerGoalToRobot(new DrivePOIGoal(poiUuid), mCurrentRobot);
    }

    /**
     * Executes the "animation" action intent
     *
     * @param aiResult in JSON format to print in case of errors
     */
    private void animateAction(Result aiResult) {
        String animationName = getAnimationNameFromVoiceRecognition(aiResult);
        if (isAnimationMissing(animationName)) {
            return;
        }

        // Animation exists for the robot.
        // Send animation action back to MainActivity.
        setVoiceAction(VoiceAction.ANIMATION);
        Toast.makeText(mActivityContext, mCurrentRobot.getName()
                + VOICE_COMMAND_ANIMATION_FEEDBACK, Toast.LENGTH_SHORT).show();

        // Send animation goal to Firebase
        AnimationInfo animation = new AnimationInfo(mCurrentRobot.uuid, animationName);
        mCloudHelper.sendPlannerGoalToRobot(new AnimationGoal(animation), mCurrentRobot);
    }

    /**
     * Checks if the given place name is null.
     *
     * @param voiceResult Voice parsing result.
     * @param place Place name.
     * @return True if the place does not exist.
     */
    private boolean isPlaceNull(Result voiceResult, String place) {
        if (place == null) {
            // Place was not found
            // TODO(Emiliano): Add analytics for error message "Place not specified"
            Toast.makeText(mActivityContext, voiceResult.getFulfillment().getDisplayText(),
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    /**
     * Checks if the uuid for a given POI is null.
     *
     * @param place Place name.
     * @param poiUuid POI UUID
     * @return True if the POI does not exist.
     */
    private boolean isPOIUuidNull(String place, String poiUuid) {
        if (poiUuid == null) {
            // POI was not found
            Toast.makeText(mActivityContext, VOICE_COMMAND_MISSING_POI + place,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    /**
     * Checks if the uuid for a POI called "google home" is null.
     *
     * @param googleHomeUuid uuid of POI "google home"
     * @return true if googleHomeUuid is null
     */
    private boolean isGoogleHomeUuidNull(String googleHomeUuid) {
        if (googleHomeUuid == null) {
            // POI named "google home" was not found
            Toast.makeText(mActivityContext, VOICE_COMMAND_MISSING_GOOGLE_HOME,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    /**
     * Checks if the given animation exists in our list of animations.
     *
     * @param animationName Animation name.
     * @return True if animation exists in our list of animations
     */
    private boolean isAnimationMissing(String animationName) {
        if (!hasAnimation(animationName)) {
            // Animation is missing or invalid
            Toast.makeText(mActivityContext, VOICE_COMMAND_MISSING_ANIMATION + animationName,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    /**
     * Checks if the asked animation exists
     *
     * @param animationName Name of the animation
     * @return True if exists the animation in Firebase
     */
    private boolean hasAnimation(final String animationName) {
        if (mAnimations == null) {
            if (SHOW_TOAST_ERROR_MESSAGE) {
                Toast.makeText(mActivityContext, CURRENT_ANIMATION_NULL, Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        return mAnimations.contains(animationName.toLowerCase());
    }

    /**
     * Checks if robot object is set.
     * @return True if robot object is set.
     */
    private boolean hasRobot() {
        if (mCurrentRobot == null) {
            Log.w(TAG, "Robot is null");
            if (SHOW_TOAST_ERROR_MESSAGE) {
                Toast.makeText(mActivityContext, CURRENT_ROBOT_NULL, Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        return true;
    }

    /**
     * Checks if POI map is set.
     * @return True if POI map is set.
     */
    @SuppressWarnings("unused")
    private boolean hasPoi() {
        if (mKeyedPoi == null) {
            Log.w(TAG, "POI map is null");
            if (SHOW_TOAST_ERROR_MESSAGE) {
                Toast.makeText(mActivityContext, CURRENT_POI_NULL, Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        return true;
    }

    /**
     * Get the Animation Name to execute
     *
     * @param voiceResult Processed JSON
     * @return Animation to be executed
     */
    private String getAnimationNameFromVoiceRecognition(Result voiceResult) {
        HashMap<String, JsonElement> parameters = voiceResult.getParameters();
        if (parameters != null && !parameters.isEmpty()) {
            for (final Map.Entry<String, JsonElement> entry : parameters.entrySet()) {
                // Takes the "AnimationName" key from the parsed JSON of DialogFlow
                if (entry.getKey().equals(VOICE_PARAMETER_ANIMATION_NAME)) {
                    // Remove the additional ""
                    return entry.getValue().toString().replace("\"", "");
                }
            }
        }
        return null;
    }

    /**
     * Get the Place where the robot should go
     *
     * @param voiceResult Processed JSON
     * @return Place to go
     */
    private String getPlaceFromVoiceRecognition(Result voiceResult) {
        HashMap<String, JsonElement> parameters = voiceResult.getParameters();
        if (parameters != null && !parameters.isEmpty()) {
            for (final Map.Entry<String, JsonElement> entry : parameters.entrySet()) {
                // Takes the "Place" key from the parsed JSON of DialogFlow
                if (entry.getKey().equals(VOICE_PARAMETER_PLACE)) {
                    // Remove the additional ""
                    return entry.getValue().toString().replace("\"", "");
                }
            }
        }
        return null;
    }

    /**
     * Get UUID from desired POI
     *
     * @param poi Point of interest
     * @return User UID (UUID)
     */
    private String getUuidFromPoi(String poi) {
        // Searches for "POI" in "mKeyedPoi"
        if (mKeyedPoi != null) {
            for (Map.Entry keyAndPoi : mKeyedPoi.entrySet()) {
                String keyName = keyAndPoi.getKey().toString();
                if (keyName.equalsIgnoreCase(poi)) {
                    return mKeyedPoi.get(keyName).uuid;
                }
            }
        }
        return null;
    }

    /**
     * Get transform from desired POI
     *
     * @param poi (a.k.a. Point of interest)
     * @return TF element of the POI
     */
    @SuppressWarnings("unused")
    private ai.cellbots.common.data.Transform getTransformFromPOI(String poi) {
        // Searches for "POI" in "mKeyedPoi"
        if (mKeyedPoi != null) {
            for (Map.Entry keyAndPoi : mKeyedPoi.entrySet()) {
                String keyName = keyAndPoi.getKey().toString();
                if (keyName.equalsIgnoreCase(poi)) {
                    return mKeyedPoi.get(keyName).getTf();
                }
            }
        }
        return null;
    }

    /**
     * Send the action detected to the activity that is listening
     * @param action Action detected by DialogFlow
     */
    private void setVoiceAction(VoiceAction action) {
        mVoiceAction = action;
        if (mOnVoiceDetectedListener != null) {
            mOnVoiceDetectedListener.onVoiceActionDetected(action);
        }
    }
}
