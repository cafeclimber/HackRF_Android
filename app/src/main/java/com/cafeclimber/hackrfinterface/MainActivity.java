package com.cafeclimber.hackrfinterface;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

// hackrf_android includes
import com.mantz_it.hackrf_android.Hackrf;
import com.mantz_it.hackrf_android.HackrfCallbackInterface;
import com.mantz_it.hackrf_android.HackrfUsbException;

import org.w3c.dom.Text;

public class MainActivity extends Activity implements HackrfCallbackInterface {
    public final static Integer INITIAL_SAMP_RATE = 15000000; // 15Msps

    // GUI Elements:
    private TextView tv = null;

    private Hackrf hackrf = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tv = (TextView) findViewById(R.id.HackRF_ID);
    }

    /**
     * Called if the user press "Open HackRF" Button. Initializes the
     * HackRF and updates the relevant text field to show the initialized
     * board's ID.
     *
     * @param view         Reference to the calling View (bt_openHackRF)
     */
    public void openHackRF(View view) {
        int queueSize = INITIAL_SAMP_RATE * 2;

        // Initialize the HackRF (opens the USB device, asking user for permission)
        if (!Hackrf.initHackrf(view.getContext(), this, queueSize)) {
            tv.append("No HackRF could be found!\n");
        }
    }

    /**
     * Is called by the hackrf_android library after the device is ready.
     * Triggered by the initHackrf() call in openHackrf().
     * See HackrfCallbackInterface.java
     *
     * @param hackrf Instance of the Hackrf class that represents the open device
     */
    @Override
    public void onHackrfReady(Hackrf hackrf) {
        tv.append("HackRF is ready!\n");

        this.hackrf = hackrf;
        // TODO: Enable other buttons now that board is available
    }

    /**
     * Is called by the hackrf_android library after an error occurs while
     * opening the device.
     * Triggered by the initHackrf() call in openHackrf().
     * See HackrfCallbackInterface.java
     *
     * @param message Short human readable error message
     */
    @Override
    public void onHackrfError(String message) {
        tv.append("Error while opening HackRF: " + message + "\n");
        // TODO: Disable GUI elements
    }

    @Override
    public void run() {
        switch(this.task) {
            case PRINT_INFO:
                infoThread();
                break;
            case RECEIVE:
                receiveThread();
                break;
            case TRANSMIT:
                transmitThread();
                break;
            default:
        }
    }
}
