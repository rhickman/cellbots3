package ai.cellbots.arcompanion.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.rajawali3d.Object3D;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.primitives.Cylinder;
import org.rajawali3d.primitives.RectangularPrism;

import ai.cellbots.arcompanion.R;

/**
 * A utility class containing boilerplate code for interacting with and building objects for
 * rendering in OpenGL.
 */

public final class RendererUtils {
    // Object Measurements. All in meters.
    private static final float MARKER_SIZE = 0.05f;
    public static final float POI_CYLINDER_LENGTH = 0.5f;
    private static final float POI_CYLINDER_RADIUS = 0.05f;
    private static final float POI_LABEL_WIDTH = 0.8f;
    private static final float POI_LABEL_HEIGHT = 0.4f;
    private static final float POI_LABEL_DEPTH = 0.01f;
    private static final float ROBOT_CYLINDER_LENGTH = 0.15f;
    private static final float ROBOT_CYLINDER_RADIUS = 0.177f;

    private static final int CYLINDER_NUM_LENGTH_SEGMENTS = 1;
    private static final int CYLINDER_NUM_CIRCLE_SEGMENTS = 40;

    // Colors (since using hex values in colors.xml results in an "int values not allowed" error)
    private static final int COLOR_RED_50_TRANSPARENT = 0x7fff0000;
    private static final int COLOR_GREEN_50_TRANSPARENT  = 0x700ff00;
    private static final int COLOR_BLUE_50_TRANSPARENT = 0x7f0000ff;

    // Angles.
    private static final float BITMAP_ROTATION_ANGLE = 180;

    // Construction of this class is NOT allowed. This class contains only static functions.
    private RendererUtils() {
        throw new AssertionError(R.string.assertion_utility_class_never_instantiated);
    }

    /**
     * Builds a parent cube for rendering the robot's active goals in AR view. Renders the goals in
     * batch for improved performance.
     *
     * Add child goals to this root cube by using RendererUtils#buildChild to build the child,
     * then this.addChild(child).
     *
     * @return A parent cube containing all active goals as children cubes.
     */
    @NonNull
    public static Cube buildGoalRootCube() {
        Cube rootCube = new Cube(MARKER_SIZE);
        Material m = new Material(true);
        m.setColor(Color.RED);
        rootCube.setMaterial(m);
        rootCube.setRenderChildrenAsBatch(true);
        // Root cube floats in space at position (0, 0, 0) due to having no position set.
        // Since we want only its children to be visible, set this root cube to be invisible.
        rootCube.setVisible(false);

        return rootCube;
    }

    public static Cylinder buildPOIRootCylinder() {
        Cylinder poiCylinder = new Cylinder(
                POI_CYLINDER_LENGTH,
                POI_CYLINDER_RADIUS,
                CYLINDER_NUM_LENGTH_SEGMENTS,
                CYLINDER_NUM_CIRCLE_SEGMENTS,
                false,
                false,
                true
        );
        Material m = new Material(true);
        m.setColor(Color.GREEN);
        poiCylinder.setMaterial(m);
        poiCylinder.setTransparent(true);
        poiCylinder.setDoubleSided(true);
        poiCylinder.setBackSided(true);
        poiCylinder.setRenderChildrenAsBatch(true);
        // POI cylinder floats in space at position (0, 0, 0) due to having no position set.
        // Since we want only the children to be visible, set this root cylinder to be invisible.
        poiCylinder.setVisible(false);

        return poiCylinder;
    }

    /**
     * Builds the 3D label for the respective POI object that displays the object's name.
     *
     * @param context The Context of this app
     * @param poiName The name of the POI
     * @param x X position of the label
     * @param y Y position of the label
     * @param z Z position of the label
     * @return A RectangularPrism rendering of the POI's name
     */
    public static RectangularPrism buildPOILabel(Context context, String poiName,
                                                 double x, double y, double z) {
        Bitmap bitmap = buildBitmapWithMultilineText(context, poiName);
        // Setting Rajawali's Object3D objects to look at targets (in our case, the camera) results
        // in the object's front face to be upside down.
        // To counteract this inversion, un-invert the bitmap by rotating it by 180 degrees.
        bitmap = rotateBitmap(bitmap, BITMAP_ROTATION_ANGLE);
        final Material material = buildMaterialForPOILabel(bitmap);

        final RectangularPrism poiLabel = new RectangularPrism(
                POI_LABEL_WIDTH,
                POI_LABEL_HEIGHT,
                POI_LABEL_DEPTH
        );
        poiLabel.setMaterial(material);
        poiLabel.setPosition(x, y, z);
        poiLabel.setTransparent(true);  // Required since canvas is semi-transparent.

        return poiLabel;
    }

