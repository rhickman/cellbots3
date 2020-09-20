package ai.cellbots.robot.control;

import android.support.annotation.NonNull;

import ai.cellbots.common.Transform;

/**
 * An action to drive to a goal.
 */
public class DriveAction extends Action {
    private final Transform mTransform;
    private final boolean mAchieveRotation;

    /**
     * Drive to a point.
     * @param completeBy Unix millisecond timestamp to complete the action by. Negative means infinite time.
     * @param transform The transform position of the goal.
     * @param achieveRotation True if the rotation should be achieved.
     */
    public DriveAction(long completeBy, @NonNull Transform transform, boolean achieveRotation) {
        super(completeBy);
        mTransform = transform;
        mAchieveRotation = achieveRotation;
    }

    /**
     * Get the transform to drive to.
     * @return The transform.
     */
    public Transform getTransform() {
        return mTransform;
    }

    /**
     * Get if we should match the transform rotation.
     * @return True if we should.
     */
    @SuppressWarnings("unused")
    public boolean getAchieveRotation() {
        return mAchieveRotation;
    }
}
