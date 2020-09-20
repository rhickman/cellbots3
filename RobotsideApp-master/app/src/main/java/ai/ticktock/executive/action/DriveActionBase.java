package ai.cellbots.executive.action;

import android.util.Log;

import java.util.Date;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.Transform;
import ai.cellbots.common.data.ExecutiveStateCommand;
import ai.cellbots.executive.ExecutivePlanner;
import ai.cellbots.executive.MacroGoal;
import ai.cellbots.executive.Types;
import ai.cellbots.executive.WorldState;
import ai.cellbots.robotlib.Controller;
import ai.cellbots.robotlib.SoundManager;

/**
 * Parent class for all drive actions. The action drives to a goal, waits for a time and then
 * quits all while playing the correct sounds. Subclasses of the action implement all the various
 * drive* and the wait actions.
 */
public abstract class DriveActionBase extends Action {
    private static final String TAG = DriveActionBase.class.getSimpleName();

    /**
     * The state of the action.
     */
    protected enum State {
        INIT, // Initializing, sending the goal to the controller.
        DRIVE, // Waiting for the controller to finish driving.
        WAIT // Waiting for the wait timeout.
    }

    /**
     * Action instance for the drive actions.
     */
    @SuppressWarnings("unused")
    protected abstract class DriveActionBaseInstance extends ActionInstance {
        private State mState = State.INIT;
        private Date mStopDate = null;
        private SoundManager.Sound mStartSound = null;
        private SoundManager.Sound mTerminateSound = null;

        /**
         * Create the ActionInstance.
         * @param goal The goal to be achieved.
         * @param callback The callback for the ExecutivePlanner executing the action.
         */
        protected DriveActionBaseInstance(ActionGoal goal, ExecutivePlanner.Callback callback) {
            super(goal, callback);
        }

        /**
         * Get the transform target for this goal from its parameters and data.
         * @param worldState The state of the world.
         * @param controller The controller.
         * @param world The world object itself.
         * @return The transform to go to, or null to skip the drive step.
         */
        protected abstract Transform getGoalPosition(WorldState worldState,
                Controller controller, DetailedWorld world);

        /**
         * Get the map target for this goal from its parameters and data.
         * @param worldState The state of the world.
         * @param controller The controller.
         * @param world The world object itself.
         * @return The map to go to, or null to skip the drive step.
         */
        protected abstract String getGoalMap(WorldState worldState,
                Controller controller, DetailedWorld world);

        /**
         * Get the goal point action for the system.
         * @param worldState The state of the world.
         * @param controller The controller.
         * @param world The world object itself.
         * @return The goal point action.
         */
        protected abstract Controller.GoalPointAction getGoalPointAction(WorldState worldState,
                Controller controller, DetailedWorld world);

        /**
         * Get the wait time for this goal from its parameters and data.
         * @param worldState The state of the world.
         * @param controller The controller.
         * @param world The world object itself.
         * @return The time to wait in milliseconds. A value <= 0 will skip waiting.
         */
        protected abstract long getWaitTime(WorldState worldState,
                Controller controller, DetailedWorld world);

        /**
         * Used internally to set the waiting state.
         * @param worldState The state of the world.
         * @param controller The controller.
         * @param world The world object itself.
         */
        private void setWaitState(WorldState worldState,
                Controller controller, DetailedWorld world) {
            long time = getWaitTime(worldState, controller, world);
            if (time < 0) {
                time = 0;
            }
            mState = State.WAIT;
            mStopDate = new Date();
            mStopDate.setTime(time + mStopDate.getTime());
        }

