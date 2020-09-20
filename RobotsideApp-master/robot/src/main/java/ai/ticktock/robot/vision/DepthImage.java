package ai.cellbots.robot.vision;

import android.support.annotation.NonNull;

import java.util.Objects;

/**
 * Depth image.
 */
public class DepthImage {
    private final CameraInfo mCameraInfo;
    private final double mTimestamp;
    private final int mWidth;
    private final int mHeight;
    private final float[] mFloats;

    /**
     * Computes the length of an image for a given format.
     *
     * @param width  The width.
     * @param height The height.
     * @return The length of the image.
     */
    public static int calculateImageFloatLength(int width, int height) {
        if (width <= 0) {
            throw new IllegalArgumentException("The width cannot be less than or equal to zero: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("The height cannot be less than or equal to zero: " + height);
        }
        return width * height;
    }

    /**
     * Creates the image.
     *
     * @param cameraInfo The camera info.
     * @param timestamp  The timestamp in seconds.
     * @param width      The width.
     * @param height     The height.
     * @param floats     The image data. Must not be changed or the image will be altered.
     */
    public DepthImage(@NonNull CameraInfo cameraInfo, double timestamp,
                      int width, int height, @NonNull float[] floats) {
        Objects.requireNonNull(cameraInfo);
        Objects.requireNonNull(floats);
        if (width <= 0) {
            throw new IllegalArgumentException("The width cannot be less than or equal to zero: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("The height cannot be less than or equal to zero: " + height);
        }
        mCameraInfo = cameraInfo;
        mTimestamp = timestamp;
        mWidth = width;
        mHeight = height;
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        mFloats = floats;
        if (calculateImageFloatLength(width, height) != floats.length) {
            throw new IllegalArgumentException("Float length is wrong. Had: " + floats.length
                    + " need " + calculateImageFloatLength(width, height));
        }
    }

    /**
     * Creates a copy of an image.
     *
     * @param copy   The image to copy.
     * @param floats The floats buffer. If non-null and is correct length will be used as image floats.
     */
    public DepthImage(@NonNull DepthImage copy, float[] floats) {
        mCameraInfo = copy.getCameraInfo();
        mTimestamp = copy.getTimestamp();
        mWidth = copy.getWidth();
        mHeight = copy.getHeight();
        int length = calculateImageFloatLength(mWidth, mHeight);
        if (floats != null && floats.length == length) {
            //noinspection AssignmentToCollectionOrArrayFieldFromParameter
            mFloats = floats;
        } else {
            mFloats = new float[length];
        }
        System.arraycopy(copy.getFloats(), 0, mFloats, 0, length);
    }

    /**
     * Creates a copy of an image.
     *
     * @param copy The image to copy.
     */
    public DepthImage(@NonNull DepthImage copy) {
        this(copy, null);
    }

    /**
     * Gets the timestamp of this image (in seconds).
     *
     * @return The timestamp in seconds.
     */
    public double getTimestamp() {
        return mTimestamp;
    }

    /**
     * Gets the CameraInfo of this image.
     *
     * @return The CameraInfo.
     */
    public CameraInfo getCameraInfo() {
        return mCameraInfo;
    }

    /**
     * Gets the width of this image.
     *
     * @return The width.
     */
    public int getWidth() {
        return mWidth;
    }

    /**
     * Gets the height of this image.
     *
     * @return The height.
     */
    public int getHeight() {
        return mHeight;
    }

    /**
     * Gets the floats of this image.
     *
     * @return The floats data. If changed, the image will be altered.
     */
    public float[] getFloats() {
        //noinspection ReturnOfCollectionOrArrayField
        return mFloats;
    }

    /**
     * Determines if a pixel is in the image.
     *
     * @param x The pixel x.
     * @param y The pixel y.
     * @return True if the pixel is valid.
     */
    public boolean isValidPixel(int x, int y) {
        return x >= 0 && y >= 0 && x < mWidth && y < mHeight;
    }

    /**
     * Gets the index of a pixel.
     *
     * @param x The pixel x.
     * @param y The pixel y.
     * @return The index of the pixel in the array.
     */
    public int getPixelIndex(int x, int y) {
        return (y * getWidth()) + x;
    }

    /**
     * Sets a pixel index.
     *
     * @param x     The pixel x.
     * @param y     The pixel y.
     * @param value The value for the pixel.
     */
    @SuppressWarnings("unused")
    public void setPixel(int x, int y, float value) {
        if (isValidPixel(x, y)) {
            mFloats[getPixelIndex(x, y)] = value;
        }
    }
}
