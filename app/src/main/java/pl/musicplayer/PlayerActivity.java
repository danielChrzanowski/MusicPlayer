package pl.musicplayer;

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
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.gauravk.audiovisualizer.visualizer.BarVisualizer;

import java.io.File;
import java.util.ArrayList;

public class PlayerActivity extends AppCompatActivity implements SensorEventListener, View.OnTouchListener {
    private static final String TAG = "PlayerActivity";
    ImageButton btnplay, btnnext, btnprev, btnff, btnfr, btnhold;
    TextView txtsname, txtsstart, txtsstop;
    SeekBar seekmusic;
    BarVisualizer visualizer;
    ImageView imageView;
    boolean btnholdPressed;

    SensorManager sensorManager;
    private float gravity[];
    private float magnetic[];
    private float accels[];
    private float mags[];
    private float[] values;

    private float azimuth;
    private float pitch;
    private float roll;

    private float mAccel;
    private float mAccelCurrent;
    private float mAccelLast;

    private AudioManager audioManager;
    String sname;
    static MediaPlayer mediaPlayer;
    int position;
    ArrayList<File> mySongs;
    Thread updateseekbar;
    int audiosessionId;

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (visualizer != null) {
            visualizer.release();
        }

        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorManager.registerListener(PlayerActivity.this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(PlayerActivity.this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_NORMAL);

        getSupportActionBar().setTitle("Music list");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#FF6200EE")));

        btnprev = findViewById(R.id.btnprev);
        btnnext = findViewById(R.id.btnnext);
        btnplay = findViewById(R.id.playbtn);
        btnff = findViewById(R.id.btnff);
        btnfr = findViewById(R.id.btnfr);
        btnhold = findViewById(R.id.btnhold);
        txtsname = findViewById(R.id.txtsn);
        txtsstart = findViewById(R.id.txtsstart);
        txtsstop = findViewById(R.id.txtsstop);
        seekmusic = findViewById(R.id.seekbar);
        visualizer = findViewById(R.id.blast);
        imageView = findViewById(R.id.imageview);

        btnholdPressed = false;
        btnhold.setOnTouchListener(this);
        audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

        accels = new float[3];
        mags = new float[3];
        values = new float[3];

        azimuth = 0;
        pitch = 0;
        roll = 0;

        mAccel = 10f;
        mAccelCurrent = SensorManager.GRAVITY_EARTH;
        mAccelLast = SensorManager.GRAVITY_EARTH;

        prepareSongs();
        updateSong();

        btnplay.setOnClickListener(view -> onBtnPlayPressed());

        btnnext.setOnClickListener(view -> playNext());

        btnprev.setOnClickListener(view -> playPrev());

        btnff.setOnClickListener(view -> {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() + 10000);
            }
        });

        btnfr.setOnClickListener(view -> {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() - 10000);
            }
        });

        audiosessionId = mediaPlayer.getAudioSessionId();

        if (audiosessionId != -1) {
            visualizer.setAudioSessionId(audiosessionId);
        }
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
        txtsname.setSelected(true);

        Uri uri = Uri.parse(mySongs.get(position).toString());
        sname = mySongs.get(position).getName();
        txtsname.setText(sname);

        mediaPlayer = MediaPlayer.create(getApplicationContext(), uri);
        mediaPlayer.start();
    }

    private void updateSong() {
        updateseekbar = new Thread(() -> {
            int totalDuration = mediaPlayer.getDuration();
            int currentPosition = 0;

            while (currentPosition < totalDuration) {
                try {
                    Thread.sleep(500);
                    currentPosition = mediaPlayer.getCurrentPosition();
                    seekmusic.setProgress(currentPosition);
                } catch (InterruptedException | IllegalStateException e) {
                    //  e.printStackTrace();
                }
            }
        });

        seekmusic.setMax(mediaPlayer.getDuration());
        updateseekbar.start();

        seekmusic.getProgressDrawable().setColorFilter(getResources().getColor(R.color.purple_700), PorterDuff.Mode.MULTIPLY);
        seekmusic.getThumb().setColorFilter(getResources().getColor(R.color.purple_700), PorterDuff.Mode.SRC_IN);

        seekmusic.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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
        txtsstop.setText(endTime);

        final Handler handler = new Handler();
        final int delay = 1000;

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String currentTime = createTime(mediaPlayer.getCurrentPosition());
                txtsstart.setText(currentTime);
                handler.postDelayed(this, delay);
            }
        }, delay);

        mediaPlayer.setOnCompletionListener(mediaPlayer -> btnnext.performClick());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        switch (sensorEvent.sensor.getType()) {
            case Sensor.TYPE_MAGNETIC_FIELD:
                mags = sensorEvent.values.clone();
                break;
            case Sensor.TYPE_ACCELEROMETER:
                accels = sensorEvent.values.clone();

                onShakeAction(sensorEvent);
                break;
        }

        if (mags != null && accels != null) {
            gravity = new float[9];
            magnetic = new float[9];

            SensorManager.getRotationMatrix(gravity, magnetic, accels, mags);
            float[] outGravity = new float[9];
            SensorManager.remapCoordinateSystem(gravity, SensorManager.AXIS_X, SensorManager.AXIS_Z, outGravity);
            SensorManager.getOrientation(outGravity, values);

            azimuth = values[0] * 57.2957795f;
            pitch = values[1] * 57.2957795f;
            roll = -1 * values[2] * 57.2957795f;
            mags = null;
            accels = null;
        }

        if (roll > 60) {
            changeVolumeWithDisablingAndEnablingSensors("up");
        } else {
            if (roll < -60) {
                changeVolumeWithDisablingAndEnablingSensors("down");
            }
        }
    }

    private void changeVolumeWithDisablingAndEnablingSensors(String direction) {
        sensorManager.unregisterListener(PlayerActivity.this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
        sensorManager.unregisterListener(PlayerActivity.this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));

        if (btnholdPressed) {
            changeVolume(direction);
        }

        roll = 0;

        sensorManager.registerListener(PlayerActivity.this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(PlayerActivity.this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_NORMAL);
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

    private void playNext() {
        playSong("next");
    }

    private void playPrev() {
        playSong("prev");
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
        sname = mySongs.get(position).getName();
        txtsname.setText(sname);
        mediaPlayer.start();

        btnplay.setBackgroundResource(R.drawable.ic_pause);
        startAnimation(imageView);

        int audiosessionId = mediaPlayer.getAudioSessionId();
        if (audiosessionId != -1) {
            visualizer.setAudioSessionId(audiosessionId);
        }

        updateSong();
    }

    private void onBtnPlayPressed() {
        if (mediaPlayer.isPlaying()) {
            btnplay.setBackgroundResource(R.drawable.ic_play);
            mediaPlayer.pause();
        } else {
            btnplay.setBackgroundResource(R.drawable.ic_pause);
            mediaPlayer.start();
        }
    }

    private void onShakeAction(SensorEvent sensorEvent) {
        float x = sensorEvent.values[0];
        float y = sensorEvent.values[1];
        float z = sensorEvent.values[2];
        mAccelLast = mAccelCurrent;
        mAccelCurrent = (float) Math.sqrt((double) (x * x + y * y + z * z));
        float delta = mAccelCurrent - mAccelLast;
        mAccel = mAccel * 0.9f + delta;
        if (mAccel > 12) {
            playNext();
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        btnholdPressed = true;

        if (motionEvent.getActionMasked() == motionEvent.ACTION_UP) {
            btnholdPressed = false;
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

}