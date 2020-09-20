package ai.cellbots.common;

import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import ai.cellbots.common.concurrent.AtomicByteArray;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for AtomicByteArray.
 */
public class AtomicByteArrayTest {
    /**
     * Tests creation of array.
     */
    @Test
    public void testArrayCreation() {
        // Zero length
        AtomicByteArray byteArray = new AtomicByteArray(0);
        assertTrue(byteArray != null);
        assertEquals(0, byteArray.length());

        // Length of 1
        byteArray = new AtomicByteArray(1);
        assertEquals(1, byteArray.length());
    }

    /**
     * Tests all accessors correctly set and get the values in the array on a single thread.
     */
    @Test
    public void testAllFunctionsOnSingleThread() {
        final int arrayLength = 10;
        final int offset = 13;
        AtomicByteArray byteArray = new AtomicByteArray(arrayLength);

        // length()
        assertEquals(arrayLength, byteArray.length());

        // set()
        for (int i = 0; i < arrayLength; i++) {
            byteArray.set(i, (byte) (i + offset));
        }

        // compareAndSet()
        for (int i = 0; i < arrayLength; i++) {
            assertTrue(byteArray.compareAndSet(i, (byte) (i + offset), (byte) (i + offset + 1)));
        }

        // get()
        for (int i = 0; i < arrayLength; i++) {
            assertEquals((byte) (i + offset + 1), byteArray.get(i));
        }

        // getAndIncrement()
        for (int i = 0; i < arrayLength; i++) {
            assertEquals((byte) (i + offset + 1), byteArray.getAndIncrement(i));
        }

        // getAndDecrement()
        for (int i = 0; i < arrayLength; i++) {
            assertEquals((byte) (i + offset + 2), byteArray.getAndDecrement(i));
        }

        // incrementAndGet()
        for (int i = 0; i < arrayLength; i++) {
            assertEquals((byte) (i + offset + 2), byteArray.incrementAndGet(i));
        }

        // decrementAndGet()
        for (int i = 0; i < arrayLength; i++) {
            assertEquals((byte) (i + offset + 1), byteArray.decrementAndGet(i));
        }

        // addAndGet()
        for (int i = 0; i < arrayLength; i++) {
            assertEquals((byte) (i + offset + 4), byteArray.addAndGet(i, 3));
        }

        // clone()
        byte[] currentArray = byteArray.clone();
        assertTrue(currentArray != null);
        assertEquals(arrayLength, currentArray.length);
        for (int i = 0; i < arrayLength; i++) {
            assertEquals(byteArray.get(i), currentArray[i]);
        }

        // copyFrom()
        byteArray.set(0, (byte) 1);
        byteArray.set(1, (byte) 2);
        assertTrue(byteArray.copyFrom(currentArray));
        for (int i = 0; i < arrayLength; i++) {
            assertEquals(currentArray[i], byteArray.get(i));
        }

        // toString()
        String stringArray = byteArray.toString();
        for (int i = 0; i < arrayLength; i++) {
            assertTrue(stringArray.contains(Byte.toString((byte) (i + offset + 4))));
        }
    }

    /**
     * Tests accessors correctly set and get values in the arrays on multiple threads.
     */
    @Test
    public void testAllFunctionsOnMultiThread() {
        final int arrayLength = 8192;
        final int numOperation = 30;
        final AtomicByteArray byteArray = new AtomicByteArray(arrayLength);

        List<Runnable> runnables = new LinkedList<>();
        final int THREADS_ALL = 7;
        final int THREADS_INC = 4;
        final int THREADS_DEC = 3;
        // Runnables for incrementing the values.
        for (int i = 0; i < THREADS_INC; i++) {
            runnables.add(new Runnable() {
                @Override
                public void run() {
                    for (int index = 0; index < arrayLength; index++) {
                        for (int i = 0; i < numOperation; i++) {
                            final int unusedValue = byteArray.incrementAndGet(index);
                        }
                    }
                }
            });
        }
        // Runnables for decrementing the values.
        for (int i = 0; i < THREADS_DEC; i++) {
            runnables.add(new Runnable() {
                @Override
                public void run() {
                    for (int index = 0; index < arrayLength; index++) {
                        for (int i = 0; i < numOperation; i++) {
                            final int unusedValue = byteArray.decrementAndGet(index);
                        }
                    }
                }
            });
        }

        List<Thread> threads = new LinkedList<>();
        for (int i = 0; i < THREADS_ALL; i++) {
            threads.add(new Thread(runnables.get(i)));
        }
        for (int i = 0; i < THREADS_ALL; i++) {
            threads.get(i).start();
        }
        try {
            for (int i = 0; i < THREADS_ALL; i++) {
                threads.get(i).join();
            }
        } catch (InterruptedException e) {
            Assert.fail("Join failed: " + e);
        }

        for (int index = 0; index < arrayLength; index++) {
            assertEquals((byte) numOperation, byteArray.get(index));
        }
    }
}