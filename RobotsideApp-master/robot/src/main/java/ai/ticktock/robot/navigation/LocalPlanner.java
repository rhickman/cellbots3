package ai.cellbots.robot.navigation;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.Transform;
import ai.cellbots.common.data.Teleop;
import ai.cellbots.robot.control.DriveAction;
import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.driver.RobotDriver;
import ai.cellbots.robot.state.RobotSessionGlobals;

/**
 * Computes the trajectory for a given goal.
 */
public class LocalPlanner implements ThreadedShutdown {
    private static final String TAG = LocalPlanner.class.getSimpleName();
    private Teleop mTeleop = null;
    private final RobotSessionGlobals mSession;
    private final PointCloudSafetyController mPointCloudSafetyController;
    private final BumperSafetyController mBumperSafetyController;
    private Path mCurrentPath;
    private boolean mIsBlocked;
    // Default distance considered close to the goal in meters.
    private static final double DEFAULT_DISTANCE_CLOSE_TO_GOAL = 0.25;
    // Maximum distance of a node considered to be close to a goal. In meters.
    private double mDistanceCloseToGoal;
    // Maximum squared distance of a node considered to be close to a goal. In square meters.
    private double mDistanceCloseToGoalSquared;
    private final VelocityGenerator mVelocityGenerator;
    private final ArrayList<Transform> mPath = new ArrayList<>();

    private RobotDriver.BumperState mBumperState = RobotDriver.BumperState.NONE;
    // Last updated time of the local planner. This is the system time in millisecond. If negative,
    // the last update is ignored.
    private long mLastUpdatedTime = -1;

    /**
     * States implemented by Local planner:
     *
     * - COMPLETED: The goal has been reached.
     * - RUNNING: The robot is going to the goal.
     * - ERROR: There was an error reaching the goal. Recovery action may be executed.
     */
    public enum LocalPlannerState {
        COMPLETED,
        RUNNING,
        ERROR
    }

    /**
     * Creates the local planner.
     *
     * @param session The session
     * @param velocityGenerator A concrete object of velocityGenerator
     * @param pointCloudSafetyController A concrete object of pointCloudSafetyController
     */
    public LocalPlanner(RobotSessionGlobals session,
                        PointCloudSafetyController pointCloudSafetyController, VelocityGenerator velocityGenerator) {
        this(session, pointCloudSafetyController, velocityGenerator, DEFAULT_DISTANCE_CLOSE_TO_GOAL);
    }

    /**
     * Creates the local planner.
     *
     * @param session The session.
     * @param velocityGenerator Class that interfaces velocityGenerator
     * @param pointCloudSafetyController A concrete object of pointCloudSafetyController
     * @param distanceCloseToGoal Distance at which the goal is considered reached (in meters)
     */
    private LocalPlanner(
            RobotSessionGlobals session, PointCloudSafetyController pointCloudSafetyController,
            VelocityGenerator velocityGenerator, double distanceCloseToGoal) {
        mSession = session;
        mVelocityGenerator = velocityGenerator;
        mDistanceCloseToGoal = distanceCloseToGoal;
        mDistanceCloseToGoalSquared = mDistanceCloseToGoal * mDistanceCloseToGoal;
        mPointCloudSafetyController = pointCloudSafetyController;
        mBumperSafetyController = new BumperSafetyController();
    }

    /**
     * Gets the current planned local path.
     *
     * @return The local path.
     */
    public List<Transform> getPath() {
        return Collections.unmodifiableList(new ArrayList<>(mPath));
    }

