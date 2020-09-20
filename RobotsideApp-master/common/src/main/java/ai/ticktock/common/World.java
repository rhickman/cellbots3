package ai.cellbots.common;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * World data class.
 */
public class World {
    private static final String TAG = World.class.getSimpleName();
    final private String mUuid;
    final private String mName;
    final private boolean mVps;

    public static final World VPS_WORLD = new World("VPS", "VPS", true);

    /**
     * Create a world.
     * @param uuid The uuid from the Tango ADF.
     * @param name The name of the world.
     */
    public World(String uuid, String name) {
        this(uuid, name, false);
    }

    /**
     * Create a world.
     * @param uuid The uuid from the Tango ADF.
     * @param name The name of the world.
     * @param vps  True if this is a VPS world.
     */
    private World(String uuid, String name, boolean vps) {
        mUuid = uuid;
        mName = name;
        mVps = vps;
    }

    /**
     * Create a world.
     * @param world World to copy.
     */
    public World(World world) {
        mUuid = world.getUuid();
        mName = world.getName();
        mVps = world.isVPS();
    }

    @SuppressWarnings("WeakerAccess")
    public final static class FloorPlanPolygon {
        final private double[][] mVertices;
        final private boolean mClosed;
        final private double mArea;
        final private int mLayer;

        static final public int LAYER_SPACE = 0;
        static final public int LAYER_WALLS = 1;
        static final public int LAYER_FURNITURE = 2;

        @SuppressWarnings("unused")
        public double[][] getVertices() {
            return mVertices;
        }

        @SuppressWarnings("unused")
        public boolean getClosed() {
            return mClosed;
        }

        @SuppressWarnings("unused")
        public double getArea() {
            return mArea;
        }

        @SuppressWarnings("unused")
        public int getLayer() {
            return mLayer;
        }

        public FloorPlanPolygon(List<float[]> vertices2d, boolean isClosed, double area,
                int layer) {
            if (vertices2d.isEmpty()) {
                throw new Error("Empty polygon from tango");
            }
            mClosed = isClosed;
            mArea = area;
            mLayer = layer;
            mVertices = new double[vertices2d.size()][2];
            for (int i = 0; i < vertices2d.size(); i++) {
                if (vertices2d.get(i).length != 2) {
                    throw new Error("2D vertex length is not 2");
                }
                mVertices[i][0] = vertices2d.get(i)[0];
                mVertices[i][1] = vertices2d.get(i)[1];
            }
        }

        private FloorPlanPolygon(ByteBuffer input) throws ParsingException {
            mClosed = input.get() != 0;
            mArea = input.getDouble();
            mLayer = input.getInt();
            int len = input.getInt();
            if (len <= 0) {
                throw new ParsingException("Zero length polygon");
            }
            mVertices = new double[len][2];
            for (double[] vertex : mVertices) {
                vertex[0] = input.getDouble();
                vertex[1] = input.getDouble();
            }
        }

        private void toByteBuffer(ByteBuffer output) {
            output.put((byte) (mClosed ? 1 : 0));
            output.putDouble(mArea);
            output.putInt(mLayer);
            output.putInt(mVertices.length);
            for (double[] vertex : mVertices) {
                output.putDouble(vertex[0]);
                output.putDouble(vertex[1]);
            }
        }

