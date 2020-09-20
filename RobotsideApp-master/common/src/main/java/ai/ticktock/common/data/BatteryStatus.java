package ai.cellbots.common.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.PropertyName;

/**
 * Stores the state of a robot's battery. There are three main categories of information stored
 * in a given battery status:
 * * percentage: the absolute percentage of the battery (mandatory)
 * * charging: a boolean flag, true if charging, false otherwise (mandatory)
 * * voltage: the voltage being drawn from the battery (optional, haveVoltage = false if unused)
 * * current: the current being drawn from the battery (optional, haveCurrent = false if unused)
 *
 * Only percentage and charging should be used for logic, because some battery systems do not
 * report voltage and current information.
 *
 * There is also additional static information:
 * * Low percentage: when the battery percentage drops below this value, the robot should interrupt
 *                background tasks and start recharging.
 * * Critical percentage: when the battery percentage drops below this value, the robot should
 *                interrupt all tasks and start recharging.
 * * Max and min voltages: the expected maximum and minimum voltages of the battery. Used only as
 *                a display hint and must not be used for any logic.
 * * Low voltage: the voltage at which the battery may be considered low. Used only as a display
 *                hint and must not be used for any logic.
 */
@SuppressWarnings("unused")
public class BatteryStatus {
    @PropertyName("name")
    private final String mName; // The name of the battery.
    @PropertyName("minVoltage")
    private final double mMinVoltage; // The minimum voltage of the battery, used only for display.
    @PropertyName("lowVoltage")
    private final double mLowVoltage; // The low voltage, used only for display.
    @PropertyName("maxVoltage")
    private final double mMaxVoltage; // The maximum voltage, used only for display.
    @PropertyName("voltage")
    private final double mVoltage; // The voltage of the battery at present, in voltages.
    @PropertyName("criticalPercentage")
    private final double mCriticalPercentage; // The percentage below which the battery is critical.
    @PropertyName("lowPercentage")
    private final double mLowPercentage; // The percentage below which the battery is low.
    @PropertyName("percentage")
    private final double mPercentage; // The percentage of the battery at present.
    @PropertyName("current")
    private final double mCurrent; // The current drawn from the battery, in amps.
    @PropertyName("haveVoltage")
    private final boolean mHaveVoltage; // True if we have battery voltage information.
    @PropertyName("haveCurrent")
    private final boolean mHaveCurrent; // True if we have battery current information.
    @PropertyName("charging")
    private final boolean mCharging; // True if we are charging the battery.

    public BatteryStatus() {
        mName = null;
        mMinVoltage = 0.0;
        mLowVoltage = 0.0;
        mMaxVoltage = 0.0;
        mCriticalPercentage = 0.0;
        mLowPercentage = 0.0;
        mVoltage = 0.0;
        mCurrent = 0.0;
        mPercentage = 0.0;
        mHaveVoltage = false;
        mHaveCurrent = false;
        mCharging = false;
    }

    @SuppressWarnings("SameParameterValue")
    public BatteryStatus(String name, double criticalPercentage, double lowPercentage,
                         double minVoltage, double lowVoltage, double maxVoltage) {
        mName = name;
        mMinVoltage = minVoltage;
        mLowVoltage = lowVoltage;
        mMaxVoltage = maxVoltage;
        mCriticalPercentage = criticalPercentage;
        mLowPercentage = lowPercentage;
        mVoltage = 0.0;
        mCurrent = 0.0;
        mPercentage = 0.0;
        mHaveVoltage = false;
        mHaveCurrent = false;
        mCharging = false;
    }
    public BatteryStatus(String name, double criticalPercentage, double lowPercentage) {
        mName = name;
        mMinVoltage = 0.0;
        mLowVoltage = 0.0;
        mMaxVoltage = 0.0;
        mCriticalPercentage = criticalPercentage;
        mLowPercentage = lowPercentage;
        mVoltage = 0.0;
        mCurrent = 0.0;
        mPercentage = 0.0;
        mHaveVoltage = false;
        mHaveCurrent = false;
        mCharging = false;
    }

    public BatteryStatus(BatteryStatus status, boolean charging, double percentage) {
        mName = status.getName();
        mMinVoltage = status.getMinVoltage();
        mLowVoltage = status.getLowVoltage();
        mMaxVoltage = status.getMaxVoltage();
        mCriticalPercentage = status.getCriticalPercentage();
        mLowPercentage = status.getLowPercentage();
        mVoltage = 0.0;
        mCurrent = 0.0;
        mPercentage = percentage;
        mHaveVoltage = false;
        mHaveCurrent = false;
        mCharging = charging;
    }

    public BatteryStatus(BatteryStatus status, boolean charging, double percentage, double voltage) {
        mName = status.getName();
        mMinVoltage = status.getMinVoltage();
        mLowVoltage = status.getLowVoltage();
        mMaxVoltage = status.getMaxVoltage();
        mCriticalPercentage = status.getCriticalPercentage();
        mLowPercentage = status.getLowPercentage();
        mVoltage = voltage;
        mCurrent = 0.0;
        mPercentage = percentage;
        mHaveVoltage = true;
        mHaveCurrent = false;
        mCharging = charging;
    }

    public BatteryStatus(BatteryStatus status, boolean charging, double percentage, double voltage, double current) {
        mName = status.getName();
        mMinVoltage = status.getMinVoltage();
        mLowVoltage = status.getLowVoltage();
        mMaxVoltage = status.getMaxVoltage();
        mCriticalPercentage = status.getCriticalPercentage();
        mLowPercentage = status.getLowPercentage();
        mVoltage = voltage;
        mCurrent = current;
        mPercentage = percentage;
        mHaveVoltage = true;
        mHaveCurrent = true;
        mCharging = charging;
    }

    @PropertyName("name")
    public String getName() {
        return mName;
    }
    @PropertyName("minVoltage")
    public double getMinVoltage() {
        return mMinVoltage;
    }
    @PropertyName("lowVoltage")
    public double getLowVoltage() {
        return mLowVoltage;
    }
    @PropertyName("maxVoltage")
    public double getMaxVoltage() {
        return mMaxVoltage;
    }
    @PropertyName("lowPercentage")
    public double getLowPercentage() {
        return mLowPercentage;
    }
    @PropertyName("criticalPercentage")
    public double getCriticalPercentage() {
        return mCriticalPercentage;
    }
    @PropertyName("current")
    public double getCurrent() {
        return mCurrent;
    }
    @PropertyName("voltage")
    public double getVoltage() {
        return mVoltage;
    }
    @PropertyName("percentage")
    public double getPercentage() {
        return mPercentage;
    }
    @PropertyName("voltage")
    public boolean haveVoltage() {
        return mHaveVoltage;
    }
    @PropertyName("haveCurrent")
    public boolean haveCurrent() {
        return mHaveCurrent;
    }
    @PropertyName("charging")
    public boolean getCharging() {
        return mCharging;
    }


    public static BatteryStatus fromFirebase(DataSnapshot snapshot) {
        return snapshot.getValue(BatteryStatus.class);
    }

    public Object toFirebase() {
        return this;
    }

    @Override
    public String toString() {
        return "BatteryStatus(" + mName + ", criticalPercentage=" + mCriticalPercentage
                + ", lowPercentage=" + mLowPercentage + ", minVoltage=" + mMinVoltage
                + ", lowVoltage=" + mLowVoltage + ", maxVoltage=" + mMaxVoltage
                + ", haveVoltage=" + mHaveVoltage + ", current=" + mCurrent + ", haveCurrent="
                + mHaveCurrent + ", charging=" + mCharging + ")";
    }
}
