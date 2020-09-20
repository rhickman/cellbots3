package ai.cellbots.tangocommon;

import android.app.Activity;

import com.google.atap.tangoservice.Tango;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Helper class for requesting and checking permission to use the Tango service.
 */
public final class TangoPermissionsHelper {

    private final Activity mParent;  // Parent class.

    // Boolean for determining if permission has been granted for using the Tango service.
    // True if permission has been granted.
    private final AtomicBoolean mTangoPermission = new AtomicBoolean(false);

    /**
     * Initial constructor for TangoPermissionsHelper.
     *
     * @param parent The activity context.
     */
    public TangoPermissionsHelper(Activity parent) {
        mParent = parent;
    }

    /**
     * Requests permission to use the Tango Service.
     *
     * @param requestCode Request code for returning in mParent's onActivityResult() when
     *                    the activity exits.
     */
    public void requestTangoPermission(int requestCode) {
        mParent.startActivityForResult(
                Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE),
                requestCode
        );
    }

    /**
     * Checks if permission has been granted to use the Tango service.
     *
     * @return True if permission has been granted.
     */
    public boolean hasPermission() {
        return mTangoPermission.get();
    }

    /**
     * Sets permission for using the Tango service.
     *
     * @param isGranted True if permission is granted.
     */
    public void setPermission(boolean isGranted) {
        mTangoPermission.set(isGranted);
    }
}
