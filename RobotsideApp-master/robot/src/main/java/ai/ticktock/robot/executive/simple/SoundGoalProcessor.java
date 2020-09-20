package ai.cellbots.robot.executive.simple;

import android.util.Log;

import java.util.Date;

import ai.cellbots.robot.executive.Executive;
import ai.cellbots.robot.executive.Goal;
import ai.cellbots.robot.executive.WorldState;
import ai.cellbots.robot.manager.SoundManager;

/**
 * Process sound goals.
 */
final public class SoundGoalProcessor extends GoalProcessor {
    private static final String TAG = SoundGoalProcessor.class.getSimpleName();

    public static final SoundGoalProcessorFactory ALARM_GOAL_FACTORY
            = new SoundGoalProcessorFactory(SoundManager.GoalSound.ALARM, "time");
    public static final SoundGoalProcessorFactory YOU_ARE_WELCOME_GOAL_FACTORY
            = new SoundGoalProcessorFactory(SoundManager.GoalSound.YOU_ARE_WELCOME, null);

    private SoundManager.GoalSound mSoundType;
    private SoundManager.Sound mSound = null;
    private String mTimeoutVariable;
    private long mStopTime = 0;

    /**
     * A factory for sound goals.
     */
    private final static class SoundGoalProcessorFactory
            implements GoalProcessor.GoalProcessorFactory {
        private SoundManager.GoalSound mSoundType;
        private String mTimeoutVariable;

        /**
         * Create a new sound goal factory.
         *
         * @param soundType The sound type.
         * @param timeout The playing timeout.
         */
        private SoundGoalProcessorFactory(SoundManager.GoalSound soundType, String timeout) {
            mSoundType = soundType;
            mTimeoutVariable = timeout;
        }

        @Override
        public GoalProcessor createGoalProcessor(Goal goal) {
            return new SoundGoalProcessor(goal, mSoundType, mTimeoutVariable);
        }
    }

    /**
     * Create the goal processor.
     *
     * @param goal The goal.
     * @param soundType The sound type.
     * @param timeout The sound timeout.
     */
    private SoundGoalProcessor(Goal goal, SoundManager.GoalSound soundType, String timeout) {
        super(goal);
        mSoundType = soundType;
        mTimeoutVariable = timeout;
    }

    /**
     * Process the goal.
     *
     * @param delegate The delegate for the planner.
     * @param worldState The world state.
     * @param cancel  True if the goal should be cancelled.
     * @param preempt True if the goal should be preempted.
     * @return The goal state.
     */
    @Override
    public Executive.GoalState processGoal(Executive.Delegate delegate, WorldState worldState,
            boolean cancel, boolean preempt) {
        Log.d(TAG, "Processing sound goal.");
        if (mSound == null) {
            mSound = delegate.getSoundManager().playGoalSound(mSoundType, 100);
            if (mTimeoutVariable != null) {
                mStopTime = (long) getGoal().getParameters().get(mTimeoutVariable);
            }
        }
        if (mSound == null || !mSound.isPlaying()) {
            return Executive.GoalState.COMPLETE;
        }
        if (mTimeoutVariable != null && new Date().getTime() > mStopTime) {
            mSound.stop();
            return Executive.GoalState.COMPLETE;
        }
        if (cancel || preempt) {
            mSound.stop();
            return cancel ? Executive.GoalState.REJECT : Executive.GoalState.PREEMPT;
        }
        return Executive.GoalState.RUNNING;
    }
}
