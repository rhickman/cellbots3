package ai.cellbots.robot.control;

import android.support.annotation.NonNull;

/**
 * Action for animation.
 */
public class AnimationAction extends Action {
    private final String mAnimation;

    /**
     * Plays an animation.
     *
     * @param completeBy Timestamp to complete the action by in milliseconds.
     *                   Negative for unlimited time.
     * @param animation The name of the animation to play.
     */
    public AnimationAction(long completeBy, @NonNull String animation) {
        super(completeBy);
        mAnimation = animation;
    }

    /**
     * Gets the name of the animation file.
     *
     * @return The animation name.
     */
    @SuppressWarnings("unused")
    public String getAnimation() {
        return mAnimation;
    }
}
