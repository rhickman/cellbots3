package ai.cellbots.companion;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ai.cellbots.common.CloudHelper;
import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.Transform;
import ai.cellbots.common.World;
import ai.cellbots.common.data.DriveGoal;
import ai.cellbots.common.data.DrivePointGoal;
import ai.cellbots.common.data.PatrolDriverGoal;
import ai.cellbots.common.data.PlannerGoal;
import ai.cellbots.common.data.PointOfInterest;
import ai.cellbots.common.data.Robot;
import ai.cellbots.common.data.VacuumSpiralGoal;

/**
 * {@code FlooplanView} class allows to visualize on a Surface View a Floorplan from Tango
 */

public class FloorplanView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = FloorplanView.class.getSimpleName();

    private static final float SCALE = 100f;
    private static final int INVALID_POINTER_ID = -1;
    private static final int SMOOTHED_PATH_MARKER_SIZE = 3;
    private static final int CUSTOM_PATH_MARKER_SIZE = 5;
    private static final int ROBOT_PATH_MARKER_SIZE = 5;
    private static final int GOAL_MARKER_SIZE = 10;

    private volatile DetailedWorld mWorld = null;
    private List<Transform> mSmoothedPath = new ArrayList<>();
    private List<Transform> mOriginalPath = new ArrayList<>();
    private List<Transform> mCustomPath = new ArrayList<>();
    private List<ai.cellbots.common.data.Transform> mRobotPath = new ArrayList<>();

    // CloudHelper and Robot needed to send goals to Firebase.
    private CloudHelper mCloudHelper = new CloudHelper();
    private Robot mCurrentRobot;

    private Paint mBackgroundPaint;
    private Paint mWallPaint;
    private Paint mSpacePaint;
    private Paint mFurniturePaint;

    private Paint mWaypointPaint;
    private Paint mPOIPaint;
    private Paint mActiveDriveGoalPaint;
    private Paint mActiveCleanGoalPaint;
    private Paint mActivePatrolGoalPaint;
    private Paint mSmoothedPathPaint;
    private Paint mCustomPathPaint;
    private Paint mOriginalPathPaint;
    private Paint mRobotPathPaint;

    private Paint mRobotMarkerPaint;

    private Path mRobotMarkerPath;

    private Matrix mCamera;
    private Matrix mCameraInverse;
    private Matrix mPanRotationZoomMatrix;
    private Transform mWaypoint;
    private boolean mDrawWaypoint;

    // Draw markers
    private List<PointOfInterest> mPOIs = new ArrayList<>();
    private boolean mDrawAllPOIs = false;

    private List<PlannerGoal> mActiveGoals = new ArrayList<>();
    private boolean mDrawActiveGoals = false;
    private boolean mDrawAdfData = false;

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mLongPressDetector;
    private RotationGestureDetector mRotationDetector;

    // The ‘active pointer’ is the one currently moving our object.
    private int mActivePointerId = INVALID_POINTER_ID;

    // Position of the last touch event.
    private float mLastTouchX;
    private float mLastTouchY;

    private float mDownActionX;
    private float mDownActionY;

    private double click_time;
    private boolean wasLongPressActivated;

    // While pressing the screen, the x-y displacement from the touch position must surpass this
    // limit (in pixels) to trigger dragging actions.
    private static final float SCROLL_THRESHOLD = 15;
    // While pressing the screen, if the x-y position on the screen stays within the threshold for
    // more than this time (in milliseconds), it is considered a long press.
    private static final float LONG_PRESS_TRIGGERING_TIME = 600;

    private float mDragX = 0;
    private float mDragY = 0;

    private float mScaleFactor = 1.f;
    private float mRotationAngle = 0;

    private SurfaceHolder mSurfaceHolder;

    private float mMinAreaSpace = 0f;
    private float mMinAreaWall = 0f;

    // The coordinates of the center of the canvas in pixels.
    private float translationX;
    private float translationY;

    // Sync flag, only update database after getting the position from it
    private boolean mUpdateDatabase = false;

    MainActivity mListener;

    private Transform mGoal;

    public void setListener(MainActivity l) {
        mListener = l;
    }

    /**
     * Custom render thread, running at a fixed 10Hz rate.
     */
    private class RenderThread extends Thread {
        @Override
        public void run() {
            while (true) {
                SurfaceHolder surfaceHolder = mSurfaceHolder;
                if (surfaceHolder == null) {
                    return;
                }
                Canvas canvas = surfaceHolder.lockCanvas();
                if (canvas != null) {
                    doDraw(canvas);
                    surfaceHolder.unlockCanvasAndPost(canvas);
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    private RenderThread mDrawThread;
    private final Object mRenderLock = new Object();

    public FloorplanView(Context context) {
        super(context);
        init(context);
    }

    public FloorplanView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FloorplanView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // Get parameters.
        TypedValue typedValue = new TypedValue();
        getResources().getValue(R.dimen.min_area_space, typedValue, true);
        mMinAreaSpace = typedValue.getFloat();
        getResources().getValue(R.dimen.min_area_wall, typedValue, true);
        mMinAreaWall = typedValue.getFloat();

        // Pre-create graphics objects.
        mWallPaint = new Paint();
        mWallPaint.setColor(getResources().getColor(android.R.color.black));
        mWallPaint.setStyle(Paint.Style.STROKE);
        mWallPaint.setStrokeWidth(3);
        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(getResources().getColor(android.R.color.white));
        mBackgroundPaint.setStyle(Paint.Style.FILL);
        mSpacePaint = new Paint();
        mSpacePaint.setColor(getResources().getColor(R.color.explored_space));
        mSpacePaint.setStyle(Paint.Style.FILL);
        mFurniturePaint = new Paint();
        mFurniturePaint.setColor(getResources().getColor(R.color.furniture));
        mFurniturePaint.setStyle(Paint.Style.FILL);
        mRobotMarkerPaint = new Paint();
        mRobotMarkerPaint.setColor(getResources().getColor(R.color.user_marker));
        mRobotMarkerPaint.setStyle(Paint.Style.FILL);
        mRobotMarkerPath = new Path();
        mRobotMarkerPath.lineTo(-0.2f * SCALE, 0);
        mRobotMarkerPath.lineTo(-0.2f * SCALE, -0.05f * SCALE);
        mRobotMarkerPath.lineTo(0.2f * SCALE, -0.05f * SCALE);
        mRobotMarkerPath.lineTo(0.2f * SCALE, 0);
        mRobotMarkerPath.lineTo(0, 0);
        mRobotMarkerPath.lineTo(0, -0.05f * SCALE);
        mRobotMarkerPath.lineTo(-0.4f * SCALE, -0.5f * SCALE);
        mRobotMarkerPath.lineTo(0.4f * SCALE, -0.5f * SCALE);
        mRobotMarkerPath.lineTo(0, 0);
        mWaypointPaint = new Paint();
        mPOIPaint = new Paint();
        mPOIPaint.setColor(Color.GREEN);
        mActiveDriveGoalPaint = new Paint();
        mActiveDriveGoalPaint.setColor(Color.BLUE);
        mActiveCleanGoalPaint = new Paint();
        mActiveCleanGoalPaint.setColor(Color.CYAN);
        mActivePatrolGoalPaint = new Paint();
        mActivePatrolGoalPaint.setColor(Color.MAGENTA);
        mSmoothedPathPaint = new Paint();
        mSmoothedPathPaint.setColor(Color.BLACK);
        mOriginalPathPaint = new Paint();
        mOriginalPathPaint.setColor(Color.YELLOW);
        mCustomPathPaint = new Paint();
        mCustomPathPaint.setColor(Color.MAGENTA);

        mCamera = new Matrix();
        mCameraInverse = new Matrix();
        mPanRotationZoomMatrix = new Matrix();

        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        mLongPressDetector = new GestureDetector(new LongPressListener());
        mRotationDetector = new RotationGestureDetector(new RotationListener(), this);

        // Register for surface callback events.
        getHolder().addCallback(this);
    }

    /**
     * Resets the pan, rotation and zoom matrix to the default.
     */
    public void resetPanRotationZoomMatrix() {
        mDragX = 0.0f;
        mDragY = 0.0f;
        mRotationAngle = 0.0f;
        mScaleFactor = 1.0f;
        invalidate();
        mUpdateDatabase = true;
    }

    private void stopDrawThread() {
        synchronized (mRenderLock) {
            mSurfaceHolder = null;
            if (mDrawThread != null) {
                mDrawThread.interrupt();
                try {
                    mDrawThread.join();
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted draw thread: ", e);
                }
                mDrawThread = null;
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        synchronized (mRenderLock) {
            stopDrawThread();
            mSurfaceHolder = surfaceHolder;
            mDrawThread = new RenderThread();
            mDrawThread.start();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        surfaceCreated(surfaceHolder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        stopDrawThread();
    }

    /**
     * Computes the bounding rectangle of a set of objects.
     */
    private final class PositionLimiter {
        private boolean mForce = true;
        private double mMaxX = 0, mMinX = 0;
        private double mMaxY = 0, mMinY = 0;

        /**
         * Creates the position limiter.
         */
        private PositionLimiter() {
        }

        /**
         * Compute if a point is out of bounds.
         * @param x The point x.
         * @param y The point y.
         */
        private boolean isOutOfBounds(double x, double y) {
            return (x < mMinX) || (y < mMinY) || (x > mMaxX) ||  (y > mMaxY);
        }

        /**
         * Clamp the X position.
         * @param x The point x.
         */
        private double clampX(double x) {
            return Math.max(mMinX, Math.min(x, mMaxX));
        }

        /**
         * Clamp the Y position.
         * @param y The point y.
         */
        private double clampY(double y) {
            return Math.max(mMinY, Math.min(y, mMaxY));
        }

        /**
         * Update with an array pose.
         * @param array A 2-element array of the x,y pose of an object.
         */
        private void update(double[] array) {
            update(array[0], array[1]);
        }

        /**
         * Update with a pose.
         * @param x The x pose of the object.
         * @param y The y pose of the object.
         */
        private void update(double x, double y) {
            if (mForce) {
                mMinX = x;
                mMaxX = x;
                mMinY = y;
                mMaxY = y;
                mForce = false;
            }
            mMinX = Math.min(x, mMinX);
            mMaxX = Math.max(x, mMaxX);
            mMinY = Math.min(y, mMinY);
            mMaxY = Math.max(y, mMaxY);
        }

        /**
         * Update with a pose from a transform.
         * @param data The transform.
         */
        private void update(ai.cellbots.common.data.Transform data) {
            if (data != null) {
                update(data.px, data.py);
            }
        }

        /**
         * Update with a pose from a transform.
         * @param data The transform.
         */
        private void update(Transform data) {
            if (data != null) {
                update(data.getPosition(0), data.getPosition(1));
            }
        }
    }

    private void doDraw(Canvas canvas) {
        // Erase the previous canvas image.
        canvas.drawColor(getResources().getColor(android.R.color.white));

        // Start drawing from the center of the canvas.
        translationX = canvas.getWidth() / 2f;
        translationY = canvas.getHeight() / 2f;
        canvas.translate(translationX, translationY);

        DetailedWorld world = mWorld;
        PositionLimiter limiter;
        List<World.FloorPlanPolygon> drawPolygons = new ArrayList<>();
        if (world != null) {
            if (!world.getLevels().isEmpty()) {
                limiter = new PositionLimiter();
                for (World.FloorPlanPolygon polygon : world.getLevels().get(0).getPolygons()) {
                    if (polygon.getArea() <= 0) {
                        continue;
                    }
                    if (polygon.getArea() < mMinAreaSpace
                            && World.FloorPlanPolygon.LAYER_SPACE == polygon.getLayer()) {
                        continue;
                    }
                    if (polygon.getArea() < mMinAreaWall
                            && World.FloorPlanPolygon.LAYER_WALLS == polygon.getLayer()) {
                        continue;
                    }
                    for (double[] vertex : polygon.getVertices()) {
                        limiter.update(vertex);
                    }
                }
                synchronized (this) {
                    for (ai.cellbots.common.data.Transform tf : mRobotPath) {
                        limiter.update(tf);
                    }
                    for (PointOfInterest poi : mPOIs) {
                        if (poi != null && poi.variables != null) {
                            limiter.update(poi.variables.location);
                        }
                    }
                    for (PlannerGoal goalRaw : mActiveGoals) {
                        if (goalRaw instanceof DriveGoal) {
                            DriveGoal goal = (DriveGoal) goalRaw;
                            limiter.update(goal.parameters.location);
                        } else if (goalRaw instanceof DrivePointGoal) {
                            DrivePointGoal goal = (DrivePointGoal) goalRaw;
                            limiter.update(goal.parameters.location);
                        } else if (goalRaw instanceof VacuumSpiralGoal) {
                            VacuumSpiralGoal goal = (VacuumSpiralGoal) goalRaw;
                            limiter.update(goal.parameters.location);
                        } else if (goalRaw instanceof PatrolDriverGoal) {
                            PatrolDriverGoal goal = (PatrolDriverGoal) goalRaw;
                            limiter.update(goal.parameters.location);
                        }
                    }
                    for (Transform tf : mSmoothedPath) {
                        limiter.update(tf);
                    }
                    if (mCustomPath != null) {
                        for (Transform tf : mCustomPath) {
                            limiter.update(tf);
                        }
                    }
                    for (Transform tf : mOriginalPath) {
                        limiter.update(tf);
                    }
                    if (mWaypoint != null) {
                        limiter.update(mWaypoint);
                    }

                    // Transform the x and y position via the angle rotation and check if it is
                    // in the bounds. If not, compute a new position by clamping the angle position.
                    // if that new position differs from the clamped values, set it as the x and y.
                    double rad = Math.toRadians(mRotationAngle);
                    double tfPx = Math.cos(rad) * mDragX + Math.sin(rad) * mDragY;
                    double tfPy = -Math.sin(rad) * mDragX + Math.cos(rad) * mDragY;
                    if (limiter.isOutOfBounds(-tfPx / SCALE / mScaleFactor, tfPy / SCALE / mScaleFactor)) {
                        float clampX = (float) -limiter.clampX(-tfPx / SCALE / mScaleFactor) * SCALE * mScaleFactor;
                        float clampY = (float) limiter.clampY(tfPy / SCALE / mScaleFactor) * SCALE * mScaleFactor;
                        tfPx = Math.cos(-rad) * clampX + Math.sin(-rad) * clampY;
                        tfPy = -Math.sin(-rad) * clampX + Math.cos(-rad) * clampY;
                        if (mDragX != (float) tfPx || mDragY != (float) tfPy) {
                            mDragX = (float) tfPx;
                            mDragY = (float) tfPy;
                            mUpdateDatabase = true;
                        }
                    }
                }
            }
        }

        // Update position and orientation based on the device position and orientation.
        updatePanRotationZoomMatrix();
        // TODO: the scale should be included here instead of applying it later when drawing.
        canvas.concat(mPanRotationZoomMatrix);

        // Draw all the polygons. Make a shallow copy in case mPolygons is reset while rendering.
        if (world != null) {
            if (!world.getLevels().isEmpty()) {
                drawPolygons = world.getLevels().get(0).getPolygons();
            }
        }
        boolean largestSpaceDrawn = false;
        for (World.FloorPlanPolygon polygon : drawPolygons) {
            Paint paint;
            switch (polygon.getLayer()) {
                case World.FloorPlanPolygon.LAYER_FURNITURE:
                    paint = mFurniturePaint;
                    break;
                case World.FloorPlanPolygon.LAYER_SPACE:
                    // Only draw free space polygons larger than 2 square meter.
                    // The goal of this is to suppress free space polygons in front of windows.
                    // Always draw holes (=negative area) independent of surface area.
                    if (polygon.getArea() > 0) {
                        if (largestSpaceDrawn && polygon.getArea() < mMinAreaSpace) {
                            continue;
                        }
                        largestSpaceDrawn = true;
                    }
                    paint = mSpacePaint;
                    break;
                case World.FloorPlanPolygon.LAYER_WALLS:
                    // Only draw wall polygons larger than 20cm x 20cm to suppress noise.
                    if (Math.abs(polygon.getArea()) < mMinAreaWall) {
                        continue;
                    }
                    paint = mWallPaint;
                    break;
                default:
                    Log.w(TAG, "Ignoring polygon with unknown layer value: " + polygon.getLayer());
                    continue;
            }
            if (polygon.getArea() < 0.0) {
                paint = mBackgroundPaint;
            }
            Path path = new Path();
            double[][] vertices = polygon.getVertices();
            if (vertices.length > 0) {
                double[] p = vertices[0];
                // NOTE: We need to flip the Y axis since the polygon data is in Tango start of
                // service frame (Y+ forward) and we want to draw image coordinates (Y+ 2D down).
                path.moveTo((float) (p[0] * SCALE), (float) (-1 * p[1] * SCALE));
                for (int i = 1; i < vertices.length; i++) {
                    double[] point = vertices[i];
                    path.lineTo((float) (point[0] * SCALE), (float) (-1 * point[1] * SCALE));
                }
                if (polygon.getClosed()) {
                    path.close();
                }
                canvas.drawPath(path, paint);
            }
        }

        synchronized (this) {
            // Draw current robot path
            Path path = new Path();
            float robotPosition[] = new float[9];
            mCameraInverse.getValues(robotPosition);
            // Start path from the current robot position
            path.moveTo(robotPosition[Matrix.MTRANS_X], robotPosition[Matrix.MTRANS_Y]);

            for (int i = 0; i < mRobotPath.size(); i++) {
                // Create point in the correct frame
                float point[] = {(float) mRobotPath.get(i).px * SCALE,
                        -(float) mRobotPath.get(i).py * SCALE};
                // Draw point
                mRobotPathPaint = new Paint();
                mRobotPathPaint.setColor(Color.RED);
                canvas.drawCircle(point[0], point[1], ROBOT_PATH_MARKER_SIZE, mRobotPathPaint);
                // Draw line to the next one if it is not the first Transform
                mRobotPathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                mRobotPathPaint.setStyle(Paint.Style.STROKE);
                mRobotPathPaint.setStrokeWidth(2);
                mRobotPathPaint.setColor(Color.RED);
                path.lineTo(point[0], point[1]);
                path.moveTo(point[0], point[1]);
            }
            if (!mRobotPath.isEmpty() && mRobotPathPaint != null) {
                canvas.drawPath(path, mRobotPathPaint);
            }

            // Draw all POIs over the map
            if (mDrawAllPOIs) {
                for (int i = 0; i < mPOIs.size(); i++) {
                    canvas.drawCircle((float) mPOIs.get(i).variables.location.px * SCALE,
                            -(float) mPOIs.get(i).variables.location.py * SCALE, GOAL_MARKER_SIZE,
                            mPOIPaint);
                }
            }

            // Draw all active goals
            if (mDrawActiveGoals) {
                for (PlannerGoal goalRaw : mActiveGoals) {
                    if (goalRaw instanceof DriveGoal) {
                        DriveGoal goal = (DriveGoal) goalRaw;
                        canvas.drawCircle((float) goal.parameters.location.px * SCALE,
                                -(float) goal.parameters.location.py * SCALE,
                                GOAL_MARKER_SIZE, mActiveDriveGoalPaint);
                    } else if (goalRaw instanceof DrivePointGoal) {
                        DrivePointGoal goal = (DrivePointGoal) goalRaw;
                        canvas.drawCircle((float) goal.parameters.location.px * SCALE,
                                -(float) goal.parameters.location.py * SCALE,
                                GOAL_MARKER_SIZE, mActiveDriveGoalPaint);
                    } else if (goalRaw instanceof VacuumSpiralGoal) {
                        VacuumSpiralGoal goal = (VacuumSpiralGoal) goalRaw;
                        canvas.drawCircle((float) goal.parameters.location.px * SCALE,
                                -(float) goal.parameters.location.py * SCALE,
                                GOAL_MARKER_SIZE, mActiveCleanGoalPaint);
                    } else if (goalRaw instanceof PatrolDriverGoal) {
                        PatrolDriverGoal goal = (PatrolDriverGoal) goalRaw;
                        canvas.drawCircle((float) goal.parameters.location.px * SCALE,
                                -(float) goal.parameters.location.py * SCALE,
                                GOAL_MARKER_SIZE, mActivePatrolGoalPaint);
                    }
                }
            }

            // Draw smoothed path
            for (int i = 0; i < mSmoothedPath.size(); i++) {
                canvas.drawCircle((float) mSmoothedPath.get(i).getPosition(0) * SCALE,
                        -(float) mSmoothedPath.get(i).getPosition(1) * SCALE,
                        SMOOTHED_PATH_MARKER_SIZE, mSmoothedPathPaint);
            }

            // Draw custom path
            if (mCustomPath != null) {
                for (int i = 0; i < mCustomPath.size(); i++) {
                    canvas.drawCircle((float) mCustomPath.get(i).getPosition(0) * SCALE,
                            -(float) mCustomPath.get(i).getPosition(1) * SCALE,
                            CUSTOM_PATH_MARKER_SIZE, mCustomPathPaint);
                }
            }

            // Draw original non-smoothed path
            if (mDrawAdfData) {
                for (int i = 0; i < mOriginalPath.size(); i++) {
                    canvas.drawCircle((float) mOriginalPath.get(i).getPosition(0) * SCALE,
                            -(float) mOriginalPath.get(i).getPosition(1) * SCALE,
                            SMOOTHED_PATH_MARKER_SIZE, mOriginalPathPaint);
                }
            }

            // Draw a single marker if there is any available
            if (mDrawWaypoint) {
                Transform waypointView = new Transform(mWaypoint);
                canvas.drawCircle((float) waypointView.getPosition()[0] * SCALE,
                        -(float) waypointView.getPosition()[1] * SCALE, GOAL_MARKER_SIZE,
                        mWaypointPaint);
            }
        }
        // Draw a robot / device marker.
        canvas.concat(mCameraInverse);
        canvas.drawPath(mRobotMarkerPath, mRobotMarkerPaint);
    }

    /**
     * Sets the new floorplan polygons model and levels.
     */
    public synchronized void setFloorplan(DetailedWorld world) {
        mWorld = world;
        mOriginalPath = mWorld.getOriginalPath();
        mSmoothedPath = mWorld.getSmoothedPath();
        mCustomPath = mWorld.getCustomTransforms();
    }

    /**
     * Sets current robot.
     */
    public synchronized void setRobot(Robot currentRobot) {
        mCurrentRobot = currentRobot;
    }

    /**
     * Updates current robot path.
     */
    public synchronized void updateRobotPath(List<ai.cellbots.common.data.Transform> path) {
        mRobotPath = new ArrayList<>(path);
    }

    /**
     * Updates the current rotation and translation to be used for the map. This is called with the
     * current device position and orientation.
     */
    public void updateCameraMatrix(float translationX, float translationY, float yawRadians) {
        mCamera.setTranslate(-translationX * SCALE, translationY * SCALE);
        mCamera.preRotate((float) Math.toDegrees(yawRadians - Math.PI / 2), translationX * SCALE,
                -translationY * SCALE);
        mCamera.invert(mCameraInverse);
    }

    /**
     * Updates the pan and zoom matrix with the most recent drag, rotation and scale information.
     */
    private void updatePanRotationZoomMatrix() {
        float dragX;
        float dragY;
        float scaleFactor;
        // Synchronize before reading fields that can be changed in a different thread.
        synchronized (this) {
            dragX = mDragX;
            dragY = mDragY;
            scaleFactor = mScaleFactor;
        }
        mPanRotationZoomMatrix.setTranslate(dragX, dragY);
        mPanRotationZoomMatrix.postScale(scaleFactor, scaleFactor, dragX, dragY);
        mPanRotationZoomMatrix.postRotate(mRotationAngle, dragX, dragY);

        // Update on the database angle and position
        publishPanRotationZoomMatrixToFirebase();
    }

    /**
     * Publish camera transformation to database
     */
    private void publishPanRotationZoomMatrixToFirebase() {
        if (mCurrentRobot != null && mCurrentRobot.map != null && mCloudHelper != null && mUpdateDatabase) {
            ai.cellbots.common.data.Transform tf = new ai.cellbots.common.data.Transform();
            float position[] = new float[2];
            mPanRotationZoomMatrix.mapPoints(position);
            tf.px = position[0];
            tf.py = position[1];
            tf.qz = Math.sin(mRotationAngle / 2);
            tf.qw = Math.cos(mRotationAngle / 2);
            tf.ts = new Date().getTime();
            mCloudHelper.setMapViewPose(mCurrentRobot.map, tf);
            mUpdateDatabase = false;
        }
    }

    /**
     * Set view from tf data
     *
     * @param tf view position and orientation
     */
    public void setPanRotationZoomMatrix(ai.cellbots.common.data.Transform tf) {
        if (tf != null) {
            double t3 = 2.0 * ((tf.qw * tf.qz) + (tf.qx * tf.qy));
            double t4 = 1.0 - (2.0 * ((tf.qy * tf.qy) + (tf.qz * tf.qz)));
            float yaw = (float) Math.atan2(t3, t4);
            synchronized (this) {
                mDragX = (float) tf.px;
                mDragY = (float) tf.py;
                mRotationAngle = (float) Math.toDegrees(yaw);
            }
        } else {
            Log.v(TAG, "No predefined view pose for this map");
        }
        mUpdateDatabase = true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Let the GestureDetectors inspect all events.
        mLongPressDetector.onTouchEvent(ev);
        mScaleDetector.onTouchEvent(ev);
        mRotationDetector.onTouchEvent(ev);

        final int action = MotionEventCompat.getActionMasked(ev);

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                final int pointerIndex = MotionEventCompat.getActionIndex(ev);
                final float x = MotionEventCompat.getX(ev, pointerIndex);
                final float y = MotionEventCompat.getY(ev, pointerIndex);

                // Remember where we started (for dragging)
                mLastTouchX = x;
                mLastTouchY = y;

                // Remember where action down started (for dragging threshold)
                mDownActionX = x;
                mDownActionY = y;

                click_time = System.currentTimeMillis();
                wasLongPressActivated = false;

                // Save the ID of this pointer (for dragging)
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                break;
            }

            // The following code adds a fix to the bug reported as long press over the map not
            // being detected. A threshold is used to avoid small motions to trigger a drag event.
            // If, in that case, after pressing the screen without reaching the threshold for 0.6
            // seconds the long press event wasn't triggered, then it activates it programmatically.

            // TODO: Handle all these events automatically in a method, rather than performing
            // manual checks.
            case MotionEvent.ACTION_MOVE: {
                // Find the index of the active pointer and fetch its position
                final int pointerIndex =
                        MotionEventCompat.findPointerIndex(ev, mActivePointerId);

                final float x = MotionEventCompat.getX(ev, pointerIndex);
                final float y = MotionEventCompat.getY(ev, pointerIndex);


                // Calculate the distance moved
                final float dx = x - mLastTouchX;
                final float dy = y - mLastTouchY;

                if (Math.abs(x - mDownActionX) >= SCROLL_THRESHOLD || Math.abs(y - mDownActionY)
                        >= SCROLL_THRESHOLD) {
                    // Synchronize while changing the drag variables, since they are read in the
                    // renderer thread.
                    synchronized (this) {
                        mDragX += dx;
                        mDragY += dy;
                    }

                    mUpdateDatabase = true;
                    invalidate();

                    // Remember this touch position for the next move event
                    mLastTouchX = x;
                    mLastTouchY = y;

                } else if (System.currentTimeMillis() - click_time > LONG_PRESS_TRIGGERING_TIME) {
                    // Only activate long press if it wasn't detected before.
                    if (!wasLongPressActivated) {
                        Log.d(TAG, "Long press on map activated programmatically");
                        wasLongPressActivated = true;
                        longPressAction(ev);
                    }
                }
            break;
        }

        case MotionEvent.ACTION_UP: {
            mActivePointerId = INVALID_POINTER_ID;
            break;
        }

        case MotionEvent.ACTION_CANCEL: {
            mActivePointerId = INVALID_POINTER_ID;
            break;
        }

        case MotionEvent.ACTION_POINTER_UP: {
            final int pointerIndex = MotionEventCompat.getActionIndex(ev);
            final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);

            if (pointerId == mActivePointerId) {
                // This was our active pointer going up. Choose a new
                // active pointer and adjust accordingly.
                final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                mLastTouchX = MotionEventCompat.getX(ev, newPointerIndex);
                mLastTouchY = MotionEventCompat.getY(ev, newPointerIndex);
                mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
            }
            break;
        }
    }
        return true;
}

/**
 * A class to handle the scale gestures.
 */
private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
    private final float mMinScaleFactor = 0.1f;
    private final float mMaxScaleFactor = 5.f;

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        // Synchronize while changing the scale factor, since it is read in the renderer thread.
        synchronized (this) {
            mScaleFactor *= detector.getScaleFactor();
            // Don't let the object get too small or too large.
            mScaleFactor = Math.max(mMinScaleFactor, Math.min(mScaleFactor, mMaxScaleFactor));
            mUpdateDatabase = true;
        }
        invalidate();
        return true;
    }
}

/**
 * A class to handle the long tap events    .
 */
private class LongPressListener extends GestureDetector.SimpleOnGestureListener {
    @Override
    public void onLongPress(MotionEvent ev) {
        Log.d(TAG, "Long press on map automatically detected");
        // Set flag to indicate that a long press event was already triggered.
        wasLongPressActivated = true;
        // triggers after onDown only for long press
        super.onLongPress(ev);
        longPressAction(ev);
    }
}

    private void longPressAction(MotionEvent ev) {
        mListener.logLongPressOnFloorplan();

        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final float x = MotionEventCompat.getX(ev, pointerIndex);
        final float y = MotionEventCompat.getY(ev, pointerIndex);

        Matrix worldToImage = new Matrix();
        Matrix invPanRotationZoomMatrix = new Matrix();
        worldToImage.setTranslate(x, y);
        // Remove scale and position from the camera
        mPanRotationZoomMatrix.invert(invPanRotationZoomMatrix);
        worldToImage.postConcat(invPanRotationZoomMatrix);

        float transformed[] = new float[2];
        invPanRotationZoomMatrix.mapPoints(transformed,
                new float[]{x - translationX, y - translationY});
        // Create a transform in ADF frame from tap position.
        mGoal = new Transform(transformed[0] * 1f / SCALE,
                -transformed[1] * 1f / SCALE, 0, 0);

        mWaypoint = new Transform(mGoal);

        mListener.showContextMenu();
    }

    /**
     * Add a drive goal to the current map in Firebase and draw it over the map in RED.
     */
    public void addDriveGoal() {
        // Send selected goal to Firebase.
        mCloudHelper.sendPlannerGoalToRobot(
                new DrivePointGoal(new ai.cellbots.common.data.Transform(mGoal),
                        mCurrentRobot.map), mCurrentRobot);
        mDrawWaypoint = true;
        mWaypointPaint.setColor(Color.RED);
    }

    /**
     * Add a clean goal to the current map in Firebase and draw it over the map in CYAN.
     */
    public void addCleanGoal(long time) {
        // Send selected goal to Firebase.
        mCloudHelper.sendPlannerGoalToRobot(
                // NOTE: THE TIME IS IN MILLISECONDS
                new VacuumSpiralGoal(new ai.cellbots.common.data.Transform(mGoal),
                        mCurrentRobot.map, time * 1000), mCurrentRobot);
        mDrawWaypoint = true;
        mWaypointPaint.setColor(Color.CYAN);
    }

    /**
     * Add a point of interest object to the current map in Firebase and draw it over the map in
     * GREEN.
     */
    public void addPOI(String label) {
        mCloudHelper.addPOI(
                new PointOfInterest(new ai.cellbots.common.data.Transform(mGoal), label),
                mCurrentRobot.map);
        mDrawWaypoint = true;
        mWaypointPaint.setColor(Color.GREEN);
    }

    /**
     * Add a patrolDriver goal to the current map in Firebase and draw it over the map in MAGENTA.
     */
    public void addPatrolDriverGoal(long time) {
        // Send selected goal to Firebase.
        mCloudHelper.sendPlannerGoalToRobot(
                // NOTE: THE TIME IS IN MILLISECONDS
                new PatrolDriverGoal(new ai.cellbots.common.data.Transform(mGoal),
                        mCurrentRobot.map, time * 1000), mCurrentRobot);
        mDrawWaypoint = true;
        mWaypointPaint.setColor(Color.MAGENTA);
    }

    /**
     * Updates markers when a new map is loaded.
     */
    public void updateMarkers() {
        // Do not draw any single marker.
        mDrawWaypoint = false;
        // Clear POIs and show active goals.
        drawAllPOIs(false);
        drawActiveGoals(true);
    }

    /**
     * Sets flag to draw a single marker over the map.
     *
     * @param state true for drawing a single marker.
     */
    public void drawSingleMarker(boolean state) {
        mDrawWaypoint = state;
    }

    /**
     * Make settings to draw a drive POI goal.
     */
    public void drawDrivePOIGoal(PointOfInterest POI) {
        mWaypoint = new Transform(POI.variables.location);
        mWaypointPaint.setColor(Color.RED);
        mDrawWaypoint = true;
    }

    public void setPOIs(List<PointOfInterest> POIs) {
        synchronized (this) {
            mPOIs = new ArrayList<>(POIs);
        }
    }

    public void setActiveGoals(List<PlannerGoal> activeGoals) {
        synchronized (this) {
            mActiveGoals = new ArrayList<>(activeGoals);
        }
    }

    /**
     * Sets flag to draw all pois over the map.
     *
     * @param state true for drawing all pois.
     */
    public void drawAllPOIs(boolean state) {
        mDrawAllPOIs = state;
    }

    /**
     * Sets flag to draw all active goals over the map.
     *
     * @param state true fro drawing active goals
     */
    public void drawActiveGoals(boolean state) {
        mDrawActiveGoals = state;
    }

    /**
     * Sets flag to draw the ADF data on the map.
     */
    public void drawOriginalPath(boolean state) {
        mDrawAdfData = state;
    }

/**
 * A class o handle rotation events
 */
private class RotationListener implements RotationGestureDetector.OnRotationGestureListener {
    @Override
    public void onRotation(float angleIncrement) {
        synchronized (this) {
            mRotationAngle += angleIncrement;
        }
        invalidate();
    }
}
}