        /**
         * Called when the action is updated. Takes the action first through the INIT state,
         * setting the goal if present and starting the start sound. Then it transition to the
         * DRIVE state and waits for the goal and starting sound to finish. If the goal is bad,
         * then it plays the reject sound and rejects the goal. If the goal is good, then the
         * system transitions to the WAIT state. In the WAIT state, it waits for the correct amount
         * of time (potentially zero) and then plays the success sound before completing the goal.
         * @param worldState The state of the world, currently.
         * @param cancel True if we should cancel the goal.
         * @param controller The controller.
         * @param preemptGoal True if we should preempt the goal.
         * @param world The current world of the goal.
         * @return A goal state to be returned from the ExecutivePlanner.
         */
        @Override
        public ExecutivePlanner.GoalState update(WorldState worldState, boolean cancel,
                Controller controller, boolean preemptGoal, DetailedWorld world) {
            // Preempt if we are no longer a random goal.
            if (getGoal().getPriority() < 100) {
                if (worldState.getState(Types.WorldStateKey.ROBOT_EXECUTIVE_STATE)
                        == ExecutiveStateCommand.ExecutiveState.STOP) {
                    cancel = true;
                }
            }
            if (mState == State.INIT) {
                Transform tf = getGoalPosition(worldState, controller, world);
                String mapUuid = getGoalMap(worldState, controller, world);
                Controller.GoalPointAction goalAction = getGoalPointAction(worldState, controller, world);
                Log.v(TAG, "INIT: tf " + tf + " map " + mapUuid);
                if (tf != null && mapUuid != null) {
                    if (!mapUuid.equals(world.getUuid())) {
                        Log.d(TAG, "Goal rejected for wrong map");
                        return ExecutivePlanner.GoalState.REJECT;
                    }
                    controller.setGoalPoint(new Controller.GoalPoint(tf, goalAction));
                    mState = State.DRIVE;
                    mStartSound = playGoalSound(SoundManager.GoalSound.STARTED_GOAL);
                } else {
                    mStartSound = playGoalSound(SoundManager.GoalSound.STARTED_GOAL);
                    setWaitState(worldState, controller, world);
                }
            }
            if (mState == State.DRIVE) {
                Log.v(TAG, "DRIVE: goal point state " + controller.getGoalPointState());
                if (controller.getGoalPointState() == Controller.GoalPointState.COMPLETED) {
                    setWaitState(worldState, controller, world);
                } else if (controller.getGoalPointState() == Controller.GoalPointState.REJECTED) {
                    // Wait for the start sound to stop, so we can play reject sound
                    if (mStartSound == null || !mStartSound.isPlaying()) {
                        // If we have not yet started playing the reject sound, start it.
                        if (mTerminateSound == null) {
                            mTerminateSound = playGoalSound(SoundManager.GoalSound.REJECTED_GOAL);
                            Log.v(TAG, "Start terminate sound");
                        }
                        // If we have finished the reject sound, then finish.
                        if (mTerminateSound == null || !mTerminateSound.isPlaying()) {
                            Log.v(TAG, "Reject goal, terminate sound " + mTerminateSound);
                            return ExecutivePlanner.GoalState.REJECT;
                        } else {
                            Log.v(TAG, "Playing terminate sound");
                        }
                    }
                }
            }
            if (mState == State.WAIT) {
                Log.v(TAG, "Waiting - " + new Date().getTime() + " - " + mStopDate.getTime());
                if (new Date().getTime() >= mStopDate.getTime()) {
                    // Wait for the start sound to stop, so we can play complete sound
                    if (mStartSound == null || !mStartSound.isPlaying()) {
                        // If we have not yet started playing the complete sound, start it.
                        if (mTerminateSound == null) {
                            mTerminateSound = playGoalSound(SoundManager.GoalSound.COMPLETED_GOAL);
                            Log.v(TAG, "Start playing terminate sound");
                        }
                        // If we have finished the complete sound, then finish.
                        if (mTerminateSound == null || !mTerminateSound.isPlaying()) {
                            Log.v(TAG, "Complete goal, terminate sound " + mTerminateSound);
                            return ExecutivePlanner.GoalState.COMPLETE;
                        } else {
                            Log.v(TAG, "Playing terminate sound");
                        }
                    } else {
                        Log.v(TAG, "Waiting for start sound to terminate");
                    }
                }
            }
            if (preemptGoal) {
                Log.v(TAG, "Preempt");
                controller.setGoalPoint(null);
                return ExecutivePlanner.GoalState.PREEMPT;
            }
            if (cancel) {
                Log.v(TAG, "Cancel");
                controller.setGoalPoint(null);
                return ExecutivePlanner.GoalState.REJECT;
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
     * @return Always true since DriveActions can always be run.
     */
    @Override
    public boolean query(WorldState worldState, MacroGoal goal, Controller controller,
            DetailedWorld world) {
        return true;
    }
}
