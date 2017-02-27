package com.cafeclimber.hackrfinterface;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.support.v4.widget.DrawerLayout;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

// hackrf_android includes
import com.mantz_it.hackrf_android.Hackrf;
import com.mantz_it.hackrf_android.HackrfCallbackInterface;
import com.mantz_it.hackrf_android.HackrfUsbException;

import org.w3c.dom.Text;

import static com.cafeclimber.hackrfinterface.MainActivity.Task.IDLE;
import static com.cafeclimber.hackrfinterface.MainActivity.Task.PRINT_INFO;

public class MainActivity extends Activity implements Runnable, HackrfCallbackInterface {
    public final static Integer INITIAL_SAMP_RATE = 15000000; // 15Msps

    // GUI Elements:
    private Button   bt_Info         = null;
    private Button   bt_OpenHackRF   = null;
    private TextView tv_output       = null;

    // HackRF Instance
    private Hackrf hackrf = null;

    // Used for other threads to access the main GUI thread
    private Handler handler;

    /**
     * List of all possible tasks which can be run in a thread
     * New threads check this variable and decide which task should be run
     */
    public enum Task {
        PRINT_INFO, TRANSMIT, RECEIVE, IDLE
    }
    private Task task = IDLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler();

        bt_Info = (Button) findViewById(R.id.bt_Info);
        bt_OpenHackRF = (Button) findViewById(R.id.bt_openHackRF);
        tv_output = (TextView) findViewById(R.id.tv_output);
        tv_output.setMovementMethod(new ScrollingMovementMethod()); // Makes it scroll
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
            Log.d("[HACKRF]:", "Writing to tv_output");
            tv_output.append("No HackRF could be found!\n");
        }
    }

    /**
     * Toggles button status depending on whether a Hack RF device is currently open.
     * Can be called from outside the GUI thread because it uses the handler reference
     * to access the TextView.
     *
     * @param enable     If true, enable buttons; if false, disable buttons
     */
    public void toggleButtonsEnabledIfHackRFReady(final boolean enable) {
        handler.post(new Runnable() {
            public void run() {
                bt_Info.setEnabled(enable);
                bt_OpenHackRF.setEnabled(!enable);
            }
        });
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
        tv_output.append("HackRF is ready!\n");

        this.hackrf = hackrf;
        toggleButtonsEnabledIfHackRFReady(true);
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
        tv_output.append("Error while opening HackRF: " + message + "\n");
        toggleButtonsEnabledIfHackRFReady(false);
    }

    /**
     * Will append the message to the tv_output TextView. Can be called from
     * outside the GUI thread because it uses the handler reference to access
     * the TextView.
     *
     * @param msg	Message to print on the screen
     */
    public void printOnScreen(final String msg)
    {
        handler.post(new Runnable() {
            public void run() {
                tv_output.append(msg);
            }
        });
    }

    public void info(View view) {
        if (hackrf != null) {
            this.task = PRINT_INFO;
            new Thread(this).start();
        }
    }

    @Override
    public void run() {
        switch(this.task) {
            case PRINT_INFO:
                infoThread();
                break;
            // TODO: Add TX and RX threads and callbacks
            /*case RECEIVE:
                receiveThread();
                break;
            case TRANSMIT:
                transmitThread();
                break;*/
            default:
        }
    }

    /**
     * Spins up a new thread, and retrieves the BoardID, Version String
     * PartID, and Serial Number from the device
     * and then prints that information to the screen
     */
    public void infoThread() {
        try {
            int boardID = hackrf.getBoardID();
            int tmp[] = hackrf.getPartIdAndSerialNo();

            printOnScreen("[INFO] Board ID:    " + boardID + "(" + Hackrf.convertBoardIdToString(boardID) + ")\n" );
            printOnScreen("[INFO] Version:     " + hackrf.getVersionString() + "\n" );
            printOnScreen("[INFO] PartID:    0x" + Integer.toHexString(tmp[0]) +
                                        "    0x" + Integer.toHexString(tmp[1]) + "\n");
            printOnScreen("[INFO] Serial No. 0x" + Integer.toHexString(tmp[2]) +
                                           " 0x" + Integer.toHexString(tmp[3]) +
                                           " 0x" + Integer.toHexString(tmp[4]) +
                                           " 0x" + Integer.toHexString(tmp[5]) + "\n\n");
        } catch (HackrfUsbException e) {
            printOnScreen("[ERROR] Failed to retrieve board information!\n");
            toggleButtonsEnabledIfHackRFReady(false);
        }
    }
}
