package ai.cellbots.robotlib;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.support.annotation.NonNull;
import android.util.Log;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Driver for robots over USB port via USB to serial.
 */
abstract public class UsbRobotDriver extends RobotDriver {
    private static final String TAG = UsbRobotDriver.class.getSimpleName();
    // Restart USB if no message for this time, in milliseconds
    private static final long USB_TIMEOUT = 2000L;

    private long mLastUpdate = 0;
    private final int[][] mDeviceIds;
    private final UsbManager mUsbManager;
    private UsbSerialDevice mUsbSerialDevice = null;
    private String mUsbDeviceSerialNumber = "";
    private String mDataBuffer = "";
    private final boolean mStoreInBinaryFormat;
    private final LinkedList<Byte> mByteQueue = new LinkedList<>();
    private final UsbConfiguration mUsbConfiguration;

    /**
     * Configuration of the USB system.
     */
    protected static class UsbConfiguration {
        private final int mBaud;
        private final int mDataBits;
        private final int mStopBits;
        private final int mParity;
        private final int mFlowControl;
        private final boolean mEnableDTR;
        private final boolean mEnableRTS;

        /**
         * Create a USB device configuration.
         * @param baud The baud rate.
         * @param dataBits The data bit count.
         * @param stopBits The stop bit count.
         * @param parity The parity, from UsbSerialInterface.PARITY_*.
         * @param flowControl The flow control, from UsbSerialInterface.FLOW_CONTROL_*.
         * @param enableDTR True if we should enable DTR.
         * @param enableRTS True if we should enable RTS.
         */
        public UsbConfiguration(int baud, int dataBits, int stopBits, int parity, int flowControl,
                boolean enableDTR, boolean enableRTS) {
            mBaud = baud;
            mDataBits = dataBits;
            mStopBits = stopBits;
            mParity = parity;
            mFlowControl = flowControl;
            mEnableDTR = enableDTR;
            mEnableRTS = enableRTS;
        }

        /**
         * Set up a UsbSerialDevice to use the configuration.
         * @param serial The UsbSerialDevice to set with the configuration.
         */
        private void setUpDevice(UsbSerialDevice serial) {
            serial.setBaudRate(mBaud);
            serial.setDataBits(mDataBits);
            serial.setStopBits(mStopBits);
            serial.setParity(mParity);
            serial.setFlowControl(mFlowControl);
            serial.setDTR(mEnableDTR);
            serial.setRTS(mEnableRTS);
        }
    }

    // The default configuration of the system
    private static final UsbConfiguration DEFAULT_CONFIGURATION
            = new UsbConfiguration(115200, UsbSerialInterface.DATA_BITS_8, 1,
                UsbSerialInterface.PARITY_NONE, UsbSerialInterface.FLOW_CONTROL_OFF, false, false);

    /**
     * Initializes a USB device for the system.
     * @param device The USB device to initialize.
     * @return True if device was opened correctly.
     */
    private synchronized boolean initializeUsbDevice(UsbDevice device) {
        if (!mUsbManager.hasPermission(device)) {
            return false;
        }

        UsbDeviceConnection connection = mUsbManager.openDevice(device);
        if (connection == null) {
            Log.e(TAG, "Unable to open: " + device);
            return false;
        }

        final UsbSerialDevice serial = UsbSerialDevice.createUsbSerialDevice(device, connection);
        if (!serial.open()) {
            Log.i(TAG, "Unable to open serial port");
            return false;
        }

        mUsbConfiguration.setUpDevice(serial);

        synchronized (this) {
            mUsbSerialDevice = serial;
            mUsbDeviceSerialNumber = device.getSerialNumber();
            mLastUpdate = new Date().getTime();
        }

        mUsbSerialDevice.read(new UsbSerialInterface.UsbReadCallback() {
            @Override
            public void onReceivedData(byte[] bytes) {
                String vs = "";
                if (mStoreInBinaryFormat) {
                    synchronized (UsbRobotDriver.this) {
                        if (serial == mUsbSerialDevice) {
                            if (bytes.length != 0) {
                                mLastUpdate = new Date().getTime();
                                for (byte b : bytes) {
                                    mByteQueue.add(b);
                                }
                            }
                        }
                    }
                } else {
                    if (bytes.length != 0) {
                        try {
                            vs = new String(bytes, "ASCII");
                        } catch (Exception ex) {
                            Log.i(TAG, "Warning, exception with USB result", ex);
                        }
                    }
                    synchronized (UsbRobotDriver.this) {
                        if (serial == mUsbSerialDevice) {
                            if (bytes.length != 0) {
                                mLastUpdate = new Date().getTime();
                                mDataBuffer += vs;
                            } else {
                                terminateSession();
                            }
                        }
                    }
                }
            }
        });
        return true;
    }

    /**
     * Get the data buffer from the USB.
     */
    protected synchronized String getDataBuffer() {
        if (mStoreInBinaryFormat) {
            throw new IllegalStateException("Cannot use string data buffer on binary device");
        }
        return mDataBuffer;
    }

    /**
     * Get the serial number of the current USB device.
     * @return The serial number or null if not found.
     */
    protected synchronized String getUsbDeviceSerialNumber() {
        return mUsbDeviceSerialNumber;
    }

    /**
     * Set the data buffer from the USB.
     * @param value The new data buffer.
     */
    protected synchronized void setDataBuffer(String value) {
        if (mStoreInBinaryFormat) {
            throw new IllegalStateException("Cannot use string data buffer on binary device");
        }
        mDataBuffer = value;
    }

