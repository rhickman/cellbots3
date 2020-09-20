package ai.cellbots.robot.navigation;

import android.util.Log;

import ai.cellbots.robot.driver.RobotDriver;

/**
 * Safety Controller tells the robot what to do when a low level bumper event happens.
 */
public class BumperSafetyController {
    final static private String TAG = BumperSafetyController.class.getSimpleName();

    /*
     * Bumper behavior constants.
     */
    private static final double BUMPER_MOVE_BACKWARD_DISTANCE = 0.10;
    private static final double BUMPER_MOVE_BACKWARD_SPEED = 0.15;
    private static final double BUMPER_MOVE_FORWARD_DISTANCE = 0.45;
    private static final double BUMPER_MOVE_FORWARD_SPEED = 0.10;
    private static final double BUMPER_ROTATING_ANGLE_LONG = 65.0 / 180.0 * Math.PI;
    private static final double BUMPER_ROTATING_ANGLE_SHORT = 35.0 / 180.0 * Math.PI;
    private static final double BUMPER_ROTATING_SPEED = 60.0 / 180.0 * Math.PI;

    /*
     * Safety concerning events.
     */
    private boolean mIsBumped = false;
    private boolean mIsLifted = false;

    /*
     * Bumper state machine.
     */
    public enum BumperManeuver {
        NONE, TURNING, MOVING_BACKWARDS, MOVING_FORWARD
    }

    private BumperManeuver mBumperManeuver = BumperManeuver.NONE;

    /*
     * Use simple behaviour, only go backwards and stop.
     */
    private boolean mUseSimpleManeuver = false;

    /*
     * Final desired speeds.
     */
    private double mSafeLinearSpeed = 0;
    private double mSafeAngularSpeed = 0;

    /*
     * Time tracking variables.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private long mCurrentTime = 0;
    private long mPreviousTime = 0;

    private RobotDriver.BumperState mBumperID = RobotDriver.BumperState.NONE;

    /*
     * Rotation variables.
     */
    private double mRotationSpeed = BUMPER_ROTATING_SPEED;
    private double mRotationAngle = BUMPER_ROTATING_ANGLE_SHORT;

    public BumperSafetyController() {
        // Intentionally left empty.
    }