    /**
     * Computes the actions towards the goal
     *
     * @param action The action to achieve.
     * @param path The path to follow from the local planner.
     * @param costMap The costMap.
     * @param transform The transform for the current pose.
     * @return The state of the action - executing, completed, or rejected.
     */
    public LocalPlannerState update(DriveAction action, Path path,
                                    CostMap costMap, Transform transform) {
        Log.v(TAG, "Begin local planner");
        if (path != null && !path.equals(mCurrentPath)) {
            //noinspection AssignmentToCollectionOrArrayFieldFromParameter
            mCurrentPath = path.copy(); // Copy this since path is an immutable list
            mIsBlocked = false;
        }

        Transform goal = action.getTransform();
        if (goal == null || transform == null) {
            setTeleop(new Teleop(0,0,0,0,0,0));
            // TODO (playerfive): We had an error, it should return ERROR to let RecoveryBehavior know
            // TODO: about this event. If this error persists, the robot will remain stopped until
            // TODO: the timeout is reached.
            Log.v(TAG, "Goal or transform is null, stopping the robot");
            return LocalPlannerState.RUNNING;
        }
        if (!isGoalReached(transform, goal) || mVelocityGenerator.isStopManager()) {
            Log.v(TAG, "Computing velocities with velocity generator");
            long delta = -1;
            long currentTime = System.currentTimeMillis();
            if (mLastUpdatedTime > 0) {
                delta = currentTime - mLastUpdatedTime;
            }
            if (delta > 120) {
                Log.w(TAG, "Warning - local planner was slow to update, delta = " + delta + " ms");
            }
            boolean isBlocked = mPointCloudSafetyController.isBlocked();
            mPath.clear();
            double[] velocities = mVelocityGenerator.computeVelocities(transform, goal, costMap,
                    path, isBlocked, mPointCloudSafetyController.getBinData(),
                    mDistanceCloseToGoal, delta, mPath);
            mLastUpdatedTime = currentTime;

            if (velocities == null) {
                Log.v(TAG, "Velocities null");
                setTeleop(new Teleop(0, 0, 0, 0, 0, 0));
                mPath.clear();
                return mVelocityGenerator.isStopManager() && isGoalReached(transform, goal)
                        ? LocalPlannerState.COMPLETED : LocalPlannerState.ERROR;
            }
            if (isBlocked && !mVelocityGenerator.isStopManager()) {
                Log.v(TAG, "Stopping for block");
                velocities = velocities.clone();
                velocities[0] = 0;
            }
            if (mBumperSafetyController.updateBumper(mBumperState)) {
                Log.v(TAG, "Safety controller is active");
                velocities[0] = mBumperSafetyController.getSafeLinearSpeed();
                velocities[1] = mBumperSafetyController.getSafeAngularSpeed();
                mPath.clear();
                mLastUpdatedTime = -1;
            }

            Log.v(TAG, "Sending linear " + velocities[0] + " angular " + velocities[1]);
            setTeleop(new Teleop(velocities[0], 0, 0, 0, 0, velocities[1]));
            return LocalPlannerState.RUNNING;
        } else {
            mLastUpdatedTime = -1;
            Log.v(TAG, "Completed");
            mPath.clear();
            setTeleop(new Teleop(0, 0, 0, 0, 0, 0));
            return LocalPlannerState.COMPLETED;
        }
    }

    /**
     * Gets the session for the local planner.
     * @return The session.
     */
    final protected RobotSessionGlobals getSession() {
        return mSession;
    }

    /**
     * Sets the teleop. Should be called in computeGoal.
     * @param teleop The new teleop.
     */
    protected final void setTeleop(Teleop teleop) {
        mTeleop = teleop;
    }

    /**
     * Gets the teleop.
     */
    public Teleop getTeleop() {
        return mTeleop;
    }

    /**
     * Gets if an action is blocked.
     */
    final boolean isRobotBlocked() {
        return mIsBlocked;
    }

    /**
     * Shuts down the planner.
     */
    @Override
    public void shutdown() {
        // Do nothing.
    }

    /**
     * Waits for the planner to shutdown.
     */
    @Override
    public void waitShutdown() {
        // Do nothing.
    }

    /**
     * Checks if the robot has reached the goal
     *
     * @param currentRobotPose Current position and orientation of the robot
     * @param goal Target location for the robot
     * @return True if the robot has reached the goal
     */
    private boolean isGoalReached(Transform currentRobotPose, Transform goal) {
        return currentRobotPose.planarDistanceToSquared(goal) < mDistanceCloseToGoalSquared;
    }

    /**
     * Sets the value for mDistanceCloseToGoal
     *
     * @param distanceCloseToGoal Distance at which the goal is considered reached (in meters)
     */
    @SuppressWarnings("unused")
    void setDistanceCloseToGoal(double distanceCloseToGoal) {
        mDistanceCloseToGoal = distanceCloseToGoal;
        mDistanceCloseToGoalSquared = distanceCloseToGoal * distanceCloseToGoal;
    }

    /**
     * Gets the value of mDistanceCloseToGoal
     *
     * @return Distance at which the goal is considered reached (in meters)
     */
    @SuppressWarnings("unused")
    double getDistanceCloseToGoal() {
        return mDistanceCloseToGoal;
    }


    /**
     * Sets the bumper state.
     * @param state The bumper state.
     */
    public void setBumperState(RobotDriver.BumperState state) {
        mBumperState = state;
    }
}
