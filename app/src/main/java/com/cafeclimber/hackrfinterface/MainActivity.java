package com.cafeclimber.hackrfinterface;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.cafeclimber.hackrfinterface.MainActivity.Task.IDLE;
import static com.cafeclimber.hackrfinterface.MainActivity.Task.PRINT_INFO;
import static com.cafeclimber.hackrfinterface.MainActivity.Task.RECEIVE;
import static com.cafeclimber.hackrfinterface.MainActivity.Task.TRANSMIT;

// TODO: Make @param docstrings consistent.
public class MainActivity extends Activity implements Runnable, HackrfCallbackInterface {
    public final static Integer INITIAL_SAMP_RATE = 15000000; // 15Msps

    // GUI Elements:
    private Button   bt_OpenHackRF  = null;
    private Button   bt_Info        = null;
    private Button   bt_TX          = null;
    private Button   bt_RX          = null;
    private Button   bt_Stop        = null;
    private TextView tv_output      = null;

    // HackRF Instance
    private Hackrf hackrf = null;

    private int sampRate = 0;
    private long frequency = 0;
    private String filename = null;
    private int vgaGain = 0;
    private int lnaGain = 0;
    private boolean amp = false;
    private boolean antennaPower = false;

    private boolean stopRequested = false; // Used to stop RX/TX thread
    private boolean repeatTransmitting = false; // Set to true to rewind sample file for each use
    private static final String foldername = "HackRF"; // Folder name for capture files

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