    /**
     * Get the next byte of data from the USB.
     * @return The next byte.
     */
    protected synchronized byte getNextByte() {
        if (!mStoreInBinaryFormat) {
            throw new IllegalStateException("Cannot use byte buffer on non-binary device");
        }
        if (mByteQueue.isEmpty()) {
            throw new IllegalStateException("Byte queue is empty");
        }
        return mByteQueue.pop();
    }

    /**
     * Get if we have a next byte of data from the USB.
     * @return True if a byte remains.
     */
    protected synchronized boolean hasNextByte() {
        if (!mStoreInBinaryFormat) {
            throw new IllegalStateException("Cannot use byte buffer on non-binary device");
        }
        return !mByteQueue.isEmpty();
    }

    /**
     * Check if the usb is connected.
     */
    protected synchronized boolean isUsbConnected() {
       return mUsbSerialDevice != null;
    }

    /**
     * Convert a device class to a string.
     * @param device The device class.
     * @return The string name of the device.
     */
    @SuppressWarnings("unused")
    private String deviceToString(UsbDevice device) {
        return device.getProductId() + ":" + device.getVendorId() + ":" + device.getDeviceId()
                + ":" + device.getDeviceClass() + ":" + device.getDeviceSubclass()
                + ":" + device.getSerialNumber();
    }

    /**
     * Create the driver.
     *
     * @param parent Android context parent.
     * @param model The robot model for the driver.
     * @param deviceIds An array of arrays of vendorId:productId for USB devices.
     * @param binary If true, then store the information in a binary format.
     */
    protected UsbRobotDriver(Context parent, RobotModel model, int[][] deviceIds, boolean binary) {
        this(parent, model, deviceIds, binary, DEFAULT_CONFIGURATION);
    }

    /**
     * Create the driver.
     *
     * @param parent Android context parent.
     * @param model The robot model for the driver.
     * @param deviceIds An array of arrays of vendorId:productId for USB devices.
     * @param binary If true, then store the information in a binary format.
     * @param configuration The USB configuration.
     */
    protected UsbRobotDriver(Context parent, RobotModel model, int[][] deviceIds, boolean binary,
                             UsbConfiguration configuration) {
        super(parent, model);
        mUsbConfiguration = configuration;
        mDeviceIds = new int[deviceIds.length][2];
        for (int i = 0; i < mDeviceIds.length; i++) {
            if (deviceIds[i].length != 2) {
                throw new IllegalArgumentException("Device IDs must have two elements for each row");
            }
            mDeviceIds[i][0] = deviceIds[i][0];
            mDeviceIds[i][1] = deviceIds[i][1];
        }
        mUsbManager = (UsbManager) parent.getSystemService(Context.USB_SERVICE);
        mStoreInBinaryFormat = binary;
    }

    /**
     * Called on shutdown of the system.
     */
    @Override
    final protected void onShutdown() {
        synchronized (this) {
            if (isUsbConnected()) {
                terminateSession();
            }
        }
    }

    /**
     * Called every driver update.
     */
    @Override
    final synchronized protected void onUpdate() {
        onUpdateBeforeUsb();
        // If we do not currently have a USB device, try to find one.
        if (!isUsbConnected()) {
            HashMap<String, UsbDevice> usbDevices = mUsbManager.getDeviceList();
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                UsbDevice device = entry.getValue();
                if (isCorrectDevice(device)) {
                    if (initializeUsbDevice(device)) {
                        break;
                    }
                }
            }
        }
        // Once we have a USB device, if we have timed out, kill the device.
        if (isUsbConnected()) {
            if (mLastUpdate < new Date().getTime() - USB_TIMEOUT) {
                Log.i(TAG, "USB has timed out, killing for a restart");
                terminateSession();
            }
        }
        // If we still have a USB device, then run USB-dependent update logic.
        if (isUsbConnected()) {
            onUpdateAfterUsb();
        }
    }

    /**
     * Writes to the USB device, if it exists.
     * @param data The data to be written out.
     * @return True if the USB write succeeded.
     */
    protected synchronized boolean writeToUsb(@NonNull byte[] data) {
        if (isUsbConnected()) {
            try {
                mUsbSerialDevice.write(data);
                return true;
            } catch (Exception ex) {
                Log.i(TAG, "Exception in writing:", ex);
                terminateSession();
                return false;
            }

        }
        return false;
    }

    /**
     * Writes an ASCII string to the USB.
     * @param data The string to write.
     * @return True if the USB write succeeded.
     */
    protected synchronized boolean writeToUsb(@NonNull String data) {
        return writeToUsb(data.getBytes());
    }

    /**
     * On update before USB is handled.
     */
    protected abstract void onUpdateBeforeUsb();

    /**
     * Called after USB logic is handled.
     */
    protected abstract void onUpdateAfterUsb();

    /**
     * Called to shutdown a USB device session, e.g. when the cable is unplugged.
     */
    protected synchronized void terminateSession() {
        if (isUsbConnected()) {
            try {
                mUsbSerialDevice.close();
            } catch (Exception e2) {
                Log.e(TAG, "Unable to close", e2);
            }
        }
        mLastUpdate = 0;
        mDataBuffer = "";
        mUsbDeviceSerialNumber = null;
        mByteQueue.clear();
        mUsbSerialDevice = null;
        setUuid(null);
        setVersionString(null);
        setStateString(null);
        onTerminateSession();
    }

    /**
     * Called when a USB session is terminated.
     */
    protected abstract void onTerminateSession();

    /**
     * Checks if a USB device is a Neato robot.
     * @param device The USB device.
     * @return True if the correct device, otherwise false.
     */
    private boolean isCorrectDevice(UsbDevice device) {
        for (int[] deviceId : mDeviceIds) {
            if (deviceId[0] == device.getVendorId() && deviceId[1] == device.getProductId()) {
                return true;
            }
        }
        return false;
    }
}
