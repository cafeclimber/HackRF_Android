package com.cafeclimber.hackrfinterface;

import android.app.Activity;
import android.os.Bundle;

// hackrf_android includes
import com.mantz_it.hackrf_android.HackrfCallbackInterface;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
