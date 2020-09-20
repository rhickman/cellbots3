package ai.cellbots.robot.executive.simple;

import android.util.Log;

import java.util.Date;

import ai.cellbots.common.Transform;
import ai.cellbots.robot.control.Action;
import ai.cellbots.robot.control.DriveAction;
import ai.cellbots.robot.control.VacuumSpiralAction;
import ai.cellbots.robot.executive.Executive;
import ai.cellbots.robot.executive.Goal;
import ai.cellbots.robot.executive.Types;
import ai.cellbots.robot.executive.WorldObject;
import ai.cellbots.robot.executive.WorldState;
import ai.cellbots.robot.manager.SoundManager;

/**
 * Drives to a goal-specified point.
 */
public final class DriveGoalProcessor extends GoalProcessor {
    private static final String TAG = DriveGoalProcessor.class.getSimpleName();

    private static final long SOUND_PRIORITY = 100; // TODO: All using the same priority

    /**
     * Action to complete at the end of the process.
     */
    private enum FinishAction {
        NONE, // Finish immediately
        WAIT, // Wait for completion
        VACUUM_SPIRAL, // Spiral vacuum pattern
    }

    /**
     * The source for the target pose.
     */
    private enum PoseSource {
        TRANSFORM, // A transformation from the variables/location + map from variables/map
        POI, // The transformation from variables/target
    }

    // Factories for various goal types.
    public static final GoalProcessor.GoalProcessorFactory DRIVE_GOAL_FACTORY
            = new DriveGoalProcessorFactory(FinishAction.NONE, PoseSource.TRANSFORM, true);
    public static final GoalProcessor.GoalProcessorFactory DRIVE_POINT_GOAL_FACTORY
            = new DriveGoalProcessorFactory(FinishAction.NONE, PoseSource.TRANSFORM, false);
    public static final GoalProcessor.GoalProcessorFactory DRIVE_POI_GOAL_FACTORY
            = new DriveGoalProcessorFactory(FinishAction.NONE, PoseSource.POI, false);
    public static final GoalProcessor.GoalProcessorFactory DRIVE_WAIT_GOAL_FACTORY
            = new DriveGoalProcessorFactory(FinishAction.WAIT, PoseSource.TRANSFORM, true);
    public static final GoalProcessor.GoalProcessorFactory DRIVE_WAIT_POINT_GOAL_FACTORY
            = new DriveGoalProcessorFactory(FinishAction.WAIT, PoseSource.TRANSFORM, false);
    public static final GoalProcessor.GoalProcessorFactory DRIVE_WAIT_POI_GOAL_FACTORY
            = new DriveGoalProcessorFactory(FinishAction.WAIT, PoseSource.POI, false);
    public static final GoalProcessor.GoalProcessorFactory VACUUM_SPIRAL_GOAL_FACTORY
            = new DriveGoalProcessorFactory(FinishAction.VACUUM_SPIRAL, PoseSource.TRANSFORM, false);


    /**
     * Factory for the goal.
     */
    private final static class DriveGoalProcessorFactory implements GoalProcessor.GoalProcessorFactory {
        private final FinishAction mFinishAction;
        private final PoseSource mPoseSource;
        private final boolean mRotation;

        /**
         * Creates the factory.
         * @param action The action.
         * @param poseSource Source for the pose of the goal.
         * @param rotation If true, we achieve the rotation.
         */
        private DriveGoalProcessorFactory(FinishAction action, PoseSource poseSource, boolean rotation) {
            mFinishAction = action;
            mPoseSource = poseSource;
            mRotation = rotation;
        }

        /**
         * Creates a goal processor object.
         *
         * @param goal The goal for the processor.
         * @return The new goal processor object.
         */
        @Override
        public GoalProcessor createGoalProcessor(Goal goal) {
            return new DriveGoalProcessor(goal, mFinishAction, mPoseSource, mRotation);
        }
    }

