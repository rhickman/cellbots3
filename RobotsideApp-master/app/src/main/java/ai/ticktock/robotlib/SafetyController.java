package ai.cellbots.robotlib;


import android.util.Log;

/**
 * Safety Controller tells the robot what to do when a low level event happens.
 */

class SafetyController {
    final static private String TAG = SafetyController.class.getSimpleName();

    /*
     * Bumper behavior constants
     */
    private static final double BUMPER_MOVE_BACKWARD_DISTANCE = 0.10;
    private static final double BUMPER_MOVE_BACKWARD_SPEED = 0.15;
    private static final double BUMPER_MOVE_FORWARD_DISTANCE = 0.45;
    private static final double BUMPER_MOVE_FORWARD_SPEED = 0.10;
    private static final double BUMPER_ROTATING_ANGLE_LONG = 65.0 / 180.0 * Math.PI;
    private static final double BUMPER_ROTATING_ANGLE_SHORT = 35.0 / 180.0 * Math.PI;
    private static final double BUMPER_ROTATING_SPEED = 60.0 / 180.0 * Math.PI;

    /*
     * Safety concerning events
     */
    private boolean mIsBumped = false;
    @SuppressWarnings({"FieldCanBeLocal", "CanBeFinal"})
    private boolean mIsLifted = false;

    /*
     * Bumper state machine
     */
    public enum BumperManeuver {
        NONE, TURNING, MOVING_BACKWARDS, MOVING_FORWARD

    }

    private BumperManeuver mBumperManeuver = BumperManeuver.NONE;

    /**
     * Get the BumperManeuver
     */
    public BumperManeuver getBumperManeuver() {
        return mBumperManeuver;
    }

    /*
     * Use simple behaviour, only go backwards and stop
     */
    private boolean mSimpleBumper = false;

    /*
     * Final desired speeds
     */
    private double mSafeLinearSpeed = 0;
    private double mSafeAngSpeed = 0;

    /*
     * Time tracking variables
     */
    @SuppressWarnings("FieldCanBeLocal")
    private long mCurrentTime = 0;
    private long mPreviousTime = 0;

    private int mBumperID = 0;

    /*
     * Rotation variables, defined
     */
    private double mRotationSpeed = BUMPER_ROTATING_SPEED;
    private double mRotationAngle = BUMPER_ROTATING_ANGLE_SHORT;


    SafetyController() {
    }

    /**
     * Get bumper state
     *
     * @return bumper state
     */
    @SuppressWarnings("unused")
    public boolean getIsBumped() {
        return mIsBumped;
    }

    /**
     * Get wheel drop sensors state
     *
     * @return wheel drop sensor state
     */
    @SuppressWarnings("unused")
    public boolean getIsLifted() {
        return mIsLifted;
    }

    public double getSafeLinearSpeed() {
        return mSafeLinearSpeed;
    }

    public double getSafeAngularSpeed() {
        return mSafeAngSpeed;
    }

    /**
     * Set bumper behavior, if it is set to true the bumper behavior it will be going backwards
     * only
     *
     * @param value enable simple bumper behavior
     */
    public void setSimpleBumperBehavior(boolean value) {
        mSimpleBumper = value;
    }

