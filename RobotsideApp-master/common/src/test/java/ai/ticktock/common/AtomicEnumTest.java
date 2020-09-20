package ai.cellbots.common;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import ai.cellbots.common.concurrent.AtomicEnum;

import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;

/**
 * Test for AtomicEnum.
 */
@SuppressWarnings("unused")
public class AtomicEnumTest {
    /**
     * Enumeration of Fruits.
     */
    private enum Fruit {
        APPLE,
        ORANGE,
        BANANA,
        PEAR,
        KIWI;

        /**
         * Returns the next fruit.
         *
         * @return Next fruit. May be empty.
         */
        public Fruit next() {
            switch (this) {
                case APPLE:
                    return ORANGE;
                case ORANGE:
                    return BANANA;
                case BANANA:
                    return PEAR;
                case PEAR:
                    return KIWI;
                case KIWI:
                    return APPLE;
                default:
                    throw new IllegalArgumentException("Unknown fruit");
            }
        }
    }

    /**
     * Tests all accessors are correctly setting new Enum values on a single thread.
     */
    @Test
    public void testAtomicEnumOnSingleThread() {
        final AtomicEnum<Fruit> favoriteFruit = new AtomicEnum<>(Fruit.APPLE);
        Runnable runnable1 = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 1000; i++) {
                    // set()
                    final Fruit currentFruit = favoriteFruit.get();
                    final Fruit nextFruit = currentFruit.next();
                    favoriteFruit.set(nextFruit);
                    assertEquals(nextFruit, favoriteFruit.get());

                    // getAndSet()
                    final Fruit obtainedFruit = favoriteFruit.getAndSet(nextFruit);
                    assertEquals(nextFruit, obtainedFruit);

                    // compareAndSet()
                    final Fruit nextNextFruit = nextFruit.next();
                    assertTrue(favoriteFruit.compareAndSet(nextFruit, nextNextFruit));
                    assertEquals(nextNextFruit, favoriteFruit.get());
                }
            }
        };
        Thread thread1 = new Thread(runnable1);
        thread1.start();
        try {
            thread1.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Tests accessors are correctly setting values on multiple threads.
     */
    @Test
    public void testAtomicEnumOnMultiThread() {
        final int numIteration = 100000;
        final AtomicEnum<Fruit> favoriteFruit = new AtomicEnum<>(Fruit.APPLE);
        final AtomicInteger count1 = new AtomicInteger(0);
        final AtomicInteger count2 = new AtomicInteger(0);
        final AtomicInteger count3 = new AtomicInteger(0);
        final AtomicInteger count4 = new AtomicInteger(0);
        final AtomicInteger count5 = new AtomicInteger(0);

        Runnable runnable1 = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < numIteration; i++) {
                    if (favoriteFruit.compareAndSet(Fruit.APPLE, Fruit.APPLE.next())) {
                        final int unusedCount = count1.incrementAndGet();
                    }
                }
            }
        };
        Runnable runnable2 = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < numIteration; i++) {
                    if (favoriteFruit.compareAndSet(Fruit.ORANGE, Fruit.ORANGE.next())) {
                        final int unusedCount = count2.incrementAndGet();
                    }
                }
            }
        };
        Runnable runnable3 = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < numIteration; i++) {
                    if (favoriteFruit.compareAndSet(Fruit.BANANA, Fruit.BANANA.next())) {
                        final int unusedCount = count3.incrementAndGet();
                    }
                }
            }
        };
        Runnable runnable4 = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < numIteration; i++) {
                    if (favoriteFruit.compareAndSet(Fruit.PEAR, Fruit.PEAR.next())) {
                        final int unusedCount = count4.incrementAndGet();
                    }
                }
            }
        };
        Runnable runnable5 = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < numIteration; i++) {
                    if (favoriteFruit.compareAndSet(Fruit.KIWI, Fruit.KIWI.next())) {
                        final int unusedCount = count5.incrementAndGet();
                    }
                }
            }
        };
        Thread thread1 = new Thread(runnable1);
        Thread thread2 = new Thread(runnable2);
        Thread thread3 = new Thread(runnable3);
        Thread thread4 = new Thread(runnable4);
        Thread thread5 = new Thread(runnable5);
        thread1.start();
        thread2.start();
        thread3.start();
        thread4.start();
        thread5.start();
        try {
            thread1.join();
            thread2.join();
            thread3.join();
            thread4.join();
            thread5.join();
        } catch (InterruptedException e) {
            Assert.fail("Join failed: " + e);
        }

        // The actual number of times of increment may not be consistent over each test run, but
        // each increment should depends on one of other threads' output, so the differences among
        // the counters should always be 0 or 1.
        assertThat(count1.get() - count2.get(), anyOf(equalTo(0), equalTo(1)));
        assertThat(count2.get() - count3.get(), anyOf(equalTo(0), equalTo(1)));
        assertThat(count3.get() - count4.get(), anyOf(equalTo(0), equalTo(1)));
        assertThat(count4.get() - count5.get(), anyOf(equalTo(0), equalTo(1)));
    }
}