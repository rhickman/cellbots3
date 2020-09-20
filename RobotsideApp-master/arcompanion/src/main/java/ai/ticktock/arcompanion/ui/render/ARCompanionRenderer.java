package ai.cellbots.arcompanion.ui.render;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;

import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangosupport.TangoSupport;

import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.primitives.Cylinder;
import org.rajawali3d.primitives.RectangularPrism;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.renderer.Renderer;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

import ai.cellbots.arcompanion.model.path.line.GoalPathLine;
import ai.cellbots.arcompanion.model.path.rootnode.CustomPathRootNode;
import ai.cellbots.arcompanion.model.path.rootnode.GoalPathRootNode;
import ai.cellbots.arcompanion.model.path.rootnode.OriginalPathRootNode;
import ai.cellbots.arcompanion.model.path.rootnode.SmoothedPathRootNode;
import ai.cellbots.common.ThreadedShutdown;
import ai.cellbots.common.TimedLoop;

/**
 * A renderer for displaying markers for touch positions on the ADF.
 *
 * Uses Rajawali for the OpenGL engine and rendering
 *
 * Created by playerthree on 8/16/17.
 */

public class ARCompanionRenderer extends Renderer implements ThreadedShutdown {

    private static final String TAG = ARCompanionRenderer.class.getSimpleName();

    private static final float[] TEXTURE_COORDINATES
            = new float[]{0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F};

    // Frequency of logging the number of children in scene.
    private static final long LOOP_UPDATE_MILLISECONDS = 1000;

    // AR related fields
    private ATexture mTangoCameraTexture;
    private boolean mSceneCameraConfigured;
    private ScreenQuad mBackgroundQuad;

    // Timed loop for logging the number of children in scene.
    private final TimedLoop mTimedLoop;

    // Semi-transparent blue cylinder that renders the robot in its current position.
    private Cylinder mRobotCylinder;

    // Valid travel paths for robot in current world/ADF
    private OriginalPathRootNode mRootCylinderWithOriginalPathNodes;
    private SmoothedPathRootNode mRootCylinderWithSmoothedPathNodes;
    private CustomPathRootNode mRootCylinderWithCustomPathNodes;
    private GoalPathRootNode mRootCylinderWithGoalPathNodes;
    private GoalPathLine mGoalPathLine;

    // Root cube containing a list of the current robot's active goals for batch rendering.
    private Cube mRootCubeWithAllActiveGoals;

    // Root cylinder and list of rectangular prisms containing the world's points of interest
    // and their respective labels.
    private Cylinder mRootCylinderWithAllPOIs;
    private final List<RectangularPrism> mPOILabels = new ArrayList<>();

    // True when MainActivity passes in a new cylinder representing the robot's position.
    private boolean mRobotMoved = false;

    // True when MainActivity passes in a new root cube containing the new goals.
    private boolean mGoalsUpdated = false;

    // True when MainActivity passes in a new root cylinder containing the new POIs.
    private boolean mPOIsUpdated = false;

    // True when MainActivity passes in the respective path's new nodes and line.
    private boolean mIsOriginalPathUpdated = false;
    private boolean mIsSmoothedPathUpdated = false;
    private boolean mIsCustomPathUpdated = false;
    private boolean mIsGoalPathUpdated = false;

    public ARCompanionRenderer(Context context) {
        super(context);
        mTimedLoop = new TimedLoop(TAG, new TimedLoop.Looped() {
            @Override
            public boolean update() {
                if (getCurrentScene() != null) {
                    Log.d(TAG, "Number of Children in scene: " + getCurrentScene().getNumChildren());
                }
                return true;
            }

            @Override
            public void shutdown() {
                onShutDown();
            }
        }, LOOP_UPDATE_MILLISECONDS);
    }

