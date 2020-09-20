package ai.cellbots.robot.slam;

import android.content.Context;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.Transform;
import ai.cellbots.common.concurrent.AtomicEnum;
import ai.cellbots.robot.costmap.CostMap;
import ai.cellbots.robot.state.RobotSessionGlobals;
import ai.cellbots.robot.vision.CameraImage;
import ai.cellbots.robot.vision.DepthImage;
import ai.cellbots.robot.vision.PointCloud;

/**
 * Handles the SLAM aspect of the system.
 */
public abstract class SLAMSystem implements ThreadedShutdown {
    private final RobotSessionGlobals mSession;
    private final Context mParent;
    private final TransformListener mTransformListener;
    private final ColorDepthListener mColorDepthListener;
    private final CameraImageListener mCameraImageListener;
    private final PointCloudListener mPointCloudListener;
    private final StatusUpdateListener mStatusUpdateListener;
    // SLAM system state.
    private final AtomicEnum<State> mState;
    // True if the SLAM system is malfunctioning.
    private final AtomicBoolean mError;
    // Pose transform.
    private Transform mTransform;
    // Lock for pose transform.
    private final Object mTransformLock = new Object();

    /**
     * Creates the SLAM system.
     * @param parent The parent context.
     * @param transformListener The transform listener.
     * @param colorDepthListener The color+depth listener.
     * @param cameraImageListener The camera image listener.
     * @param pointCloudListener The point cloud listener.
     * @param session The session for the robot.
     */
    protected SLAMSystem(Context parent, TransformListener transformListener,
                ColorDepthListener colorDepthListener, CameraImageListener cameraImageListener,
                PointCloudListener pointCloudListener, StatusUpdateListener statusUpdateListener,
                RobotSessionGlobals session) {
        mParent = parent;
        mState = new AtomicEnum<>(State.INITIALIZING);
        mError = new AtomicBoolean(false);
        mTransformListener = transformListener;
        mColorDepthListener = colorDepthListener;
        mCameraImageListener = cameraImageListener;
        mPointCloudListener = pointCloudListener;
        mStatusUpdateListener = statusUpdateListener;
        mSession = session;
    }

    /**
     * Sets if an error occurred.
     */
    protected final void setError() {
        mError.set(true);
    }

    /**
     * Gets if an error occurred.
     *
     * @return True if there was an error.
     */
    public final boolean getError() {
        return mError.get();
    }

    /**
     * Listener for transform updates.
     */
    public interface TransformListener {
        /**
         * Called to update the system.
         * @param slamSystem The SLAM system.
         * @param transform The transform.
         */
        void onTransform(SLAMSystem slamSystem, Transform transform);
    }

    /**
     * Listener for new depth and color data synchronized.
     */
    public interface ColorDepthListener {
        /**
         * Called when a new depth image that is synchronized with a color image is produced.
         *
         * @param slamSystem The SLAM system.
         * @param colorImage The color image.
         * @param depthImage The depth image.
         * @param worldToCameraPose The pose from world origin (adf) to current frame
         *                          represented as [tx, ty, tz, qx, qy, qz, qw]
         */
        void onColorDepth(SLAMSystem slamSystem, CameraImage colorImage, DepthImage depthImage,
                double[] worldToCameraPose);
    }

    /**
     * Listener for updates of the camera image.
     */
    public interface CameraImageListener {
        /**
         * Called when a new camera image is produced. The all actions on the image must be
         * completed by the time the function returns.
         *
         * @param slamSystem The SLAM system.
         * @param colorImage The color image.
         */
        void onColorImage(SLAMSystem slamSystem, CameraImage colorImage);
    }

    /**
     * Listener for point clouds.
     */
    public interface PointCloudListener {
        /**
         * Called when a point cloud is published.
         *
         * @param slamSystem    The SLAM system.
         * @param pointCloud    The PointCloud object.
         * @param depthLocation The depth location.
         */
        void onPointCloud(SLAMSystem slamSystem, PointCloud pointCloud, Transform depthLocation);
    }

    /**
     * Listener for SLAM system status update.
     */
    public interface StatusUpdateListener {
        /**
         * Called when SLAM system status is updated.
         */
        void onSLAMStatusUpdated();
    }

    /**
     * Creates the current state system.
     */
    public enum State {
        INITIALIZING,
        MAPPING,
        NAVIGATING,
        SAVING_MAP,
        SAVED_MAP,
        STOPPING,
        SHUTDOWN
    }

    /**
     * Gets a list of CostMaps.
     *
     * @return The CostMap collection for floor plan, prior trajectory, octomap, etc.
     */
    public abstract Collection<CostMap> getCostMaps();

    /**
     * Sets the SLAM system state.
     *
     * @param state The new state.
     */
    protected final void setState(State state) {
        mState.set(state);
    }

    /**
     * Called when the SLAM system status is updated.
     */
    protected final void statusUpdated() {
        mStatusUpdateListener.onSLAMStatusUpdated();
    }

    /**
     * Sets the transform.
     *
     * @param transform The new transform, or null if not localized.
     */
    protected final void setTransform(Transform transform) {
        synchronized (mTransformLock) {
            mTransform = transform;
        }
        mTransformListener.onTransform(this, transform);
    }

    /**
     * Sets the color and depth data.
     *
     * @param colorImage The color image.
     * @param depthImage The depth image.
     * @param worldToCameraPose The pose from world origin (adf) to current frame
     *                          represented as [tx, ty, tz, qx, qy, qz, qw]
     */
    protected final void setNewColorDepth(CameraImage colorImage, DepthImage depthImage,
            double[] worldToCameraPose) {
        mColorDepthListener.onColorDepth(this, colorImage, depthImage, worldToCameraPose);
    }

    /**
     * Sets the new color image.
     *
     * @param colorImage The color image.
     */
    protected final void setNewColorImage(CameraImage colorImage) {
        mCameraImageListener.onColorImage(this, colorImage);
    }

    /**
     * Sets the new color image.
     *
     * @param pointCloud    The PointCloud object.
     * @param depthLocation The depth location.
     */
    protected final void setNewPointCloud(PointCloud pointCloud, Transform depthLocation) {
        mPointCloudListener.onPointCloud(this, pointCloud, depthLocation);
    }

    /**
     * Gets the transform.
     *
     * @return The current transform.
     */
    public final Transform getTransform() {
        synchronized (mTransformLock) {
            return mTransform;
        }
    }

    /**
     * Gets the SLAM system state.
     *
     * @return The current state of the SLAM system.
     */
    public final State getState() {
        return mState.get();
    }

    /**
     * Gets the parent activity.
     *
     * @return The context.
     */
    protected final Context getParent() {
        return mParent;
    }

    /**
     * Saves the map to a file.

     * @param mapName The map file name to save.
     */
    public abstract void saveMap(final String mapName);

    /**
     * Gets the current session.

     * @return The session state.
     */
    protected final RobotSessionGlobals getSession() {
        return mSession;
    }

    /**
     * Gets if the camera is localized.
     *
     * @return True if the camera is localized.
     */
    public abstract boolean isLocalized();

    /**
     * Gets if the system is currently mapping the world.
     *
     * @return True if the system is currently mapping.
     */
    public abstract boolean isMapping();

    /**
     * Gets if we can save the current world.
     *
     * @return True if the world can be saved.
     */
    public abstract boolean canSaveWorld();
}
