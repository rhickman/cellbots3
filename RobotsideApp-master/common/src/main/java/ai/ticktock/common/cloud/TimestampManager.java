package ai.cellbots.common.cloud;

import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.Date;

/**
 * Manages a data object's Timestamp field, and manage the local time synchronized with the cloud
 * timestamp.
 */
public class TimestampManager {
    private static final String TAG = TimestampManager.class.getSimpleName();
    private static final Object sLock = new Object();
    private static long sTimestampOffset = 0;
    private static long sSynchronizedTime = 0;

    private static final long SYNCHRONIZE_RATE = 60 * 1000; // Time to synchronize in ms
    // Considered out of sync if longer than this many ms have passed.
    private static final long SYNCHRONIZE_TIMEOUT = SYNCHRONIZE_RATE + 10 * 1000;

    /**
     * Returns true if timestamp values are synchronized with the server.
     * @return True if the values are synchronized.
     */
    public static boolean isSynchronized() {
        synchronized (sLock) {
            return sSynchronizedTime != 0
                    && sSynchronizedTime > new Date().getTime() - SYNCHRONIZE_TIMEOUT;
        }
    }

    /**
     * If the time has exceeded the rate, the system is re-synchronized.
     */
    public static void synchronize() {
        synchronized (sLock) {
            if (sSynchronizedTime != 0
                    && sSynchronizedTime > new Date().getTime() - SYNCHRONIZE_RATE) {
                return;
            }
        }
        FirebaseDatabase.getInstance().getReference().child(".info").child("serverTimeOffset")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot == null || dataSnapshot.getValue() == null) {
                            Log.w(TAG, "Unable to get synchronization time, snapshot null");
                            return;
                        }
                        Long nv;
                        try {
                            nv = dataSnapshot.getValue(Long.class);
                        } catch (DatabaseException ex) {
                            Log.w(TAG, "Unable to get synchronization time, snapshot error", ex);
                            return;
                        }
                        if (nv == null) {
                            Log.w(TAG, "Unable to get synchronization time, snapshot result null");
                            return;
                        }
                        synchronized (sLock) {
                            sSynchronizedTime = new Date().getTime();
                            sTimestampOffset = nv;
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                    }
                });
    }

    /**
     * Get the current global timestamp, synchronized with the server.
     * @return True if the timestamp is synchronized.
     */
    public static long getCurrentTimestamp() {
        synchronized (sLock) {
            return new Date().getTime() + sTimestampOffset;
        }
    }

    /**
     * An interface for an object that has a timestamp and is compatible with firebase.
     */
    public interface Timestamped {
        /**
         * Get the current timestamp of the object as a long. In general, this simply calls
         * TimestampManager.getTimestamp(), which in turn calls getRawTimestamp().
         * @return The timestamp, or 0 if invalid.
         */
        long getTimestamp();

        /**
         * Get the raw timestamp of the object. May be null, 0, an Integer, a Long a String or
         * ServerValue.TIMESTAMP. It may also return another object in the event of an error.
         * Use TimestampManager.getTimestamp() on this object's timestamp manager to get the
         * actual timestamp.
         * @return The raw timestamp object.
         */
        Object getRawTimestamp();
    }

    private final long mStart;
    private final Timestamped mTimestamped;

    /**
     * Create a timestamp manager for an object that implements Timestamped.
     * @param object The object.
     */
    public TimestampManager(Timestamped object) {
        mTimestamped = object;
        mStart = getCurrentTimestamp();
    }

    /**
     * Create a timestamp manager for an object that implements Timestamped.
     * @param object The object.
     * @param copy The object to copy from.
     */
    public TimestampManager(Timestamped object, Timestamped copy) {
        mTimestamped = object;
        if (ServerValue.TIMESTAMP.equals(copy.getRawTimestamp())) {
            mStart = copy.getTimestamp();
        } else {
            mStart = getCurrentTimestamp();
        }
    }

    /**
     * Get the timestamp of the object.
     * @return The timestamp as a long, or 0 if invalid.
     */
    public long getTimestamp() {
        Object raw = mTimestamped.getRawTimestamp();
        if (raw == null) {
            return 0;
        }
        if (ServerValue.TIMESTAMP.equals(raw)) {
            return mStart;
        }
        if (raw instanceof Long) {
            return (long) raw;
        }
        if (raw instanceof Integer) {
            return (long) raw;
        }
        if (raw instanceof Double) {
            return (long) ((double)raw);
        }
        if (raw instanceof Float) {
            return (long) ((float)raw);
        }
        if (raw instanceof String) {
            try {
                return Long.valueOf(raw.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
