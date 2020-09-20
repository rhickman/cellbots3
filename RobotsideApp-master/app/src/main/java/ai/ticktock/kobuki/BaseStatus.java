package ai.cellbots.kobuki;

/**
 * Stores the status of the base (bumpers, etc).
 */
public class BaseStatus {

    // Robot state variables from http://yujinrobot.github.com/kobuki/enAppendixProtocolSpecification.html

    /* Time Stamp
     * Timestamp generated internally in milliseconds. It circulates from 0 to 65535
     */
    private short mTimeStamp = 0;
    /* Bumper status
     * Flag will be set when bumper is pressed
     * 0x01 for right bumper
     * 0x02 for central bumper
     * 0x04 for left bumper
     */
    private byte mBumper = 0;
    /* Wheel drop status
     * Flag will be set when wheel is dropped
     * 0x01 for right wheel
     * 0x02 for left wheel
     */
    private byte mWheelDrop = 0;
    /* Cliff sensor status
     * Flag will be set when cliff is detected
     * 0x01 for right cliff sensor
     * 0x02 for central cliff sensor
     * 0x04 for left cliff sensor
     */
    private byte mCliff = 0;
    /* Button status
     * Flag will be set when button is pressed
     * 0x01 for Button 0
     * 0x02 for Button 1
     * 0x04 for Button 2
     */
    private byte mButton = 0;
    /* Charger status
     * 0 for DISCHARGING state
     * 2 for DOCKING_CHARGED state
     * 6 for DOCKING_CHARGING state
     * 18 for ADAPTER_CHARGED state
     * 22 for ADAPTER_CHARGING state
     */
    private byte mCharger = 0;
    /* Battery status
     * Voltage of battery in 0.1 V. Typically 16.7 V when fully charged
     */
    private byte mBattery = 0;
    /* Distance from encoders
     * Accumulated encoder data of left and right wheels in ticks
     * Increments of this value means forward direction. It circulates from 0 to 65535
     */
    private int mLeftDistance = 0;
    private int mRightDistance = 0;
    /* Inertial data
     * Angle and angle rate around Z axis.
     */
    private short mAngle = 0;
    private short mAngleRate = 0;

    @SuppressWarnings("unused")
    public short getTimestamp() {
        return mTimeStamp;
    }

    void setTimeStamp(short mTimeStamp) {
        this.mTimeStamp = mTimeStamp;
    }

    @SuppressWarnings("unused")
    public byte getWheelDrop() {
        return mWheelDrop;
    }

    void setWheelDrop(byte mWheelDrop) {
        this.mWheelDrop = mWheelDrop;
    }

    @SuppressWarnings("unused")
    public byte getCliff() {
        return mCliff;
    }

    void setCliff(byte mCliff) {
        this.mCliff = mCliff;
    }

    @SuppressWarnings("unused")
    public byte getButton() {
        return mButton;
    }

    void setButton(byte mButton) {
        this.mButton = mButton;
    }

    @SuppressWarnings("unused")
    public byte getCharger() {
        return mCharger;
    }

    void setCharger(byte mCharger) {
        this.mCharger = mCharger;
    }

    @SuppressWarnings("unused")
    public byte getBattery() {
        return mBattery;
    }

    void setBattery(byte mBattery) {
        this.mBattery = mBattery;
    }

    @SuppressWarnings("unused")
    public short getAngle() {
        return mAngle;
    }

    @SuppressWarnings("unused")
    public void setAngle(short mAngle) {
        this.mAngle = mAngle;
    }

    @SuppressWarnings("unused")
    public short getAngleRate() {
        return mAngleRate;
    }

    @SuppressWarnings("unused")
    public void setAngleRate(short mAngleRate) {
        this.mAngleRate = mAngleRate;
    }

    @SuppressWarnings("unused")
    public int getLeftDistance() {
        return mLeftDistance;
    }

    @SuppressWarnings("unused")
    public void setLeftDistance(int mLeftDistance) {
        this.mLeftDistance = mLeftDistance;
    }

    @SuppressWarnings("unused")
    public int getRightDistance() {
        return mRightDistance;
    }

    @SuppressWarnings("unused")
    public void setRightDistance(int mRightDistance) {
        this.mRightDistance = mRightDistance;
    }

    @SuppressWarnings("unused")
    int getBumper() {
        return (int)mBumper;
    }

    void setBumper(byte mBumper) {
        this.mBumper = mBumper;
    }
}