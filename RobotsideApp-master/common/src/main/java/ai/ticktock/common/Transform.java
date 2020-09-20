package ai.cellbots.common;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Transformation between two 3D points.
 */
public class Transform {
    @SuppressWarnings("unused")
    private static final String TAG = "Transform";

    private final double[] mPosition; // The position of the transform in (x,y,z) format
    private final double[] mRotation; // The rotation quaternion in (x,y,z,w) format
    private final double mTimestamp; // The timestamp in Tango milliseconds. Negative if invalid.

    /**
     * Get the position variable of the transform.
     *
     * @param i The axis to get.
     * @return Position value, generally in meters.
     */
    public double getPosition(int i) {
        return mPosition[i];
    }

    /**
     * Get a copy of the position array values.
     *
     * @return The position array copy.
     */
    public double[] getPosition() {
        double[] r = new double[mPosition.length];
        System.arraycopy(mPosition, 0, r, 0, r.length);
        return r;
    }

    /**
     * Get the euler X rotation.
     *
     * @return The rotation around the X axis, in radians.
     */
    @SuppressWarnings("unused")
    public double getRotationX() {
        if (mRotation.length != 4) {
            throw new Error("Invalid rotation");
        }
        double qx = mRotation[0];
        double qy = mRotation[1];
        double qz = mRotation[2];
        double qw = mRotation[3];

        double t0 = +2.0 * ((qw * qx) + (qy * qz));
        double t1 = +1.0 - (2.0 * ((qx * qx) + (qy * qy)));
        return Math.atan2(t0, t1);
    }

    /**
     * Get the euler Z rotation.
     *
     * @return The rotation around the Z axis, in radians.
     */
    @SuppressWarnings("unused")
    public double getRotationY() {
        if (mRotation.length != 4) {
            throw new Error("Invalid rotation");
        }
        double qx = mRotation[0];
        double qy = mRotation[1];
        double qz = mRotation[2];
        double qw = mRotation[3];

        double t2 = +2.0 * ((qw * qy) - (qz * qx));
        t2 = (t2 > 1.0) ? 1.0 : t2;
        t2 = (t2 < -1.0) ? -1.0 : t2;
        return Math.asin(t2);
    }

    /**
     * Get the euler Z rotation.
     *
     * @return The rotation around the Z axis, in radians.
     */
    @SuppressWarnings("WeakerAccess")
    public double getRotationZ() {
        if (mRotation.length != 4) {
            throw new Error("Invalid rotation");
        }
        double qx = mRotation[0];
        double qy = mRotation[1];
        double qz = mRotation[2];
        double qw = mRotation[3];

        double t3 = +2.0 * ((qw * qz) + (qx * qy));
        double t4 = +1.0 - (2.0 * ((qy * qy) + (qz * qz)));

        //Log.i(TAG, "RAW ROTATION: " + qx + " " + qy + " " + qz + " " + qw);
        return Math.atan2(t3, t4);
    }

    /**
     * Get a quaternion value.
     *
     * @param i The index of the quaternion (X, Y, Z, W).
     * @return The quaternion value.
     */
    @SuppressWarnings("WeakerAccess")
    public double getRotation(int i) {
        return mRotation[i];
    }

    /**
     * Get a copy of the rotation quaternion.
     *
     * @return The copy of the quaternion array.
     */
    @SuppressWarnings("unused")
    public double[] getRotation() {
        double[] r = new double[mRotation.length];
        System.arraycopy(mRotation, 0, r, 0, r.length);
        return r;
    }

    /**
     * Get the timestamp of the Transform.
     *
     * @return The timestamp.
     */
    public double getTimestamp() {
        return mTimestamp;
    }

    /**
     * Initialize a transform at an XYZ coordinate with a rotation about Z.
     *
     * @param x X position, generally in meters.
     * @param y Y position, generally in meters.
     * @param z Z position, generally in meters.
     * @param r The rotation about the Z-axis in radians.
     */
    public Transform(@SuppressWarnings("SameParameterValue") double x,
            @SuppressWarnings("SameParameterValue") double y,
            double z, @SuppressWarnings("SameParameterValue") double r) {
        mPosition = new double[]{x, y, z};
        mRotation = new double[]{0, 0, Math.sin(r / 2), Math.cos(r / 2)};
        mTimestamp = -1.0;
    }

