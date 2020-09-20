package ai.cellbots.robotlib;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.util.Log;

import com.google.atap.tangoservice.TangoPointCloudData;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.Transform;

/**
 * Controls the robot at a high-level. In the current solution, the controllers control the entire
 * functioning of the robot. Eventually there will be one controller which will be equivalent to
 * the local and global planners (to make that happen we need to work on a lot of point cloud
 * management infrastructure).
 */
public abstract class Controller {
    @SuppressWarnings("unused")
    private final static String TAG = "Controller";
    private final LinkedList<String> mLogMessages = new LinkedList<>();
    private final static double MIN_SPEED = 0.1;
    private final static double MAX_DELTA_SPEED = 0.025;

    private Animation mAnimationNext = null;
    private Animation mAnimation = null;
    private int mAnimationStep = 0;
    private Date mAnimationWaitOver = null;
    private boolean mCancelAnimation = false;
    private final LinkedList<String> mPlaySoundsQueue = new LinkedList<>();

    private double mMinimumSpeed = 0.0;
    private double mMaximumDeltaSpeed = 0.0;
    private DetailedWorld mWorld = null;
    private Transform mLocation = null;
    private double mSpeed = 0.0;
    private double mAngular = 0.0;
    private boolean mAction1 = false;
    private boolean mAction2 = false;
    private boolean mUpdate = false;
    private RobotDriver.RobotModel mModel = null;
    private TangoPointCloudData mPoints = null;
    private Transform mDeviceLocation = null;
    private Transform mPointCloudLocation = null;
    private float[] mPointColors = null;
    private boolean mPointColorsValid = false;
    public boolean mRobotIsBlocked = false;
    private double mMinimumBlockedDistance = Double.MAX_VALUE;

    // Maximum distance from the world available transformations that goals are allowed
    @SuppressWarnings("unused")
    protected double mGoalMaxDistance = 1.0;

    // Time to wait before giving up on a goal [seconds]
    protected double mGoalTimeoutSec = 30;

    // Time that the vacuum was started up.
    private long mVacuumStart;

    // Time count and timeout variable to give up on a goal.
    private boolean mWasBlocked = false;

    // Robot block strings
    private static final String ROBOT_BLOCKED_POINTS = "[BLOCKED] Robot is blocked by pointcloud";
    private static final String ROBOT_UNBLOCKED_POINTS =
            "[BLOCKED] Robot is unblocked by pointcloud";

    /**
     * Get if we should align the rotation with the goal point.
     * @return True if we should align with the goal point.
     */
    protected boolean shouldAlignGoalRotation() {
        return mGoalPoint != null && mGoalPoint.getAction() == GoalPointAction.ALIGN_ROTATION;
    }

    /**
     * True if a goal point should immediately terminate without running the action.
     * @return True if immediate termination is required.
     */
    protected boolean goalPointImmediateTerminate() {
        return mGoalPoint != null
                && (mGoalPoint.getAction() == GoalPointAction.NO_ACTION
                || mGoalPoint.getAction() == GoalPointAction.ALIGN_ROTATION);
    }

    /**
     * Start the goal point action.
     * @return True if the action was started successfully.
     */
    protected boolean startGoalPointAction() {
        if (mGoalPoint == null) {
            return false;
        }
        if (mGoalPoint.getAction() == GoalPointAction.VACUUM_SPIRAL) {
            mVacuumStart = new Date().getTime();
            addLogMessage("[CONTROLLER] Starting vacuuming");
            return true;
        }
        return false;
    }

    /**
     * Handle a goal point.
     * @return The REJECTED if there is an error, COMPLETED if successful.
     */
    protected GoalPointState handleGoalPointAction() {
        if (mGoalPoint == null) {
            return GoalPointState.REJECTED;
        }
        if (mGoalPoint.getAction() == GoalPointAction.VACUUM_SPIRAL) {
            final long VACUUM_SPIRAL_TIME = 30000L;
            final double VACUUM_SPIRAL_SPEED = 0.1;
            final double VACUUM_SPIRAL_RADIUS = 0.3;
            if (new Date().getTime() > mVacuumStart + VACUUM_SPIRAL_TIME) {
                setMotion(0.0, 0.0);
                setAction1(false);
                setAction2(false);
                return GoalPointState.COMPLETED;
            } else {
                double radius = VACUUM_SPIRAL_RADIUS *
                        ((new Date().getTime() - mVacuumStart) / ((double) VACUUM_SPIRAL_TIME));
                setMotion(VACUUM_SPIRAL_SPEED, VACUUM_SPIRAL_SPEED / radius);
                setAction1(true);
                setAction2(true);
            }
            // Stop the robot if we see the wall
            if (isBlockedDepth()) {
                setMotion(0, getAngular());
            }
            return GoalPointState.RUNNING;
        }
        return GoalPointState.REJECTED;
    }


