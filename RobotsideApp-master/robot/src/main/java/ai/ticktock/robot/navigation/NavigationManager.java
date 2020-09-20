package ai.cellbots.robot.navigation;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import ai.cellbots.common.EventProcessor;
import ai.cellbots.common.Transform;
import ai.cellbots.common.data.Teleop;
import ai.cellbots.robot.control.Action;
import ai.cellbots.robot.control.Controller;
import ai.cellbots.robot.control.DriveAction;
import ai.cellbots.robot.control.VelocityMultiplexer;
import ai.cellbots.robot.control.VelocityMultiplexer.MuxPriority;
import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.costmap.CostMapPose;
import ai.cellbots.robot.navigation.LocalPlanner.LocalPlannerState;
import ai.cellbots.robot.state.RobotSessionGlobals;

/**
 * Navigates over maps.
 */
public class NavigationManager extends Controller<DriveAction> {
    private static final String TAG = NavigationManager.class.getSimpleName();
    private final GlobalPlanner mGlobalPlanner;
    private final LocalPlanner mLocalPlanner;
    private final Listener mListener;
    private final EventProcessor mListenerEvents;
    private RecoveryState mRecoveryState;
    private List<Transform> mLastLocalPath;
    private Path mLastGlobalPath;

    /**
     * The update listener.
     */
    public interface Listener {
        /**
         * On the update of a new path.
         *
         * @param path The new path.
         */
        void onPath(Path path);
        /**
         * On the update of a new local plan.
         *
         * @param path The new local plan.
         */
        void onLocalPlan(List<Transform> path);
    }

    private enum RecoveryState {
        OFF,                // Initial RecoveryState.
        SAFE_RESET,         // Cleans CostMaps, restarts timer and stops robot.
        GLOBAL_REPLANNING,  // Sends a new goal to the GlobalPlanner.
        ABORT               // Unrecoverable, NavigationManager will reject the goal
                            // and clears global path in the cloud.
    }

    /**
     * Creates the navigator.
     *
     * @param session             The session.
     * @param velocityMultiplexer The velocity multiplexer.
     * @param globalPlanner       The global planner.
     * @param localPlanner        The local planner.
     * @param listener            The listener for planner updates.
     */
    public NavigationManager(RobotSessionGlobals session, VelocityMultiplexer velocityMultiplexer,
                             GlobalPlanner globalPlanner, LocalPlanner localPlanner, Listener listener) {
        super(TAG, session, velocityMultiplexer, 100, DriveAction.class);
        mGlobalPlanner = globalPlanner;
        mLocalPlanner = localPlanner;
        mListener = listener;
        mListenerEvents = new EventProcessor("NavigationListener", new EventProcessor.Processor() {
            @Override
            public boolean update() {
                Path globalPath;
                List<Transform> localPath;
                synchronized (this) {
                    globalPath = mLastGlobalPath;
                    localPath = mLastLocalPath;
                }
                mListener.onPath(globalPath);
                mListener.onLocalPlan(localPath);
                return true;
            }

            @Override
            public void shutdown() {
            }
        });
        setVelocityMuxPriority(MuxPriority.ANIMATION);
        // Start the RecoveryState as OFF.
        executeRecovery(RecoveryState.OFF, null);
    }

    /**
     * Checks if the controller is ready.
     *
     * @return True as it is always ready.
     */
    @Override
    protected boolean isReady() {
        return true;
    }

    /**
     * Pushes updates to the controller.
     *
     * @param action    The current action.
     * @param newAction True if the action is a new state.
     * @return          The state of the current action.
     */
    @Override
    protected Action.State onUpdate(DriveAction action, boolean newAction) {
        Log.d(TAG, "Begin navigation");
        // Check for errors and malformed data.
        if (action == null) {
            Log.v(TAG, "Action is null, rejecting");
            // Executes the ABORT recovery state and rejects the given goal.
            executeRecovery(RecoveryState.ABORT, null);
            return Action.State.REJECTED;
        }
        CostMap latestFullyInflatedCostMap = getFullyInflatedCostMap();
        CostMap latestProportionallyInflatedCostMap = getProportionallyInflatedCostMap();
        // Ensure the CostMap is valid
        if (latestFullyInflatedCostMap == null || !latestFullyInflatedCostMap.isValid() ||
                latestProportionallyInflatedCostMap == null || !latestProportionallyInflatedCostMap.isValid()) {
            // This error is externally generated, so the goal is QUEUED until it's solved.
            if (!executeRecovery(RecoveryState.SAFE_RESET, action)) {
                Log.i(TAG, "Waiting for SAFE_RESET");
                return Action.State.QUEUED;
            }
            // If the NavigationManager tries to set the RecoveryState to SAFE_RESET and it receives
            // "true", that means that the actual RecoveryState is ABORT and the
            // NavigationManager will reject the goal.
            Log.e(TAG, "Recovery failure for invalid CostMap");
            return Action.State.REJECTED;
        }

        // Update the data in the global planner with valid data.
        mGlobalPlanner.setNext(latestProportionallyInflatedCostMap, getTransform());

        // Updates the global planner with the new action.
        if (newAction) {
            executeRecovery(RecoveryState.OFF, action);
            mGlobalPlanner.setNextAction(action);
            resetTimeout();
        }

        // Check if the robot takes too long to complete the goal.
        if (isGoalTimeout()) {
            Log.w(TAG, "Goal has timed out");
            // Try to set recovery state to SAFE_RESET, executeRecovery will return false if successful.
            // If executeRecovery returns true (i.e. the recovery state is ABORT), then reject the goal.
            if (executeRecovery(RecoveryState.SAFE_RESET, action)) {
                return Action.State.REJECTED;
            }
            return Action.State.EXECUTING;
        }

        // Obtain global path.
        Path globalPath = mGlobalPlanner.getPath(action);
        if (globalPath == null) {
            Log.w(TAG, "Global path is null.");
        } else {
            boolean pathThroughObstacle = PathUtil.isPathThroughObstacle(globalPath, latestProportionallyInflatedCostMap);
            if (pathThroughObstacle) {
                Log.d(TAG, "Global path contains an obstacle. Stopping Robot.");
                stopRobot();
                return Action.State.EXECUTING;
            }
        }

        Log.i(TAG, "Begin local planner update");
        // If we have a path, execute the local planner.
        LocalPlannerState currentLocalPlannerState =
                mLocalPlanner.update(action, globalPath, latestFullyInflatedCostMap, getTransform());

        Log.i(TAG, "Saving paths");
        synchronized (this) {
            mLastGlobalPath = globalPath == null ? null : globalPath.copy();
            mLastLocalPath = mLocalPlanner.getPath();
            if (mLastLocalPath != null) {
                mLastLocalPath = new ArrayList<>(mLastLocalPath);
            }
        }
        mListenerEvents.onEvent();
        Log.i(TAG, "Local planner state: " + currentLocalPlannerState);
        switch (currentLocalPlannerState) {
            case COMPLETED:
                // Stop the robot and set the Action State to COMPLETED.
                stopRobot();
                // Clear the action in the global planner.
                // TODO(playerfour) The global planner should not keep computing the path, once an action
                // TODO(playerfour) is completed. However, the change below setting, setNextAction to null,
                // TODO(playerfour) causes regression when canceling active goals. Fix that.
                //mGlobalPlanner.setNextAction(null);
                return Action.State.COMPLETED;
            case RUNNING:
                // Send velocity to VelocityMultiplexer.
                // TODO (playerone): Check if the robot is oscillating and execute the recovery behavior
                setTeleop(mLocalPlanner.getTeleop());
                break;
            case ERROR:
                // Something went wrong.
                executeRecovery(RecoveryState.GLOBAL_REPLANNING, action);
                break;
        }

        return Action.State.EXECUTING;
    }

