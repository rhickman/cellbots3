package ai.cellbots.container;

import android.support.annotation.NonNull;

import java.util.HashMap;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Fibonacci heap for implementing Dijkstra's
 * @param <T> The class to be stored in the heap.
 * TODO: implement an actual Fibonacci heap.
 */
public class FibonacciHeap<T> {
    /**
     * Class for internal storage representation.
     *
     * @param <T> The class to be stored in the heap.
     */
    private final static class ScorePair<T> implements Comparable<ScorePair<T>> {
        private final T mTarget;
        private final double mScore;

        private ScorePair(T target, double score) {
            Objects.requireNonNull(target);
            mTarget = target;
            mScore = score;
        }

        /**
         * Compare a ScorePair to another ScorePair.
         * @param o The other object.
         * @return The compare value.
         */
        @Override
        public int compareTo(@NonNull ScorePair<T> o) {
            return Double.compare(mScore, o.mScore);
        }
    }

    private final PriorityQueue<ScorePair<T>> mSorted = new PriorityQueue<>();
    private final HashMap<T, ScorePair<T>> mNodeMap = new HashMap<>();
    private final HashMap<T, Double> mScores = new HashMap<>();

    /**
     * Create the FibonacciHeap.
     */
    public FibonacciHeap() {
    }

    /**
     * Set the score of an element of the heap. If the element was removed from the heap
     * it will be added back in to the heap.
     *
     * @param target The target element to set or re-add.
     * @param score  The score of the element.
     */
    public void setScore(@NonNull T target, double score) {
        Objects.requireNonNull(target);
        if (mNodeMap.containsKey(target)) {
            ScorePair<T> sp = mNodeMap.get(target);
            mSorted.remove(sp);
            mNodeMap.remove(target);
        }
        ScorePair<T> sp = new ScorePair<>(target, score);
        mSorted.add(sp);
        mNodeMap.put(target, sp);
        mScores.put(target, score);
    }

    /**
     * Size of the heap queue.
     * @return The size of the queue.
     */
    public int size() {
        return mSorted.size();
    }

    /**
     * Get the score of a value.
     * @param target The target element of which to get the score.
     * @return The score value for the element.
     */
    public double getScore(@NonNull T target) {
        Objects.requireNonNull(target);
        return mScores.get(target);
    }

    /**
     * Get if the heap is empty.
     * @return True if the heap is empty.
     */
    public boolean isEmpty() {
        return mSorted.isEmpty();
    }

    /**
     * Remove an element from the heap but preserve the score value. The element removed always
     * has the lowest possible score.
     * @return The element object.
     */
    public T pop() {
        ScorePair<T> next = mSorted.peek();
        mSorted.remove(next);
        mNodeMap.remove(next.mTarget);
        return next.mTarget;
    }

}