    /**
     * Copy a transform.
     *
     * @param t The transform to copy.
     */
    public Transform(Transform t) {
        mPosition = t.getPosition();
        mRotation = t.getRotation();
        mTimestamp = t.getTimestamp();
    }

    /**
     * Create a transform from a ai.cellbots.common.data.Transform object.
     * @param t ai.cellbots.common.data.Transform instance.
     */
    public Transform(ai.cellbots.common.data.Transform t) {
        mPosition = new double[]{t.px, t.py, t.pz};
        mRotation = new double[]{
                t.qx, t.qy, t.qz, t.qw};
        mTimestamp = t.ts;
    }

    /**
     * Create a transform from raw values..
     *
     * @param pos The position array (3 values), generally meters.
     * @param rot The quaternion array (4 values).
     * @param ts  The timestamp.
     */
    public Transform(double[] pos, double[] rot, double ts) {
        if (pos.length != 3) {
            throw new Error("Array size for position is not 3");
        }
        if (rot.length != 4) {
            throw new Error("Array size for rotation is not 4");
        }
        mPosition = new double[3];
        mRotation = new double[4];
        System.arraycopy(pos, 0, mPosition, 0, pos.length);
        System.arraycopy(rot, 0, mRotation, 0, rot.length);
        mTimestamp = ts;
    }

    /**
     * Compose a transform of two transforms. The final point will be the first transform
     * plus the second transform, rotated by the first transform's quaternion. The timestamp
     * will be the maximum of the two timestamps.
     *
     * @param t1 The initial transform.
     * @param t2 The second transform, added after rotation.
     */
    @SuppressWarnings("unused")
    public Transform(Transform t1, Transform t2) {
        mTimestamp = Math.max(t1.getTimestamp(), t2.getTimestamp());
        mRotation = quaternionMultiply(
                vectorNormalize(t1.getRotation()), vectorNormalize(t2.getRotation()));
        double[] tPos = t1.rotatePoint(t2.getPosition());
        mPosition = vectorAdd(t1.getPosition(), tPos);
    }

    /**
     * Create a transform by summing two transforms positions and then setting a fixed
     * rotation about the Z axis. The timestamp will be the maximum of the two transform
     * timestamps.
     *
     * @param t1 The first transform.
     * @param t2 The second transform.
     * @param r  The rotation about the Z axis in radians.
     */
    @SuppressWarnings("unused")
    public Transform(Transform t1, Transform t2, double r) {
        mRotation = new double[]{0, 0, Math.sin(r / 2), Math.cos(r / 2)};
        mTimestamp = Math.max(t1.getTimestamp(), t2.getTimestamp());
        mPosition = new double[]{
                t1.getPosition(0) + t2.getPosition(0),
                t1.getPosition(1) + t2.getPosition(1),
                t1.getPosition(2) + t2.getPosition(2),
        };
    }

    /**
     * Create a transform from an (X,Y) coordinate. Set Z = 0, and orientation towards following
     * target node.
     *
     * @param Node1 double array with X, Y and timestamp data for the current node.
     * @param Node2 double array with X, Y and timestamp data for target node.
     */
    @SuppressWarnings("unused")
    public Transform(double[] Node1, double[] Node2) {
        double yaw = Math.atan2(Node2[1] - Node1[1], Node2[0] - Node1[0]);
        mRotation = new double[]{0, 0, Math.sin(yaw / 2), Math.cos(yaw / 2)};
        mPosition = new double[]{Node1[0], Node1[1], 0.0};
        mTimestamp = Node1[2];
    }

    /**
     * Create an array of three elements: the X and Y coordinates and the timestamp
     * of the Transform.
     */
    @SuppressWarnings("unused")
    public double[] toXYTimestamp() {
        double[] nodeData = new double[3];
        nodeData[0] = mPosition[0];
        nodeData[1] = mPosition[1];
        nodeData[2] = mTimestamp;
        return nodeData;
    }

    /**
     * Multiply two quaternions.
     *
     * @param q First quaternion in W, X, Y, Z format.
     * @param r Second quaternion in W, X, Y, Z format.
     * @return Final quaternion in W, X, Y, Z format.
     */
    private static double[] quaternionMultiply(double[] q, double[] r) {
        double newX = q[1] * r[2] - q[2] * r[1] + q[0] * r[3] + q[3] * r[0];
        double newY = q[2] * r[0] - q[0] * r[2] + q[1] * r[3] + q[3] * r[1];
        double newZ = q[0] * r[1] - q[1] * r[0] + q[2] * r[3] + q[3] * r[2];
        double newW = q[3] * r[3] - q[0] * r[0] - q[1] * r[1] - q[2] * r[2];
        return new double[]{newX, newY, newZ, newW};
    }

