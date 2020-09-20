package ai.cellbots.common.data;

import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;

/**
 * SmootherParams data class. Describes parameters for path smoother.
 */
@IgnoreExtraProperties
public class SmootherParams {
    @PropertyName("deviation")
    private final double mDeviation;
    @PropertyName("smoothness")
    private final double mSmoothness;

    public SmootherParams(double deviation, double smoothness) {
        mDeviation = deviation;
        mSmoothness = smoothness;
    }

    public SmootherParams() {
        mDeviation = 0.0;
        mSmoothness = 0.0;
    }

    @PropertyName("deviation")
    public double getDeviation() {
        return mDeviation;
    }

    @PropertyName("smoothness")
    public double getSmoothness() {
        return mSmoothness;
    }

    @Override
    public String toString() {
        return "Smoother params(" + Double.toString(mDeviation) + ", "
                + Double.toString(mSmoothness) + ")";
    }
}
