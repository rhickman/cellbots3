package ai.cellbots.robotapp;

import android.os.Bundle;

import ai.cellbots.common.settings.RobotPreferencesFragment;

/**
 * Fragment to display application robot_preferences.
 * Default implementation according to Android development recommendations.
 */

public class SettingsFragment extends RobotPreferencesFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //get XML robot_preferences
        addPreferencesFromResource(R.xml.preferences);
    }
}
