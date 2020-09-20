package ai.cellbots.robot.executive;

import android.content.Context;
import android.util.Log;

import java.util.HashMap;
import java.util.Random;

import ai.cellbots.common.Transform;
import ai.cellbots.robot.control.Action;
import ai.cellbots.robot.control.ActionMediator;
import ai.cellbots.robot.control.AnimationManager;
import ai.cellbots.robot.control.DriveAction;
import ai.cellbots.robot.manager.SoundManager;
import ai.cellbots.robot.ros.ROSNodeManager;
import ai.cellbots.robot.state.RobotSessionGlobals;

/**
 * A simple executive system that drives to a new random goal each time.
 */
public class RandomDriverExecutive extends Executive {
    private static final String TAG = RandomDriverExecutive.class.getSimpleName();
    private final static int RANDOM_SEED = 1337;
    private final Random mRandom = new Random(RANDOM_SEED);
    private Action mAction = null;

    /**
     * Start the executive planner
     * @param parent The parent context.
     * @param session The session.
     * @param actionMediator Robot command action mediator.
     * @param soundManager Sound manager.
     * @param animationManager Animation manager.
     * @param rosNodeManager ROS node manager.
     */
    public RandomDriverExecutive(Context parent, RobotSessionGlobals session,
            ActionMediator actionMediator, SoundManager soundManager,
            AnimationManager animationManager, ROSNodeManager rosNodeManager) {
        super(parent, session, actionMediator, soundManager, animationManager,
                rosNodeManager, new HashMap<String, GoalType>());
        Log.i(TAG, "Created");
    }

    /**
     * Determine if a goal should start execution. We ignore all goals.
     *
     * @param worldState The state of the planner's current world.
     * @param goal       The goal to query for.
     * @return Always true.
     */
    @Override
    protected boolean query(WorldState worldState, Goal goal) {
        return true;
    }

    /**
     * Process each goal. In our case, returns COMPLETE instantly.
     *
     * @param worldState The state of the planner's current world.
     * @param goal       The current goal to process.
     * @param isNew      True if the goal is new.
     * @param cancel     True if the goal should be cancelled.
     * @param preempt    True if the goal should be preempted.
     * @return Always COMPLETE.
     */
    @Override
    protected GoalState processGoal(WorldState worldState, Goal goal,
            boolean isNew, boolean cancel, boolean preempt) {
        return GoalState.COMPLETE;
    }

    /**
     * Updates the executive.
     */
    @Override
    protected void onUpdate() {
        if (mAction == null
                || getActionMediator().getActionState(mAction) == Action.State.COMPLETED
                || getActionMediator().getActionState(mAction) == Action.State.REJECTED) {
            Transform transform;
            if (getSession().getWorld().getCustomTransformCount() > 0) {
                transform = getSession().getWorld().getCustomTransform(
                        mRandom.nextInt(getSession().getWorld().getCustomTransformCount()));
            } else {
                transform = getSession().getWorld().getSmoothedTransform(
                        mRandom.nextInt(getSession().getWorld().getSmoothedTransformCount()));
            }
            mAction = new DriveAction(-1, transform, false);
            getActionMediator().setAction(mAction);
        }
    }

    /**
     * Shuts down the executive.
     */
    @Override
    protected void onShutdown() {
        // Does nothing.
    }
}
