package ai.cellbots.robot.navigation;

import android.util.Log;

import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.TimedLoop;
import ai.cellbots.common.Transform;
import ai.cellbots.robot.control.DriveAction;
import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.costmap.CostMapPose;
import ai.cellbots.robot.state.RobotSessionGlobals;

/**
 * Plans for a global set of goals.
 */
public class GlobalPlanner implements ThreadedShutdown {
    private final static String TAG = GlobalPlanner.class.getSimpleName();
    private final TimedLoop mTimedLoop;
    private final RobotSessionGlobals mSession;
    private CostMap mNextCostMap;
    private CostMap mCostMap;
    private DriveAction mNextAction;
    private DriveAction mAction;
    private Transform mTransform;
    private Transform mNextTransform;

    private PathFinder mPathFinder;

    private Path mPath;
    private boolean mIsNewAction;

    private static final long LOOP_MILLISECONDS = 500; // milliseconds

    /**
     * Starts the GlobalPlanner.
     *
     * @param session The session.
     * @param finder The path finder.
     */
    public GlobalPlanner(RobotSessionGlobals session, PathFinder finder) {
        mSession = session;
        mPathFinder = finder;
        // TODO(playerfour) Global planner stills computes a path after cancel active goals is selected.
        // TODO(playerfour) Set mAction to null once active goals are cancelled.
        mTimedLoop = new TimedLoop(TAG, new TimedLoop.Looped() {
            @Override
            public boolean update() {
                Log.d(TAG, "Starting GlobalPlanner loop");
                onUpdate();
                Log.d(TAG, "Ending GlobalPlanner loop");
                return true;
            }

            @Override
            public void shutdown() {
                onShutdown();
            }
        }, LOOP_MILLISECONDS, Thread.MIN_PRIORITY);
    }

    /**
     * Updates the global path.
     */
    final void onUpdate() {
        synchronized (this) {
            mCostMap = mNextCostMap;
            mTransform = mNextTransform;
            if (mNextAction != mAction) {
                mPath = null;
                mAction = mNextAction;
                mIsNewAction = true;
            }
        }
        if (mAction != null && mCostMap != null && mTransform != null) {
            Log.d(TAG, "Start up the plan");
            CostMapPose origin, target;
            origin = new CostMapPose(
                    (int) Math.floor(mTransform.getPosition(0) / mCostMap.getResolution()),
                    (int) Math.floor(mTransform.getPosition(1) / mCostMap.getResolution()));
            target = new CostMapPose(
                    (int) Math.floor(mAction.getTransform().getPosition(0)
                            / mCostMap.getResolution()),
                    (int) Math.floor(mAction.getTransform().getPosition(1)
                            / mCostMap.getResolution()));
            Path path = computePlan(origin, target);
            Log.i(TAG, "Generated plan: " + path);
            synchronized (this) {
                mIsNewAction = false;
                if (path != null) {
                    mPath = path;
                } else {
                    mPath = null;
                }
            }
        } else {
            if (mAction == null) {
                Log.v(TAG, "Null action");
            } else {
                if (mCostMap == null) {
                    Log.w(TAG, "No plan because CostMap is empty");
                }
                if (mTransform == null) {
                    Log.w(TAG, "No plan because of not being localized");
                }
            }
            mPath = null;
        }
    }

    /**
     * Sets the next costmap and next transform.
     *
     * @param costMap The new costMap.
     * @param transform The new transform.
     */
    final synchronized void setNext(CostMap costMap, Transform transform) {
        mNextCostMap = costMap;
        mNextTransform = transform;
        mPathFinder.setCostMap(costMap);
    }

    /**
     * Gets the current CostMap.
     *
     * @return The CostMap.
     */
    final protected CostMap getCostMap() {
        return mCostMap;
    }

    /**
     * Gets the current transform.
     *
     * @return The transform.
     */
    final protected Transform getTransform() {
        return mTransform;
    }

    /**
     * Gets the current session.
     *
     * @return The session.
     */
    final protected RobotSessionGlobals getSession() {
        return mSession;
    }

    /**
     * Sets the next action.
     */
    final synchronized void setNextAction(DriveAction action) {
        mNextAction = action;
    }

    /**
     * Gets the action for planning.
     */
    final protected DriveAction getAction() {
        return mAction;
    }

    /**
     * Gets the plan for an action.
     *
     * @param action The action to get the plan for, or null if not generated.
     */
    final synchronized Path getPath(DriveAction action) {
        if (mAction == action && mPath != null) {
            return mPath.copy();
        }
        return null;
    }

    /**
     * Computes a new plan for the origin and the target.
     *
     * @param origin The origin of the path
     * @param target The goal of the path
     * @return A new path or null if a new path cannot be found.
     */
    public Path computePlan(CostMapPose origin, CostMapPose target) {
        // TODO: The planning algorithm should only handle origin, target and costmap. We shall
        // TODO: discuss where to use isNewAction properly.

        // TODO: compute plan takes CostMapLocations as arguments so, in order to use it for the
        // TODO: global plan, Transforms should implement that interface.
        return mPathFinder.computePlan(origin, target);
    }

    /**
     * Called during shutdown.
     */
    protected void onShutdown() {
        // Do nothing.
    }

    /**
     * Shuts down the global planner.
     */
    @Override
    final public void shutdown() {
        mTimedLoop.shutdown();
    }

    /**
     * Waits for the global planner to shutdown.
     */
    @Override
    final public void waitShutdown() {
        mTimedLoop.waitShutdown();
    }
}