    /**
     * Used to store the state of the current goal.
     */
    @SuppressWarnings("WeakerAccess")
    public enum GoalPointState {
        NEW,       // Set if the goal is new and subclass should restart
        RUNNING,   // Set by the subclass while goal is being executed
        COMPLETED, // Set by the subclass when the goal is finished
        REJECTED   // Set by the subclass if it cannot achieve the goal
    }

    /**
     * Used to trigger additional actions after a goal is achieved.
     */
    public enum GoalPointAction {
        NO_ACTION, // Do not take any actions
        ALIGN_ROTATION, // Align the rotation correctly
        VACUUM_SPIRAL, // Vacuum a spiral around the target
    }

    /**
     * Goal point class, used to specify goals to the controller.
     */
    public static class GoalPoint {
        private final Transform mTransform;
        private final GoalPointAction mAction;

        /**
         * Stores a goal point for the controller.
         *
         * @param tf     The transform.
         * @param action The action to take at the goal.
         */
        public GoalPoint(Transform tf, GoalPointAction action) {
            if (tf == null) {
                throw new IllegalArgumentException("Transform (tf) must not be null.");
            }
            mTransform = tf;
            mAction = action;
        }

        /**
         * Get the goal's transform.
         *
         * @return The transform
         */
        public Transform getTransform() {
            return mTransform;
        }

        /**
         * Get the goal's action.
         */
        public GoalPointAction getAction() {
            return mAction;
        }

        /**
         * Convert to string.
         *
         * @return String representation.
         */
        public String toString() {
            return "GoalPoint(transform: " + mTransform + " action: " + mAction + ")";
        }
    }

    // Store the latest goal points and states, always synchronized.
    private GoalPoint mWriteGoalPoint = null;
    private GoalPointState mReadGoalPointState = null;

    // Store the current goal points, only modified from the update() thread.
    private GoalPoint mGoalPoint = null;
    private GoalPointState mGoalPointState = GoalPointState.NEW;

    /**
     * Get the goal point of the controller.
     *
     * @return The goal point transform.
     */
    @SuppressWarnings("WeakerAccess")
    protected GoalPoint getCurrentGoalPoint() {
        return mGoalPoint;
    }

    /**
     * Get the goal point state of the controller.
     *
     * @return The goal point of the controller.
     */
    @SuppressWarnings("WeakerAccess")
    protected GoalPointState getCurrentGoalPointState() {
        return mGoalPointState;
    }

    /**
     * Set the goal point state of the controller.
     *
     * @param goalPointState The state of the goal point.
     */
    protected void setCurrentGoalPointState(GoalPointState goalPointState) {
        mGoalPointState = goalPointState;
    }

    /**
     * Set the goal point of the controller.
     *
     * @param goal The new goal point.
     */
    @SuppressWarnings("WeakerAccess")
    public synchronized void setGoalPoint(GoalPoint goal) {
        mReadGoalPointState = GoalPointState.NEW;
        mWriteGoalPoint = goal;
    }

    /**
     * Get the goal point state of the controller.
     *
     * @return The goal point of the controller.
     */
    @SuppressWarnings("WeakerAccess")
    public synchronized GoalPointState getGoalPointState() {
        return mReadGoalPointState;
    }

    /**
     * Class constructor.
     *
     * @param minSpeed      The minimum speed at which the robot could move.
     * @param maxDeltaSpeed The maximum difference between consecutive linear speed commands.
     */
    @SuppressWarnings("unused")
    protected Controller(double minSpeed, double maxDeltaSpeed) {
        // Check if minimum speed is within speed range for the robot model. If not, set default
        // value.
        mMinimumSpeed = max(min(minSpeed, 0.65), 0);

        // Check if delta speed is non negative and if it not higher than 0.1, as for values higher
        // than these the robot moves jumpy. If the conditions are not reached, set default value.
        mMaximumDeltaSpeed = max(min(maxDeltaSpeed, MAX_DELTA_SPEED), 0);
    }

