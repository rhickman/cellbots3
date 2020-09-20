package ai.cellbots.robot.costmap;

import android.util.Log;

public final class InflatorJNINative {
    static {
        try {
            System.loadLibrary("cpp_costmap_inflation");
        } catch (SecurityException e) {
            Log.i("TEST", "Error loading library! SecurityException");
        } catch (UnsatisfiedLinkError e) {
            Log.i("TEST", "Error loading library! UnsatisfiedLinkError");
        } catch (NullPointerException e) {
            Log.i("TEST", "Error loading library! NullPointerException");
        }
    }

    /**
     * TODO
     */
    public static native byte[] inflate(byte[] grid, double robotRadius, double gridResolution,
            int[] gridLimits);
}