        bt_OpenHackRF = (Button) findViewById(R.id.bt_openHackRF);
        bt_Info       = (Button) findViewById(R.id.bt_Info);
        bt_TX         = (Button) findViewById(R.id.bt_TX);
        bt_RX         = (Button) findViewById(R.id.bt_RX);
        bt_Stop       = (Button) findViewById(R.id.bt_Stop);
        tv_output     = (TextView) findViewById(R.id.tv_output);
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
        toggleButtonsEnabledIfTransceiving(false);
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
                bt_TX.setEnabled(enable);
                bt_RX.setEnabled(enable);
                bt_Stop.setEnabled(enable);
                bt_OpenHackRF.setEnabled(!enable);
            }
        });
    }

    /**
     * Toggles 'Info', 'TX', and 'RX' buttons and toggles 'Stop' to
     * the opposite. Can be called from outside the GUI thread because it uses
     * the hanndler reference to access the TextView.
     *
     * @param enable If true, disable 'Info', 'TX', and 'RX', and enable 'Stop', and vice versa
     */
    public void toggleButtonsEnabledIfTransceiving(final boolean enable) {
        handler.post(new Runnable() {
            public void run() {
                bt_Info.setEnabled(!enable);
                bt_TX.setEnabled(!enable);
                bt_RX.setEnabled(!enable);
                bt_Stop.setEnabled(enable);
            }
        });
    }

    /**
     * Will append the message to the tv_output TextView. Can be called from
     * outside the GUI thread because it uses the handler reference to access
     * the TextView.
     *
     * @param msg	Message to print on the screen
     */
    public void printOnScreen(final String msg) {
        handler.post(new Runnable() {
            public void run() {
                tv_output.append(msg);
            }
        });
    }

    /**
     * Callback method for Info button. Changes task to PRINT_INFO
     * and spins up a new thread.
     *
     * @param view The parent view
     */
    public void info(View view) {
        if (hackrf != null) {
            this.task = PRINT_INFO;
            new Thread(this).start();
        }
    }

    /**
     * Callback for 'TX' button. Reads GUI Elements in to class variables,
     * sets the task to transmit, and spins up a new thread (which will run receiveThread())
     *
     * @param view The view context in which this function was called
     */
    public void tx(View view) {
        if (hackrf != null) {
            // TODO: Read GUI elements to populate variables
            task = RECEIVE;
            stopRequested = false;
            new Thread(this).start();
            toggleButtonsEnabledIfTransceiving(true);
        }
    }

    /**
     * Callback for 'RX' button. Reads GUI Elements in to class variables,
     * sets the task to receive, and spins up a new thread (which will run receiveThread())
     *
     * @param view The view context in which this function was called
     */
    public void rx(View view) {
        if (hackrf!= null) {
            // TODO: Read GUI elements to populate variables
            task = TRANSMIT;
            stopRequested = false;
            new Thread(this).start();
            toggleButtonsEnabledIfTransceiving(true);
        }
    }

    /**
     * Callback for the 'Stop' button. Sets the stopRequested attribute to true,
     * which causes the current thread to to shut down. It will then set the
     * transceiver mode of the HackRF to off.
     *
     * @param view Context in which this function was called
     */
    public void stop(View view) {
        stopRequested = false;
        toggleButtonsEnabledIfTransceiving(false);

        if(hackrf != null) {
            try {
                hackrf.stop();
            } catch (HackrfUsbException e) {
                printOnScreen("[ERROR] USB Exception!\n");
                toggleButtonsEnabledIfHackRFReady(false);
            }
        }
    }

    /**
     * Calls the proper thread function depending on the task
     * variable in the current thread
     */
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

    /**
     * Runs in a separate thread spun up by tx(). Sets the HackRF into transmitting
     * mode and then read the samples from a file and pass them to the HackRF. Will
     * run forever until the user presses the 'Stop' button.
     */
    public void transmitThread() {

    }

    /**
     * Runs in a separate thread spun up by rx(). Sets the HackRF into receiving
     * mode and then save the received samples to a file. Will run forever until
     * the user presses the 'Stop' button.
     */
    public void receiveThread() {
        int basebandFilterWidth = Hackrf.computeBasebandFilterBandwidth((int)(0.75 * sampRate));
        int i = 0;
        long lastTransceiverPacketCounter = 0;
        long lastTransceivingTime = 0;

        // vgaGain and lnaGain range from 0 - 100. Must be scaled appropriately.
        vgaGain = (vgaGain * 62) / 100;
        lnaGain = (lnaGain * 40) / 100;

        try {
            // First set all parameter:
            printOnScreen("Setting Sample Rate to " + sampRate + "Sps ...");
            hackrf.setSampleRate(sampRate, 1);
            printOnScreen(" Done. \n Setting Frequency to " + frequency + "Hz ...");
            hackrf.setFrequency(frequency);
            printOnScreen(" Done. \n Setting Baseband Filter Bandwidth to " + basebandFilterWidth + " Hz ...");
            hackrf.setBasebandFilterBandwidth(basebandFilterWidth);
            printOnScreen(" Done. \n Setting RX VGA Gain to " + vgaGain + " ...");
            hackrf.setRxVGAGain(lnaGain);
            printOnScreen(" Done. \n Setting LNA Gain to " + lnaGain + "...");
            hackrf.setRxLNAGain(vgaGain);
            printOnScreen(" Done. \n Setting Amplifier to " + amp + " ... ");
            hackrf.setAmp(amp);
            printOnScreen(" Done. \n Setting Antenna Power to " + antennaPower + " ...");
            hackrf.setAntennaPower(antennaPower);
            printOnScreen(" Done.\n\n");

            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                printOnScreen("External Media Storage not available. \n\n");
                return;
            }

            // Create a file...
            // If no filename was given, write to /dev/null
            File file;
            if(filename.equals("")) {
                file = new File("/dev/", "null");
            }
            else {
                file = new File(Environment.getExternalStorageState() + "/" + foldername, filename);
            }

            file.getParentFile().mkdir(); // Create folder if it doesn't exist
            printOnScreen("Saving samples to " + file.getAbsolutePath() + "\n");

            // ... then open it with a buffered output stream
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file));

            // Start Receiving:
            printOnScreen("Start receiving... \n");
            ArrayBlockingQueue<byte[]> queue = hackrf.startRX();

            // Run until the use hits the 'Stop' button
            while (!stopRequested) {
                i++; // Only for statistics

                // Grab one packet from the top of the queue. Will block if queue is
                // empty and timeout after one second if the queue stays empty.

                // TODO: Get rid of magic constant?
                // TODO: Change packet size?
                byte[] receivedBytes = queue.poll(1000, TimeUnit.MILLISECONDS);

                /* HERE should be the DSP portion of the app. The receivedBytes
                 * variable now contains a byte array of size hackrf.getPacketSize().
                 * This is currently set to 16KB, but may change in the future.
                 * The bytes are interleaved, 8-bit, signed IQ samples (in-phase
                 * component first, followed by the quadrature component):
                 *
                 * [--------- first sample ----------]   [-------- second sample --------]
				 *     I                  Q                  I                Q ...
				 * receivedBytes[0]   receivedBytes[1]   receivedBytes[2]       ...
				 *
				 * Note: Make sure you read from the queue fast enough. If it runs full,
				 * the hackrf_android library will abort receiving and go back to OFF mode.
                 */

                // We just write the whole packet into the file:
                if (receivedBytes != null) {
                    // May be too slow for some phones
                    bufferedOutputStream.write(receivedBytes);

                    /* IMPORTANT: After we used the receivedBytes buffer and don't need it
                     * anymore, we should return it to the buffer pool of the hackrf! This
                     * will save a lot of allocation time and the garbage collector won't
                     * go off every second.
                     */
                    hackrf.returnBufferToBufferPool(receivedBytes);
                }
                else {
                    printOnScreen("[ERROR] Queue is empty! This is likely due to the queue"
                         + " running full, which causes the Hackrf class to stop receiving"
                         + " Writing the samples to a file seems to be working too slowly"
                         + " ... try a lower sample rate.)\n");
                    break;
                }

                // print statistics
                if (i % 1000 == 0) {
                    long bytes = (hackrf.getTransceiverPacketCounter() - lastTransceiverPacketCounter) * hackrf.getPacketSize();
                    double time = (hackrf.getTransceivingTime() - lastTransceivingTime) / 1000.0;
                    printOnScreen( String.format("Current Transfer Rate: %4.1f MB/s\n", (bytes / time) / 1000000.0));
                    lastTransceiverPacketCounter = hackrf.getTransceiverPacketCounter();
                    lastTransceivingTime = hackrf.getTransceivingTime();
                }
            }

            // After loop ends, close the file and print more statistics
            bufferedOutputStream.close();
            printOnScreen( String.format("Finished! (Average Transfer Rate: %4.1f MB/s\n",
                    hackrf.getAverageTransceiveRate() / 1000000.0));
            printOnScreen( String.format("Recorded %d packets (of %d bytes) in %5.3f seconds.\n\n,",
                    hackrf.getTransceiverPacketCounter(),
                    hackrf.getPacketSize(),
                    hackrf.getTransceivingTime() / 1000.0));
            toggleButtonsEnabledIfTransceiving(false);
        } catch (HackrfUsbException e) {
            // This exception is thrown if a USB communication error occurs (e.g. you unplug / reset
            // the device while receiving).
            printOnScreen("[ERROR] USB Exception!\n");
            toggleButtonsEnabledIfHackRFReady(false);
        } catch (IOException e) {
            // This exception is thrown if the file could not be opened or write fails.
            printOnScreen("[ERROR] File I/O Exception!\n");
            toggleButtonsEnabledIfTransceiving(false);
        } catch (InterruptedException e) {
            // This exception is thrown if queue.poll() is interrupted
            printOnScreen("[ERROR] Queue polling interrupted!\n");
            toggleButtonsEnabledIfTransceiving(false);
        }
    }
}