        private int getByteLength() {
            return 1 + 8 + 4 + 4 + (mVertices.length * 8 * 2);
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static class FloorPlanLevel {
        final private double mMinZ;
        final private double mMaxZ;
        final private List<FloorPlanPolygon> mPolygons;

        @SuppressWarnings("unused")
        public double getMaxZ() {
            return mMaxZ;
        }

        @SuppressWarnings("unused")
        public double getMinZ() {
            return mMinZ;
        }

        @SuppressWarnings("unused")
        public List<FloorPlanPolygon> getPolygons() {
            return mPolygons;
        }

        public FloorPlanLevel(double minZ, double maxZ, List<FloorPlanPolygon> polygons) {
            mMinZ = minZ;
            mMaxZ = maxZ;
            mPolygons = new ArrayList<>(polygons);
        }


        public FloorPlanLevel(ByteBuffer input) throws ParsingException {
            mMinZ = input.getDouble();
            mMaxZ = input.getDouble();
            int pc = input.getInt();
            if (pc < 0) {
                throw new ParsingException("Negative polygon count");
            }
            mPolygons = new ArrayList<>(pc);
            while (pc > 0) {
                mPolygons.add(new FloorPlanPolygon(input));
                pc--;
            }
        }

        public int getByteLength() {
            int len = 8 + 8 + 4;
            for (FloorPlanPolygon polygon : mPolygons) {
                len += polygon.getByteLength();
            }
            return len;
        }

        public void toByteBuffer(ByteBuffer output) {
            output.putDouble(mMinZ);
            output.putDouble(mMaxZ);
            output.putInt(mPolygons.size());
            for (FloorPlanPolygon polygon : mPolygons) {
                polygon.toByteBuffer(output);
            }
        }
    }

    /**
     * Create world from the firebase data.
     *
     * @param data The data map.
     */
    @SuppressWarnings("ConstantConditions")
    public World(Map<String, Object> data) {
        if (data == null) {
            Log.wtf(TAG, "Input data is null");
        }
        mUuid = data.get("uuid").toString();
        mName = data.get("name").toString();
        mVps = false;
    }

    /**
     * Validate a firebase world key.
     *
     * @param data The data map.
     * @return True if the data can be used
     */
    public static boolean isWorldMapValid(Map<String, Object> data) {
        return data != null && data.containsKey("uuid") && data.containsKey("name");
    }

    /**
     * Get the name of the world.
     * @return The world name.
     */
    public String getName() {
        return mName;
    }

    /**
     * Get the uuid of the world.
     *
     * @return The world uuid.
     */
    public String getUuid() {
        return mUuid;
    }

    /**
     * Get if the world is a VPS.
     *
     * @return True if the VPS is saved.
     */
    public boolean isVPS() {
        return mVps;
    }

    /**
     * Get the name of the world.
     * @return The world name.
     */
    public String toString() {
        return mName;
    }

    private static final String INTENT_CLASS_PACKAGE = "com.google.tango";
    private static final String INTENT_DEPRECATED_CLASS_PACKAGE = "com.projecttango.tango";
    //    private static final String INTENT_REQUEST_PERMISSION_CLASSNAME =
    //            "com.google.atap.tango.RequestPermissionActivity";
    private static final String INTENT_IMPORT_EXPORT_CLASSNAME =
            "com.google.atap.tango.RequestImportExportActivity";
    private static final int TANGO_EXPORT_INTENT_ACTIVITY_CODE = 3;
    private static final int TANGO_IMPORT_INTENT_ACTIVITY_CODE = 4;
    private static final String EXTRA_KEY_SOURCE_UUID = "SOURCE_UUID";
    private static final String EXTRA_KEY_DESTINATION_FILE = "DESTINATION_FILE";
    private static final String EXTRA_KEY_SOURCE_FILE = "SOURCE_FILE";

    /**
     * Is it a tango export intent
     *
     * @param requestCode The code
     * @return True if tango
     */
    public static boolean isTangoExportIntent(int requestCode) {
        return requestCode == TANGO_EXPORT_INTENT_ACTIVITY_CODE;
    }

    /**
     * Is it a tango import intent
     *
     * @param requestCode The code
     * @return True if tango
     */
    public static boolean isTangoImportIntent(int requestCode) {
        return requestCode == TANGO_IMPORT_INTENT_ACTIVITY_CODE;
    }

    /**
     * Get the ADF file directory
     *
     * @param parent Parent context.
     * @return The file path.
     */
    static private String getAdfFileDirectory(Context parent) {
        if (parent == null) {
            throw new IllegalArgumentException("Parent must be non-null");
        }
        String pathName = parent.getExternalFilesDir(null) + "/adf/";
        File path = new File(pathName);
        if (path.exists()) {
            if (path.isDirectory()) {
                return pathName;
            } else {
                if (!path.delete()) {
                    throw new IllegalStateException(
                            "Failed to delete non-directory blocking adf directory");
                }
            }
        }
        if (!path.mkdirs()) {
            if (!path.exists() || !path.isDirectory()) {
                throw new IllegalStateException("Could not create ADF directory");
            }
        }
        return pathName;
    }

    /**
     * Get the ADF file
     *
     * @param parent Parent context.
     * @return The file path and name.
     */
    public String getAdfFile(Context parent) {
        //noinspection StringConcatenationMissingWhitespace
        return getAdfFileDirectory(parent) + mUuid;
    }

    /**
     * Save a world to a file.
     *
     * @param parent The activity to manage.
     */
    public void exportToFile(Activity parent) {
        Intent exportIntent = new Intent();
        exportIntent.setClassName(INTENT_CLASS_PACKAGE, INTENT_IMPORT_EXPORT_CLASSNAME);
        if (exportIntent.resolveActivity(parent.getApplicationContext().getPackageManager()) == null) {
            exportIntent = new Intent();
            exportIntent.setClassName(INTENT_DEPRECATED_CLASS_PACKAGE,
                    INTENT_IMPORT_EXPORT_CLASSNAME);
        }
        exportIntent.putExtra(EXTRA_KEY_SOURCE_UUID, mUuid);
        exportIntent.putExtra(EXTRA_KEY_DESTINATION_FILE, World.getAdfFileDirectory(parent));
        parent.startActivityForResult(exportIntent, TANGO_EXPORT_INTENT_ACTIVITY_CODE);
    }

    /**
     * Import a world.
     *
     * @param parent The activity to manage.
     */
    public void importFromFile(Activity parent) {
        Intent importIntent = new Intent();
        importIntent.setClassName(INTENT_CLASS_PACKAGE, INTENT_IMPORT_EXPORT_CLASSNAME);
        if (importIntent.resolveActivity(parent.getApplicationContext().getPackageManager()) == null) {
            importIntent = new Intent();
            importIntent.setClassName(INTENT_DEPRECATED_CLASS_PACKAGE,
                    INTENT_IMPORT_EXPORT_CLASSNAME);
        }
        importIntent.putExtra(EXTRA_KEY_SOURCE_FILE, getAdfFile(parent));
        parent.startActivityForResult(importIntent, TANGO_IMPORT_INTENT_ACTIVITY_CODE);
    }
}
