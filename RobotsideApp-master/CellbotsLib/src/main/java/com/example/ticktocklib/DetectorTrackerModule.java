package com.example.cellbotslib;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.media.Image;
import android.media.Image.Plane;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.os.Trace;
import android.text.TextUtils;
import android.util.Size;
import android.util.SparseArray;
import android.util.TypedValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import org.tensorflow.demo.Classifier;
import org.tensorflow.demo.env.BorderedText;
import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;
import org.tensorflow.demo.tracking.MultiBoxTracker;

import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Landmark;


@SuppressWarnings("unused")
public class DetectorTrackerModule {
    public interface DetectorTrackerModuleCallbackInterface {

        //This callback is called whenever Detector thread finish a track session.
        void onComputeFinished();
    }

    private DetectorTrackerModuleCallbackInterface onFinishedCallback = null;

    private final float IS_FACING_CAMERA_TH = 20;

    private static final Logger LOGGER = new Logger();

    private FaceDetector face_detector = null;

    List<Classifier.Recognition> latestRecognitions = new LinkedList<>();
    private final Object detections_tracking_mutex = new Object();

    // Configuration values for the prepackaged multibox model.
    private static final int MB_INPUT_SIZE = 224;
    private static final int MB_IMAGE_MEAN = 128;
    private static final float MB_IMAGE_STD = 128;
    private static final String MB_INPUT_NAME = "ResizeBilinear";
    private static final String MB_OUTPUT_LOCATIONS_NAME = "output_locations/Reshape";
    private static final String MB_OUTPUT_SCORES_NAME = "output_scores/Reshape";
    private static final String MB_MODEL_FILE = "file:///android_asset/multibox_model.pb";
    private static final String MB_LOCATION_FILE =
            "file:///android_asset/multibox_location_priors.txt";

    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final String TF_OD_API_MODEL_FILE =
            "file:///android_asset/ssd_mobilenet_v1_android_export.pb";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/coco_labels_list.txt";

    // Configuration values for tiny-yolo-voc. Note that the graph is not included with TensorFlow and
    // must be manually placed in the assets/ directory by the user.
    // Graphs and models downloaded from http://pjreddie.com/darknet/yolo/ may be converted e.g. via
    // DarkFlow (https://github.com/thtrieu/darkflow). Sample command:
    // ./flow --model cfg/tiny-yolo-voc.cfg --load bin/tiny-yolo-voc.weights --savepb --verbalise
    private static final String YOLO_MODEL_FILE = "file:///android_asset/graph-tiny-yolo-voc.pb";
    private static final int YOLO_INPUT_SIZE = 416;
    private static final String YOLO_INPUT_NAME = "input";
    private static final String YOLO_OUTPUT_NAMES = "output";
    private static final int YOLO_BLOCK_SIZE = 32;

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.  Optionally use legacy Multibox (trained using an older version of the API)
    // or YOLO.
    private enum DetectorMode {
        TF_OD_API, MULTIBOX, YOLO;
    }
    public enum DetectorTarget {
        FACE_ONLY,PERSON_ONLY,BOTH_FACE_PERSON
    }
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;

    private DetectorTarget detectorTarget = DetectorTarget.BOTH_FACE_PERSON;

    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;
    private static final float MINIMUM_CONFIDENCE_MULTIBOX = 0.1f;
    private static final float MINIMUM_CONFIDENCE_YOLO = 0.25f;

    private static final boolean MAINTAIN_ASPECT = MODE == DetectorMode.YOLO;

    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;

    private Integer sensorOrientation = 0;

    private Classifier detector;

    private int previewWidth = 0;
    private int previewHeight = 0;
    private byte[][] yuvBytes;
    private int[] rgbBytes = null;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap scaledLoadedBitmap = null;
    private AtomicBoolean computing = new AtomicBoolean(false);

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private Matrix fileToCropTransform;
    private Matrix cropToFileTransform;

    private Bitmap cropCopyBitmap;

    private MultiBoxTracker tracker;

    private byte[] luminance;

    private BorderedText borderedText;

    private long lastProcessingTimeMs;

    final private int rotation = 90;
    final int screenOrientation = 0; //PORTRAIT

    private Handler handler;
    private HandlerThread handlerThread;

    MultiBoxTracker.TrackedRecognition largest_person = null;
    private final Paint boxPaint = new Paint();

    public synchronized void setOnFinishedCallback(DetectorTrackerModuleCallbackInterface callback)
    {
        onFinishedCallback = callback;
    }

