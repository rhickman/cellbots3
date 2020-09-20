package ai.cellbots.robotapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Objects;

public class StartRobotAppBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean startService = preferences.getBoolean(context.getString(
                R.string.pref_start_app_on_boot), false);
        if (startService && Objects.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)) {
            Intent startRunningActivityIntent = new Intent(context, MainActivity.class);
            startRunningActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(startRunningActivityIntent);
        }
    }
}