    /**
     * Current state of execution of the goal.
     */
    private enum State {
        INIT, // Initializing the drive action for the mediator.
        DRIVE, // Waiting for completion of the drive action for the mediator.
        WAIT, // Waiting after completion of the drive action.
        VACUUM, // Vacuum spiral is being executed.
    }

    private final FinishAction mFinishAction;
    private final PoseSource mPoseSource;
    private final boolean mRotation;

    private State mState;
    private long mFinishActionStartTime;
    private DriveAction mDriveAction;
    private VacuumSpiralAction mVacuumSpiralAction;

    private SoundManager.Sound mStartSound = null;
    private SoundManager.Sound mTerminateSound = null;

    /**
     * Creates the drive goal processor.
     * @param goal The goal.
     * @param action The action.
     * @param poseSource Source for the pose of the goal.
     * @param rotation If true, we achieve the rotation.
     */
    private DriveGoalProcessor(Goal goal, FinishAction action, PoseSource poseSource, boolean rotation) {
        super(goal);
        mState = State.INIT;
        mFinishAction = action;
        mPoseSource = poseSource;
        mRotation = rotation;
    }

    /**
     * Gets the goal position of this goal.
     *
     * @param worldState The world state.
     * @return The goal point transform.
     */
    private Transform getGoalPosition(WorldState worldState) {
        if (mPoseSource == PoseSource.POI) {
            WorldObject target = worldState.getWorldObject(getGoal().getParameters().get("target").toString());
            if (target != null) {
                return (Transform)target.getValue("location");
            }
            return null;
        } else {
            return (Transform)getGoal().getParameters().get("location");
        }
    }

    /**
     * Gets the goal world of this transform.
     *
     * @param worldState The world state.
     * @return The goal world uuid.
     */
    private String getGoalWorld(WorldState worldState) {
        if (mPoseSource == PoseSource.POI) {
            WorldObject target = worldState.getWorldObject(getGoal().getParameters().get("target").toString());
            if (target != null) {
                return target.getMapUuid();
            }
            return null;
        } else {
            return (String) getGoal().getParameters().get("map");
        }
    }


    /**
     * Gets the timeout.
     * @return The time for the goal to wait or execute vacuum spiral.
     */
    private long getTime() {
        return (long)getGoal().getParameters().get("time");
    }

