package ai.cellbots.common.cloud;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;

import java.util.LinkedList;
import java.util.List;

/**
 * Log events and errors to firebase.
 */
public enum CloudLog {
    ;
    private static final String TAG = CloudLog.class.getSimpleName();

    private static final String FORMATTING_ERROR = "FIREBASE_FORMATTING_ERROR";
    private static final String FORMATTING_ERROR_PATH = "PATH_";
    private static final String FORMATTING_ERROR_TEXT = "ERROR";

    /**
     * Create a report for when a firebase object fails to meet formatting criteria.
     * @param parent The parent.
     * @param path The path.
     * @param error The error.
     */
    static public void reportFirebaseFormattingError(Context parent, List<String> path, String error) {
        StringBuilder print = new StringBuilder("Database formatting: '");
        Bundle bundle = new Bundle();
        bundle.putString(FORMATTING_ERROR_TEXT, error);
        for (int i = 0; i < path.size(); i++) {
            if (i != 0) {
                print.append("'/'");
            }
            print.append(path.get(i));
            bundle.putString(FORMATTING_ERROR_PATH + i, path.get(i));
        }
        print.append("' - error: ");
        print.append(error);
        Log.e(TAG, print.toString());
        FirebaseAnalytics.getInstance(parent).logEvent(FORMATTING_ERROR, bundle);
    }

    /**
     * Create a report for when a firebase object fails to meet formatting criteria.
     * @param parent The parent.
     * @param path The path.
     * @param error The error.
     */
    static public void reportFirebaseFormattingError(Context parent, DataSnapshot path, String error) {
        reportFirebaseFormattingError(parent, path.getRef(), error);
    }

    /**
     * Create a report for when a firebase object fails to meet formatting criteria.
     * @param parent The parent.
     * @param path The path.
     * @param error The error.
     */
    static public void reportFirebaseFormattingError(Context parent, DatabaseReference path, String error) {
        LinkedList<String> r = new LinkedList<>();
        while (path != null) {
            if (path.getKey() != null) {
                r.push(path.getKey());
            }
            path = path.getParent();
        }
        reportFirebaseFormattingError(parent, r, error);
    }
}