    /**
     * Class constructor. Sets minimum speed and maximum delta speed from default values.
     */
    protected Controller() {
        mMinimumSpeed = MIN_SPEED;
        mMaximumDeltaSpeed = MAX_DELTA_SPEED;
    }

    /**
     * Wrap an angle so it is always between -PI and +PI.
     *
     * @param a The angle to wrap
     * @return The angle wrapped around.
     */
    protected static double wrapAngle(double a) {
        while (a < -Math.PI) a += 2 * Math.PI;
        while (a > +Math.PI) a -= 2 * Math.PI;
        return a;
    }

    /**
     * Calculates the dot product between a vector and a vector
     *
     * @param v1 The first vector
     * @param v2 The second vector
     * @return The dot product value
     */
    private static double dotProduct(double v1[], double v2[]) {
        double r = 0.0;
        for (int i = 0; (i < v1.length) && (i < v2.length); i++) {
            r += v1[i] * v2[i];
        }
        return r;
    }

    /**
     * Sets an animation.
     *
     * @param next The next animation.
     */
    public void setAnimation(Animation next) {
        mAnimationNext = next;
    }

    /**
     * Get the current animation.
     *
     * @return The current animation.
     */
    public Animation getAnimation() {
        return mAnimation;
    }

    /**
     * Cancels the current animation.
     */
    public void cancelAnimation() {
        mCancelAnimation = true;
    }

    /**
     * Updates the controller.
     *
     * @param world    The World in which operations take place.
     * @param location The location of the robot base.
     * @param model    The model of robot.
     */
    final void update(DetailedWorld world, Transform location, RobotDriver.RobotModel model,
            Transform deviceLocation, Transform pointCloudLocation,
            TangoPointCloudData points, float pointColors[],
            double goalMaxDistance, double goalTimeout) {
        mLocation = location;
        mModel = model;
        mPoints = points;
        mPointCloudLocation = pointCloudLocation;
        mDeviceLocation = deviceLocation;
        mGoalMaxDistance = goalMaxDistance;
        mGoalTimeoutSec = goalTimeout;
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        mPointColors = pointColors;
        mPointColorsValid = false;
        if ((world != null) && (mLocation != null) && (model != null)
                && (mPointCloudLocation != null) && (points != null)) {
            synchronized (this) {
                // Copy the GoalPoint over from the write goal if it exits. If so then set the
                // state of the goal to be new and set the states to be new.
                if (mWriteGoalPoint != mGoalPoint) {
                    mGoalPoint = mWriteGoalPoint;
                    mGoalPointState = GoalPointState.NEW;
                    mReadGoalPointState = GoalPointState.NEW;
                }
            }

            if (mCancelAnimation) {
                mAnimation = null;
                mCancelAnimation = false;
            }
            if (mAnimationNext != null) {
                mAnimation = mAnimationNext;
                mAnimationWaitOver = null;
                mAnimationStep = 0;
                mAnimationNext = null;
                setMotion(0, 0);
                setAction1(false);
                setAction2(false);
            }
            if (mAnimation == null) {
                boolean newWorld = (mWorld != world);
                mWorld = world;
                if (newWorld) {
                    mWasBlocked = false;
                    onNewWorld();
                }
                onUpdate();
            } else {
                processAnimation();
            }
            synchronized (this) {
                // If we are still tracking the goal, then store its state.
                if (mWriteGoalPoint == mGoalPoint) {
                    mReadGoalPointState = mGoalPointState;
                }
            }
        } else if (model == null) {
            throw new Error("Controller error: no model in controller");
        } else if (mLocation == null) {
            throw new Error("Controller error: no location in controller");
        } else if (points == null) {
            throw new Error("Controller error: no points in controller");
        } else if (mPointCloudLocation == null) {
            throw new Error("Controller error: no point cloud location in controller");
        } else { //(mWorld == null)
            throw new Error("Controller error: no world in controller");
        }
    }