    /**
     * Called on shutdown of the controller.
     */
    @Override
    protected void onShutdown() {
        mGlobalPlanner.shutdown();
        mLocalPlanner.shutdown();
        mListenerEvents.shutdown();
        mGlobalPlanner.waitShutdown();
        mLocalPlanner.waitShutdown();
        mListenerEvents.waitShutdown();
    }

    /**
     * Requests the CostMapManager to update the cost map in an asynchronous manner.
     */
    private void requestCostMapUpdate() {
        // TODO (playerfour): Send a signal to CostMapManager to do a full cost map update
    }

    /**
     * Clears the global path.
     */
    private void clearGlobalPath() {
        Log.d(TAG, "Clearing global path.");
        mListener.onPath(null);
    }

    /**
     * Stops the robot.
     */
    private void stopRobot() {
        setTeleop(new Teleop(0, 0, 0, 0, 0, 0));
    }

    /**
     * Sets a RecoveryState and it manages the behavior of the robot planner. You can set 4 Recovery
     * States:
     * - OFF: Initial state. Is used every time a new goal is set.
     * - GLOBAL_REPLANNING: Used when a bad local path is generated.
     * - SAFE_RESET: Used when a bad global path is generated.
     * - ABORT: Closes the Navigation loop (cancel the goal) because neither GLOBAL_REPLANNING
     * nor SAFE_RESET are making effect.
     *
     * @param state  RecoveryState to be set (OFF, GLOBAL_REPLANNING, SAFE_RESET or ABORT)
     * @param action Current action that's been executed
     * @return True if the RecoveryBehavior cannot recover throws an ABORT
     */
    private boolean executeRecovery(RecoveryState state, DriveAction action) {
        // Set RecoveryState.
        if (state == RecoveryState.GLOBAL_REPLANNING) {
            // If the RecoveryState is already in GLOBAL_REPLANNING state, it will be set a
            // SAFE_RESET state instead.
            if (mRecoveryState != RecoveryState.GLOBAL_REPLANNING) {
                mRecoveryState = RecoveryState.GLOBAL_REPLANNING;
            } else {
                mRecoveryState = RecoveryState.SAFE_RESET;
            }
        }
        // If the RecoveryState is already in SAFE_RESET state, it will be set to ABORT instead.
        else if (state == RecoveryState.SAFE_RESET) {
            if (mRecoveryState != RecoveryState.SAFE_RESET) {
                mRecoveryState = RecoveryState.SAFE_RESET;
            } else {
                mRecoveryState = RecoveryState.ABORT;
            }
        } else {
            // RecoveryState is OFF or ABORT.
            mRecoveryState = state;
        }
        // Execute actions based on RecoveryState.
        switch (mRecoveryState) {
            case OFF:
                // The robot won't stop when it's OFF because it's used when a new goal is received.
                // CostMaps won't be reset because we want to maintain the previous data.
                break;
            case GLOBAL_REPLANNING:
                // When we do a global replanning, the robot will stop.
                stopRobot();
                // The global planner will replan without doing a requestCostMapUpdate().
                // Send a new action to the global planner.
                mGlobalPlanner.setNextAction(action);
                break;
            case SAFE_RESET:
                // Stop the robot.
                stopRobot();
                // Request the cost map manager to update the fused cost map.
                requestCostMapUpdate();
                break;
            case ABORT:
                // Stops the robot.
                stopRobot();
                // Clean cost maps.
                requestCostMapUpdate();
                // Clear the global path.
                clearGlobalPath();
                // If the RecoveryBehavior throw an ABORT, we give up.
                return true;
        }
        // Returns false when the RecoveryState is not an ABORT.
        return false;
    }
}
