package org.kuyil.metronome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.puredata.android.io.AudioParameters;
import org.puredata.android.service.PdService;
import org.puredata.android.utils.PdUiDispatcher;
import org.puredata.core.PdBase;
import org.puredata.core.PdListener;
import org.puredata.core.utils.IoUtils;

import java.io.File;
import java.io.IOException;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    TextView mTickTextView;
    FloatingActionButton mFab;
    boolean mIsPlaying;

    private PdUiDispatcher pdUiDispatcher;
    private PdService pdService = null;

    private final PdListener pdListener = new PdListener() {
        @Override
        public void receiveBang(String source) {
            if (source.equals("tick")) {
                Random rnd = new Random();
                int color = Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256));
                mTickTextView.setTextColor(color);
            }
        }

        @Override
        public void receiveFloat(String source, float x) {

        }

        @Override
        public void receiveSymbol(String source, String symbol) {

        }

        @Override
        public void receiveList(String source, Object... args) {

        }

        @Override
        public void receiveMessage(String source, String symbol, Object... args) {

        }
    };

    private final ServiceConnection pdConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            pdService = ((PdService.PdBinder) service).getService();
            try {
                initPd();
                loadPatch();
                PdBase.sendBang("start");
            } catch (IOException e) {
                Log.e(this.getClass().getName(), e.toString());
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // this method will never be called
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTickTextView = (TextView) findViewById(R.id.tick_textview);
        mFab = (FloatingActionButton) findViewById(R.id.fab);

        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsPlaying) {
                    mute();
                } else {
                    play();
                }
            }
        });

        initSystemServices();
        bindService(new Intent(this, PdService.class), pdConnection, BIND_AUTO_CREATE);
    }

    private void play() {
        PdBase.sendBang("start");
        mIsPlaying = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mFab.setImageDrawable(getResources().getDrawable(R.drawable.ic_stop_24dp, getApplicationContext().getTheme()));
        } else {
            mFab.setImageDrawable(getResources().getDrawable(R.drawable.ic_stop_24dp));
        }
    }

    private void mute() {
        PdBase.sendBang("stop");
        mIsPlaying = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mFab.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_arrow_24dp, getApplicationContext().getTheme()));
        } else {
            mFab.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_arrow_24dp));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(pdConnection);
    }

    private void initPd() throws IOException {
        // Configure the audio glue
        AudioParameters.init(this);
        int sampleRate = AudioParameters.suggestSampleRate();
        pdService.initAudio(sampleRate, 0, 2, 10.0f);
        start();

        pdUiDispatcher = new PdUiDispatcher();
        PdBase.setReceiver(pdUiDispatcher);
        pdUiDispatcher.addListener("tick", pdListener);
    }

    private void start() {
        if (!pdService.isRunning()) {
            Intent intent = new Intent(MainActivity.this,
                    MainActivity.class);
            pdService.startAudio(intent, R.drawable.icon,
                    "Metronome", "Return to Metronome.");
            mIsPlaying = true;
        }
    }

    private void stop() {
        pdService.stopAudio();
        mIsPlaying = false;
    }

    private void loadPatch() throws IOException {
        File dir = getFilesDir();
        IoUtils.extractZipResource(
                getResources().openRawResource(R.raw.pd_patch), dir, true);
        File patchFile = new File(dir, "metronome.pd");
        PdBase.openPatch(patchFile.getAbsolutePath());
    }

    private void initSystemServices() {
        TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        telephonyManager.listen(new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                if (pdService == null) return;
                if (state == TelephonyManager.CALL_STATE_IDLE) {
                    start();
                } else {
                    stop();
                }
            }
        }, PhoneStateListener.LISTEN_CALL_STATE);
    }
}
