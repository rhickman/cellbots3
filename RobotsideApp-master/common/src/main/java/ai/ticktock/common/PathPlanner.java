package ai.cellbots.common;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import ai.cellbots.common.Transform;

public class PathPlanner {
    @SuppressWarnings("unused")
    private static final String TAG = "PathPlanner";

    // Parameters that will affect the smoothness of the new path. Default values are used in
    // case they are not provided.
    private final int mExtraNodes;
    private final double mWeightDeviation;
    private final double mWeightSmooth;
    private final double mPathTolerance;

    private double[][] mSmoothPath = null;
    private double[][] mUpsampledPath = null;
    private List<Transform> mRawNodes = null;
    private List<Transform> mSmoothedNodes = null;

    /**
     * Class constructor.
     *
     * @param nodeTransforms  - List of transforms in the current World (nodes that make the path to
     *                        follow).
     * @param extraNodes      - int which indicates how many nodes will be injected to perform
     *                        smoothing (total number of samples between consecutive nodes).
     * @param weightDeviation - double in the range [0,1], where 1 indicates no deviation from
     *                        original path.
     * @param weightSmooth    - double in the range [0,1], where 1 indicates a really curved path.
     * @param pathTolerance   - double used to adjust iterations in the planning algorithm.
     */
    @SuppressWarnings("SameParameterValue")
    PathPlanner(List<Transform> nodeTransforms, int extraNodes,
                       double weightDeviation, double weightSmooth, double pathTolerance) {
        mRawNodes = new ArrayList<>(nodeTransforms);
        mExtraNodes = extraNodes;
        mWeightDeviation = weightDeviation;
        mWeightSmooth = weightSmooth;
        mPathTolerance = pathTolerance;
    }

    /**
     * Class constructor. Only one input argument:
     *
     * @param nodeTransforms - List of transforms in the current World (nodes that make the path to
     *                       follow).
     */
    @SuppressWarnings("unused")
    public PathPlanner(List<Transform> nodeTransforms) {
        this(nodeTransforms, 0, 0.1, 0.5, 0.0000001);
    }

    /**
     * Class constructor. Two input args:
     *
     * @param nodeTransforms - List of transforms in the current World (nodes that make the path to
     *                       follow).
     * @param ExtraNodes     - int which indicates how many nodes will be injected to perform
     *                       smoothing (total number of samples between consecutive nodes).
     */
    @SuppressWarnings("unused")
    public PathPlanner(List<Transform> nodeTransforms, int ExtraNodes) {
        this(nodeTransforms, ExtraNodes, 0.1, 0.5, 0.0000001);
    }

    /**
     * This will calculate a smooth path based on the program parameters.
     */
    void smoothPathCalculator() {
        if (mRawNodes.size() > 0) {
            int j;
            // First extract X, Y and timestamp data from the Nodes Transforms.
            double[][] originalPath = new double[mRawNodes.size()][];

            for (int i = 0; i < mRawNodes.size(); i++) {
                originalPath[i] = (mRawNodes.get(i)).toXYTimestamp();
            }

            // Inject extra samples and smooth the path
            mUpsampledPath = injectSamples(originalPath);
            System.out.print(mUpsampledPath[0].length + "\n");
            mSmoothPath = pathSmoother();

            // Convert the smoothed nodes back to transforms.
            mSmoothedNodes = new ArrayList<>();
            for (j = 0; j < mSmoothPath.length - 1; j++) {
                mSmoothedNodes.add(new Transform(mSmoothPath[j], mSmoothPath[j + 1]));
            }
            // For the ending point, keep the orientation from the original ending node.
            double[] lastPos = new double[]{mUpsampledPath[j][0], mUpsampledPath[j][1], 0};
            double[] lastRot = (mRawNodes.get(mRawNodes.size() - 1)).getRotation();
            double lastTS = mUpsampledPath[j][2];
            mSmoothedNodes.add(new Transform(lastPos, lastRot, lastTS));

        } else {
            Log.e(TAG, "World has no transforms");
        }

    }