    /*
     * updateBumper tracks the bumper status. If the bumper is pressed
     * then it will go back and turn. Those maneuver states are tracked
     * in this function and it sets the speed required for that.
     *
     * @param bumper status, true if it is pressed
     * @return boolean if it is necessary to read the safety speeds or not
     */
    boolean updateBumper(int bumper) {
        if (bumper != 0 || mIsBumped) {
            mCurrentTime = System.currentTimeMillis();
            // If it is moving backwards
            switch (mBumperManeuver) {
                case NONE:
                    mIsBumped = true;
                    mBumperManeuver = BumperManeuver.MOVING_BACKWARDS;
                    mBumperID = bumper;
                    mPreviousTime = mCurrentTime;
                    Log.v(TAG, "Bumper maneuver starts");
                    break;
                case MOVING_BACKWARDS:
                    // Check time
                    if ((double) (mCurrentTime - mPreviousTime) / 1000.0
                            < BUMPER_MOVE_BACKWARD_DISTANCE
                            / BUMPER_MOVE_BACKWARD_SPEED) {
                        // Continue moving backwards
                        mSafeAngSpeed = 0;
                        mSafeLinearSpeed = -BUMPER_MOVE_BACKWARD_SPEED;
                    } else {
                        // If simple behaviour is selected stop there
                        if (mSimpleBumper) {
                            mBumperManeuver = BumperManeuver.NONE;
                            mIsBumped = false;
                            mSafeAngSpeed = 0;
                            mSafeLinearSpeed = 0;
                            mPreviousTime = mCurrentTime;
                            Log.v(TAG, "Finish going back, finish bumper maneuver");
                        } else {
                            // Finish moving backwards state, start rotation state
                            mBumperManeuver = BumperManeuver.TURNING;
                            mSafeAngSpeed = 0;
                            mSafeLinearSpeed = 0;
                            mPreviousTime = mCurrentTime;
                            // Define based on the bumper ID rotation angle and speed direction
                            switch (mBumperID) {
                                case RobotDriver.LEFT_BUMPER_ID:
                                    mRotationAngle = BUMPER_ROTATING_ANGLE_SHORT;
                                    mRotationSpeed = -BUMPER_ROTATING_SPEED;
                                    break;
                                case RobotDriver.CENTER_LEFT_BUMPER_ID:
                                    mRotationAngle = BUMPER_ROTATING_ANGLE_LONG;
                                    mRotationSpeed = -BUMPER_ROTATING_SPEED;
                                    break;
                                case RobotDriver.CENTER_RIGHT_BUMPER_ID:
                                    mRotationAngle = BUMPER_ROTATING_ANGLE_LONG;
                                    mRotationSpeed = BUMPER_ROTATING_SPEED;
                                    break;
                                case RobotDriver.RIGHT_BUMPER_ID:
                                    mRotationAngle = BUMPER_ROTATING_ANGLE_SHORT;
                                    mRotationSpeed = BUMPER_ROTATING_SPEED;
                                    break;
                            }
                            Log.v(TAG, "Finish going back, start rotation");
                        }
                    }
                    break;
                // If it is rotating
                case TURNING:
                    // Check time
                    if ((double) (mCurrentTime - mPreviousTime) / 1000.0 < mRotationAngle
                            / BUMPER_ROTATING_SPEED) {
                        // Continue rotation
                        mSafeAngSpeed = mRotationSpeed;
                        mSafeLinearSpeed = 0;
                    } else {
                        // Finish rotation state, finish bumper maneuver
                        mBumperManeuver = BumperManeuver.MOVING_FORWARD;
                        mSafeAngSpeed = 0;
                        mSafeLinearSpeed = 0;
                        Log.v(TAG, "Finish rotation, moving forward");
                    }
                    break;
                case MOVING_FORWARD:
                    if (bumper != 0) {
                        mBumperManeuver = BumperManeuver.NONE;
                        return updateBumper(bumper);
                    } else if ((double) (mCurrentTime - mPreviousTime) / 1000.0
                            < BUMPER_MOVE_FORWARD_DISTANCE
                            / BUMPER_MOVE_FORWARD_SPEED) {
                        // Continue moving forward
                        mSafeAngSpeed = 0;
                        mSafeLinearSpeed = BUMPER_MOVE_FORWARD_SPEED;
                    } else {
                        // Finish moving forward
                        mBumperManeuver = BumperManeuver.NONE;
                        mIsBumped = false;
                        mSafeAngSpeed = 0;
                        mSafeLinearSpeed = 0;
                        mPreviousTime = mCurrentTime;
                        Log.v(TAG, "Finish bumper maneuver");
                    }
                    break;
                default:
                    break;
            }
        }

        return mIsBumped;
    }
}