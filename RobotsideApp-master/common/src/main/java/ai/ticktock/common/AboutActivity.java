package ai.cellbots.common;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

/**
 * Activity to display About screen.
 */

public class AboutActivity extends AppCompatActivity {
    private static final String TAG = AboutActivity.class.getSimpleName();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about);
        TextView textViewVersion = findViewById(R.id.about_version);
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            textViewVersion.setText(getString(R.string.display_application_version, packageInfo.versionName));
        } catch (PackageManager.NameNotFoundException ex) {
            Log.e(TAG, "Exception getting application version number.", ex);
        }
    }
}