    /**
     * Process the animation
     */
    private void processAnimation() {
        List<Animation.AnimationCommand> commands = mAnimation.getAnimationCommands();
        while (mAnimationStep < commands.size()) {
            Animation.AnimationCommand anim = commands.get(mAnimationStep);
            if (anim instanceof Animation.SetMotorAnimationCommand) {
                Animation.SetMotorAnimationCommand cmd = (Animation.SetMotorAnimationCommand) anim;
                setMotion(cmd.getLinear(), cmd.getAngular());
            } else if (anim instanceof Animation.WaitAnimationCommand) {
                Animation.WaitAnimationCommand cmd = (Animation.WaitAnimationCommand) anim;
                if (mAnimationWaitOver == null) {
                    mAnimationWaitOver = new Date();
                    mAnimationWaitOver.setTime(mAnimationWaitOver.getTime() + cmd.getTime());
                }
                if (mAnimationWaitOver.after(new Date())) {
                    // Wait until the animation wait is over.
                    mUpdate = true;
                    return;
                }
                mAnimationWaitOver = null;
            } else if (anim instanceof Animation.PlayAudioAnimationCommand) {
                Animation.PlayAudioAnimationCommand cmd =
                        (Animation.PlayAudioAnimationCommand) anim;
                synchronized (mPlaySoundsQueue) {
                    mPlaySoundsQueue.add(cmd.getName());
                }
            }

            mAnimationStep++;
        }
        // If we are finished the animation, stop
        if (mAnimationStep >= commands.size()) {
            mAnimation = null;
            setMotion(0, 0);
        }
    }

    /**
     * Get and clear the sound files to be played.
     *
     * @return The sounds array strings.
     */
    String[] getAndClearSounds() {
        synchronized (mPlaySoundsQueue) {
            String[] r = mPlaySoundsQueue.toArray(new String[0]);
            mPlaySoundsQueue.clear();
            return r;
        }
    }

    /**
     * Called when a new world is loaded in to the controller.
     */
    protected abstract void onNewWorld();

    /**
     * Called when the controller is updated (approximately 10Hz)
     */
    protected abstract void onUpdate();

    /**
     * Set the motion of this controller.
     *
     * @param speed   The robot liner speed in meters/second.
     * @param angular The robot angular speed in radians/second.
     */
    protected synchronized void setMotion(double speed, double angular) {
        mSpeed = speed;
        mAngular = angular;
        mUpdate = true;
    }

    /**
     * Sets the action1 state.
     *
     * @param action1 The action1 state.
     */
    protected synchronized void setAction1(boolean action1) {
        mAction1 = action1;
        mUpdate = true;
    }

    /**
     * Gets the action1 state.
     */
    public boolean getAction1() {
        return mAction1;
    }

    /**
     * Sets the action2 state.
     *
     * @param action2 The action2 state.
     */
    protected synchronized void setAction2(boolean action2) {
        mAction2 = action2;
        mUpdate = true;
    }

    /**
     * Gets the action2 state.
     *
     * @return The action2 state.
     */
    public boolean getAction2() {
        return mAction2;
    }

    /**
     * Get the linear motion of this controller.
     *
     * @return The commanded robot liner speed in meters/second.
     */
    @SuppressWarnings("WeakerAccess")
    protected synchronized double getSpeed() {
        return mSpeed;
    }

    /**
     * Get the angular motion of this controller.
     *
     * @return The commanded robot angular speed in radians/second.
     */
    protected synchronized double getAngular() {
        return mAngular;
    }

    /**
     * Get the world for this controller.
     *
     * @return The world we are operating within.
     */
    protected DetailedWorld getWorld() {
        return mWorld;
    }

    /**
     * Get the location of the robot.
     *
     * @return The location of the robot within the world.
     */
    protected Transform getLocation() {
        return mLocation;
    }

    /**
     * Get the path of the next steps assumed for the robot for display to the user.
     *
     * @return A list of world transforms that make up the path.
     */
    protected abstract List<Transform> getPath();

    /**
     * Get the point cloud data from this system.
     *
     * @return The point cloud data
     */
    @SuppressWarnings("WeakerAccess")
    protected TangoPointCloudData getPoints() {
        return mPoints;
    }

    /**
     * Get the point cloud sensor location from the device location
     *
     * @return The point cloud sensor location
     */
    @SuppressWarnings("WeakerAccess")
    protected Transform getPointCloudLocation() {
        return mPointCloudLocation;
    }

