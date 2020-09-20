package ai.cellbots.robotapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

/**
 * Helper class for requesting required manifest permissions for RobotApp.
 */
public class RobotAppPermissionsHelper {
    // Codes for Requesting Permissions
    private static final int REQUEST_CAMERA_PERMISSION_CODE = 54;
    private static final int REQUEST_LOCATION_PERMISSION_CODE = 55;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_CODE = 112;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION_CODE = 113;

    // Permissions
    private static final String CAMERA_PERMISSION = android.Manifest.permission.CAMERA;
    private static final String WRITE_EXTERNAL_STORAGE_PERMISSION =
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final String LOCATION_PERMISSION =
            android.Manifest.permission.ACCESS_FINE_LOCATION;
    private static final String RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO;

    private final Activity mParent;  // Parent class.

    /**
     * Initial constructor for the RobotAppPermissionsHelper.
     *
     * @param parent The activity context.
     */
    public RobotAppPermissionsHelper(Activity parent) {
        mParent = parent;
    }

    /**
     * Checks if RobotApp has been granted all necessary permissions to run.
     *
     * @return True if all permissions have been granted to the Robot app.
     */
    public boolean hasAllPermissions() {
        return hasWriteExternalStoragePermission()
                && hasCameraPermission()
                && hasLocationPermission()
                && hasRecordAudioPermission();
    }

    /**
     * Checks if the permission "android.permission.WRITE_EXTERNAL_STORAGE" is granted.
     *
     * @return True if permission is granted.
     */
    public boolean hasWriteExternalStoragePermission() {
        return ContextCompat.checkSelfPermission(mParent, WRITE_EXTERNAL_STORAGE_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Checks if the permission "android.Manifest.permission.CAMERA" is granted.
     *
     * @return True if permission is granted.
     */
    public boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(mParent, CAMERA_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Checks if the permission "android.Manifest.permission.ACCESS_FINE_LOCATION" is granted.
     *
     * @return True if permission is granted.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(mParent, LOCATION_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Checks if the permission "android.Manifest.permission.RECORD_AUDIO" is granted.
     *
     * @return True if permission is granted.
     */
    public boolean hasRecordAudioPermission() {
        return ContextCompat.checkSelfPermission(mParent, RECORD_AUDIO_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Requests permission for writing files to the external storage.
     * If denied, shows a request permission rationale explaining why this permission is needed.
     */
    public void requestWriteExternalStoragePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(mParent,
                WRITE_EXTERNAL_STORAGE_PERMISSION)) {
            showRequestExternalStoragePermissionRationale();
        } else {
            ActivityCompat.requestPermissions(
                    mParent,
                    new String[]{WRITE_EXTERNAL_STORAGE_PERMISSION},
                    REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_CODE
            );
        }
    }

    /**
     * Requests permission for using the camera.
     * If denied, shows a request permission rationale explaining why this permission is needed.
     */
    public void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(mParent, CAMERA_PERMISSION)) {
            showRequestCameraPermissionRationale();
        } else {
            ActivityCompat.requestPermissions(
                    mParent,
                    new String[]{CAMERA_PERMISSION},
                    REQUEST_CAMERA_PERMISSION_CODE
            );
        }
    }

    /**
     * Requests permission for accessing the phone's location.
     * If denied, shows a request permission rationale explaining why this permission is needed.
     */
    public void requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(mParent, LOCATION_PERMISSION)) {
            showRequestLocationPermissionRationale();
        } else {
            ActivityCompat.requestPermissions(
                    mParent,
                    new String[]{LOCATION_PERMISSION},
                    REQUEST_LOCATION_PERMISSION_CODE
            );
        }
    }

    /**
     * Requests permission for recording audio using the phone's microphone.
     * If denied, shows a request permission rationale explaining why this permission is needed.
     */
    public void requestRecordAudioPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(mParent, RECORD_AUDIO_PERMISSION)) {
            showRequestRecordAudioPermissionRationale();
        } else {
            ActivityCompat.requestPermissions(
                    mParent,
                    new String[]{RECORD_AUDIO_PERMISSION},
                    REQUEST_RECORD_AUDIO_PERMISSION_CODE
            );
        }
    }

    /**
     * Displays a dialog explaining why the permission "android.permission.WRITE_EXTERNAL_STORAGE"
     * is needed.
     */
    private void showRequestExternalStoragePermissionRationale() {
        final AlertDialog dialog = new AlertDialog.Builder(mParent)
                .setMessage("System requires external storage read and write permissions for vision data storage")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        ActivityCompat.requestPermissions(
                                mParent,
                                new String[]{WRITE_EXTERNAL_STORAGE_PERMISSION},
                                REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_CODE
                        );
                    }
                })
                .create();
        dialog.show();
    }

    /**
     * Displays a dialog explaining why the permission "android.Manifest.permission.CAMERA"
     * is needed.
     */
    private void showRequestCameraPermissionRationale() {
        final AlertDialog dialog = new AlertDialog.Builder(mParent)
                .setMessage("System requires camera permission to save RGB images")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        ActivityCompat.requestPermissions(
                                mParent,
                                new String[]{CAMERA_PERMISSION},
                                REQUEST_CAMERA_PERMISSION_CODE
                        );
                    }
                })
                .create();
        dialog.show();
    }

    /**
     * Displays a dialog explaining why the permission "android.Manifest.permission.ACCESS_FINE_LOCATION"
     * is needed.
     */
    private void showRequestLocationPermissionRationale() {
        final AlertDialog dialog = new AlertDialog.Builder(mParent)
                .setMessage("System requires location permission to load cloud ADFs")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        ActivityCompat.requestPermissions(
                                mParent,
                                new String[]{LOCATION_PERMISSION},
                                REQUEST_LOCATION_PERMISSION_CODE
                        );
                    }
                })
                .create();
        dialog.show();
    }

    /**
     * Displays a dialog explaining why the permission "android.Manifest.permission.RECORD_AUDIO"
     * is needed.
     */
    private void showRequestRecordAudioPermissionRationale() {
        final AlertDialog dialog = new AlertDialog.Builder(mParent)
                .setMessage("System requires recording audio permission for using voice commands" +
                        " in POI controller mode.")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        ActivityCompat.requestPermissions(
                                mParent,
                                new String[]{RECORD_AUDIO_PERMISSION},
                                REQUEST_RECORD_AUDIO_PERMISSION_CODE
                        );
                    }
                })
                .create();
        dialog.show();
    }
}