    @Override
    protected void initScene() {
        // Create a quad covering the whole background and assign a texture to it where the
        // Tango color camera contents will be rendered.
        if (mBackgroundQuad == null) {
            mBackgroundQuad = new ScreenQuad();
            mBackgroundQuad.getGeometry().setTextureCoords(TEXTURE_COORDINATES);
        }
        Material tangoCameraMaterial = new Material();
        tangoCameraMaterial.setColorInfluence(0);
        // We need to use Rajawali's {@code StreamingTexture} since it sets up the texture
        // for GL_TEXTURE_EXTERNAL_OES rendering.
        mTangoCameraTexture =
                new StreamingTexture("camera", (StreamingTexture.ISurfaceListener) null);
        try {
            tangoCameraMaterial.addTexture(mTangoCameraTexture);
            mBackgroundQuad.setMaterial(tangoCameraMaterial);
        } catch (ATexture.TextureException e) {
            Log.e(TAG, "Exception creating texture for RGB camera contents", e);
        }
        getCurrentScene().addChildAt(mBackgroundQuad, 0);

        // Add a directional light in an arbitrary direction.
        DirectionalLight light = new DirectionalLight(1, 0.2, -1);
        light.setColor(1, 1, 1);
        light.setPower(0.8f);
        light.setPosition(3, 2, 4);
        getCurrentScene().addLight(light);
    }

    @Override
    protected void onRender(long elapsedRealtime, double deltaTime) {
        synchronized (this) {
            if (mRobotMoved) {
                // Update robot position by rendering its new cylinder
                if (mRobotCylinder != null) {
                    getCurrentScene().addChild(mRobotCylinder);
                }
                mRobotMoved = false;
            }
            if (mIsOriginalPathUpdated) {
                // Add new original path to scene
                if (mRootCylinderWithOriginalPathNodes != null) {
                    getCurrentScene().addChild(mRootCylinderWithOriginalPathNodes);
                }
                mIsOriginalPathUpdated = false;
            }
            if (mIsSmoothedPathUpdated) {
                // Add new smoothed path to scene
                if (mRootCylinderWithSmoothedPathNodes != null) {
                    getCurrentScene().addChild(mRootCylinderWithSmoothedPathNodes);
                }
                mIsSmoothedPathUpdated = false;
            }
            if (mIsCustomPathUpdated) {
                // Add new custom path to scene
                if (mRootCylinderWithCustomPathNodes != null) {
                    getCurrentScene().addChild(mRootCylinderWithCustomPathNodes);
                }
                mIsCustomPathUpdated = false;
            }
            if (mIsGoalPathUpdated) {
                // Add robot's new goal path to scene
                if (mRootCylinderWithGoalPathNodes != null) {
                    getCurrentScene().addChild(mRootCylinderWithGoalPathNodes);
                }
                if (mGoalPathLine != null) {
                    getCurrentScene().addChild(mGoalPathLine);
                }
                mIsGoalPathUpdated = false;
            }
            if (mGoalsUpdated) {
                // Add new goals to scene
                if (mRootCubeWithAllActiveGoals != null) {
                    getCurrentScene().addChild(mRootCubeWithAllActiveGoals);
                }
                mGoalsUpdated = false;
            }
            if (mPOIsUpdated) {
                // Add new POIs and their labels to scene
                if (mRootCylinderWithAllPOIs != null && !mPOILabels.isEmpty()) {
                    getCurrentScene().addChild(mRootCylinderWithAllPOIs);
                    for (RectangularPrism poiLabel : mPOILabels) {
                        poiLabel.setLookAt(getCurrentCamera().getPosition());
                        getCurrentScene().addChild(poiLabel);
                    }
                }
                mPOIsUpdated = false;
            }
        }
        super.onRender(elapsedRealtime, deltaTime);
    }

    @Override
    public void onRenderSurfaceSizeChanged(GL10 gl, int width, int height) {
        super.onRenderSurfaceSizeChanged(gl, width, height);
        mSceneCameraConfigured = false;
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset,
                                 float xOffsetStep, float yOffsetStep,
                                 int xPixelOffset, int yPixelOffset) {

    }

    @Override
    public void onTouchEvent(MotionEvent event) {

    }

    public void updateColorCameraTextureUv(int rotation){
        if (mBackgroundQuad == null) {
            mBackgroundQuad = new ScreenQuad();
        }

        float[] textureCoords =
                TangoSupport.getVideoOverlayUVBasedOnDisplayRotation(TEXTURE_COORDINATES, rotation);
        mBackgroundQuad.getGeometry().setTextureCoords(textureCoords);
        mBackgroundQuad.getGeometry().reload();
    }

