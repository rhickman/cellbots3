package ai.cellbots.robotapp;

import android.app.Activity;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Parcelable;

import java.util.Objects;

/**
 * This is a transparent activity that receives USB events and broadcast in a service.
 */
public class UsbEventReceiverActivity extends Activity {
    private static final String ACTION_USB_DEVICE_ATTACHED = "ai.cellbots.ACTION_USB_DEVICE_ATTACHED";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        if (intent != null) {
            if (Objects.equals(intent.getAction(), UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                Parcelable usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                // Create a new intent and put the usb device in as an extra.
                Intent broadcastIntent = new Intent(ACTION_USB_DEVICE_ATTACHED);
                broadcastIntent.putExtra(UsbManager.EXTRA_DEVICE, usbDevice);

                // Broadcast this event so we can receive it.
                sendBroadcast(broadcastIntent);
            }
        }
        // Close the activity.
        finish();
    }
}