    /**
     * This method takes the original, upsampled, path and makes it smoother according the deviation
     * and tolerance parameters indicated
     *
     * @return a 2D double for the smoother path.
     */
    private double[][] pathSmoother() {
        // Initialize new path values with original path
        double[][] smoothedPath = new double[mUpsampledPath.length][];
        double pathModification = mPathTolerance;

        for (int i = 0; i < mUpsampledPath.length; i++) {
            smoothedPath[i] = mUpsampledPath[i].clone();
        }

        // Now apply gradient descent algorithm to smooth the path
        while (pathModification >= mPathTolerance) {
            pathModification = 0.0;
            for (int i = 1; i < mUpsampledPath.length - 1; i++)
                for (int j = 0; j < mUpsampledPath[i].length - 1; j++) {
                    double aux = smoothedPath[i][j];
                    smoothedPath[i][j] += mWeightDeviation * (mUpsampledPath[i][j] -
                            smoothedPath[i][j]) + mWeightSmooth * (smoothedPath[i - 1][j] +
                            smoothedPath[i + 1][j] - (2.0 * smoothedPath[i][j]));
                    pathModification += Math.abs(aux - smoothedPath[i][j]);
                }
        }
        return smoothedPath;
    }

    /**
     * Inject extra samples between the given data points
     *
     * @return a 2D array which is the upsampled path.
     */
    private double[][] injectSamples(double[][] nodes) {
        int w = nodes.length + mExtraNodes * (nodes.length - 1);
        double upSPath[][] = new double[w][nodes[0].length];

        // Initialize upsampled path with the x and y values for the first node in the original path
        int j = 0;
        upSPath[j][0] = nodes[j][0];
        upSPath[j][1] = nodes[j][1];
        upSPath[j][2] = nodes[j][2];
        j++;

        for (int i = 0; i < nodes.length - 1; i++) {
            // Calculate x and y increments to place extra samples between nodes
            double dx = (nodes[i + 1][0] - nodes[i][0]) / (mExtraNodes + 1);
            double dy = (nodes[i + 1][1] - nodes[i][1]) / (mExtraNodes + 1);
            // Now inject equidistant samples between two nodes of the original path
            while (j < (i + 1) * (mExtraNodes + 1)) {
                upSPath[j][0] = upSPath[j - 1][0] + dx;
                upSPath[j][1] = upSPath[j - 1][1] + dy;
                // clone the timestamp from the preceding original node on the injected samples
                upSPath[j][2] = nodes[i][2];
                j++;
            }
            // For the main nodes, load values from the original path (to avoid inaccuracy when
            // summing increments)
            upSPath[j][0] = nodes[i + 1][0];
            upSPath[j][1] = nodes[i + 1][1];
            upSPath[j][2] = nodes[i + 1][2];
            j++;
        }
        return upSPath;
    }

    /**
     * Get the smoother path.
     *
     * @return a 2D array with the (X, Y) coordinates and the timestamp for the smoothed path.
     */
    @SuppressWarnings("unused")
    public double[][] getSmoothPath() {
        return mSmoothPath;
    }

    /**
     * Get the upsampled path as an array.
     *
     * @return a 2D array with the (X, Y) coordinates and the timestamp for the original path,
     * upsampled.
     */
    @SuppressWarnings("unused")
    public double[][] getUpsampledPath() {
        return mUpsampledPath;
    }

    /**
     * Get the smoother path as a list of transforms.
     *
     * @return a list of transforms for the smoothed path.
     */
    List<Transform> getSmoothPathTransforms() {
        return mSmoothedNodes;
    }

    /**
     * Get a transform from the smooth path list.
     *
     * @param index the transform to get.
     * @return transform for the specified node.
     */
    @SuppressWarnings("unused")
    public Transform getSmoothedTransform(int index) {
        return mSmoothedNodes.get(index);
    }

    /**
     * Get the number of transforms in the smoothed path list.
     *
     * @return the number of smoothed nodes.
     */
    @SuppressWarnings("unused")
    public int getSmoothPathLength() {
        return mSmoothedNodes.size();
    }
}
