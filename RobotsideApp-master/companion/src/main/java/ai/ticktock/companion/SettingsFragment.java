package ai.cellbots.companion;

import android.os.Bundle;
import android.preference.Preference;

import ai.cellbots.common.settings.RobotPreferencesFragment;

/**
 * Fragment to display application robot_preferences.
 * Default implementation according to Android development recommendations.
 */

public class SettingsFragment extends RobotPreferencesFragment {
    Preference mShowActiveGoalsPreference;
    Preference mShowOriginalPathPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //get XML robot_preferences
        addPreferencesFromResource(R.xml.preferences);

        mShowActiveGoalsPreference = getPreferenceScreen().findPreference(
                getActivity().getString(R.string.pref_key_show_active_goals));
        mShowActiveGoalsPreference.setOnPreferenceChangeListener(activeGoalsCheckListener);

        mShowOriginalPathPreference = getPreferenceScreen().findPreference(
                getActivity().getString(R.string.pref_key_show_original_path));
        mShowOriginalPathPreference.setOnPreferenceChangeListener(OriginalPathCheckListener);
    }

    /**
     * Checks if show active goals preference is checked or not
     */
    Preference.OnPreferenceChangeListener activeGoalsCheckListener =
            new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                ((Preference.OnPreferenceChangeListener) getActivity()).onPreferenceChange(
                        preference, newValue);
                return true;
                }
            };

    /**
     * Checks if the original path can be shown
     */
    Preference.OnPreferenceChangeListener OriginalPathCheckListener =
            new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                ((Preference.OnPreferenceChangeListener) getActivity()).onPreferenceChange(
                        preference, newValue);
                return true;
                }
            };
}
