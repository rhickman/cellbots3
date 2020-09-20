package ai.cellbots.companion;

import android.support.v4.view.MotionEventCompat;
import android.view.MotionEvent;
import android.view.View;

/**
 * Rotation gesture detector from:
 * https://stackoverflow.com/questions/10682019/android-two-finger-rotation/18276033#18276033
 */

public class RotationGestureDetector {
    private static final String TAG = FloorplanView.class.getSimpleName();
    private static final int INVALID_POINTER_ID = -1;

    // Pointer 1 tracks the first finger.
    private int mPointer1Id = INVALID_POINTER_ID;
    private float mPointer1X;
    private float mPointer1Y;

    // Pointer 2 tracks the second finger.
    private int mPointer2Id = INVALID_POINTER_ID;
    private float mPointer2X;
    private float mPointer2Y;

    private boolean mIsFirstMovement;
    private View mView;

    private OnRotationGestureListener mListener;

    /**
     * Class constrictor
     *
     * @param listener externally defined listener that implements onRotation method
     * @param v        view
     */
    public RotationGestureDetector(OnRotationGestureListener listener, View v) {
        mListener = listener;
        mView = v;
    }

    /**
     * Touch event handler
     *
     * @param event touch event
     * @return always returns true
     */
    public boolean onTouchEvent(MotionEvent event) {
        switch (MotionEventCompat.getActionMasked(event)) {
            case MotionEvent.ACTION_DOWN: {
                final int pointer1Index = MotionEventCompat.getActionIndex(event);
                mPointer1Id = MotionEventCompat.getPointerId(event, 0);
                mPointer1X = MotionEventCompat.getX(event, pointer1Index);
                mPointer1Y = MotionEventCompat.getY(event, pointer1Index);
                mIsFirstMovement = true;
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int pointer2Index = MotionEventCompat.getActionIndex(event);
                mPointer2Id = MotionEventCompat.getPointerId(event, pointer2Index);
                mPointer2X = MotionEventCompat.getX(event, pointer2Index);
                mPointer2Y = MotionEventCompat.getY(event, pointer2Index);
                mIsFirstMovement = true;
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mPointer1Id != INVALID_POINTER_ID && mPointer2Id != INVALID_POINTER_ID) {
                    float angleIncrement;
                    final int pointer1Index =
                            MotionEventCompat.findPointerIndex(event, mPointer1Id);
                    final int pointer2Index =
                            MotionEventCompat.findPointerIndex(event, mPointer2Id);
                    float newPointer1X = MotionEventCompat.getX(event, pointer1Index);
                    float newPointer1Y = MotionEventCompat.getY(event, pointer1Index);
                    float newPointer2X = MotionEventCompat.getX(event, pointer2Index);
                    float newPointer2Y = MotionEventCompat.getY(event, pointer2Index);
                    if (mIsFirstMovement) {
                        angleIncrement = 0;
                        mIsFirstMovement = false;
                    } else {
                        angleIncrement = angleBetweenLineSegments(mPointer1X, mPointer1Y,
                                mPointer2X, mPointer2Y,
                                newPointer1X, newPointer1Y,
                                newPointer2X, newPointer2Y);
                    }
                    mPointer1X = newPointer1X;
                    mPointer1Y = newPointer1Y;
                    mPointer2X = newPointer2X;
                    mPointer2Y = newPointer2Y;

                    if (mListener != null) {
                        mListener.onRotation(angleIncrement);
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                mPointer1Id = INVALID_POINTER_ID;
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                mPointer2Id = INVALID_POINTER_ID;
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                mPointer1Id = INVALID_POINTER_ID;
                mPointer2Id = INVALID_POINTER_ID;
                break;
            }
        }
        return true;
    }

    /**
     * Get angles between the first point detected and the last one
     *
     * @return returns the angle change between first finger and the second one
     */
    private float angleBetweenLineSegments(float start1X, float start1Y, float end1X, float end1Y,
            float start2X, float start2Y, float end2X, float end2Y) {
        double slopeSegment1 = Math.atan2((start1Y - end1Y), (start1X - end1X));
        double slopeSegment2 = Math.atan2((start2Y - end2Y), (start2X - end2X));

        float delta  = (float)((Math.toDegrees(slopeSegment2) - Math.toDegrees(slopeSegment1)) % 360);
        return (delta > 180) ? delta : delta - 360;
    }

    public interface OnRotationGestureListener {
        void onRotation(float angleIncrement);
    }
}