    /**
     * Rotate a point by the quaternion of this transform.
     *
     * @param point The X, Y, Z point to rotate.
     * @return The point rotated.
     */
    private double[] rotatePoint(double[] point) {
        double[] r = {point[0], point[1], point[2], 0};
        double len = vectorLength(r);
        if (len == 0.0) {
            return point;
        }
        r = vectorScale(r, 1.0 / len);
        double[] out = quaternionMultiply(
                quaternionMultiply(vectorNormalize(mRotation), r),
                quaternionConjugate(vectorNormalize(mRotation)));
        return new double[]{out[0] * len, out[1] * len, out[2] * len};
    }


    /**
     * Get the length of this transform when written out the bytes.
     *
     * @return The number of bytes.
     */
    public int getByteLength() {
        return 10 + (mPosition.length * 8) + (mRotation.length * 8);
    }

    /**
     * Create a transform by reading bytes from a buffer.
     *
     * @param byteBuffer The buffer to read from.
     * @throws ParsingException Thrown if an invalid transform is read.
     */
    public Transform(ByteBuffer byteBuffer) throws ParsingException {
        if (byteBuffer.remaining() < 10) {
            throw new ParsingException("WorldTransform cannot be read from header, need at "
                    + "least 10 bytes but have " + byteBuffer.remaining());
        }

        int posLen = (int) byteBuffer.get();
        int rotLen = (int) byteBuffer.get();

        if ((posLen < 1) || (posLen > 127)) {
            throw new ParsingException("WorldTransform cannot be read from buffer since"
                    + " it has an invalid position length: " + posLen);
        }
        if ((rotLen < 1) || (rotLen > 127)) {
            throw new ParsingException("WorldTransform cannot be read from buffer since"
                    + " it has an invalid rotation length: " + posLen);
        }

        mPosition = new double[posLen];
        mRotation = new double[rotLen];
        mTimestamp = byteBuffer.getDouble();
        //Log.i(TAG, "Read pl " + mPosition.length + " rl " + mRotation.length + " ts " +
        // mTimestamp);

        int need = (8 * mPosition.length) + (8 * mRotation.length);
        if (byteBuffer.remaining() < need) {
            throw new ParsingException("WorldTransform cannot be read from header, need at "
                    + "least " + need + " but have " + byteBuffer.remaining());
        }

        for (int i = 0; i < mPosition.length; i++) {
            mPosition[i] = byteBuffer.getDouble();
        }
        for (int i = 0; i < mRotation.length; i++) {
            mRotation[i] = byteBuffer.getDouble();
        }
    }

    /**
     * Write the transform out to a byte buffer.
     *
     * @param byteBuffer The byte buffer to write.
     */
    public void toByteBuffer(ByteBuffer byteBuffer) {
        byteBuffer.put((byte) mPosition.length);
        byteBuffer.put((byte) mRotation.length);
        byteBuffer.putDouble(mTimestamp);
        for (double v : mPosition) {
            byteBuffer.putDouble(v);
        }
        for (double v : mRotation) {
            byteBuffer.putDouble(v);
        }
    }

    /**
     * Returns the squared distance to the target in the X and Y axes only.
     *
     * @param target Target transform.
     * @return The distance squared in only X and Y axes.
     */
    @SuppressWarnings("WeakerAccess")
    public double planarDistanceToSquared(Transform target) {
        double[] tPosition = target.getPosition();
        if ((tPosition.length < 2) || (mPosition.length < 2)) {
            return 0.0;
        }
        double dx = tPosition[0] - mPosition[0];
        double dy = tPosition[1] - mPosition[1];
        return (dx * dx) + (dy * dy);
    }

