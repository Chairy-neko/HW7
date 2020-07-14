package com.example.myvideoplayer;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myvideoplayer.Player.VideoPlayerIJK;
import com.example.myvideoplayer.Player.VideoPlayerListener;

import java.util.Locale;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;


public class VideoActivity extends AppCompatActivity {
    // UI
    private View mLoading;
    private View mPlaying;
    // video player
    private VideoPlayerIJK ijkPlayer;
    // widgets
    private Button mPauseBtn;
    private SeekBar mProgressBar;
    private TextView mPlayTimeView;
    private View mToolBar;

    // handler for progress bar
    private static final int REFRESH_DELAY = 50;
    private static Handler mProgressHandler = new Handler();
    private boolean isAuto = false;
    private Runnable mRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            if(ijkPlayer.isPlaying()) {
                isAuto = true;
                synchonizeProgress();
                isAuto = false;
                mProgressHandler.postDelayed(this, REFRESH_DELAY);
            }
        }
    };

    private static final int HIDE_DELAY = 5000;
    private static Handler mHideHandler = new Handler();
    private Runnable mHideRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            mToolBar.setVisibility(View.INVISIBLE);
            mHideHandler.removeCallbacksAndMessages(null);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        getSupportActionBar().hide();
        initWindow();
        initIJKPlayer();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        startAutoProgress();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (ijkPlayer != null && ijkPlayer.isPlaying()) {
            ijkPlayer.stop();
        }
        IjkMediaPlayer.native_profileEnd();
        stopAutoProgress();
    }

    @Override
    protected void onDestroy() {
        if (ijkPlayer != null) {
            ijkPlayer.stop();
            ijkPlayer.release();
            ijkPlayer = null;
        }

        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus && Build.VERSION.SDK_INT >= 19) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE//显示状态栏，Activity不全屏显示(恢复到有状态栏的正常情况)。
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void initWindow()
    {
        // get widgets
        mLoading = findViewById(R.id.loading);
        mPlaying = findViewById(R.id.playing);
        mToolBar = findViewById(R.id.tool_bar);
        ijkPlayer = findViewById(R.id.ijkPlayer);
        mProgressBar = findViewById(R.id.sb_progress);
        mPauseBtn = findViewById(R.id.btn_pause);
        mPlayTimeView = findViewById(R.id.tv_time);

        // init widgets
        mPlaying.setVisibility(View.INVISIBLE);

        mPauseBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                resetHideHandler();
                if(ijkPlayer.isPlaying()) {
                    ijkPlayer.pause();
                    mPauseBtn.setText(getString(R.string.resume));
                } else {
                    ijkPlayer.start();
                    mPauseBtn.setText(getString(R.string.pause));
                }
            }
        });

        mProgressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            private boolean isPlaying;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                if(!isAuto) {
                    resetHideHandler();

                    double ratio = 1.0 * seekBar.getProgress() / seekBar.getMax();
                    ijkPlayer.seekTo((long) (ijkPlayer.getDuration() * ratio));
                    synchonizeProgress();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar)
            {
                stopAutoProgress();

                isPlaying = ijkPlayer.isPlaying();
                ijkPlayer.pause();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
                if(isPlaying)
                    ijkPlayer.start();

                startAutoProgress();
                isAuto = false;
            }
        });

        mPlaying.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if(mToolBar.getVisibility() == View.VISIBLE) {
                    mToolBar.setVisibility(View.INVISIBLE);
                    mHideHandler.removeCallbacksAndMessages(null);
                }
                else if(mToolBar.getVisibility() == View.INVISIBLE) {
                    mToolBar.setVisibility(View.VISIBLE);
                    resetHideHandler();
                }
            }
        });

        startAutoProgress();
    }

    private void resetHideHandler()
    {
        mHideHandler.removeCallbacksAndMessages(null);
        mHideHandler.postDelayed(mHideRunnable, HIDE_DELAY);
    }

    private void toPortrait()
    {
        boolean isPlaying = ijkPlayer.isPlaying();
        ijkPlayer.pause();

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float windowWidth  = metrics.widthPixels;
        float windowHeight = metrics.heightPixels;
        if(windowWidth > windowHeight)
            windowWidth = windowHeight;
        float ratio = windowWidth / ijkPlayer.getVideoWidth();

        FrameLayout.LayoutParams layoutParams =
                (FrameLayout.LayoutParams) mPlaying.getLayoutParams();
        layoutParams.height = (int) (ijkPlayer.getVideoHeight() * ratio);
        layoutParams.width = (int) (ijkPlayer.getVideoWidth() * ratio);
        mPlaying.setLayoutParams(layoutParams);

        if(isPlaying)
            ijkPlayer.start();
    }

    private void toLandscape()
    {
        boolean isPlaying = ijkPlayer.isPlaying();
        ijkPlayer.pause();

        FrameLayout.LayoutParams layoutParams =
                (FrameLayout.LayoutParams) mPlaying.getLayoutParams();
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        mPlaying.setLayoutParams(layoutParams);

        if(isPlaying)
            ijkPlayer.start();
    }

    private void synchonizeProgress()
    {
        long currents = (ijkPlayer.getCurrentPosition() / 1000) % 60;
        long currentm = (ijkPlayer.getCurrentPosition() / 1000) / 60;
        long totals = (ijkPlayer.getDuration() / 1000) % 60;
        long totalm = (ijkPlayer.getDuration() / 1000) / 60;

        String time =
                String.format(Locale.getDefault(), "%02d:%02d/%02d:%02d",
                        currentm, currents, totalm, totals);

        mPlayTimeView.setText(time);
        if (isAuto && (ijkPlayer.getDuration() / 1000) != 0) {
            mProgressBar.setProgress((int)
                    (1.0 * mProgressBar.getMax() * ijkPlayer.getCurrentPosition() / ijkPlayer.getDuration()));
        }
    }

    private void stopAutoProgress()
    {
        mProgressHandler.removeCallbacksAndMessages(null);
    }

    private void startAutoProgress()
    {
        mProgressHandler.postDelayed(mRunnable, REFRESH_DELAY);
    }

    private void initIJKPlayer()
    {
        // load native library
        try {
            IjkMediaPlayer.loadLibrariesOnce(null);
            IjkMediaPlayer.native_profileBegin("libijkplayer.so");
        } catch (Exception e) {
            this.finish();
        }

        // init video
        ijkPlayer.setVideoResource(R.raw.bytedance);
        ijkPlayer.setListener(new VideoPlayerListener() {
            @Override
            public void onPrepared(IMediaPlayer iMediaPlayer)
            {
                super.onPrepared(iMediaPlayer);

                // delay a few moment
                // to show the loading UI
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                toPortrait();

                mLoading.setVisibility(View.INVISIBLE);
                mPlaying.setVisibility(View.VISIBLE);

                mHideHandler.postDelayed(mHideRunnable, 3000);
                startAutoProgress();
                iMediaPlayer.start();
            }

            @Override
            public void onCompletion(IMediaPlayer iMediaPlayer)
            {
                super.onCompletion(iMediaPlayer);
                stopAutoProgress();

                mProgressBar.setProgress(0);
                mPauseBtn.setText(getString(R.string.resume));
            }
        });

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (ijkPlayer == null) {
            return;
        }
        if (this.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            // 横屏
            toLandscape();
        } else {
            // 竖屏
            toPortrait();
        }
    }

}
