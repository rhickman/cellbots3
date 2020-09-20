package ai.cellbots.common;

import android.util.Log;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.cellbots.common.data.SmootherParams;

/**
 * A world's detailed information.
 */
public final class DetailedWorld extends World {
    private static final String TAG = "DetailedWorld";

    private final List<Transform> mTransforms;
    private final List<FloorPlanLevel> mLevels;
    private final List<Transform> mSmoothedTransforms;
    private final List<Transform> mCustomTransforms;
    private final String mUserUuid;

    private static final double SMOOTHER_DEFAULT_DEVIATION = 0.1;
    private static final double SMOOTHER_DEFAULT_SMOOTHNESS = 0.3;
    private static final double MIN_NODES_DISTANCE_SQUARED = 0.04;  // In squared meters.

    public static final DetailedWorld VPS_WORLD = new DetailedWorld(World.VPS_WORLD);

    /**
     * Create an empty world with a parent world.
     * @param world Parent world.
     */
    private DetailedWorld(World world) {
        super(world);
        mTransforms = Collections.emptyList();
        mLevels = Collections.emptyList();
        mSmoothedTransforms = Collections.emptyList();
        mCustomTransforms = Collections.emptyList();
        mUserUuid = null;
    }

    /**
     * Loads a detailed world from a Tango device.
     *
     * @param uuid The uuid of the world to load.
     * @param name The name of the world to load.
     * @param file The input stream.
     * @param smootherParams The smoother params.
     * @return The world, or null if it is invalid.
     */
    public static DetailedWorld loadDetailedWorldFromInputStream(
            @SuppressWarnings("SameParameterValue") String uuid,
            @SuppressWarnings("SameParameterValue") String name, InputStream file,
            SmootherParams smootherParams) {
        List<Transform> transforms = new ArrayList<>();
        List<Transform> customTransforms = new ArrayList<>();
        List<FloorPlanLevel> levels = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (!loadFromInputStream(uuid, file, transforms, customTransforms, levels, params)) {
            return null;
        }

        String userUuid = params.containsKey("user_uuid") ? (String) params.get("user_uuid") : null;

        return new DetailedWorld(uuid, name, transforms, customTransforms, levels, userUuid, smootherParams);
    }

    /**
     * Internal function to load a world from a input stream.
     *
     * @param uuid                The uuid of the world.
     * @param fs                  The input stream.
     * @param transforms          The transforms list to be loaded from the file.
     * @param customTransforms    The transforms list to be loaded from the file.
     * @param levels              The levels list to be loaded from the file.
     * @param params              The map of additional data read.
     * @return True if the world was valid, else null.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean loadFromInputStream(String uuid, InputStream fs,
            List<Transform> transforms,
            List<Transform> customTransforms,
            List<FloorPlanLevel> levels,
            Map<String, Object> params) {
        boolean first_node;
        try {
            final byte[] bytes = IOUtils.toByteArray(fs);
            fs.close();

            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

            byte version = byteBuffer.get();

            if (version == 0) {
                first_node = true;
                while (byteBuffer.remaining() > 0) {
                    try {
                        Transform transform =  new Transform(byteBuffer);
                        if (first_node) {
                            transforms.add(transform);
                            first_node = false;
                        } else {
                            // Discard nodes which are less than 0.2m apart to avoid over sampling
                            if (transform.planarDistanceToSquared(
                                    transforms.get(transforms.size() - 1)) >
                                        MIN_NODES_DISTANCE_SQUARED) {
                                transforms.add(transform);
                            }
                        }
                    } catch (ParsingException e) {
                        Log.e(TAG, "World " + uuid + " parsing error for version 0", e);
                        break;
                    }
                }

                for (Transform t : transforms) {
                    Log.d(TAG, "Transform: " + t);
                }
            } else if (version == 1 || version == 2 || version == 3) {
                try {
                    if (version >= 2) {
                        int userNameLen = byteBuffer.getInt();
                        if (userNameLen > 0) {
                            byte[] userBytes = new byte[userNameLen];
                            byteBuffer.get(userBytes);
                            params.put("user_uuid", new String(userBytes));
                        }
                    }

                    int tfCount = byteBuffer.getInt();
                    first_node = true;
                    while (tfCount > 0) {
                        if (first_node) {
                            transforms.add(new Transform(byteBuffer));
                            tfCount--;
                            first_node = false;
                        } else {
                            Transform aux = new Transform(byteBuffer);
                            // Discard nodes which are less than 0.2m apart to avoid over sampling
                            if (aux.planarDistanceToSquared(
                                    transforms.get(transforms.size() - 1)) >
                                        MIN_NODES_DISTANCE_SQUARED) {
                                transforms.add(aux);
                            }
                            tfCount--;
                        }
                    }

                    if (version >= 3) {
                        int customTfCount = byteBuffer.getInt();
                        while (customTfCount > 0) {
                            customTransforms.add(new Transform(byteBuffer));
                            customTfCount--;
                        }
                    }

                    int fpCount = byteBuffer.getInt();
                    while (fpCount > 0) {
                        levels.add(new World.FloorPlanLevel(byteBuffer));
                        fpCount--;
                    }

                } catch (ParsingException e) {
                    Log.e(TAG, "World " + uuid + " parsing error for version 1,2 v = " + version,
                            e);
                }
            } else {
                throw new Error("File version is not zero");
            }

        } catch (IOException e) {
            // Does nothing
            Log.e(TAG, "Error reading file.", e);
            return false;
        } catch (BufferUnderflowException e) {
            Log.e(TAG, "Error getting elements of buffer.", e);
            return false;
        }
        return true;
    }

    /**
     * Creates a world and load the metadata.
     *
     * @param uuid                  The UUID of the world.
     * @param name                  The name of the world.
     * @param transforms            The list of transforms.
     * @param customTransforms      The list of transforms.
     * @param levels                The list of levels.
     * @param userUuid              The user uuid.
     * @param params                The smoother params.
     */
    private DetailedWorld(String uuid, String name, List<Transform> transforms,
            List<Transform> customTransforms,
            List<FloorPlanLevel> levels, String userUuid, SmootherParams params) {
        super(uuid, name);

        mTransforms = Collections.unmodifiableList(transforms);
        mCustomTransforms = Collections.unmodifiableList(customTransforms);
        mLevels = Collections.unmodifiableList(levels);
        mUserUuid = userUuid;

        // Take nodes from current world and calculate smooth path.
        // Remember that if no node is added, smoothedNodes() may return null list.
        List<Transform> smoothed_nodes = smoothedNodes(params);
        if (smoothed_nodes != null) {
            mSmoothedTransforms = Collections.unmodifiableList(smoothed_nodes);
        } else {
            mSmoothedTransforms = Collections.emptyList();
        }
    }

