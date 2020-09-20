package ai.cellbots.robotlib;

import android.content.Context;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;
import java.util.HashSet;

/**
 * Handles exceptions in the service, so that it the said exceptions can be managed.
 */
public class CrashReporter implements Thread.UncaughtExceptionHandler {
    private static final String TAG = CrashReporter.class.getSimpleName();

    private final Thread.UncaughtExceptionHandler mDefaultUEH;
    private final Context mContext;

    private static final String EVENT_CRASH = "CRASH";
    private static final String EVENT_EXCEPTION = "EXCEPTION_";
    private static final String EVENT_STACK_TRACE = "STACK_TRACE_";

    private static final int TRUNCATE_STRING_LENGTH = 192;

    /**
     * Creates a crash reporter.
     * @param context The global context.
     */
    public CrashReporter(Context context) {
        mDefaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        mContext = context;
    }

    /**
     * Truncate strings over 192 characters, so that firebase will accept them.
     * @param str The string to truncate.
     * @return The truncated result.
     */
    private static String truncateForFirebase(String str) {
        if (str.length() > TRUNCATE_STRING_LENGTH) {
            return str.substring(0, TRUNCATE_STRING_LENGTH);
        }
        return str;
    }

    /**
     * Called when an exception is thrown.
     * @param t The thread that had the exception.
     * @param e The exception value.
     */
    public void uncaughtException(Thread t, Throwable e) {
        Bundle bundle = new Bundle();
        int i = 0;
        Throwable currentException = e;
        HashSet<Throwable> blacklist = new HashSet<>();

        while (currentException != null) {
            blacklist.add(currentException);
            bundle.putString(EVENT_EXCEPTION + i, truncateForFirebase(currentException.toString()));
            StackTraceElement[] arr = currentException.getStackTrace();
            for (int k = 0; k < arr.length && k < 5; k++) {
                bundle.putString(EVENT_STACK_TRACE + i + "_" + k,
                        truncateForFirebase(arr[k].toString()));
            }
            i++;
            currentException = currentException.getCause();
            if (blacklist.contains(currentException)) {
                currentException = null;
            }
        }
        FirebaseAnalytics.getInstance(mContext).logEvent(EVENT_CRASH, bundle);

        mDefaultUEH.uncaughtException(t, e);
    }
}