    /**
     * Get the device location from the map location.
     *
     * @return The point cloud sensor location
     */
    @SuppressWarnings("unused")
    protected Transform getDeviceLocation() {
        return mDeviceLocation;
    }

    /**
     * Return true if we have motion to set to the robot driver and clear the flag.
     *
     * @return True if we have motion update.
     */
    boolean haveMotion() {
        boolean up = mUpdate;
        mUpdate = false;
        return up;
    }

    /**
     * Get the robot model.
     *
     * @return The robot model.
     */
    @SuppressWarnings("WeakerAccess")
    protected RobotDriver.RobotModel getModel() {
        return mModel;
    }

    /**
     * Drives to a given location within the world without concern for obstacles.
     *
     * @param target        The target to drive towards.
     * @param matchRotation True if we should match the Z rotation of the target.
     * @return True if we have reached the goal, otherwise false.
     */
    protected boolean driveToLocation(Transform target,
            @SuppressWarnings({"SameParameterValue", "UnusedParameters"}) boolean matchRotation,
            boolean avoidAction) {
        // Maximum target angle
        double maxTargetDeviationAngle = 5 * Math.PI / 180;

        double dx = target.getPosition(0) - getLocation().getPosition(0);
        double dy = target.getPosition(1) - getLocation().getPosition(1);
        double dist = Math.sqrt((dx * dx) + (dy * dy));

        double targetAngle = Math.atan2(dy, dx);
        double deltaAngle = wrapAngle(targetAngle - getLocation().getRotationZ());

        // Set the maximum drift in angles for the linear trajectory. If the angle from initial
        // orientation to target is higher than this, then the robot's motion will be purely
        // rotational.
        double maxLinearDeviationAngle;
        if (avoidAction) {
            maxLinearDeviationAngle = 5 * Math.PI / 180;
        } else {
            maxLinearDeviationAngle = 30 * Math.PI / 180;
        }

        // Always reset time count after a goal has been reached.
        if (dist < 0.25) {
            if (matchRotation) {
                double targetAngleF = target.getRotationZ();
                double deltaAngle2 = wrapAngle(targetAngleF - getLocation().getRotationZ());

                if (Math.abs(deltaAngle2) < maxTargetDeviationAngle) {
                    return true;
                }
                setMotion(0, deltaAngle2);
                return false;
            }
            return true;
        } else if (Math.abs(deltaAngle) < maxLinearDeviationAngle) {
            // Check if the robot is blocked. If so, set a flag to inform the path follower about it
            mRobotIsBlocked = false;

            // The speed of the robot will follow an exponential pattern. The -5 constant was chosen
            // experimentally so that the maximum speed could be achieved when the distance between
            // nodes is approx. 0.25.
            double speed = (1 - Math.exp(-5 * dist));
            if (speed > mModel.getMaxSpeed()) speed = mModel.getMaxSpeed();
            speed = speed * (1 - Math.abs(deltaAngle / maxLinearDeviationAngle));
            // Limit the minimum value for the linear speed.
            if (speed < mMinimumSpeed) speed = mMinimumSpeed;

            // Regulate speed increments to avoid jumps. Don't let speed change more than
            // mMaximumDeltaSpeed the last speed value.
            if (Math.abs(mSpeed - speed) > mMaximumDeltaSpeed) {
                if (speed >= mSpeed) {
                    speed = mSpeed + mMaximumDeltaSpeed;
                    // After the robot has been stopped, restart with the minimum speed.
                    if (mSpeed == 0) {
                        speed = mMaximumDeltaSpeed;
                    }
                } else if (speed < mSpeed) {
                    speed = mSpeed - mMaximumDeltaSpeed;
                }
            }
            // Log linear speed for testing.
            Log.v(TAG, "Linear motion. Speed:" + Double.toString(speed) + '\n');
            setMotion(speed, deltaAngle);

        } else {
            // Make the robot move faster when it is rotating to avoid an obstacle.
            if(avoidAction) {
                deltaAngle = Math.signum(deltaAngle) * 30 * Math.PI / 180;
            }
            setMotion(0, deltaAngle);
        }
        return false;
    }

    /**
     * Set color
     *
     * @param i   The index
     * @param rgb The Red/Green/Blue as an int, e.g. 0xFFEEDD sets FF red, EE green, DD blue
     */
    @SuppressWarnings("WeakerAccess")
    protected void setPointColor(int i, int rgb) {
        mPointColors[i] = Float.intBitsToFloat(rgb);
    }

