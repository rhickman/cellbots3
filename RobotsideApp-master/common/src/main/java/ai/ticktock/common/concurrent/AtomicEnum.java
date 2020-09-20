package ai.cellbots.common.concurrent;

import java.util.concurrent.atomic.AtomicReference;

/**
 * AtomicEnum provides atomic operation on enumeration.
 *
 * This is thread safe. Ok to access any public members concurrently without the need for external
 * synchronization.
 *
 * @param <T> Enum type.
 */
public final class AtomicEnum<T extends Enum<T>> implements java.io.Serializable {
    private static final long serialVersionUID = -2308431214976778501L;

    // Atomic reference.
    private final AtomicReference<T> mReference;

    /**
     * Constructs a new atomic Enum object.
     *
     * @param reference  Reference to the Enum object.
     */
    public AtomicEnum(final T reference) {
        mReference = new AtomicReference<>(reference);
    }

    /**
     * Sets to the given value of the Enum.
     *
     * @param newValue the new value to set.
     */
    public void set(final T newValue) {
        mReference.set(newValue);
    }

    /**
     * Gets the current value.
     *
     * @return the current value.
     */
    public T get() {
        return mReference.get();
    }

    /**
     * Sets the given value and returns the old value.
     *
     * @param newValue the new value to set.
     * @return the old value.
     */
    public T getAndSet(final T newValue) {
        return mReference.getAndSet(newValue);
    }

    /**
     * Atomically set the value if the current value == the expected value.
     *
     * @param expectedValue Expected value.
     * @param newValue New value.
     * @return True if successful.
     */
    public boolean compareAndSet(final T expectedValue, final T newValue) {
        return mReference.compareAndSet(expectedValue, newValue);
    }
}
