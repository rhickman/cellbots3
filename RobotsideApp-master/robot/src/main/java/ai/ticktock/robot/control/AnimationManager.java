package ai.cellbots.robot.control;

import android.content.Context;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

import ai.cellbots.common.cloud.CloudFileManager;
import ai.cellbots.common.cloud.CloudPath;
import ai.cellbots.common.cloud.CloudSingletonQueueMonitor;
import ai.cellbots.robot.state.RobotSessionGlobals;

/**
 * Manages the animations of a robot.
 */
public class AnimationManager extends Controller<AnimationAction> implements CloudFileManager.Listener {
    private static final String TAG = AnimationManager.class.getSimpleName();

    private final CloudSingletonQueueMonitor<RobotAnimation, AnimationManager.Animation> mCloudQueue;
    private final CloudFileManager mLocalAnimationFileManager;
    private final CloudFileManager mGlobalAnimationFileManager;
    private AtomicBoolean mIsFullySynced;
    private AtomicBoolean mDoAnimationsNeedUpdate;
    private static final long UPDATE_TIME_MILLISECOND = 100;

    /**
     * Animation class.
     */
    public final class Animation {
    }

    /**
     * Create an animator for a robot.
     *
     * @param context  The parent context.
     * @param session  The session.
     * @param velocityMultiplexer  Velocity multiplexer.
     */
    public AnimationManager(Context context, RobotSessionGlobals session, VelocityMultiplexer velocityMultiplexer) {
        super(AnimationManager.class.getSimpleName(),
                session, velocityMultiplexer, UPDATE_TIME_MILLISECOND, AnimationAction.class);
        mIsFullySynced = new AtomicBoolean(false);
        mDoAnimationsNeedUpdate = new AtomicBoolean(false);
        mLocalAnimationFileManager = new CloudFileManager(context, this, "local_animation",
                "local_animation","animation_list.bin", CloudPath.LOCAL_ANIMATIONS_PATH,
                CloudPath.LOCAL_ANIMATIONS_STORAGE_PATH, this);
        mGlobalAnimationFileManager = new CloudFileManager(context, this, "global_animation",
                "global_animation","animation_list.bin", CloudPath.GLOBAL_ANIMATIONS_PATH,
                CloudPath.GLOBAL_ANIMATIONS_STORAGE_PATH, this);
        Log.w(TAG, "Animation queue path: " + CloudPath.ROBOT_ANIMATION_QUEUE_PATH.getDatabaseReference(
                session.getUserUuid(), session.getRobotUuid()));
        mCloudQueue = new CloudSingletonQueueMonitor<>(context, this, CloudPath.ROBOT_ANIMATION_QUEUE_PATH,
                new CloudSingletonQueueMonitor.Listener<RobotAnimation, Animation>() {
                    @Override
                    public boolean dataElementValid(RobotAnimation data) {
                        Log.d(TAG, "dataElementValid()");
                        return false;
                    }

                    @Override
                    public void storedElementFinish(Animation stored) {
                        Log.d(TAG, "storedElementFinish()");
                    }

                    @Override
                    public boolean storedElementIsFinished(Animation stored) {
                        Log.d(TAG, "storedElementIsFinished()");
                        return false;
                    }

                    @Override
                    public Animation storedElementFromDataElement(RobotAnimation data) {
                        Log.d(TAG, "storedElementFromDataElement()");
                        return null;
                    }

                    @Override
                    public void afterListenerTerminated() {
                        Log.d(TAG, "afterListenerTerminated()");
                        mIsFullySynced.set(false);
                    }

                    @Override
                    public void beforeListenerStarted() {
                        Log.w(TAG, "beforeListenerStarted()");
                    }
                }, RobotAnimation.FACTORY);
    }

    /**
     * Called when CloudFileManager is updated.
     */
    @Override
    public void onCloudFileManagerStatusUpdate() {
        Log.d(TAG, "onCloudFileManagerStatusUpdate()");
        mDoAnimationsNeedUpdate.set(true);
    }

    /**
     * Checks if the animation manager is ready for goals to be accepted, which occurs after
     * the cloud file managers are fully synchronized.
     *
     * @return True if it is ready.
     */
    @Override
    protected boolean isReady() {
        /*
        if (mLocalAnimationFileManager.isFullySynchronized() &&
                mGlobalAnimationFileManager.isFullySynchronized() &&
                mAnimationUserUuid != null && mAnimationRobotUuid != null) {
            return true;
        }
        return false;
        */
        return true;
    }

    /**
     * Called on a new action.
     *
     * @param action    The current action.
     * @param newAction True if the action is a new state.
     * @return The state of the animation.
     */
    @Override
    protected Action.State onUpdate(AnimationAction action, boolean newAction) {
        // TODO implement from Controller animation logic, using setTeleop() to return state
        return Action.State.REJECTED;
    }

    /**
     * Stops all existing animations at all levels.
     */
    public void stopAllAnimations() {
        Log.d(TAG, "stopAllAnimations()");
    }

    /**
     * Called when this animation manager is shutdown.
     */
    @Override
    public void onShutdown() {
        stopAllAnimations();
        mLocalAnimationFileManager.shutdown();
        mGlobalAnimationFileManager.shutdown();
        mCloudQueue.shutdown();
        update();
    }

    /**
     * Updates the animation queue.
     */
    public void update() {
        // Update the animation queue.
    }

    public synchronized void update(final String userUuid, final String robotUuid) {
        Log.d(TAG, "update()");
        mLocalAnimationFileManager.update(userUuid, robotUuid);
        mGlobalAnimationFileManager.update(userUuid, robotUuid);
        mCloudQueue.update(userUuid, robotUuid);
        update();
        if (!mIsFullySynced.get()
                && mLocalAnimationFileManager.isFullySynchronized()
                && mGlobalAnimationFileManager.isFullySynchronized()) {
            mIsFullySynced.set(true);
        }
    }
}

