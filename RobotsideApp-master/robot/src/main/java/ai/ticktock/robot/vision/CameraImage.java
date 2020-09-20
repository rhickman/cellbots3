package ai.cellbots.robot.vision;

import android.support.annotation.NonNull;

import java.util.Objects;

/**
 * An image from a camera.
 */
public class CameraImage {
    /**
     * The image format.
     */
    public enum Format {
        Y_CR_CB_420
    }

    private final CameraInfo mCameraInfo;
    private final Format mFormat;
    private final double mTimestamp;
    private final int mWidth;
    private final int mHeight;
    private final byte[] mBytes;

    /**
     * Computes the length of an image for a given format.
     *
     * @param format The format of the image.
     * @param width  The width.
     * @param height The height.
     * @return The length of the image.
     */
    public static int calculateImageByteLength(Format format, int width, int height) {
        if (width <= 0) {
            throw new IllegalArgumentException("The width cannot be less than or equal to zero: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("The height cannot be less than or equal to zero: " + height);
        }
        if (format == Format.Y_CR_CB_420) {
            return (3 * width * height) / 2;
        } else {
            throw new IllegalArgumentException("The format of the image is invalid: " + format);
        }
    }

    /**
     * Creates the image.
     *
     * @param cameraInfo The camera info.
     * @param format     The format.
     * @param timestamp  The timestamp in seconds.
     * @param width      The width.
     * @param height     The height.
     * @param bytes      The image data. Must not be changed or the image will be altered.
     */
    public CameraImage(@NonNull CameraInfo cameraInfo, @NonNull Format format,
                       double timestamp, int width, int height, @NonNull byte[] bytes) {
        Objects.requireNonNull(cameraInfo);
        Objects.requireNonNull(bytes);
        Objects.requireNonNull(format);
        if (width <= 0) {
            throw new IllegalArgumentException("The width cannot be less than or equal to zero: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("The height cannot be less than or equal to zero: " + height);
        }
        mCameraInfo = cameraInfo;
        mFormat = format;
        mTimestamp = timestamp;
        mWidth = width;
        mHeight = height;
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        mBytes = bytes;
        if (calculateImageByteLength(format, width, height) != bytes.length) {
            throw new IllegalArgumentException("Byte length is wrong. Had: " + bytes.length
                    + " need " + calculateImageByteLength(format, width, height));
        }
    }

    /**
     * Creates a copy of an image.
     *
     * @param copy  The image to copy.
     * @param bytes The byte buffer. If non-null and is correct length will be used as image bytes.
     */
    public CameraImage(@NonNull CameraImage copy, byte[] bytes) {
        mCameraInfo = copy.getCameraInfo();
        mFormat = copy.getFormat();
        mTimestamp = copy.getTimestamp();
        mWidth = copy.getWidth();
        mHeight = copy.getHeight();
        int length = calculateImageByteLength(mFormat, mWidth, mHeight);
        if (bytes != null && bytes.length == length) {
            //noinspection AssignmentToCollectionOrArrayFieldFromParameter
            mBytes = bytes;
        } else {
            mBytes = new byte[length];
        }
        System.arraycopy(copy.getBytes(), 0, mBytes, 0, length);
    }

    /**
     * Creates a copy of an image.
     *
     * @param copy The image to copy.
     */
    public CameraImage(@NonNull CameraImage copy) {
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
     * Gets the format of this image.
     *
     * @return The format.
     */
    public Format getFormat() {
        return mFormat;
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
     * Gets the bytes of this image.
     *
     * @return The bytes data. If changed, the image will be altered.
     */
    public byte[] getBytes() {
        //noinspection ReturnOfCollectionOrArrayField
        return mBytes;
    }
}
