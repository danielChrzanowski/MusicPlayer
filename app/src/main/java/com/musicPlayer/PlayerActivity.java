package com.musicPlayer;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.gauravk.audiovisualizer.visualizer.BarVisualizer;
import com.squareup.seismic.ShakeDetector;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerActivity extends AppCompatActivity implements SensorEventListener, View.OnTouchListener, ShakeDetector.Listener {
    ImageButton btnPlay, btnNext, btnPrev, btnFF, btnFR, btnHold;
    TextView txtSongName, txtSongStart, txtSongStop;
    SeekBar seekBar;
    BarVisualizer visualizer;
    ImageView imageView;
    boolean btnHoldPressed;

    SensorManager sensorManager;
    private float[] accels;
    private float[] mags;
    private float[] values;
    private float roll;

    private AudioManager audioManager;
    static MediaPlayer mediaPlayer;
    String songName;
    int position;

    ShakeDetector shakeDetector;
    ArrayList<File> mySongs;
    ExecutorService executor;
    int audioSessionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        Objects.requireNonNull(getSupportActionBar()).setTitle("Music list");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#FF6200EE")));

        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnPlay = findViewById(R.id.btnPlay);
        btnFF = findViewById(R.id.btnFF);
        btnFR = findViewById(R.id.btnFR);
        btnHold = findViewById(R.id.btnHold);
        txtSongName = findViewById(R.id.txtSongName);
        txtSongStart = findViewById(R.id.txtSongStart);
        txtSongStop = findViewById(R.id.txtSongStop);
        seekBar = findViewById(R.id.seekBar);
        visualizer = findViewById(R.id.blast);
        imageView = findViewById(R.id.imageView);

        btnHoldPressed = false;
        btnHold.setOnTouchListener(this);
        audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

        accels = new float[3];
        mags = new float[3];
        values = new float[3];
        roll = 0;

        shakeDetector = new ShakeDetector(this);
        shakeDetector.start(sensorManager);

        executor = Executors.newFixedThreadPool(1);

        prepareSongs();

        btnPlay.setOnClickListener(view -> {
            if (mediaPlayer.isPlaying()) {
                btnPlay.setBackgroundResource(R.drawable.ic_play);
                mediaPlayer.pause();
            } else {
                btnPlay.setBackgroundResource(R.drawable.ic_pause);
                mediaPlayer.start();
            }
        });

        btnNext.setOnClickListener(view -> playSong("next"));
        btnPrev.setOnClickListener(view -> playSong("prev"));

        btnFF.setOnClickListener(view -> {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() + 10000);
            }
        });

        btnFR.setOnClickListener(view -> {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() - 10000);
            }
        });

        audioSessionId = mediaPlayer.getAudioSessionId();

        if (audioSessionId != -1) {
            visualizer.setAudioSessionId(audioSessionId);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        shakeDetector.start(sensorManager);

        super.onResume();
    }

    @Override
    protected void onStop() {
        shakeDetector.stop();

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (visualizer != null) {
            visualizer.release();
        }

        shakeDetector.stop();

        super.onDestroy();
    }

    private void prepareSongs() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }

        Intent i = getIntent();
        Bundle bundle = i.getExtras();

        mySongs = (ArrayList) bundle.getParcelableArrayList("songs");
        position = bundle.getInt("pos", 0);
        txtSongName.setSelected(true);

        Uri uri = Uri.parse(mySongs.get(position).toString());
        songName = mySongs.get(position).getName();
        songName = trimName(songName);
        txtSongName.setText(songName);

        mediaPlayer = MediaPlayer.create(getApplicationContext(), uri);
        mediaPlayer.start();

        updateSong();
    }

    private void playSong(String direction) {
        mediaPlayer.stop();
        mediaPlayer.release();

        if (direction.equals("next")) {
            position = ((position + 1) % mySongs.size());
        } else {
            if (direction.equals("prev")) {
                position = ((position - 1) < 0 ? (mySongs.size() - 1) : position - 1);
            }
        }

        Uri u = Uri.parse(mySongs.get(position).toString());
        mediaPlayer = MediaPlayer.create(getApplicationContext(), u);
        songName = mySongs.get(position).getName();
        Log.d("----------------------------------", songName);
        songName = trimName(songName);
        txtSongName.setText(songName);
        mediaPlayer.start();

        btnPlay.setBackgroundResource(R.drawable.ic_pause);
        startAnimation(imageView);

        int audioSessionId = mediaPlayer.getAudioSessionId();
        if (audioSessionId != -1) {
            visualizer.setAudioSessionId(audioSessionId);
        }

        updateSong();
    }

    private void updateSong() {
        Runnable updateSeekbar = new Thread(() -> {
            int totalDuration = mediaPlayer.getDuration();
            int currentPosition = 0;

            while (currentPosition < totalDuration) {
                try {
                    Thread.sleep(500);
                    currentPosition = mediaPlayer.getCurrentPosition();
                    seekBar.setProgress(currentPosition);
                } catch (InterruptedException | IllegalStateException e) {
                    // e.printStackTrace();
                }
            }
        });

        seekBar.setMax(mediaPlayer.getDuration());
        executor.execute(updateSeekbar);

        seekBar.getProgressDrawable().setColorFilter(ContextCompat.getColor(this, R.color.purple_700), PorterDuff.Mode.MULTIPLY);
        seekBar.getThumb().setColorFilter(ContextCompat.getColor(this, R.color.purple_700), PorterDuff.Mode.SRC_IN);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mediaPlayer.seekTo(seekBar.getProgress());
            }
        });

        String endTime = createTime(mediaPlayer.getDuration());
        txtSongStop.setText(endTime);

        final Handler handler = new Handler(Looper.getMainLooper());
        final int delay = 1000;

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String currentTime = createTime(mediaPlayer.getCurrentPosition());
                txtSongStart.setText(currentTime);
                handler.postDelayed(this, delay);
            }
        }, delay);

        mediaPlayer.setOnCompletionListener(mediaPlayer -> playSong("next"));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (btnHoldPressed) {
            switch (sensorEvent.sensor.getType()) {
                case Sensor.TYPE_MAGNETIC_FIELD:
                    mags = sensorEvent.values.clone();
                    break;

                case Sensor.TYPE_ACCELEROMETER:
                    accels = sensorEvent.values.clone();
                    break;
            }

            if (mags != null && accels != null) {
                float[] gravity = new float[9];
                float[] magnetic = new float[9];

                SensorManager.getRotationMatrix(gravity, magnetic, accels, mags);
                float[] outGravity = new float[9];
                SensorManager.remapCoordinateSystem(gravity, SensorManager.AXIS_X, SensorManager.AXIS_Z, outGravity);
                SensorManager.getOrientation(outGravity, values);

                roll = -1 * values[2] * 57.2957795f;
                mags = null;
                accels = null;
            }

            if (roll > 50) {
                changeVolume("up");
            } else {
                if (roll < -50) {
                    changeVolume("down");
                }
            }

            roll = 0;
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (!btnHoldPressed) {
            sensorManager.registerListener(PlayerActivity.this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(PlayerActivity.this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_NORMAL);
        }
        btnHoldPressed = true;

        if (motionEvent.getActionMasked() == motionEvent.ACTION_UP) {
            sensorManager.unregisterListener(PlayerActivity.this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
            sensorManager.unregisterListener(PlayerActivity.this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
            btnHoldPressed = false;
        }

        return false;
    }

    private void changeVolume(String direction) {
        if (direction.equals("up")) {
            audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
        } else {
            if (direction.equals("down")) {
                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
            }
        }
    }

    public void startAnimation(View view) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(imageView, "rotation", 0f, 360f);
        animator.setDuration(1000);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(animator);
        animatorSet.start();
    }

    public String createTime(int duration) {
        String time = "";
        int min = duration / 1000 / 60;
        int sec = duration / 1000 % 60;

        time += min + ":";

        if (sec < 10) {
            time += "0";
        }

        time += sec;

        return time;
    }

    private String trimName(String songName) {
        return songName
                .replace(".mp3", "")
                .replace(".wav", "");
    }

    @Override
    public void hearShake() {
        playSong("next");
    }
}