    /**
     * NOTE: This must be called from the OpenGL render thread; it is not thread safe.
     */
    public void updateRenderCameraPose(TangoPoseData cameraPose) {
        float[] rotation = cameraPose.getRotationAsFloats();
        float[] translation = cameraPose.getTranslationAsFloats();
        updateRenderCameraPose(translation, rotation);
    }

    private void updateRenderCameraPose(float[] translation, float[] rotation) {
        Quaternion quaternion = new Quaternion(rotation[3], rotation[0], rotation[1], rotation[2]);
        // Conjugating the Quaternion is needed because Rajawali uses left-handed convention for
        // quaternions.
        getCurrentCamera().setRotation(quaternion.conjugate());
        getCurrentCamera().setPosition(translation[0], translation[1], translation[2]);
    }

    /**
     * It returns the ID currently assigned to the texture where the Tango color camera contents
     * should be rendered.
     * NOTE: This must be called from the OpenGL render thread; it is not thread safe.
     */
    public int getTextureId() {
        return mTangoCameraTexture == null ? -1 : mTangoCameraTexture.getTextureId();
    }

    public boolean isSceneCameraConfigured() {
        return mSceneCameraConfigured;
    }

    /**
     * Sets the projection matrix for the scene camera to match the parameters of the color camera,
     * provided by the {@code TangoCameraIntrinsics}.
     */
    public void setProjectionMatrix(float[] matrixFloats) {
        getCurrentCamera().setProjectionMatrix(new Matrix4(matrixFloats));
    }

    public void updateRobotCylinder(Cylinder robotCylinder) {
        synchronized (this) {
            getCurrentScene().removeChild(mRobotCylinder);
            mRobotCylinder = robotCylinder;
            mRobotMoved = true;
        }
    }

    public void updateOriginalPath(OriginalPathRootNode rootCylinderWithOriginalPathNodes) {
        synchronized (this) {
            getCurrentScene().removeChild(mRootCylinderWithOriginalPathNodes);
            destroyOriginalPathRootNode();
            mRootCylinderWithOriginalPathNodes = rootCylinderWithOriginalPathNodes;

            mIsOriginalPathUpdated = true;
        }
    }

    private void destroyOriginalPathRootNode() {
        if (mRootCylinderWithOriginalPathNodes != null) {
            if (mRootCylinderWithOriginalPathNodes.getMaterial() != null) {
                getTextureManager().removeTextures(mRootCylinderWithOriginalPathNodes.getMaterial().getTextureList());
            }
            mRootCylinderWithOriginalPathNodes.destroy();
            mRootCylinderWithOriginalPathNodes = null;
        }
    }

    public void updateSmoothedPath(SmoothedPathRootNode rootCylinderWithSmoothedPathNodes) {
        synchronized (this) {
            getCurrentScene().removeChild(mRootCylinderWithSmoothedPathNodes);
            destroySmoothedPathRootNode();
            mRootCylinderWithSmoothedPathNodes = rootCylinderWithSmoothedPathNodes;

            mIsSmoothedPathUpdated = true;
        }
    }

    private void destroySmoothedPathRootNode() {
        if (mRootCylinderWithSmoothedPathNodes != null) {
            if (mRootCylinderWithSmoothedPathNodes.getMaterial() != null) {
                getTextureManager().removeTextures(mRootCylinderWithSmoothedPathNodes.getMaterial().getTextureList());
            }
            mRootCylinderWithSmoothedPathNodes.destroy();
            mRootCylinderWithSmoothedPathNodes = null;
        }
    }

    public void updateCustomPath(CustomPathRootNode rootCylinderWithCustomPathNodes) {
        synchronized (this) {
            getCurrentScene().removeChild(mRootCylinderWithCustomPathNodes);
            destroyCustomPathRootNode();
            mRootCylinderWithCustomPathNodes = rootCylinderWithCustomPathNodes;

            mIsCustomPathUpdated = true;
        }
    }

