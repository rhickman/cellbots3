package ai.cellbots.common.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import ai.cellbots.common.R;
import ai.cellbots.common.data.RobotPreferences;

/**
 * Fragment to display application preferences.
 * Default implementation according to Android development recommendations.
 */

public class RobotPreferencesFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    final static private String TAG = RobotPreferencesFragment.class.getSimpleName();

    protected Preference mGoalDistancePreference;
    protected Preference mGoalTimeoutPreference;
    protected Preference mSmootherDeviationPreference;
    protected Preference mSmootherSmoothnessPreference;
    protected SeekBarPreference mInflationPreference;
    protected SeekBarPreference mSeekBarMaxVelPreference;
    protected Button mButtonSaveRobotConfigAsGeneral = null;

    protected boolean mEnableSmootherPreferences = true;
    protected boolean mEnableInflationPreferences = true;
    protected boolean mEnableMaxVelPreferences = true;

    updatePreferencesListener mUpdatePreferencesListener;
    PreferenceKeyChangeListener<String> mListenerKey;

    public interface PreferenceKeyChangeListener<T> {
        void onChange(String key);
    }

    public interface updatePreferencesListener {
        void update();
    }

    public void setPreferencesListener(PreferenceKeyChangeListener<String> listener) {
        mListenerKey = listener;
    }

    public void setUpdatePreferencesListener(updatePreferencesListener listener) {
        mUpdatePreferencesListener = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get XML preferences
        addPreferencesFromResource(R.xml.robot_preferences);

        // get a handle on preferences that require validation
        mGoalDistancePreference = getPreferenceScreen().findPreference(
                this.getString(R.string.pref_max_goal_distance_from_adf));
        mGoalTimeoutPreference = getPreferenceScreen().findPreference(
                this.getString(R.string.pref_goal_timeout));
        mSmootherDeviationPreference = getPreferenceScreen().findPreference(
                this.getString(R.string.pref_smoother_deviation));
        mSmootherSmoothnessPreference = getPreferenceScreen().findPreference(
                this.getString(R.string.pref_smoother_smoothness));
        mInflationPreference = (SeekBarPreference) getPreferenceScreen().findPreference(
                this.getString(R.string.seekbar_occ_grid_key_preference));
        mSeekBarMaxVelPreference = (SeekBarPreference) getPreferenceScreen().findPreference(
                this.getString(R.string.seekbar_max_vel_key_preference));

        // Validate numbers only
        mGoalDistancePreference.setOnPreferenceChangeListener(numberCheckListener);
        mGoalTimeoutPreference.setOnPreferenceChangeListener(numberCheckListener);
        mSmootherDeviationPreference.setOnPreferenceChangeListener(SmoothParamsListener);
        mSmootherSmoothnessPreference.setOnPreferenceChangeListener(SmoothParamsListener);
        mInflationPreference.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        mSeekBarMaxVelPreference.getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);

        // Enable/Disable smoother preferences. These are disabled by default.
        mSmootherDeviationPreference.setEnabled(mEnableSmootherPreferences);
        mSmootherSmoothnessPreference.setEnabled(mEnableSmootherPreferences);
        mInflationPreference.setEnabled(mEnableInflationPreferences);
        mSeekBarMaxVelPreference.setEnabled(mEnableMaxVelPreferences);
    }

    /**
     * Before showing the display update settings
     */
    @Override
    public void onStart() {
        super.onStart();
        if (mUpdatePreferencesListener != null) {
            mUpdatePreferencesListener.update();
        }
    }

    /**
     * After showing the display update settings
     */
    @Override
    public void onPause() {
        super.onPause();
        if (mUpdatePreferencesListener != null) {
            mUpdatePreferencesListener.update();
        }
    }

    /**
     * onCreateView actions
     *
     * @param inflater           Layout inflater
     * @param container          container ViewGroup
     * @param savedInstanceState bundle with savedInstanceState
     * @return view
     */
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (view != null) {
            view.setBackgroundColor(Color.WHITE);
        } else {
            Log.wtf(TAG, "view is null");
        }
        return view;
    }

    /**
     * Checks that a preference is a valid numerical value
     */
    Preference.OnPreferenceChangeListener numberCheckListener =
            new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    //Check that the string is an integer.
                    return checkNumber(newValue);
                }
            };

    /**
     * Checks that a preference is a valid numerical value and updates settings
     */
    Preference.OnPreferenceChangeListener SmoothParamsListener =
            new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    //Check that the string is an integer and update settings
                    if (checkNumber(newValue)) {
                        // Update preference manually before sending a message to the listener,
                        // to have the new parameters.
                        // It is not possible to listen to the OK button on an EditTextPreference.
                        SharedPreferences.Editor editor = preference.getEditor();
                        editor.putString(preference.getKey(), (String) newValue);
                        editor.commit();
                        if (mListenerKey != null) {
                            mListenerKey.onChange(preference.getKey());
                        }
                    }
                    return checkNumber(newValue);
                }
            };

    /**
     * Check if a string is a number
     *
     * @param newValue object to be verify
     * @return boolean, true if it is a number
     */
    protected boolean checkNumber(Object newValue) {
        if (!newValue.toString().equals("") && (newValue.toString().matches("\\d*\\.\\d+") ||
                newValue.toString().matches("\\d*"))) {
            return true;
        } else {
            Toast.makeText(getActivity(), newValue + " no valid number", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * Enable or disable smoother preferences, to avoid crash when robot is not connected.
     *
     * @param enable boolean.
     */
    public void enableSmootherPreferences(boolean enable) {
        mEnableSmootherPreferences = enable;
    }

    public void enableInflationPreferences(boolean enable) {
        mEnableInflationPreferences = enable;
    }

    public void enableMaxVelPreference(boolean enable) {
        mEnableMaxVelPreferences = enable;
    }

    public void setSeekBarVelocity(int value) {
        if (mSeekBarMaxVelPreference != null) {
            mSeekBarMaxVelPreference.setDefaults(value);
        } else {
            Log.e(TAG, "Tried to set default value on a null seekbar for max velocity preference");
        }
    }

    /**
     * Executes this handler when the seekBar changes
     *
     * @param sharedPreferences Parameters that remain constant
     * @param key               SeekBar preference key
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (mListenerKey != null) {
            mListenerKey.onChange(key);
        }
    }

    /**
     * Get robot preferences data from robot shared preferences. This method is called only when the
     * save preferences button is pressed
     *
     * @param context Application context
     * @return Robot preferences
     */
    public static RobotPreferences getRobotPreferencesData(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        boolean useOctomap = preferences.getBoolean(context.getString(
                R.string.pref_experimental_use_octomap), RobotPreferences.DEFAULT_USE_OCTOMAP);
        boolean importWorlds = preferences.getBoolean(context.getString(
                R.string.pref_import_worlds), RobotPreferences.DEFAULT_IMPORT_MAPS);
        boolean bundleAdjustment = preferences.getBoolean(context.getString(
                R.string.pref_adjust_poses), RobotPreferences.DEFAULT_ADJUST_POSES);
        boolean enableSoundsAutoGeneratedGoals = preferences.getBoolean(context.getString(
                R.string.pref_autogen_sounds), RobotPreferences.DEFAULT_AUTOGEN_SOUNDS);
        boolean enableSoundsUserGeneratedGoals = preferences.getBoolean(context.getString(
                R.string.pref_user_sounds), RobotPreferences.DEFAULT_USER_SOUNDS);
        boolean enableSoundsCommands = preferences.getBoolean(context.getString(
                R.string.pref_command_sounds), RobotPreferences.DEFAULT_COMMANDS_SOUNDS);

        double maxDistanceGoalFromADF = Double.valueOf(preferences.getString(
                context.getString(R.string.pref_max_goal_distance_from_adf),
                Double.toString(RobotPreferences.DEFAULT_MAX_GOAL_DISTANCE_FROM_ADF)));
        double goalTimeout = Double.valueOf(preferences.getString(
                context.getString(R.string.pref_goal_timeout),
                Double.toString(RobotPreferences.DEFAULT_GOAL_TIMEOUT_SECONDS)));
        double smootherDeviation = Double.valueOf(preferences.getString(
                context.getString(R.string.pref_smoother_deviation),
                Double.toString(RobotPreferences.DEFAULT_SMOOTHER_DEVIATION)));
        double smootherSmoothness = Double.valueOf(preferences.getString(
                context.getString(R.string.pref_smoother_smoothness),
                Double.toString(RobotPreferences.DEFAULT_SMOOTHER_SMOOTHNESS)));
        int maxLinearVelocity = preferences.getInt(
                context.getString(R.string.seekbar_max_vel_key_preference),
                RobotPreferences.DEFAULT_SEEKBAR_PERCENTAGE);
        int occupancyGridInflation = preferences.getInt(
                context.getString(R.string.seekbar_occ_grid_key_preference),
                RobotPreferences.DEFAULT_INFLATION_DISTANCE_CM);

        return new RobotPreferences(useOctomap, importWorlds,
                bundleAdjustment, enableSoundsAutoGeneratedGoals, enableSoundsUserGeneratedGoals,
                enableSoundsCommands, maxDistanceGoalFromADF, maxLinearVelocity, goalTimeout,
                smootherDeviation, smootherSmoothness, occupancyGridInflation);
    }

    /**
     * Ser robot shared preferences from a RobotPreferences data
     *
     * @param context          Application context
     * @param robotPreferences Application preferences to be set
     */
    public static void setRobotPreferencesData(Context context, RobotPreferences robotPreferences) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (context != null && preferences != null) {

            SharedPreferences.Editor editor = preferences.edit();

            editor.remove(context.getString(R.string.pref_experimental_use_octomap));
            editor.remove(context.getString(R.string.pref_command_sounds));
            editor.remove(context.getString(R.string.pref_import_worlds));
            editor.remove(context.getString(R.string.pref_adjust_poses));
            editor.remove(context.getString(R.string.pref_adjust_poses));
            editor.remove(context.getString(R.string.pref_autogen_sounds));
            editor.remove(context.getString(R.string.pref_user_sounds));
            editor.remove(context.getString(R.string.pref_max_goal_distance_from_adf));
            editor.remove(context.getString(R.string.pref_goal_timeout));
            editor.remove(context.getString(R.string.pref_smoother_deviation));
            editor.remove(context.getString(R.string.pref_smoother_smoothness));
            editor.remove(context.getString(R.string.seekbar_max_vel_key_preference));
            editor.remove(context.getString(R.string.seekbar_occ_grid_key_preference));

            editor.putBoolean(context.getString(R.string.pref_experimental_use_octomap),
                    robotPreferences.mUseOctomap);
            editor.putBoolean(context.getString(R.string.pref_import_worlds),
                    robotPreferences.mImportWorlds);
            editor.putBoolean(context.getString(R.string.pref_adjust_poses),
                    robotPreferences.mBundleAdjustment);
            editor.putBoolean(context.getString(R.string.pref_autogen_sounds),
                    robotPreferences.mEnableSoundsAutoGeneratedGoals);
            editor.putBoolean(context.getString(R.string.pref_user_sounds),
                    robotPreferences.mEnableSoundsUserGeneratedGoals);
            editor.putBoolean(context.getString(R.string.pref_command_sounds),
                    robotPreferences.mEnableSoundsCommands);

            editor.putString(context.getString(R.string.pref_max_goal_distance_from_adf),
                    Double.toString(robotPreferences.mMaxDistanceGoalFromADF));
            editor.putString(context.getString(R.string.pref_goal_timeout),
                    Double.toString(robotPreferences.mGoalTimeout));
            editor.putString(context.getString(R.string.pref_smoother_deviation),
                    Double.toString(robotPreferences.mSmootherDeviation));
            editor.putString(context.getString(R.string.pref_smoother_smoothness),
                    Double.toString(robotPreferences.mSmootherSmoothness));
            editor.putInt(context.getString(R.string.seekbar_max_vel_key_preference),
                    robotPreferences.mMaxLinearVelocity);
            editor.putInt(context.getString(R.string.seekbar_occ_grid_key_preference),
                    robotPreferences.mOccupancyGridInflation);
            editor.commit();
        }
    }

    /**
     * Set robot preferences to default values
     *
     * @param context Application context
     */
    public void setRobotPreferencesToDefault(Context context) {
        setRobotPreferencesData(context, new RobotPreferences());
    }
}
