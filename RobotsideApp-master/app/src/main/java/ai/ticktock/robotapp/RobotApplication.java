package ai.cellbots.robotapp;

import android.app.Application;

import com.github.anrwatchdog.ANRWatchDog;

/**
 * Base class within Cellbots's RobotApp that contains all other components such as
 * activities and services. Primarily used for initialization of the global state before
 * MainActivity is displayed.
 */
public class RobotApplication extends Application {

    private static final String TAG = RobotApplication.class.getSimpleName();

    // A simple watchdog library for detecting ANRs.
    // Link: https://github.com/SalomonBrys/ANR-WatchDog
    private ANRWatchDog mWatchDog;

    /**
     * Called when the application is starting, before any other application objects
     * have been created.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        mWatchDog = new ANRWatchDog();
        mWatchDog.start();

        /**
         * Note: ANR logging is still available for the release version of this app via Google's
         * Play Store. This library (and listener) are purely for logging the call stack when
         * connected to logcat in debug mode.
         *
         * Uncommenting this listener will prevent the app from crashing when an ANR
         * is detected. By implementing this listener, you can log the ANR and report the error,
         * but the app will remain hung (longer than the default 5 seconds) until the Android OS
         * decides to kill it.
         */
/*        mWatchDog.setANRListener(new ANRWatchDog.ANRListener() {
            @Override
            public void onAppNotResponding(ANRError error) {
                Log.e(TAG, "App is frozen/hanging.");
            }
        }).start();*/
    }
}
