package ai.cellbots.common.concurrent;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * AtomicByteArray provides atomic operation on Byte.
 *
 * This is thread safe. Ok to access any public members concurrently without the need for external
 * synchronization.
 */
public class AtomicByteArray implements java.io.Serializable {
    private static final long serialVersionUID = -2308431214976778500L;
    private final AtomicIntegerArray mArray;
    private final int mLength;

    /**
     * Creates a new atomic byte array. Initializes all elements to zero.
     *
     * @param length the length of the byte array.
     */
    public AtomicByteArray(int length) {
        mLength = length;
        mArray = new AtomicIntegerArray((length + 3) / 4);
    }

    /**
     * Gets the current value at the index.
     *
     * @param index the index.
     * @return the current value.
     */
    public byte get(int index) {
        return (byte) (mArray.get(index >>> 2) >> ((index & 3) << 3));
    }

    /**
     * Sets the new value at the index in the array.
     *
     * @param index the index.
     * @param value the value to set.
     */
    public void set(int index, byte value) {
        int integer_index = index >>> 2;
        int shift_in_integer = (index & 3) << 3;
        int mask = 0xFF << shift_in_integer;
        int integer_value = (value & 0xff) << shift_in_integer;
        while (true) {
            int integerValue = mArray.get(integer_index);
            int valueToSet = (integerValue & ~ mask) | integer_value;
            if ((integerValue == valueToSet) ||
                    mArray.compareAndSet(integer_index, integerValue, valueToSet)) {
                return;
            }
        }
    }

    /**
     * Atomically sets the value at the index in the array if the current value is equivalent to
     * the expected value.
     *
     * @param index the index.
     * @param expect the expected value.
     * @param value the value to set.
     *
     * @return True if the value is set successful. False when the actual value is not equivalent
     * to the expected value.
     */
    public boolean compareAndSet(int index, byte expect, byte value) {
        int integer_index = index >>> 2;
        int shift = (index & 3) << 3;
        int mask = 0xFF << shift;
        int integer_expected = (expect & 0xff) << shift;
        int integer_value = (value & 0xff) << shift;
        while (true) {
            int currentIntegerValue = mArray.get(integer_index);
            if ((currentIntegerValue & mask) != integer_expected) {
                return false;
            }
            int valueToSet = (currentIntegerValue & ~ mask) | integer_value;
            if ((currentIntegerValue == valueToSet) ||
                    mArray.compareAndSet(integer_index, currentIntegerValue, valueToSet)) {
                return true;
            }
        }
    }

    /**
     * Atomically increments by one the value at the index.
     *
     * @param index the index.
     * @return the previous value.
     */
    public final byte getAndIncrement(int index) {
        return getAndAdd(index, 1);
    }

    /**
     * Atomically decrements by one the value at index.
     *
     * @param index the index.
     * @return the previous value.
     */
    public final byte getAndDecrement(int index) {
        return getAndAdd(index, - 1);
    }

    /**
     * Atomically adds the given value to the element at index.
     *
     * @param index the index.
     * @param delta the value to add.
     * @return the previous value.
     */
    public final byte getAndAdd(int index, int delta) {
        while (true) {
            byte current = get(index);
            byte next = (byte) (current + delta);
            if (compareAndSet(index, current, next)) {
                return current;
            }
        }
    }

    /**
     * Atomically increments by one the value at index.
     *
     * @param index the index.
     * @return the updated value.
     */
    public final byte incrementAndGet(int index) {
        return addAndGet(index, 1);
    }

    /**
     * Atomically decrements by one the value at index.
     *
     * @param index the index.
     * @return the updated value.
     */
    public final byte decrementAndGet(int index) {
        return addAndGet(index, - 1);
    }

    /**
     * Atomically adds the given value to the value at index.
     *
     * @param index the index.
     * @param delta the value to add.
     * @return the updated value
     */
    public final byte addAndGet(int index, int delta) {
        while (true) {
            byte current = get(index);
            byte next = (byte) (current + delta);
            if (compareAndSet(index, current, next)) {
                return next;
            }
        }
    }

    /**
     * Clones the current array.
     *
     * @return A copy of byte array.
     */
    public byte[] clone() {
        byte array[] = new byte[mLength];
        for (int i = 0; i < mLength; i++) {
            array[i] = get(i);
        }
        return array;
    }

    /**
     * Copies from an array. If an array is provided and its length is the same as the current
     * array's length, then the values in the input array will be copied to the internal array.
     *
     * @param array  Input array.
     * @return True if successfully copied.
     */
    public boolean copyFrom(byte[] array) {
        if ((array == null) || (array.length != mLength)) {
            return false;
        }
        for (int i = 0; i < mLength; i++) {
            set(i, array[i]);
        }
        return true;
    }

    /**
     * Returns a string representation of the current values of array.
     * The returned values are not guaranteed to be from the same time instant.
     *
     * @return the String representation of the current values of array.
     */
    @Override
    public String toString() {
        return Arrays.toString(clone());
    }

    /**
     * Returns the length of the array.
     *
     * @return the length of the array
     */
    public int length() {
        return mLength;
    }
}