    /**
     * Get the levels of the world.
     *
     * @return The levels.
     */
    @SuppressWarnings("unused")
    public List<FloorPlanLevel> getLevels() {
        return Collections.unmodifiableList(mLevels);
    }

    /**
     * Find cost map boundaries given a list of all floor plan polygons.
     *
     * @param polygons List of floor plan polygons.
     * @return array of doubles with the form {initial:{x,y},final:{x,y}}.
     */
    static public double[][] findFloorPlanBoundaries(List<World.FloorPlanPolygon> polygons) {
        if (polygons.isEmpty()) {
            return null;
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (World.FloorPlanPolygon polygon : polygons) {
            double[][] vertices = polygon.getVertices();

            for (double[] vertex : vertices) {
                minX = Math.min(minX, vertex[0]);
                maxX = Math.max(maxX, vertex[0]);
                minY = Math.min(minY, vertex[1]);
                maxY = Math.max(maxY, vertex[1]);
            }
        }
        return new double[][]{{minX, minY}, {maxX, maxY}};
    }


    /**
     * Gets a transform from the smoothed path list.
     *
     * @param index the transform to get.
     * @return transform for the specified node.
     */
    public Transform getSmoothedTransform(int index) {
        return mSmoothedTransforms.get(index);
    }

    /**
     * Gets the smoothed path list.
     *
     * @return list of transform for the smoothed path.
     */
    public List<Transform> getSmoothedPath() {
        return Collections.unmodifiableList(mSmoothedTransforms);
    }

    /**
     * Gets the bounding box limits of the smoothed path list.
     *
     * @return an array containing (xmin, ymin) and (xmax, ymax).
     */
    public double[][] getSmoothedPathLimits() {
        double[] lower_lim = {Double.MAX_VALUE, Double.MAX_VALUE};
        double[] upper_lim = {-Double.MAX_VALUE, -Double.MAX_VALUE};

        for (int i = 0; i< mSmoothedTransforms.size(); i++) {
            double x = mSmoothedTransforms.get(i).getPosition(0);
            double y = mSmoothedTransforms.get(i).getPosition(1);
            if(x < lower_lim[0]) {
                lower_lim[0] = x;
            }
            if(y < lower_lim[1]) {
                lower_lim[1] = y;
            }
            if(x > upper_lim[0]) {
                upper_lim[0] = x;
            }
            if(y > upper_lim[1]) {
                upper_lim[1] = y;
            }
        }
        return new double[][]{lower_lim, upper_lim};
    }

    /**
     * Gets the non-smoothed path list.
     *
     * @return list of transform for the original path
     */
    public List<Transform> getOriginalPath() {
        return Collections.unmodifiableList(mTransforms);
    }
    /**
     * Gets the number of transforms in the smoothed path list.
     *
     * @return the number of smoothed nodes.
     */
    public int getSmoothedTransformCount() {
        return mSmoothedTransforms.size();
    }

    /**
     * Gets the custom path list.
     *
     * @return list of transform for the custom path
     */
    public List<Transform> getCustomTransforms() {
        return Collections.unmodifiableList(mCustomTransforms);
    }

    /**
     * Gets a transform in the custom path list.
     * @param index The index of the transform.
     * @return the transform
     */
    public Transform getCustomTransform(int index) {
        return mCustomTransforms.get(index);
    }

    /**
     * Gets the bounding box limits of the custom transforms list.
     *
     * @return an array containing (xmin, ymin) and (xmax, ymax).
     */
    public double[][] getCustomTransformsLimits() {
        double[] lower_lim = {Double.MAX_VALUE, Double.MAX_VALUE};
        double[] upper_lim = {Double.MIN_VALUE, Double.MIN_VALUE};

        for (int i = 0; i< mCustomTransforms.size(); i++) {
            double x = mCustomTransforms.get(i).getPosition(0);
            double y = mCustomTransforms.get(i).getPosition(1);
            if(x < lower_lim[0]) {
                lower_lim[0] = x;
            }
            if(y < lower_lim[1]) {
                lower_lim[1] = y;
            }
            if(x > upper_lim[0]) {
                upper_lim[0] = x;
            }
            if(y > upper_lim[1]) {
                upper_lim[1] = y;
            }
        }
        return new double[][]{lower_lim, upper_lim};
    }

    /**
     * Gets the number of transforms in the custom path list.
     *
     * @return the number of custom nodes.
     */
    public int getCustomTransformCount() {
        return mCustomTransforms.size();
    }

    /**
     * Gets the user UUID that created the world. Could be null for old worlds.
     *
     * @return The user UUID, or null.
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    public String getUserUuid() {
        return mUserUuid;
    }

    /**
     * Gets world and compute the smooth path.
     *
     * @param params The smoother params.
     * @return The transform list for the smooth path. Returns null if a smooth path cannot be
     * calculated.
     */
    private List<Transform> smoothedNodes(SmootherParams params) {
        double deviation;
        double smoothness;

        // Print world's transforms. TESTING PURPOSE.
        Log.v("Original Path LENGTH", Integer.toString(mTransforms.size()));
        for (int i = 0; i < mTransforms.size(); i++) {
            Log.v("Original Path", ((mTransforms.get(i)).toString()));
        }

        if (params != null) {
            deviation = params.getDeviation();
            smoothness = params.getSmoothness();
        } else {
            // If smoother params from Firebase are null, then set default values.
            deviation = SMOOTHER_DEFAULT_DEVIATION;
            smoothness = SMOOTHER_DEFAULT_SMOOTHNESS;
        }

        PathPlanner pathPlanner = new PathPlanner(mTransforms, 0, deviation,
                smoothness, 0.0000001);
        pathPlanner.smoothPathCalculator();

        if (pathPlanner.getSmoothPathTransforms() != null) {
            // Print transforms of the smoothed path. TESTING PURPOSE
            Log.v("Smoothed Path LENGTH",
                    Integer.toString(pathPlanner.getSmoothPathTransforms().size()));
            for (int i = 0; i < (pathPlanner.getSmoothPathTransforms()).size(); i++) {
                Log.v("Smoother Path", (((pathPlanner.getSmoothPathTransforms()).get(
                        i)).toString()));
            }
            return pathPlanner.getSmoothPathTransforms();
        } else {
            return null;
        }
    }


    /**
     * Gets user id of a world.
     *
     * @param fs The inputStream of the world.
     * @param uuid The uuid of the world.
     * @return The user uuid, or null if the user does not exist.
     */
    public static String getWorldUserUuid(InputStream fs, String uuid) {
        final int HEADER_LEN = 5;
        try {
            byte[] headerBytes = new byte[HEADER_LEN];  // 1 byte for version and 4 for length
            if (fs.read(headerBytes) != HEADER_LEN) {
                fs.close();
                return null;
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(headerBytes);
            byte version = byteBuffer.get();
            // Only files with version >= 2 have uuid encoded
            if (version < 2) {
                Log.w(TAG, "Old world with no version: " + version);
                fs.close();
                return null;
            }

            int length = byteBuffer.getInt();
            // Zero length username = null
            if (length == 0) {
                Log.w(TAG, "World has no user, empty: " + length);
                fs.close();
                return null;
            }
            if (length < 0) {
                Log.e(TAG, "Invalid length for username in file " + uuid + " value " + length);
                fs.close();
                return null;
            }

            byte[] usernameBytes = new byte[length];
            if (fs.read(usernameBytes) != length) {
                Log.e(TAG, "Could not read username from " + uuid + " wanted " + length);
                fs.close();
                return null;
            }
            fs.close();
            return new String(usernameBytes);
        } catch (IOException e) {
            Log.w(TAG, "Error attempting to find world user uuid for: " + uuid, e);
        }
        return null;
    }
}