package ai.cellbots.robot.control;

import android.util.Log;

import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.TimedLoop;
import ai.cellbots.common.Transform;
import ai.cellbots.common.data.Teleop;
import ai.cellbots.robot.control.VelocityMultiplexer.MuxPriority;
import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.state.RobotSessionGlobals;

/**
 * A controller that accepts goals from the mediator and generates teleop results.
 */
public abstract class Controller<T extends Action> implements ThreadedShutdown {
    private static final String TAG = Controller.class.getSimpleName();

    private final TimedLoop mTimedLoop;
    private final Class<T> mActionClass;
    private final RobotSessionGlobals mSession;

    private T mAction;
    private T mNextAction;
    private boolean mProcessed = true;
    private Action.State mActionState;
    private CostMap mFullyInflatedCostMap;
    private CostMap mProportionallyInflatedCostMap;
    private CostMap mNextFullyInflatedCostMap;
    private CostMap mNextProportionallyInflatedCostMap;
    private Transform mTransform;
    private Transform mNextTransform;

    // Time count and timeout variable to give up on a goal.
    private long mTimeSinceLastGoal = 0;
    // Time to wait before giving up on a goal in milliseconds.
    private static final long GOAL_TIMEOUT_MILLISECOND = 90000L;
    private MuxPriority mClassPriority = MuxPriority.NAVIGATION;
    private final VelocityMultiplexer mVelocityMultiplexer;

    /**
     * Gets if the controller is ready for goals to be accepted.
     *
     * @return True if it is ready.
     */
    protected abstract boolean isReady();

    /**
     * Controller settings.
     *
     * @param name The name of the controller class.
     * @param session The current session.
     * @param velocityMultiplexer The velocity multiplexer.
     * @param updateTime The milliseconds between calls to onUpdate().
     * @param actionClass The action class.
     */
    protected Controller(String name, RobotSessionGlobals session, VelocityMultiplexer velocityMultiplexer,
                         long updateTime, Class<T> actionClass) {
        mActionClass = actionClass;
        mSession = session;
        mVelocityMultiplexer = velocityMultiplexer;
        mTimedLoop = new TimedLoop(name, new TimedLoop.Looped() {
            @Override
            public boolean update() {
                boolean newAction = false;
                synchronized (this) {
                    if (mAction != mNextAction) {
                        mAction = mNextAction;
                        mActionState = Action.State.EXECUTING;
                        newAction = true;
                        mProcessed = false;
                    }
                }
                mTransform = mNextTransform;
                mFullyInflatedCostMap = mNextFullyInflatedCostMap;
                mProportionallyInflatedCostMap = mNextProportionallyInflatedCostMap;
                Action.State state = onUpdate(mAction, newAction);
                synchronized (this) {
                    mActionState = state;
                    mProcessed = true;
                }
                return true;
            }

            @Override
            public void shutdown() {
                onShutdown();
            }
        }, updateTime);
    }

    /**
     * Checks if we are stopped.
     *
     * @return True if we are stopped.
     */
    final synchronized public boolean isStopped() {
        return (mProcessed && mAction == null && mNextAction == null);
    }

    /**
     * Gets the Action subclass of this controller.
     *
     * @return The Action subclass.
     */
    final Class<T> getActionClass() {
        return mActionClass;
    }

    /**
     * Sets the next action.
     * 
     * @param action The new action to start.
     */
    final synchronized void setAction(T action) {
        mNextAction = action;
    }

    /**
     * Called by subclasses to set the state of the teleop.
     *
     * @param next Sets the next teleop.
     */
    final protected void setTeleop(Teleop next) {
        // Enqueue velocity into VelocityMultiplexer
        mVelocityMultiplexer.enqueueVelocity(next, mClassPriority);
    }

    /**
     * Sets the next fully inflated CostMap.
     *
     * @param fullyInflatedCostMap The fully inflated CostMap.
     */
    final void setFullyInflatedCostMap(CostMap fullyInflatedCostMap) {
        mNextFullyInflatedCostMap = fullyInflatedCostMap;
    }

    /**
     * Sets the next proportionally inflated CostMap.
     *
     * @param proportionallyInflatedCostMap The proportionally inflated CostMap.
     */
    final void setProportionallyInflatedCostMap(CostMap proportionallyInflatedCostMap) {
        mNextProportionallyInflatedCostMap = proportionallyInflatedCostMap;
    }

    /**
     * Gets the current fully inflated CostMap.
     *
     * @return The current fully inflated CostMap.
     */
    final protected CostMap getFullyInflatedCostMap() {
        return mFullyInflatedCostMap;
    }

    /**
     * Gets the current proportionally inflated CostMap.
     *
     * @return The current proportionally inflated CostMap.
     */
    final protected CostMap getProportionallyInflatedCostMap() {
        return mProportionallyInflatedCostMap;
    }

    /**
     * Sets the current transform.
     *
     * @param transform The new transform.
     */
    final void setTransform(Transform transform) {
        mNextTransform = transform;
    }

    /**
     * Gets the current transform.
     *
     * @return The current transform.
     */
    final protected Transform getTransform() {
        return mTransform;
    }

    /**
     * Gets the current session.
     *
     * @return The robot session.
     */
    @SuppressWarnings("unused")
    final protected RobotSessionGlobals getSession() {
        return mSession;
    }

    /**
     * Gets the state of an action.
     *
     * @param action The action to check.
     * @return The state, or null if no state is available.
     */
    @SuppressWarnings("WeakerAccess")
    final public synchronized Action.State getActionState(T action) {
        if (mAction == action) {
            return mActionState;
        }
        if (mNextAction == action) {
            return Action.State.QUEUED;
        }
        return null;
    }

    /**
     * Gets True if a timeout time has been reached
     *
     * @return True if the timeout time has been reached
     */
    protected boolean isGoalTimeout() {
        if (mTimeSinceLastGoal == 0) {
            mTimeSinceLastGoal = System.currentTimeMillis();
        } else {
            if (System.currentTimeMillis() - mTimeSinceLastGoal > GOAL_TIMEOUT_MILLISECOND) {
                Log.d(TAG, "Goal timeout has been reached");
                mTimeSinceLastGoal = 0;
                return true;
            }
        }
        return false;
    }

    /**
     * Resets the timer
     */
    protected void resetTimeout() {
        // TODO(playerone) have to call this function if the robot doesn't move (even with calling
        // setTeleop) for some time.
        mTimeSinceLastGoal = 0;
    }

    /**
     * Called every update.
     *
     * @param action The current action.
     * @param newAction True if the action is a new state.
     * @return The state of the current action.
     */
    protected abstract Action.State onUpdate(T action, boolean newAction);

    /**
     * Changes the priority of the velocity commands from Controller-inherited classes
     *
     * @param priority MuxPriorities priority to set
     */
    @SuppressWarnings("unused")
    protected void setVelocityMuxPriority(MuxPriority priority) {
        mClassPriority = priority;
    }

    /**
     * Gets actual multiplexer priority
     *
     * @return Actual MuxPriorities multiplexer priority
     */
    @SuppressWarnings("unused")
    protected MuxPriority getVelocityMuxPriority() {
        return mClassPriority;
    }

    /**
     * Called on shutdown
     */
    protected abstract void onShutdown();

    /**
     * Shuts down the controller.
     */
    @Override
    final public void shutdown() {
        mTimedLoop.shutdown();
    }

    /**
     * Waits for the controller to shutdown.
     */
    @Override
    final public void waitShutdown() {
        mTimedLoop.waitShutdown();
    }
}