    public synchronized void setDetectorTarget(DetectorTarget target)
    {
        detectorTarget = target;
    }

    public synchronized DetectorTarget getDetectorTarget()
    {
        return detectorTarget;
    }

    public boolean init(final AssetManager assetManager,
                       final Context context,
                        final Size previewSize)
    {
        tracker = new MultiBoxTracker(context);

        int cropSize;
        if (MODE == DetectorMode.YOLO) {
            detector =
                    TensorFlowYoloDetector.create(
                            assetManager,
                            YOLO_MODEL_FILE,
                            YOLO_INPUT_SIZE,
                            YOLO_INPUT_NAME,
                            YOLO_OUTPUT_NAMES,
                            YOLO_BLOCK_SIZE);
            cropSize = YOLO_INPUT_SIZE;
        } else if (MODE == DetectorMode.MULTIBOX) {
            detector =
                    TensorFlowMultiBoxDetector.create(
                            assetManager,
                            MB_MODEL_FILE,
                            MB_LOCATION_FILE,
                            MB_IMAGE_MEAN,
                            MB_IMAGE_STD,
                            MB_INPUT_NAME,
                            MB_OUTPUT_LOCATIONS_NAME,
                            MB_OUTPUT_SCORES_NAME);
            cropSize = MB_INPUT_SIZE;
        } else {
            try {
                detector = TensorFlowObjectDetectionAPIModel.create(
                        assetManager, TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);
                cropSize = TF_OD_API_INPUT_SIZE;
            } catch (final IOException e) {
                LOGGER.e("Exception initializing classifier!", e);
                return false;
            }
        }

        previewWidth = previewSize.getWidth();
        previewHeight = previewSize.getHeight();


        LOGGER.i("Sensor orientation: %d, Screen orientation: %d", rotation, screenOrientation);

        sensorOrientation += screenOrientation;

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbBytes = new int[previewWidth * previewHeight];
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);
        //For loaded Images from sdcard
        fileToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        0, MAINTAIN_ASPECT);

        cropToFileTransform = new Matrix();
        fileToCropTransform.invert(cropToFileTransform);

        yuvBytes = new byte[3][];


        face_detector = new FaceDetector.Builder(context)
                .setLandmarkType(FaceDetector.NO_LANDMARKS)
                .setMode(FaceDetector.ACCURATE_MODE)
                .setMinFaceSize(0.1f)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setTrackingEnabled(false)
                .build();

        if (!face_detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            LOGGER.w("Face detector dependencies are not yet available.");
        }

        handlerThread = new HandlerThread("Cellbots Inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Style.STROKE);
        boxPaint.setStrokeWidth(12.0f);
        boxPaint.setStrokeCap(Paint.Cap.ROUND);
        boxPaint.setStrokeJoin(Paint.Join.ROUND);
        boxPaint.setStrokeMiter(100);
        final float textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);

        return true;
    }

    public Vector<String> getDebugInfo()
    {
        final Vector<String> lines = new Vector<>();
        lines.add("");

        lines.add("Frame: " + previewWidth + "x" + previewHeight);
        lines.add("Rotation: " + sensorOrientation);
        lines.add("Inference time: " + lastProcessingTimeMs + "ms");
        return lines;
    }

    public void drawDetections(Canvas canvas)
    {
        List<Classifier.Recognition> detections = this.getLatestDetections();
        final int frameHeight = previewHeight;
        final int frameWidth = previewWidth;
        Matrix frameToCanvasMatrix;
        if (sensorOrientation != 0) {
            float multiplier =
                    Math.min(canvas.getWidth() / (float) frameHeight, canvas.getHeight() / (float) frameWidth);
            frameToCanvasMatrix =
                    ImageUtils.getTransformationMatrix(
                            frameWidth,
                            frameHeight,
                            (int) (multiplier * frameHeight),
                            (int) (multiplier * frameWidth),
                            sensorOrientation,
                            false);
        } else {
            float multiplier =
                    Math.min(canvas.getWidth() / (float) frameWidth, canvas.getHeight() / (float) frameHeight);
            frameToCanvasMatrix =
                    ImageUtils.getTransformationMatrix(
                            frameWidth,
                            frameHeight,
                            (int) (multiplier * frameWidth),
                            (int) (multiplier * frameHeight),
                            sensorOrientation,
                            false);

        }
        for (final Classifier.Recognition recognition : detections) {
            final RectF trackedPos = recognition.getLocation();

            frameToCanvasMatrix.mapRect(trackedPos);

            final float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
            canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);

            final String labelString =
                    !TextUtils.isEmpty(recognition.getTitle())
                            ? String.format("%s %.2f", recognition.getTitle(), recognition.getConfidence())
                            : String.format("%.2f", recognition.getConfidence());
            if (borderedText!=null) {
                borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.bottom, labelString);
            }
        }
    }

    public void drawTracking(Canvas canvas, final boolean debug)
    {
        tracker.draw(canvas);
        if (debug){
            tracker.drawDebug(canvas);
        }
    }

    private synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }


    private void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }


    public boolean feedFrame(String path)
    {
        BitmapFactory.Options op = new BitmapFactory.Options();
        op.inPreferredConfig = Config.ARGB_8888;
        op.inMutable = true;
        Bitmap bitmap = BitmapFactory.decodeFile(path,op);
        return feedFrame(bitmap);
    }



    public void convert_ARGB8888_to_int(byte buf[], int[] intArr) {
        int offset = 0;
        for(int i = 0; i < intArr.length; i++) {
            intArr[i] = (buf[2 + offset] & 0xFF) | ((buf[1 + offset] & 0xFF) << 8) |
                    ((buf[offset] & 0xFF) << 16) | ((buf[3 + offset] & 0xFF) << 24);
            offset += 4;
        }
        //return intArr;
    }

    /**
     * Image is rgb format
     * return false in case of error.
     */
    @SuppressWarnings("SpellCheckingInspection")
    public boolean feedFrame(Bitmap image)
    {
        if (image == null)
        {
            LOGGER.e("%s", "Null Image input!");
            return false;
        }

        tracker.sensorOrientation = sensorOrientation;
        Bitmap scaled_image = Bitmap.createScaledBitmap(image,previewWidth,previewHeight,true);
        scaledLoadedBitmap = scaled_image.copy(scaled_image.getConfig(),true);

        LOGGER.d("scaled_image byte sizes: %d %d",scaled_image.getByteCount(),scaled_image.getAllocationByteCount());
        ++timestamp;
        final long currTimestamp = timestamp;
        Trace.beginSection("imageAvailable");

        ByteBuffer buffer2 = ByteBuffer.allocate(scaled_image.getHeight() * scaled_image.getRowBytes());
        scaled_image.copyPixelsToBuffer(buffer2);
        byte[] yuvbytes = new byte[ImageUtils.getYUVByteSize(scaled_image.getWidth(), scaled_image.getHeight())];
        convert_ARGB8888_to_int(buffer2.array(),rgbBytes);
        //System.arraycopy(buffer2.array(),0,rgbBytes,0,buffer2.remaining());
        //tmprgbbytes.get(rgbBytes);
        ImageUtils.convertARGB8888ToYUV420SP(rgbBytes.clone(),yuvbytes,scaled_image.getWidth(),scaled_image.getHeight());
        int uvsize = ((scaled_image.getWidth() + 1) / 2) * ((scaled_image.getHeight() + 1) / 2) * 2;
        if (yuvBytes[0] == null) {
            LOGGER.d("Initializing buffer %d at size %d", 0, scaled_image.getWidth()*scaled_image.getHeight());
            yuvBytes[0] = new byte[scaled_image.getWidth()*scaled_image.getHeight()];
            yuvBytes[1] = new byte[uvsize];
            yuvBytes[2] = new byte[uvsize];
        }
        System.arraycopy(yuvbytes, 0, yuvBytes[0], 0, scaled_image.getWidth()*scaled_image.getHeight());
        System.arraycopy(yuvbytes, 0, yuvBytes[1], 0, uvsize);
        System.arraycopy(yuvbytes, 0, yuvBytes[2], 0, uvsize);

        LOGGER.d("ROW BYTES COUNT: %d",scaled_image.getRowBytes());
        tracker.onFrame(
                previewWidth,
                previewHeight,
                scaled_image.getHeight(),
                sensorOrientation,
                yuvBytes[0],
                timestamp);

        LOGGER.d("feed frame called");

        if (computing.get()) {
            return false;
        }
        computing.set(true);

        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);




        final Canvas canvas = new Canvas(croppedBitmap);


        canvas.drawBitmap(rgbFrameBitmap,sensorOrientation == 0 ? fileToCropTransform : frameToCropTransform,null);

        detect_track(currTimestamp,canvas,sensorOrientation == 0 ? cropToFileTransform : cropToFrameTransform);

        Trace.endSection();
        LOGGER.d("feed frame ended");

        return true;
    }



    /**
     * image is yuv420 android camera image
     * return false in case of error.
     */
    public boolean feedFrame(Image image)
    {
        ++timestamp;
        final long currTimestamp = timestamp;
        tracker.sensorOrientation = sensorOrientation;
        try {
            if (image == null) {
                return false;
            }

            Trace.beginSection("imageAvailable");

            final Plane[] planes = image.getPlanes();
            this.fillBytes(planes, yuvBytes);
            tracker.onFrame(
                    previewWidth,
                    previewHeight,
                    planes[0].getRowStride(),
                    sensorOrientation,
                    yuvBytes[0],
                    timestamp);

            // No mutex needed as this method is not reentrant.
            if (computing.get()) {
                image.close();
                return false;
            }
            computing.set(true);

            final int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();
            LOGGER.d("yRowStride = %d",yRowStride);
            LOGGER.d("uvRowStride = %d",uvRowStride);
            LOGGER.d("uvPixelStride = %d",uvPixelStride);
            LOGGER.d("Image: ",image.getWidth(),image.getHeight());
            ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes[0],
                    yuvBytes[1],
                    yuvBytes[2],
                    previewWidth,
                    previewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    rgbBytes);

            image.close();
        } catch (final Exception e) {
            image.close();
            LOGGER.e(e, "Exception!");
            Trace.endSection();
            return false;
        }

        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);




        final Canvas canvas = new Canvas(croppedBitmap);


        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);


        detect_track(currTimestamp,canvas,cropToFrameTransform);

        Trace.endSection();
        return true;
    }

    public synchronized void setSensorOrientation(final int o) {
        sensorOrientation = o;
    }

    private void detect_track(final long currTimestamp,final Canvas canvas,final Matrix fixDetectionsMatrix)
    {





        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        if (luminance == null) {
            luminance = new byte[yuvBytes[0].length];
        }
        System.arraycopy(yuvBytes[0], 0, luminance, 0, luminance.length);

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        final long startTime = SystemClock.uptimeMillis();
                        List<Classifier.Recognition> results = new LinkedList<>();
                        final DetectorTarget detection_target = getDetectorTarget();

                        if (detection_target == DetectorTarget.PERSON_ONLY ||
                                detection_target == DetectorTarget.BOTH_FACE_PERSON) {
                            results = detector.recognizeImage(croppedBitmap);
                        }
                        Frame frame = new Frame.Builder().setBitmap(croppedBitmap).build();

                        SparseArray<Face> faces = new SparseArray<>();
                        if (detection_target == DetectorTarget.FACE_ONLY ||
                                detection_target == DetectorTarget.BOTH_FACE_PERSON) {
                            faces = face_detector.detect(frame);
                        }
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        Paint mFacePositionPaint = new Paint();
                        mFacePositionPaint.setColor(Color.YELLOW);
                        mFacePositionPaint.setStyle(Style.STROKE);
                        mFacePositionPaint.setStrokeWidth(2.0f);
                        ArrayList<Classifier.Recognition> face_recognitions = new ArrayList<>();

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);


                        for (int i = 0; i < faces.size(); ++i) {
                            Face face = faces.valueAt(i);
                            float left = face.getPosition().x;
                            float top = face.getPosition().y;
                            float right = (face.getPosition().x + face.getWidth());
                            float bottom = (face.getPosition().y + face.getHeight());
                            RectF rect = new RectF(left,top,right,bottom);
                            final float face_y_rotation = face.getEulerY();

                            final boolean facing_camera = face_y_rotation > -IS_FACING_CAMERA_TH && face_y_rotation < IS_FACING_CAMERA_TH;
                            LOGGER.d("face %d is eulerY = %f",face.getId(),face_y_rotation);
                            Classifier.FaceRecognition face_recognition = new Classifier.FaceRecognition(
                                    String.valueOf(i+results.size()),"face",1.f,rect,facing_camera);
                            face_recognition.setY_rotation(face_y_rotation);
                            face_recognitions.add(face_recognition);

                            //float x = (face.getPosition().x + face.getWidth() / 2);
                            //float y = (face.getPosition().y + face.getHeight() / 2);
                            //canvas.drawCircle(x, y, 5, mFacePositionPaint);


                            for (Landmark landmark : face.getLandmarks()) {
                                int cx = (int) (landmark.getPosition().x * 1);
                                int cy = (int) (landmark.getPosition().y * 1);
                                canvas.drawCircle(cx, cy, 5, mFacePositionPaint);
                            }
                        }

                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE) {
                            case TF_OD_API: minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API; break;
                            case MULTIBOX: minimumConfidence = MINIMUM_CONFIDENCE_MULTIBOX; break;
                            case YOLO: minimumConfidence = MINIMUM_CONFIDENCE_YOLO; break;
                        }

                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<>();

                        for (final Classifier.Recognition result : results) {
                            if (!result.getTitle().startsWith("person"))
                            {
                                continue;
                            }
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                canvas.drawRect(location, paint);

                                fixDetectionsMatrix.mapRect(location);
                                result.setLocation(location);
                                mappedRecognitions.add(result);
                            }
                        }
                        for (final Classifier.Recognition result : face_recognitions) {
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                canvas.drawRect(location, mFacePositionPaint);

                                fixDetectionsMatrix.mapRect(location);
                                result.setLocation(location);
                                mappedRecognitions.add(result);
                            }
                        }
                        synchronized (detections_tracking_mutex) {
                            latestRecognitions = new ArrayList<>(mappedRecognitions);
                            tracker.trackResults(mappedRecognitions, luminance, currTimestamp);
                        }
                        if (onFinishedCallback!=null)
                        {
                            onFinishedCallback.onComputeFinished();
                        }
                        LOGGER.d("Compute ended.");
                        computing.set(false);
                    }
                });
    }

    public MultiBoxTracker.TrackedRecognition getLargestPerson()
    {
        final DetectorTarget target_detection = this.getDetectorTarget();
        if (target_detection==DetectorTarget.PERSON_ONLY ||
                target_detection==DetectorTarget.BOTH_FACE_PERSON) {
            LinkedList<MultiBoxTracker.TrackedRecognition> trackedRecognitions =
                    getCurrentTrackedRecognitions();
            LOGGER.d("getLargestPerson() called %d", trackedRecognitions.size());

            if (largest_person == null) {
                float max_area = 0;
                for (final MultiBoxTracker.TrackedRecognition t :
                        trackedRecognitions) {
                    if (t == null || t.trackedObject == null ||
                            t.trackedObject.getTrackedPositionInPreviewFrame() == null) {
                        continue;
                    }
                    final RectF t_loc = t.trackedObject.getTrackedPositionInPreviewFrame();
                    float t_area = t_loc.height() *
                            t_loc.width();
                    if (t_area > max_area) {
                        largest_person = t;
                        LOGGER.d("Largest person found = %s", t_loc);
                        max_area = t_area;
                    }
                }
            } else {
                boolean biggest_person_still_tracked = false;
                for (final MultiBoxTracker.TrackedRecognition t :
                        trackedRecognitions) {

                    if (largest_person.color == t.color) {
                        largest_person = t;
                        LOGGER.d("Largest person still exist = %d", t.color);

                        biggest_person_still_tracked = true;
                        break;
                    }
                }
                if (!biggest_person_still_tracked) {
                    largest_person = null;
                }
            }
        } else {
            LOGGER.e("Attempted to find largest person on non-valid detection target");
            return null;
        }
        LOGGER.d("getLargestPerson() ended");
        return largest_person;
    }

    public List<Classifier.Recognition> getLatestDetections()
    {
        LinkedList<Classifier.Recognition> copyList;
        synchronized (detections_tracking_mutex) {
            copyList = new LinkedList<>(latestRecognitions);
        }
        return copyList;

    }

    public LinkedList<MultiBoxTracker.TrackedRecognition> getCurrentTrackedRecognitions()
    {
        LinkedList<MultiBoxTracker.TrackedRecognition> copyList;
        synchronized (detections_tracking_mutex) {
            copyList = new LinkedList<>(tracker.getTrackedObjects());
        }
        return copyList;
    }


    public void closeLib()
    {
        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            LOGGER.e(e, "Exception!");
        }
        face_detector.release();
    }

    public Bitmap getDebugCroppedBitmap()
    {
        return cropCopyBitmap;
    }

    public void enableStatLogging(final boolean debug)
    {
        if (detector!=null){
            detector.enableStatLogging(debug);
        }
    }

    public Bitmap getPreviewBitmap()
    {
        //final Canvas canvas = new Canvas(scaledLoadedBitmap);
        //canvas.drawBitmap(scaledLoadedBitmap,sensorOrientation == 0 ? fileToCropTransform : frameToCropTransform,null);
        return scaledLoadedBitmap;
    }
}
