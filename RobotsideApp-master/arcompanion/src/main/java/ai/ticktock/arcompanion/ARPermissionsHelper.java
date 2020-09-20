package ai.cellbots.arcompanion;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for requesting required manifest permissions for ARCompanion.
 */
public class ARPermissionsHelper {
    private static final String TAG = ARPermissionsHelper.class.getSimpleName();

    // Request code for requesting needed permissions.
    private static final int REQUEST_PERMISSIONS_CODE = 0;

    // Permissions that require user approval at runtime.
    private final String[] mPermissions = {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
    };

    private final Activity mParent;  // Parent class.

    /**
     * Initial constructor for ARPermissionsHelper.
     *
     * @param parent The activity context.
     */
    public ARPermissionsHelper(Activity parent) {
        mParent = parent;
    }

    /**
     * Checks to see if all the necessary permissions have been granted.
     *
     * @return True if all permissions have been granted.
     */
    public boolean hasAllPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : mPermissions) {
                if (!hasPermission(permission)) {
                    Log.i(TAG, "Not all permissions were granted.");
                    return false;
                }
            }
        }
        Log.i(TAG, "All permissions have been granted.");
        return true;
    }

    /**
     * Asks the needed permissions.
     */
    public void requestPermissions() {
        String[] permissionsNeeded = checkPermissionsNeeded();
        if (permissionsNeeded.length > 0) {
            Log.i(TAG, "Requesting needed permissions.");
            ActivityCompat.requestPermissions(mParent, permissionsNeeded, REQUEST_PERMISSIONS_CODE);
        }
    }

    /**
     * Checks the required permissions and returns a String array containing the ones that
     * require approval.
     *
     * @return String array containing the needed permissions that weren't granted.
     */
    @NonNull
    private String[] checkPermissionsNeeded() {
        List<String> permissionsNeeded = new ArrayList<>();
        for (String permission : mPermissions) {
            if (!hasPermission(permission)) {
                permissionsNeeded.add(permission);
            }
        }

        int numPermissionsNeeded = permissionsNeeded.size();
        Log.d(TAG, "Number of Needed Permissions: " + numPermissionsNeeded);
        return permissionsNeeded.toArray(new String[numPermissionsNeeded]);
    }

    /**
     * Checks if the given permission has been granted for this application.
     *
     * @param permission The requested permission.
     * @return True if the permission has been granted for the app.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean hasPermission(String permission) {
        return ActivityCompat.checkSelfPermission(mParent, permission) == PackageManager.PERMISSION_GRANTED;
    }
}