    /**
     * Adds a log messages.
     *
     * @param s The string to add.
     */
    protected synchronized void addLogMessage(String s) {
        Log.i(TAG, "Controller message: " + s);
        mLogMessages.add(s);
    }

    /**
     * Get and reset log messages.
     *
     * @return The log messages array.
     */
    synchronized String[] getAndClearLogMessages() {
        String[] r = mLogMessages.toArray(new String[0]);
        mLogMessages.clear();
        return r;
    }

    /**
     * Detect if the robot is blocked by an obstacle.
     */
    final protected boolean isBlockedDepth() {
        // Check for obstacles using the point cloud otherwise.
        boolean blocked = isBlockedPointCloud();
        if (blocked && !mWasBlocked) {
            mWasBlocked = true;
            addLogMessage(ROBOT_BLOCKED_POINTS);
        } else if (!blocked && mWasBlocked) {
            mWasBlocked = false;
            addLogMessage(ROBOT_UNBLOCKED_POINTS);
        }
        return blocked;
    }

    /**
     * Detect if the robot is blocked by an obstacle using the latest point cloud.
     * This is @playertwo's original version
     */
    private boolean isBlockedPointCloud() {
        // Generate a planar floor below the point cloud.
        /*Transform t = new Transform(new double[]{0.0, 0.0, 0.0},
                getPointCloudLocation().getRotation(), getPointCloudLocation().getTimestamp());
        double floorZ[] = new Transform(t, new Transform(0, 0, 1, 0)).getPosition();
        float pt[] = new float[3];
        Log.i(TAG, "Floor Z: " + floorZ[0] + " " + floorZ[1] + " " + floorZ[2]);*/

        if (getPoints() == null) {
            Log.d(TAG, "No cloud, skipping depth calculations");
            return false;
        }
        if (getPointCloudLocation() == null) {
            Log.d(TAG, "No cloud location, skipping depth calculations");
            return false;
        }
        double[] pt = new double[3];
        double[] rot = {1.0, 0, 0, 0};

        double floor_limit = new Transform(getPointCloudLocation()).getPosition(2);
        double ceil_limit = getModel().getHeight() + floor_limit;

        double[] forwards = {+Math.cos(getLocation().getRotationZ()),
                +Math.sin(getLocation().getRotationZ())};
        double[] sideways = {-Math.sin(getLocation().getRotationZ()),
                +Math.cos(getLocation().getRotationZ())};
        double[] off = {0, 0};

        int blockers = 0;

        Log.d(TAG, "Running depth on " + getPoints().numPoints + " points");

        for (int i = 0; i < getPoints().numPoints; i += 10) {
            for (int k = 0; k < 3; k++) {
                pt[k] = getPoints().points.get((i * 4) + k);
            }
            Transform rw = new Transform(getPointCloudLocation(), new Transform(pt, rot, 0));
            if ((rw.getPosition(2) > floor_limit) && (rw.getPosition(2) < ceil_limit)) {
                off[0] = rw.getPosition(0) - getLocation().getPosition(0);
                off[1] = rw.getPosition(1) - getLocation().getPosition(1);
                double side = dotProduct(off, sideways);
                if (Math.abs(side) < (getModel().getWidth() / 2)) {
                    double front = dotProduct(off, forwards);
                    if ((front < ((getModel().getLength() / 2) + 0.4225)) && (front > 0)) {
                        setPointColor(i, 0xFF0000);
                        blockers++;
                    } else {
                        setPointColor(i, 0xFFFF00);
                    }
                } else {
                    setPointColor(i, 0x00FF00);
                }
            } else {
                setPointColor(i, 0x0000FF);
            }
        }
        setPointColorsValid();
        return blockers > 100;
    }

    /**
     * Set point colors valid.
     */
    protected void setPointColorsValid() {
        mPointColorsValid = true;
    }

    /**
     * Get if point colors are valid.
     *
     * @return True if they are.
     */
    public boolean getPointColorsValid() {
        return mPointColorsValid;
    }

    /**
     * Get the reason why a goal has been rejected.
     *
     * @return String indicating the reason.
     */
    public abstract String getGoalRejectionReason();
}