    /**
     * Builds a cylinder for rendering the robot in AR view.
     *
     * @return semi-transparent blue cylinder rendered at the robot's current position in AR view.
     */
    public static Cylinder buildRobotCylinder() {
        Cylinder robotCylinder = new Cylinder(
                ROBOT_CYLINDER_LENGTH,
                ROBOT_CYLINDER_RADIUS,
                CYLINDER_NUM_LENGTH_SEGMENTS,
                CYLINDER_NUM_CIRCLE_SEGMENTS
        );
        Material m = new Material(true);
        m.setColor(COLOR_BLUE_50_TRANSPARENT);
        robotCylinder.setMaterial(m);
        robotCylinder.setTransparent(true);
        robotCylinder.setDoubleSided(true);
        robotCylinder.rotate(Vector3.Axis.X, 90.0); // Vertical, like the Y-axis

        return robotCylinder;
    }

    /**
     * A child object cloned from parent and set with a position for rendering in 3D space.
     * Contains the same material, shape, color, properties, etc. of its parent.
     *
     * @param parent Parent object
     * @param x X position of the child object
     * @param y Y position of the child object
     * @param z Z position of the child object
     * @return Object3D clone of the parent with position.
     */
    public static Object3D buildChild(Object3D parent, double x, double y, double z) {
        Object3D child = parent.clone();
        child.setPosition(x, y, z);
        // Parent is set to invisible to prevent it from randomly floating at position (0, 0, 0).
        // Since child inherits from its parent and needs to be visible, set the child's visibility
        // state to visible.
        child.setVisible(true);

        return child;
    }

    /**
     * Clones the path root object at the given position with the rotation.
     *
     * @param pathRoot  The object for path root
     * @param x X position of the child object
     * @param y Y position of the child object
     * @param z Z position of the child object
     * @return A clone of the input pathRoot object.
     */
    public static Object3D buildChildWithRotation(Object3D pathRoot,
                                                  double x,
                                                  double y,
                                                  double z,
                                                  double angleX, double angleY, double angleZ) {
        Object3D child = pathRoot.clone();
        child.setPosition(x, y, z);
        child.rotate(Vector3.Axis.X, angleX);
        child.rotate(Vector3.Axis.Y, angleY);
        child.rotate(Vector3.Axis.Z, angleZ);

        // Parent is set to invisible to prevent it from randomly floating at position (0, 0, 0).
        // Since child inherits from its parent and needs to be visible, set the child's visibility
        // state to visible.
        child.setVisible(true);

        return child;
    }

    /**
     * Draws a String onto a Bitmap and automatically expands bitmap height and adds line breaks if
     * the text is too long.
     *
     * Reference: https://www.skoumal.net/en/android-drawing-multiline-text-on-bitmap/
     *
     * @param context The Context of this app
     * @param poiName The name of the input POI
     * @return Bitmap image containing the input text.
     */
    @NonNull
    private static Bitmap buildBitmapWithMultilineText(Context context, String poiName) {
        float scale = context.getResources().getDisplayMetrics().density;

        // Create bitmap to draw to.
        final Bitmap bitmap = Bitmap.createBitmap(400, 150, Bitmap.Config.ARGB_8888);

        // Create a canvas to do the drawing.
        final Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(ContextCompat.getColor(context, R.color.semiTransparentBlack));

        // Paint for coloring.
        final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextSize(14 * scale);
        paint.setShadowLayer(1f, 0f, 1f, Color.BLACK);
        int textWidth = canvas.getWidth() - (int)(14 * scale);

        // Initialize StaticLayout for text.
        StaticLayout textLayout =
                new StaticLayout(poiName, paint, textWidth, Layout.Alignment.ALIGN_CENTER,
                        1.0f, 0.0f, false);

        // Get height of multiline text.
        int textHeight = textLayout.getHeight();

        // Get position of text's top left corner.
        float posX = (bitmap.getWidth() - textWidth) / 2;
        float posY = (bitmap.getHeight() - textHeight) / 2;

        // Draw text to canvas center.
        canvas.save();
        canvas.translate(posX, posY);
        textLayout.draw(canvas);
        canvas.restore();

        return bitmap;
    }

    /**
     * Rotates the given source bitmap by the angle specified.
     *
     * @param bitmap The bitmap to rotate.
     * @param angle Angle of rotation in degrees
     * @return New bitmap rotated by the specified angle
     */
    private static Bitmap rotateBitmap(Bitmap bitmap, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                matrix, true);
    }

    @NonNull
    private static Material buildMaterialForPOILabel(Bitmap bitmap) {
        // Create material using the bitmap and canvas
        final Material material = new Material();
        material.enableLighting(true);
        material.setColorInfluence(0);

        try {
            // Add changes from the bitmap
            material.addTexture(new Texture("poiLabel", bitmap));
        } catch (ATexture.TextureException e) {
            e.printStackTrace();
        }
        return material;
    }
}