    /**
     * Tracks and updates bumper status.
     *
     * If bumper is bumped, the robot will move backwards and turn.
     * Maneuver states and the robot's speed are both tracked and set in this function.
     *
     * @param bumperState status, true if it is pressed
     * @return boolean if it is necessary to read the safety speeds or not
     */
    boolean updateBumper(RobotDriver.BumperState bumperState) {
        if (bumperState != RobotDriver.BumperState.NONE || mIsBumped) {
            mCurrentTime = System.currentTimeMillis();

            switch (mBumperManeuver) {
                case NONE:
                    mIsBumped = true;
                    mBumperManeuver = BumperManeuver.MOVING_BACKWARDS;
                    mBumperID = bumperState;
                    mPreviousTime = mCurrentTime;
                    Log.v(TAG, "Bumper maneuver starts");
                    break;
                case MOVING_BACKWARDS:
                    // Check time
                    if ((double) (mCurrentTime - mPreviousTime) / 1000.0
                            < BUMPER_MOVE_BACKWARD_DISTANCE
                            / BUMPER_MOVE_BACKWARD_SPEED) {
                        // Continue moving backwards.
                        mSafeAngularSpeed = 0;
                        mSafeLinearSpeed = -BUMPER_MOVE_BACKWARD_SPEED;
                    } else {
                        // If simple behaviour is selected stop there.
                        if (mUseSimpleManeuver) {
                            mBumperManeuver = BumperManeuver.NONE;
                            mIsBumped = false;
                            mSafeAngularSpeed = 0;
                            mSafeLinearSpeed = 0;
                            mPreviousTime = mCurrentTime;
                            Log.v(TAG, "Finish going back, finish bumper maneuver");
                        } else {
                            // Finish moving backwards state, start rotation state.
                            mBumperManeuver = BumperManeuver.TURNING;
                            mSafeAngularSpeed = 0;
                            mSafeLinearSpeed = 0;
                            mPreviousTime = mCurrentTime;
                            // Define based on the bumper ID rotation angle and speed direction.
                            switch (mBumperID) {
                                case LEFT:
                                    mRotationAngle = BUMPER_ROTATING_ANGLE_SHORT;
                                    mRotationSpeed = -BUMPER_ROTATING_SPEED;
                                    break;
                                case CENTER_LEFT:
                                    mRotationAngle = BUMPER_ROTATING_ANGLE_LONG;
                                    mRotationSpeed = -BUMPER_ROTATING_SPEED;
                                    break;
                                case CENTER:
                                    mRotationAngle = BUMPER_ROTATING_ANGLE_LONG;
                                    mRotationSpeed = -BUMPER_ROTATING_SPEED;
                                    break;
                                case CENTER_RIGHT:
                                    mRotationAngle = BUMPER_ROTATING_ANGLE_LONG;
                                    mRotationSpeed = BUMPER_ROTATING_SPEED;
                                    break;
                                case RIGHT:
                                    mRotationAngle = BUMPER_ROTATING_ANGLE_SHORT;
                                    mRotationSpeed = BUMPER_ROTATING_SPEED;
                                    break;
                                case NONE:
                                    mRotationAngle = BUMPER_ROTATING_ANGLE_SHORT;
                                    mRotationSpeed = BUMPER_ROTATING_SPEED;
                                    break;
                            }
                            Log.v(TAG, "Finish going back, start rotation");
                        }
                    }
                    break;
                case TURNING:
                    // Check time
                    if ((double) (mCurrentTime - mPreviousTime) / 1000.0 < mRotationAngle
                            / BUMPER_ROTATING_SPEED) {
                        // Continue rotation
                        mSafeAngularSpeed = mRotationSpeed;
                        mSafeLinearSpeed = 0;
                    } else {
                        // Finish rotation state, finish bumper maneuver
                        mBumperManeuver = BumperManeuver.MOVING_FORWARD;
                        mSafeAngularSpeed = 0;
                        mSafeLinearSpeed = 0;
                        Log.v(TAG, "Finish rotation, moving forward");
                    }
                    break;
                case MOVING_FORWARD:
                    if (bumperState != RobotDriver.BumperState.NONE) {
                        mBumperManeuver = BumperManeuver.NONE;
                        return updateBumper(bumperState);
                    } else if ((double) (mCurrentTime - mPreviousTime) / 1000.0
                            < BUMPER_MOVE_FORWARD_DISTANCE
                            / BUMPER_MOVE_FORWARD_SPEED) {
                        // Continue moving forward
                        mSafeAngularSpeed = 0;
                        mSafeLinearSpeed = BUMPER_MOVE_FORWARD_SPEED;
                    } else {
                        // Finish moving forward
                        mBumperManeuver = BumperManeuver.NONE;
                        mIsBumped = false;
                        mSafeAngularSpeed = 0;
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

    /**
     * Gets the BumperManeuver.
     *
     * @return The bumper maneuver.
     */
    @SuppressWarnings("unused")
    public BumperManeuver getBumperManeuver() {
        return mBumperManeuver;
    }

    /**
     * Gets if the robot is bumped or not.
     *
     * @return True if the robot is bumped.
     */
    @SuppressWarnings("unused")
    public boolean getIsBumped() {
        return mIsBumped;
    }

    /**
     * Gets wheel drop sensors state.
     *
     * @return wheel drop sensor state.
     */
    @SuppressWarnings("unused")
    public boolean getIsLifted() {
        return mIsLifted;
    }

    /**
     * Gets the safe linear speed.
     *
     * @return the safe linear speed.
     */
    public double getSafeLinearSpeed() {
        return mSafeLinearSpeed;
    }

    /**
     * Gets safe angular speed.
     *
     * @return the safe angular speed.
     */
    public double getSafeAngularSpeed() {
        return mSafeAngularSpeed;
    }

    /**
     * Sets if the robot will do simple bump maneuver.
     *
     * If value is true, the robot will only move backwards.
     *
     * @param value enable simple bumper behavior
     */
    @SuppressWarnings("unused")
    public void useSimpleManeuver(boolean value) {
        mUseSimpleManeuver = value;
    }
}