    private void destroyCustomPathRootNode() {
        if (mRootCylinderWithCustomPathNodes != null) {
            if (mRootCylinderWithCustomPathNodes.getMaterial() != null) {
                getTextureManager().removeTextures(mRootCylinderWithCustomPathNodes.getMaterial().getTextureList());
            }
            mRootCylinderWithCustomPathNodes.destroy();
            mRootCylinderWithCustomPathNodes = null;
        }
    }

    public void updateGoalPath(GoalPathRootNode pathRootCylinder,
                               GoalPathLine pathLine) {
        synchronized (this) {
            getCurrentScene().removeChild(mRootCylinderWithGoalPathNodes);
            getCurrentScene().removeChild(mGoalPathLine);
            if (mRootCylinderWithGoalPathNodes != null) {
                if (mRootCylinderWithGoalPathNodes.getMaterial() != null) {
                    getTextureManager().removeTextures(mRootCylinderWithGoalPathNodes.getMaterial().getTextureList());
                }
                mRootCylinderWithGoalPathNodes.destroy();
            }
            if (mGoalPathLine != null) {
                if (mGoalPathLine.getMaterial() != null) {
                    getTextureManager().removeTextures(mGoalPathLine.getMaterial().getTextureList());
                }
                mGoalPathLine.destroy();
            }
            mRootCylinderWithGoalPathNodes = pathRootCylinder;
            mGoalPathLine = pathLine;

            mIsGoalPathUpdated = true;
        }
    }

    public void updateRootCubeWithAllGoals(Cube rootGoalCube) {
        synchronized (this) {
            getCurrentScene().removeChild(mRootCubeWithAllActiveGoals); // Clears scene of current cubes
            mRootCubeWithAllActiveGoals = rootGoalCube;
            mGoalsUpdated = true;
        }
    }

    public void updateRootObjectsWithAllPOIs(Cylinder rootPOICylinder, List<RectangularPrism> poiLabels) {
        synchronized (this) {
            // Clear scene of current POIs
            getCurrentScene().removeChild(mRootCylinderWithAllPOIs);
            // Clear scene of current POI labels
            for (RectangularPrism prism : mPOILabels) {
                getCurrentScene().removeChild(prism);
            }

            mRootCylinderWithAllPOIs = rootPOICylinder;
            mPOILabels.clear();
            mPOILabels.addAll(poiLabels);
            mPOIsUpdated = true;
        }
    }

    public void hidePOIs() {
        synchronized (this) {
            getCurrentScene().removeChild(mRootCylinderWithAllPOIs);
            for (RectangularPrism poiLabel : mPOILabels) {
                getCurrentScene().removeChild(poiLabel);
            }
        }
    }

    /**
     * Clear current scene of all rendered objects.
     */
    public void clear() {
        getCurrentScene().removeChild(mRobotCylinder);
        getCurrentScene().removeChild(mRootCylinderWithOriginalPathNodes);
        getCurrentScene().removeChild(mRootCylinderWithSmoothedPathNodes);
        getCurrentScene().removeChild(mRootCylinderWithCustomPathNodes);
        getCurrentScene().removeChild(mRootCylinderWithGoalPathNodes);
        getCurrentScene().removeChild(mGoalPathLine);
        getCurrentScene().removeChild(mRootCubeWithAllActiveGoals);
        getCurrentScene().removeChild(mRootCylinderWithAllPOIs);

        destroyOriginalPathRootNode();
        destroySmoothedPathRootNode();
        destroyCustomPathRootNode();

        // Clear POI Labels
        for (RectangularPrism poiLabel : mPOILabels) {
            getCurrentScene().removeChild(poiLabel);
        }
    }

    /**
     * Called during shutdown of TimedLoop.
     */
    private void onShutDown() {
        Log.d(TAG, "TimedLoop has shut down.");
    }

    /**
     * Shuts down the TimedLoop.
     */
    @Override
    public void shutdown() {
        mTimedLoop.shutdown();
    }

    /**
     * Waits for the TimedLoop to shut down.
     */
    @Override
    public void waitShutdown() {
        mTimedLoop.waitShutdown();
    }
}
