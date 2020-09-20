package ai.cellbots.common.webrtc;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.support.annotation.NonNull;
import android.util.Log;

import org.webrtc.VideoRenderer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * A renderer for a WebRTCView, rendering YUV textures extracted from the frames from the WebRTC
 * call into an OpenGL window. Uses shaders to do all processing on the GPU for greater speed.
 */
public class WebRTCRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = WebRTCRenderer.class.getSimpleName();
    private int mWidth = 0; // Width of the video call
    private int mHeight = 0; // Height of the video call
    private int mProgram;
    private final int[] mYuvTextures = new int[3];
    private String mCallUuid = null;
    private String mNewCallUuid = null;
    private Frame mFrame; // Current frame.
    private Frame mQueueFrame; // Next frame to display.
    private final ArrayList<Frame> mStoreFrames = new ArrayList<>();
    private final Listener mListener;

    @SuppressWarnings("SpellCheckingInspection")
    private static final String VERTEX_SHADER_STRING =
            "varying vec2 interp_tc;\n" +
                    "attribute vec4 in_pos;\n" +
                    "attribute vec2 in_tc;\n" +
                    "\n" +
                    "void main() {\n" +
                    "  gl_Position = in_pos;\n" +
                    "  interp_tc = in_tc;\n" +
                    "}\n";

    @SuppressWarnings("SpellCheckingInspection")
    private static final String FRAGMENT_SHADER_STRING =
            "precision mediump float;\n" +
                    "varying vec2 interp_tc;\n" +
                    "\n" +
                    "uniform sampler2D y_tex;\n" +
                    "uniform sampler2D u_tex;\n" +
                    "uniform sampler2D v_tex;\n" +
                    "\n" +
                    "void main() {\n" +
                    // CSC according to http://www.fourcc.org/fccyvrgb.php
                    "  float y = texture2D(y_tex, interp_tc).r;\n" +
                    "  float u = texture2D(u_tex, interp_tc).r - 0.5;\n" +
                    "  float v = texture2D(v_tex, interp_tc).r - 0.5;\n" +
                    "  gl_FragColor = vec4(y + 1.403 * v, " +
                    "                      y - 0.344 * u - 0.714 * v, " +
                    "                      y + 1.77 * u, 1);\n" +
                    "}\n";

    /**
     * Create the renderer
     * @param listener The listener
     */
    public WebRTCRenderer(Listener listener) {
        mListener = listener;
    }

    /**
     * Wrap a float[] in a direct FloatBuffer using native byte order.
     * @param array The input float[] array
     * @return The output FloatBuffer.
     */
    private static FloatBuffer directNativeFloatBuffer(float[] array) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(array.length * 4).order(
                ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(array);
        buffer.flip();
        return buffer;
    }

    // A set of texture coordinates for the view
    private static final FloatBuffer mTextureCoords = directNativeFloatBuffer(
            new float[] {
                    0, 0, 0, 1, 1, 0, 1, 1
            });


    /**
     * Class that implements a VideoRenderer for WebRTC.
     */
    private static final class Callbacks implements VideoRenderer.Callbacks {
        private final String mCallUuid;
        private final WebRTCRenderer mRenderer;

        private Callbacks(WebRTCRenderer render, String callUuid) {
            mCallUuid = callUuid;
            mRenderer = render;
        }

        @Override
        public void renderFrame(VideoRenderer.I420Frame i420Frame) {
            mRenderer.renderFrame(i420Frame, mCallUuid);
        }
    }

    /**
     * Get a video renderer for the WebRTC track.
     * @param callUuid The call uuid.
     * @return The callbacks for the video renderer.
     */
    public VideoRenderer.Callbacks getVideoRenderer(String callUuid) {
        return new Callbacks(this, callUuid);
    }

    /**
     * A listener for the WebRTCRenderer.
     */
    public interface Listener {
        /**
         * Called when a frame must be rendered.
         */
        void onFrame();

        /**
         * Called when the width and height of the view are updated.
         * @param width Pixel width.
         * @param height Pixel height.
         */
        @SuppressWarnings("unused")
        void updateSize(int width, int height);
    }


    /**
     * Set the current call uuid.
     * @param callUuid The call uuid.
     */
    public void setCallUuid(String callUuid) {
        mNewCallUuid = callUuid;
    }

    /**
     * A local frame stored in the system.
     */
    private final class Frame {
        private final int mWidth; // Width of the frame
        private final int mHeight; // Height of the frame
        private final ByteBuffer[] mPlanes = new ByteBuffer[3]; // Data buffers
        private final String mCallUuid;

        /**
         * Create a frame.
         * @param width Frame width.
         * @param height Frame height.
         * @param callUuid The call uuid.
         */
        private Frame(int width, int height, @NonNull String callUuid) {
            mWidth = width;
            mHeight = height;
            mCallUuid = callUuid;
            mPlanes[0] = ByteBuffer.allocate(width * height);
            mPlanes[1] = ByteBuffer.allocate(width / 2 * height / 2);
            mPlanes[2] = ByteBuffer.allocate(width / 2 * height / 2);
        }

        /**
         * Load a frame into the system.
         * @param i420Frame The frame to load from.
         */
        private void loadFrame(VideoRenderer.I420Frame i420Frame) {
            for (int i = 0; i < 3; i++) {
                mPlanes[i].rewind();
                mPlanes[i].put(i420Frame.yuvPlanes[i]);
                mPlanes[i].rewind();
            }
        }

        /**
         * Get the byte buffer on the given plane.
         * @param i Plane index.
         * @return The plane byte buffer.
         */
        private ByteBuffer getPlane(int i) {
            return mPlanes[i];
        }

        /**
         * Get the width.
         * @return The pixel width.
         */
        private int getWidth() {
            return mWidth;
        }

        /**
         * Get the height.
         * @return The pixel height.
         */
        private int getHeight() {
            return mHeight;
        }

        /**
         * Get the call uuid.
         * @return The call uuid.
         */
        private String getCallUuid() {
            return mCallUuid;
        }
    }

    /**
     * Render a frame from the video.
     * @param i420Frame The frame to render.
     * @param callUuid The callUuid of the frame.
     */
    private void renderFrame(VideoRenderer.I420Frame i420Frame, @NonNull String callUuid) {
        if (!i420Frame.yuvFrame) {
            Log.w(TAG, "Reject frame for not being YUV");
            VideoRenderer.renderFrameDone(i420Frame);
            return;
        }
        if (i420Frame.yuvPlanes.length != 3) {
            Log.w(TAG, "Frame does not contain 3 planes: " + i420Frame.yuvPlanes.length);
            VideoRenderer.renderFrameDone(i420Frame);
            return;
        }
        if (i420Frame.yuvStrides.length != 3) {
            Log.w(TAG, "Frame does not contain 3 strides: " + i420Frame.yuvStrides.length);
            VideoRenderer.renderFrameDone(i420Frame);
            return;
        }

        Frame writeFrame = null;
        synchronized (this) {
            mCallUuid = mNewCallUuid;
            if (!callUuid.equals(mCallUuid)) {
                VideoRenderer.renderFrameDone(i420Frame);
                return;
            }
            while (writeFrame == null
                    || writeFrame.getWidth() != i420Frame.width
                    || writeFrame.getHeight() != i420Frame.height
                    || !writeFrame.getCallUuid().equals(callUuid)) {
                if (!mStoreFrames.isEmpty()) {
                    writeFrame = mStoreFrames.get(0);
                    mStoreFrames.remove(0);
                } else {
                    Log.v(TAG, "Create new frame storage object");
                    writeFrame = new Frame(i420Frame.width, i420Frame.height, mCallUuid);
                }
            }
        }

        writeFrame.loadFrame(i420Frame);
        VideoRenderer.renderFrameDone(i420Frame);
        synchronized (this) {
            mCallUuid = mNewCallUuid;
            if (!callUuid.equals(mCallUuid)) {
                return;
            }
            mQueueFrame = writeFrame;
        }
        mListener.onFrame();
    }

    /**
     * Create the shader program.
     * @param program The program number.
     */
    private void create(int program) {
        Log.v(TAG, "  YuvImageRenderer.createTextures");
        mProgram = program;

        // Generate 3 texture ids for Y/U/V and place them into |textures|.
        GLES20.glGenTextures(3, mYuvTextures, 0);
        for (int i = 0; i < 3; i++)  {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mYuvTextures[i]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
                    128, 128, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        }
        checkNoGLES2Error();
    }

    /**
     * Draw a frame.
     * @param width The width of the window.
     * @param height The height of the window.
     */
    private void draw(int width, int height) {
        Frame frame;
        synchronized (this) {
            mCallUuid = mNewCallUuid;
            if (mQueueFrame != null) {
                if (mFrame != null) {
                    mStoreFrames.add(mFrame);
                }
                mFrame = mQueueFrame;
                mQueueFrame = null;
            }
            frame = mFrame;
            if (frame != null && !frame.getCallUuid().equals(mCallUuid)) {
                frame = null;
            }
        }
        int program = mProgram;
        if (frame == null) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            return;
        }
        int[] textures = {GLES20.GL_TEXTURE0, GLES20.GL_TEXTURE1, GLES20.GL_TEXTURE2};
        for (int i = 0; i < 3; ++i) {
            int w = (i == 0) ? frame.getWidth() : frame.getWidth() / 2;
            int h = (i == 0) ? frame.getHeight() : frame.getHeight() / 2;
            GLES20.glActiveTexture(textures[i]);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mYuvTextures[i]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
                    w, h, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE,
                    frame.getPlane(i));
        }

        // Compute the scale to either width or height
        float scale = width / ((float)frame.getWidth());
        if (frame.getHeight() * scale > height) {
            scale = height / ((float)frame.getHeight());
        }

        float xLeft = -1 * (scale * (float)frame.getWidth() / width); //(x - 50) / 50.0f; //-1
        float yTop = 1 * (scale * (float)frame.getHeight() / height); // (50 - y) / 50.0f; //1
        float xRight = 1 * (scale * (float)frame.getWidth() / width); // Math.min(1.0f, (x + width - 50) / 50.0f);
        float yBottom = -1 * (scale * (float)frame.getHeight() / height); // Math.max(-1.0f, (50 - y - height) / 50.0f);
        float[] textureVerticesFloat = {
                xLeft, yTop,
                xLeft, yBottom,
                xRight, yTop,
                xRight, yBottom
        };
        FloatBuffer textureVertices = directNativeFloatBuffer(textureVerticesFloat);

        int posLocation = GLES20.glGetAttribLocation(program, "in_pos");
        GLES20.glEnableVertexAttribArray(posLocation);
        GLES20.glVertexAttribPointer(
                posLocation, 2, GLES20.GL_FLOAT, false, 0, textureVertices);
        int texLocation = GLES20.glGetAttribLocation(program, "in_tc");
        GLES20.glEnableVertexAttribArray(texLocation);
        GLES20.glVertexAttribPointer(
                texLocation, 2, GLES20.GL_FLOAT, false, 0, mTextureCoords);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(posLocation);
        GLES20.glDisableVertexAttribArray(texLocation);
        checkNoGLES2Error();
    }

    /**
     * Assert that no OpenGL ES 2.0 error has been raised.
     */
    private static void checkNoGLES2Error() {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            throw new Error("GLES20 error: " + error);
        }
    }

    /**
     * Compile & attach a |type| shader specified by |source| to |program|.
     * @param type The type of OpenGL shader.
     * @param source The source code for the shader.
     * @param program The program for the shader.
     */
    private static void addShaderTo(int type, String source, int program) {
        int[] result = {GLES20.GL_FALSE};
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result, 0);
        if (result[0] != GLES20.GL_TRUE) {
            throw new Error(GLES20.glGetShaderInfoLog(shader) + ", source: " + source);
        }
        GLES20.glAttachShader(program, shader);
        GLES20.glDeleteShader(shader);

        checkNoGLES2Error();
    }

    /**
     * On the creation of the OpenGL surface, configure it.
     * @param gl10 The OpenGL object.
     * @param eglConfig The OpenGL configuration.
     */
    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        // Create program.
        int program = GLES20.glCreateProgram();
        addShaderTo(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_STRING, program);
        addShaderTo(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_STRING, program);

        GLES20.glLinkProgram(program);
        int[] result = {GLES20.GL_FALSE};
        result[0] = GLES20.GL_FALSE;
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, result, 0);
        if (result[0] != GLES20.GL_TRUE) {
            throw new Error(GLES20.glGetProgramInfoLog(program));
        }
        GLES20.glUseProgram(program);

        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "y_tex"), 0);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "u_tex"), 1);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "v_tex"), 2);

        create(program);

        checkNoGLES2Error();
        GLES20.glClearColor(0.0f, 0.0f, 0.3f, 1.0f);
    }

    /**
     * Called when the size of the OpenGL surface is changed.
     * @param gl10 The OpenGL object.
     * @param width The width of the surface.
     * @param height The height of the surface.
     */
    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        mWidth = width;
        mHeight = height;
        Log.i(TAG, "Surface changed: " + width + " " + height);
        GLES20.glViewport(0, 0, width, height);
    }

    /**
     * Called when an OpenGL render is requested.
     * @param gl10 The OpenGL object.
     */
    @Override
    public void onDrawFrame(GL10 gl10) {
        draw(mWidth, mHeight);
    }

}