    /**
     * Get a human readable string of the transform.
     *
     * @return The transform to read.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean start = true;
        for (double v : mPosition) {
            if (start) {
                sb.append("T: ");
                sb.append(mTimestamp);
                sb.append(", P: [");
                start = false;
            } else {
                sb.append(", ");
            }
            sb.append(v);
        }
        start = true;
        for (double v : mRotation) {
            if (start) {
                sb.append("], R: [");
                start = false;
            } else {
                sb.append(", ");
            }
            sb.append(v);
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Compare two transforms.
     *
     * @return true if both transforms are equal, false if they are not.
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Transform)) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        Transform t = (Transform) obj;
        double[] tPosition = t.getPosition();
        double[] tRotation = t.getRotation();
        if (tPosition.length != mPosition.length) {
            return false;
        }
        if (tRotation.length != mRotation.length) {
            return false;
        }
        for (int i = 0; i < mPosition.length; i++) {
            if (tPosition[i] != mPosition[i]) {
                return false;
            }
        }
        for (int i = 0; i < mRotation.length; i++) {
            if (tRotation[i] != mRotation[i]) {
                return false;
            }
        }
        return t.getTimestamp() == mTimestamp;
    }

    /**
     * Get the double value from a map.
     *
     * @param map The map.
     * @param key The key.
     * @param def The default value.
     * @return The value or the default if it could not be loaded.
     */
    private static double getDouble(Map<String, Object> map, String key, double def) {
        if (!map.containsKey(key)) {
            return def;
        }
        if (map.get(key) instanceof Double) {
            return (double) map.get(key);
        }
        if (map.get(key) instanceof Float) {
            return (double) map.get(key);
        }
        try {
            return Double.valueOf(map.get(key).toString());
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * Create a transform from a map.
     *
     * @param map The map to load from.
     */
    public Transform(Map<String, Object> map) {
        mTimestamp = getDouble(map, "ts", -1.0);
        mPosition = new double[3];
        mPosition[0] = getDouble(map, "px", 0.0);
        mPosition[1] = getDouble(map, "py", 0.0);
        mPosition[2] = getDouble(map, "pz", 0.0);
        mRotation = new double[4];
        mRotation[0] = getDouble(map, "qx", 0.0);
        mRotation[1] = getDouble(map, "qy", 0.0);
        mRotation[2] = getDouble(map, "qz", 0.0);
        mRotation[3] = getDouble(map, "qw", 0.0);
    }

    /**
     * Return a map for the cloud.
     *
     * @return The cloud map.
     */
    public Map<String, Object> toMap() {
        if ((mPosition.length < 3) || (mRotation.length < 4)) {
            return null;
        }
        Map<String, Object> r = new HashMap<>();
        r.put("px", mPosition[0]);
        r.put("py", mPosition[1]);
        r.put("pz", mPosition[2]);
        r.put("qx", mRotation[0]);
        r.put("qy", mRotation[1]);
        r.put("qz", mRotation[2]);
        r.put("qw", mRotation[3]);
        r.put("ts", mTimestamp);
        return r;
    }

    private static final Transform ORIGIN = new Transform(0, 0, 0, 0);

    /**
     * Compute the squared distance to a position.
     * @param tf The transform.
     * @return The squared distance.
     */
    @SuppressWarnings("WeakerAccess")
    public double distanceToSquared(Transform tf) {
        double[] tPosition = tf.getPosition();
        if ((tPosition.length < 3) || (mPosition.length < 3)) {
            return 0.0;
        }
        double dx = tPosition[0] - mPosition[0];
        double dy = tPosition[1] - mPosition[1];
        double dz = tPosition[2] - mPosition[2];
        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    /**
     * Compute the distance to a position.
     * @param tf The transform.
     * @return The distance.
     */
    @SuppressWarnings("WeakerAccess")
    public double distanceTo(Transform tf) {
        return Math.sqrt(distanceToSquared(tf));
    }

    /**
     * Subtract vectors
     * @param v1 The first vector.
     * @param v2 The second vector.
     * @return The subtracted result vector.
     */
    private static double[] vectorSub(double[] v1, double[] v2) {
        double[] out = v1.clone();
        for (int i = 0; i < out.length && i < v2.length; i++) {
            out[i] -= v2[i];
        }
        return out;
    }

    /**
     * Add vectors
     * @param v1 The first vector.
     * @param v2 The second vector.
     * @return The subtracted result vector.
     */
    private static double[] vectorAdd(double[] v1, double[] v2) {
        double[] out = v1.clone();
        for (int i = 0; i < out.length && i < v2.length; i++) {
            out[i] += v2[i];
        }
        return out;
    }

    /**
     * Dot product of vectors
     * @param v1 The first vector.
     * @param v2 The second vector.
     * @return The dot product of the resulting vectors.
     */
    private static double vectorDot(double[] v1, double[] v2) {
        double out = 0;
        for (int i = 0; i < v1.length && i < v2.length; i++) {
            out += v1[i] * v2[i];
        }
        return out;
    }

    /**
     * Vector length.
     * @param v1 The vector.
     * @return The vector length.
     */
    private static double vectorLength(double[] v1) {
        double out = 0;
        for (double x : v1) {
            out += x * x;
        }
        return Math.sqrt(out);
    }

    /**
     * Normalize the vector.
     * @param v1 The vector.
     * @return The normalized result vector.
     */
    private static double[] vectorNormalize(double[] v1) {
        double len = vectorLength(v1);
        if (len == 0.0) {
            double[] out = new double[v1.length];
            out[out.length - 1] = 1.0;
            return out;
        }
        return vectorScale(v1, 1.0 / len);
    }

    /**
     * Rescale a vector.
     * @param v1 The vector.
     * @param factor The factor to scale by.
     * @return The result vector.
     */
    private static double[] vectorScale(double[] v1, double factor) {
        double[] out = v1.clone();
        for (int i = 0; i < out.length; i++) {
            out[i] *= factor;
        }
        return out;
    }

    /**
     * Compute the cross product of two vectors
     * @param v1 The first vector.
     * @param v2 The second vector.
     * @return The cross product of the vectors.
     */
    private static double[] vectorCrossProduct(double[] v1, double[] v2) {
        return new double[]{v1[1]*v2[2] - v1[2]*v2[1],
                v1[2]*v2[0] - v1[0]*v2[2],
                v1[0]*v2[1] - v1[1]*v2[0]};
    }


    /**
     * Sets the quaternion from the given x-, y- and z-axis from a matrix.
     *
     * Taken from Bones framework for JPCT, see http://www.aptalkarga.com/bones/ which in turn took
     * it from Graphics Gem code at ftp://ftp.cis.upenn.edu/pub/graphics/shoemake/quatut.ps.Z.
     *
     * @param normalizeAxes whether to normalize the axes (necessary when they contain scaling)
     * @param xx x-axis x-coordinate
     * @param xy x-axis y-coordinate
     * @param xz x-axis z-coordinate
     * @param yx y-axis x-coordinate
     * @param yy y-axis y-coordinate
     * @param yz y-axis z-coordinate
     * @param zx z-axis x-coordinate
     * @param zy z-axis y-coordinate
     * @param zz z-axis z-coordinate
     * @return The quaternion in x, y, z, w format.
     */
    private static double[] quaternionFromMatrix(boolean normalizeAxes, double xx, double xy,
            double xz, double yx, double yy, double yz, double zx, double zy, double zz) {
        if (normalizeAxes) {
            final double lx = 1.0 / vectorLength(new double[] {xx, xy, xz});
            final double ly = 1.0 / vectorLength(new double[] {yx, yy, yz});
            final double lz = 1.0 / vectorLength(new double[] {zx, zy, zz});
            xx *= lx;
            xy *= lx;
            xz *= lx;
            yx *= ly;
            yy *= ly;
            yz *= ly;
            zx *= lz;
            zy *= lz;
            zz *= lz;
        }
        // the trace is the sum of the diagonal elements; see
        // http://mathworld.wolfram.com/MatrixTrace.html
        final double t = xx + yy + zz;
        double x, y, z, w;

        // we protect the division by s by ensuring that s>=1
        if (t >= 0) { // |w| >= .5
            double s = Math.sqrt(t + 1); // |s|>=1 ...
            w = 0.5 * s;
            s = 0.5 / s; // so this division isn't bad
            x = (zy - yz) * s;
            y = (xz - zx) * s;
            z = (yx - xy) * s;
        } else if ((xx > yy) && (xx > zz)) {
            double s = Math.sqrt(1.0 + xx - yy - zz); // |s|>=1
            x = s * 0.5; // |x| >= .5
            s = 0.5 / s;
            y = (yx + xy) * s;
            z = (xz + zx) * s;
            w = (zy - yz) * s;
        } else if (yy > zz) {
            double s = Math.sqrt(1.0 + yy - xx - zz); // |s|>=1
            y = s * 0.5; // |y| >= .5
            s = 0.5 / s;
            x = (yx + xy) * s;
            z = (zy + yz) * s;
            w = (xz - zx) * s;
        } else {
            double s = Math.sqrt(1.0 + zz - xx - yy); // |s|>=1
            z = s * 0.5; // |z| >= .5
            s = 0.5 / s;
            x = (xz + zx) * s;
            y = (zy + yz) * s;
            w = (yx - xy) * s;
        }

        return new double[] {x, y, z, w};
    }

    /**
     * Convert a matrix to a quaternion.
     * @param matrix The input matrix.
     * @return The resulting quaternion.
     */
    private static double[] quaternionFromMatrix(double[][] matrix) {
        return quaternionFromMatrix(false,
                matrix[0][0], matrix[0][1], matrix[0][2],
                matrix[1][0], matrix[1][1], matrix[1][2],
                matrix[2][0], matrix[2][1], matrix[2][2]);
    }

    /**
     * Get the Earth transform
     * @param centroid The centroid
     * @return The projected position
     */
    private static double[][] getProjectionRotationMatrix(Transform centroid) {
        // The centroid is invalid, e.g. we are inside earth or it is unspecified
        if (centroid == null || centroid.distanceTo(ORIGIN) < 1.0) {
            centroid = new Transform(0, 0, 1, 0);
        }

        // TODO: this is inaccurate at the poles or with altitude changes
        // TODO: use ECEF standard to compute normal vector
        double[] n = vectorNormalize(centroid.getPosition());

        // TODO: verify correct behavior at the poles
        double distVert = n[2];
        double[] vertVector = vectorSub(new double[]{0, 0, 1}, vectorScale(n, distVert));

        // If the vector is near the poles, then we simply set to z
        if (vectorLength(vertVector) < 0.0001) {
            return new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        }

        // Compute the vertical and horizontal components
        double[] vertComponent = vectorNormalize(vertVector);
        double[] horizComponent = vectorNormalize(vectorCrossProduct(vertComponent, n));
        return new double[][]{horizComponent, vertComponent, n};
    }

    /**
     * Multiply a matrix by a vector.
     * @param matrix A matrix.
     * @param pos A vector.
     * @return The result of (matrix * pos).
     */
    private static double[] matrixMultiply(double[][] matrix, double[] pos) {
        double[] out = new double[pos.length];
        for (int row = 0; row < matrix.length; row++) {
            out[row] = vectorDot(matrix[row], pos);
        }
        return out;
    }

    /**
     * Transpose a matrix.
     * @param matrix The matrix.
     * @return The transpose of the matrix.
     */
    private static double[][] transposeMatrix(double[][] matrix) {
        double[][] out = new double[matrix[0].length][matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            for (int k = 0; k < matrix[0].length; k++) {
                out[k][i] = matrix[i][k];
            }
        }
        return out;
    }

    /**
     * Computes the conjugate of a quaternion.
     * @param quat The quaternion.
     * @return The conjugate.
     */
    private static double[] quaternionConjugate(double[] quat) {
        return new double[]{-quat[0], -quat[1], -quat[2], quat[3]};
    }

    /**
     * Project a transform into approximate 2D space from the ECEF.
     * @param centroid The transform for the origin of the projection.
     * @return The transform result.
     */
    public Transform project(Transform centroid) {
        double[][] matrix = getProjectionRotationMatrix(centroid);

        double[] posCenter = centroid == null ? getPosition()
                : vectorSub(getPosition(), centroid.getPosition());

        double[] posTransformed = matrixMultiply(matrix, posCenter);

        double[] quat = quaternionFromMatrix(matrix);

        return new Transform(posTransformed,
                quaternionMultiply(quat, getRotation()),
                getTimestamp());
    }

    /**
     * Undo the projection of the transform into 2d space.
     * @param centroid The projection centroid.
     * @return The transform.
     */
    @SuppressWarnings("WeakerAccess")
    public Transform unproject(Transform centroid) {
        double[][] matrix = transposeMatrix(getProjectionRotationMatrix(centroid));

        double[] posTransformed = matrixMultiply(matrix, getPosition());

        double[] posCenter = centroid == null ? posTransformed
                : vectorAdd(posTransformed, centroid.getPosition());

        double[] quat = quaternionFromMatrix(matrix);

        return new Transform(posCenter, // mPosition
                quaternionMultiply(quat, getRotation()),
                getTimestamp());
    }

    /**
     * Undo the projection of the transform into 2d space.
     * @param transform The transform to unproject, could be null.
     * @param centroid The projection centroid.
     * @return The transform or null.
     */
    public static Transform unproject(Transform transform, Transform centroid) {
        if (transform == null || centroid == null) {
            return transform;
        }
        return transform.unproject(centroid);
    }
}