package ai.cellbots.executive.action;

import java.util.Date;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.executive.ExecutivePlanner;
import ai.cellbots.executive.MacroGoal;
import ai.cellbots.executive.WorldState;
import ai.cellbots.robotlib.Controller;
import ai.cellbots.robotlib.SoundManager;

/**
 * Action that plays a sound until either the sound is completed or a certain time limit is up.
 */
public class SoundPlayerAction extends Action {
    private final SoundManager.GoalSound mSoundType;
    private final String mTimeoutVariable;

    /**
     * Creates a sound player action with a given sound starter.
     * @param soundType The sound starter.
     * @param timeoutVariable The name of the timeout parameter. Null for no timeouts.
     */
    public SoundPlayerAction(SoundManager.GoalSound soundType, String timeoutVariable) {
        mSoundType = soundType;
        mTimeoutVariable = timeoutVariable;
    }

    /**
     * SoundPlayer instance.
     */
    private final class SoundPlayerActionInstance extends ActionInstance {
        private SoundManager.Sound mSound = null;
        private long mStartTime = 0;
        private long mStopTime = 0;

        /**
         * Create the ActionInstance.
         * @param goal The goal to be achieved.
         * @param callback The callback for the ExecutivePlanner executing the action.
         */
        private SoundPlayerActionInstance(ActionGoal goal, ExecutivePlanner.Callback callback) {
            super(goal, callback);
        }

        @Override
        public ExecutivePlanner.GoalState update(WorldState worldState, boolean cancel,
                Controller controller, boolean preemptGoal, DetailedWorld world) {
            if (mSound == null) {
                mSound = playGoalSound(mSoundType);
                mStartTime = new Date().getTime();
                if (mTimeoutVariable != null) {
                    mStopTime = mStartTime + (long) getGoal().getParameters().get(mTimeoutVariable);
                }
            }
            if (mSound == null || !mSound.isPlaying()) {
                return ExecutivePlanner.GoalState.COMPLETE;
            }
            if (mTimeoutVariable != null && new Date().getTime() > mStopTime) {
                mSound.stop();
                return ExecutivePlanner.GoalState.COMPLETE;
            }
            if (cancel || preemptGoal) {
                mSound.stop();
                return cancel ? ExecutivePlanner.GoalState.REJECT : ExecutivePlanner.GoalState.PREEMPT;
            }
            return ExecutivePlanner.GoalState.WAIT;
        }
    }


    /**
     * Determines if the goal should run given a state of the system.
     * @param worldState The current world state.
     * @param goal The goal to be evaluated.
     * @param controller The controller.
     * @param world The world.
     * @return If true, the goal will start executing, if false it will stay in the queue.
     */
    @Override
    public boolean query(WorldState worldState, MacroGoal goal, Controller controller,
            DetailedWorld world) {
        return true;
    }


    /**
     * Called to create an instance of the Action.
     * @param goal The goal to be handled.
     * @param callback The callback from the ExecutivePlanner.
     * @return An ActionInstance subclass for handling the goal.
     */
    public ActionInstance onCreateInstance(ActionGoal goal, ExecutivePlanner.Callback callback) {
        return new SoundPlayerActionInstance(goal, callback);
    }
}
