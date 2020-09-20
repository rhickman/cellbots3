package ai.cellbots.common.cloud;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Manages the synchronization of an object that is consistently updated.
 * @param <T> The object type to be stored.
 */
public class Synchronizer<T extends TimestampManager.Timestamped> {
    private final String[] mSources;
    private final Set<String> mSourcesSet;
    private final HashMap<String, T> mLatest;
    private final long mTimeout;

    /**
     * An interface used for determining if an argument is acceptable by some criteria.
     * @param <T> The type for the object.
     */
    public interface Criteria<T> {
        /**
         * Determine if the value is acceptable.
         * @param value The object value to assess. Must not be null.
         * @return True if acceptable by the criteria.
         */
        boolean isAcceptable(@NonNull T value);
    }

    /**
     * Create a synchronizer.
     * @param sources The list of named sources for the system.
     */
    public Synchronizer(@NonNull String[] sources) {
        this(sources, 0, new HashMap<String, T>());
    }
    /**
     *
     * Create a synchronizer.
     * @param sources The list of named sources for the system.
     * @param timeout The timeout. May be 0 for no timeout.
     */
    public Synchronizer(@NonNull String[] sources, long timeout) {
        this(sources, timeout, new HashMap<String, T>());
    }

    /**
     * Create a synchronizer.
     * @param sources The list of named sources for the system.
     * @param timeout The timeout. May be 0 for no timeout.
     * @param initialValues The map of initial values for the system.
     */
    public Synchronizer(@NonNull String[] sources, long timeout, @NonNull Map<String, T> initialValues) {
        Objects.requireNonNull(sources);
        Objects.requireNonNull(initialValues);
        for (String s : sources) {
            Objects.requireNonNull(s);
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("Timeout must be non-zero");
        }
        mTimeout = timeout;
        mSources = Arrays.copyOf(sources, sources.length);
        HashSet<String> sourceSet = new HashSet<>(sources.length);
        Collections.addAll(sourceSet, mSources);
        mSourcesSet = Collections.unmodifiableSet(sourceSet);
        if (mSources.length != mSourcesSet.size()) {
            throw new IllegalArgumentException("Duplicate source");
        }
        mLatest = new HashMap<>();
    }

    /**
     * Get if there is a valid value for a source.
     * @param source The source.
     * @param time The current timestamp.
     * @return True if there is an object for the source.
     */
    private synchronized boolean hasValueForSource(String source, long time) {
        return mLatest.containsKey(source) &&
                (mTimeout == 0 || mLatest.get(source).getTimestamp() > time - mTimeout);
    }

    /**
     * Set a value for the synchronizer.
     * @param source The source channel. May be null to clear.
     * @param value The value to write.
     */
    public synchronized void setValue(String source, T value) {
        if (!mSourcesSet.contains(source)) {
            throw new IllegalArgumentException("Source invalid: " + source);
        }
        if (value != null) {
            mLatest.put(source, value);
        } else if (mLatest.containsKey(source)) {
            mLatest.remove(source);
        }
    }

    /**
     * Set a value for the synchronizer.
     * @param source The source channel. May be null to clear.
     * @param values The values to write. The latest non-null value is selected.
     */
    public synchronized void setValues(String source, T[] values) {
        if (!mSourcesSet.contains(source)) {
            throw new IllegalArgumentException("Source invalid: " + source);
        }
        T value = null;
        for (T cv : values) {
            if (cv != null) {
                if ((value == null) || (value.getTimestamp() < cv.getTimestamp())) {
                    value = cv;
                }
            }
        }
        setValue(source, value);
    }

    /**
     * Get the newest value that has not timed out.
     * @return The newest value or null if not found.
     */
    public synchronized T getNewestValue() {
        T best = null;
        long time = TimestampManager.getCurrentTimestamp();
        for (String source : mSources) {
            if (hasValueForSource(source, time)) {
                if (best == null || mLatest.get(source).getTimestamp() > best.getTimestamp()) {
                    best = mLatest.get(source);
                }
            }
        }
        return best;
    }

    /**
     * Get the newest value that has not timed out and meets the criteria.
     * @param criteria The criteria for filtering the values.
     * @return The newest value or null if not found.
     */
    @SuppressWarnings("unused")
    public synchronized T getNewestValue(@NonNull Criteria<T> criteria) {
        Objects.requireNonNull(criteria);
        T best = null;
        long time = TimestampManager.getCurrentTimestamp();
        for (String source : mSources) {
            if (hasValueForSource(source, time) && criteria.isAcceptable(mLatest.get(source))) {
                if (best == null || mLatest.get(source).getTimestamp() > best.getTimestamp()) {
                    best = mLatest.get(source);
                }
            }
        }
        return best;
    }

    /**
     * Get a specific value.
     * @param source The source channel.
     * @return The source, or null if not present.
     */
    public synchronized T getValue(String source) {
        if (!mSourcesSet.contains(source)) {
            throw new IllegalArgumentException("Source invalid");
        }
        return mLatest.containsKey(source) ? mLatest.get(source) : null;
    }

    /**
     * Get the first value that has not timed out.
     * @return The first value that has not timed out.
     */
    @SuppressWarnings("unused")
    public synchronized T getFirstValue() {
        long time = TimestampManager.getCurrentTimestamp();
        for (String source : mSources) {
            if (hasValueForSource(source, time)) {
                return mLatest.get(source);
            }
        }
        return null;
    }

    /**
     * Get the first value that has not timed out and meets the criteria.
     * @param criteria The criteria for filtering the values.
     * @return The first value that has not timed out and isAcceptable().
     */
    public synchronized T getFirstValue(@NonNull Criteria<T> criteria) {
        Objects.requireNonNull(criteria);
        long time = TimestampManager.getCurrentTimestamp();
        for (String source : mSources) {
            if (hasValueForSource(source, time) && criteria.isAcceptable(mLatest.get(source))) {
                return mLatest.get(source);
            }
        }
        return null;
    }

    /**
     * Get the values that have not timed out in order of priority.
     * @return The list of values.
     */
    @SuppressWarnings("unused")
    public synchronized List<T> orderByPriority() {
        List<T> list = new ArrayList<>(mSources.length);
        long time = TimestampManager.getCurrentTimestamp();
        for (String source : mSources) {
            if (hasValueForSource(source, time)) {
                list.add(mLatest.get(source));
            }
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Get the values that have not timed out in order of priority.
     * @param criteria The criteria for filtering the values.
     * @return The list of values.
     */
    @SuppressWarnings("unused")
    public synchronized List<T> orderByPriority(@NonNull Criteria<T> criteria) {
        Objects.requireNonNull(criteria);
        List<T> list = new ArrayList<>(mSources.length);
        long time = TimestampManager.getCurrentTimestamp();
        for (String source : mSources) {
            if (hasValueForSource(source, time) && criteria.isAcceptable(mLatest.get(source))) {
                list.add(mLatest.get(source));
            }
        }
        return Collections.unmodifiableList(list);
    }
}