    /**
     * Processes the given goal.
     *
     * @param delegate The delegate for the planner.
     * @param worldState The world state.
     * @param cancel  True if the goal should be cancelled.
     * @param preempt True if the goal should be preempted.
     * @return The state of the goal.
     */
    @Override
    public Executive.GoalState processGoal(Executive.Delegate delegate,
            WorldState worldState, boolean cancel, boolean preempt) {
        if (mState == State.INIT) {
            Transform tf = getGoalPosition(worldState);
            String mapUuid = getGoalWorld(worldState);
            Log.v(TAG, "INIT: tf " + tf + " map " + mapUuid);
            if (tf != null && mapUuid != null) {
                if (!mapUuid.equals(worldState.getState(Types.WorldStateKey.ROBOT_MAP))) {
                    Log.d(TAG, "Goal rejected for wrong map");
                    delegate.getActionMediator().setAction(null);
                    return Executive.GoalState.REJECT;
                }
                mDriveAction = new DriveAction(-1, tf, mRotation);
                delegate.getActionMediator().setAction(mDriveAction);
                mState = State.DRIVE;
            } else {
                // Invalid map uuid or tf.
                delegate.getActionMediator().setAction(null);
                return Executive.GoalState.REJECT;
            }
            mStartSound = delegate.getSoundManager().playGoalSound(SoundManager.GoalSound.STARTED_GOAL, SOUND_PRIORITY);
        }
        if (mState == State.DRIVE) {
            Action.State state = delegate.getActionMediator().getActionState(mDriveAction);
            if (state == null) {
                Log.w(TAG, "Goal state was null for drive action");
                return Executive.GoalState.REJECT;
            }
            Log.v(TAG, "DRIVE: goal point state " + state);
            if (state == Action.State.COMPLETED) {
                if (mFinishAction == FinishAction.WAIT) {
                    mState = State.WAIT;
                    mFinishActionStartTime = new Date().getTime() + getTime();
                } else if (mFinishAction == FinishAction.VACUUM_SPIRAL) {
                    mState = State.VACUUM;
                    mVacuumSpiralAction = new VacuumSpiralAction(-1, getTime());
                    delegate.getActionMediator().setAction(mVacuumSpiralAction);
                } else {
                    delegate.getActionMediator().setAction(null);
                    return Executive.GoalState.COMPLETE;
                }
            } else if (state == Action.State.REJECTED) {
                // Reject the goal for invalid state
                delegate.getActionMediator().setAction(null);
                // Wait for the start sound to stop, so we can play reject sound
                if (mStartSound == null || !mStartSound.isPlaying()) {
                    // If we have not yet started playing the reject sound, start it.
                    if (mTerminateSound == null) {
                        // TODO(playerone) Once it is played, it is going to play again? probably not.
                        mTerminateSound = delegate.getSoundManager().playGoalSound(SoundManager.GoalSound.REJECTED_GOAL,SOUND_PRIORITY);
                        Log.v(TAG, "Start terminate sound");
                    }
                    // If we have finished the reject sound, then finish.
                    if (mTerminateSound == null || !mTerminateSound.isPlaying()) {
                        Log.v(TAG, "Reject goal, terminate sound " + mTerminateSound);
                        return Executive.GoalState.REJECT;
                    } else {
                        Log.v(TAG, "Playing terminate sound");
                    }
                }
                return Executive.GoalState.REJECT;
            }
        }
        if (mState == State.WAIT) {
            Log.v(TAG, "Waiting - " + new Date().getTime() + " - " + mFinishActionStartTime);
            if (new Date().getTime() >= mFinishActionStartTime) {
                delegate.getActionMediator().setAction(null);
                return Executive.GoalState.COMPLETE;
            }
            // Wait for the start sound to stop, so we can play complete sound
            if (mStartSound == null || !mStartSound.isPlaying()) {
                // If we have not yet started playing the complete sound, start it.
                if (mTerminateSound == null) {
                    mTerminateSound = delegate.getSoundManager().playGoalSound(SoundManager.GoalSound.COMPLETED_GOAL, SOUND_PRIORITY);
                    Log.v(TAG, "Start playing terminate sound");
                }
                // If we have finished the complete sound, then finish.
                if (mTerminateSound == null || !mTerminateSound.isPlaying()) {
                    Log.v(TAG, "Complete goal, terminate sound " + mTerminateSound);
                    return Executive.GoalState.COMPLETE;
                } else {
                    Log.v(TAG, "Playing terminate sound");
                }
            } else {
                Log.v(TAG, "Waiting for start sound to terminate");
            }
        }
        if (mState == State.VACUUM) {
            Action.State state = delegate.getActionMediator().getActionState(mVacuumSpiralAction);
            if (state == null) {
                Log.w(TAG, "Goal state was null for vacuum action");
                return Executive.GoalState.REJECT;
            }
            Log.v(TAG, "Vacuum - state: " + state);
            if (state == Action.State.COMPLETED) {
                // Action has completed
                delegate.getActionMediator().setAction(null);
                return Executive.GoalState.COMPLETE;
            } else if (state == Action.State.REJECTED) {
                // Reject the goal for invalid state
                delegate.getActionMediator().setAction(null);
                return Executive.GoalState.REJECT;
            }
        }
        if (preempt) {
            Log.v(TAG, "Preempt");
            delegate.getActionMediator().setAction(null);
            return Executive.GoalState.PREEMPT;
        }
        if (cancel) {
            Log.v(TAG, "Cancel");
            delegate.getActionMediator().setAction(null);
            return Executive.GoalState.REJECT;
        }
        return Executive.GoalState.RUNNING;
    }

}
