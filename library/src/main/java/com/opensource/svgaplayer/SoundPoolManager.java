package com.opensource.svgaplayer;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;

import com.opensource.svgaplayer.entities.SVGAAudioEntity;
import com.opensource.svgaplayer.proto.AudioEntity;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: newSalton@outlook.com
 * Date: 2019/1/22 3:50 PM
 * ModifyTime: 3:50 PM
 * Description:
 */
public enum SoundPoolManager implements SoundPool.OnLoadCompleteListener {
    INSTANCE;

    interface AudioLoadCallback {
        void onLoadSuccess(List<AudioDataSource> mAudioDataSources);

        void onLoadError(int type);

        int TYPE_NULL = 0;
        int TYPE_EX = 1;
        int TYPE_TIME_OUT = 2;
    }

    private SoundPool mSoundPool;
    HashMap<AudioEntity, File> mEntities = new HashMap<>();
    private List<AudioDataSource> mAudioDataSources = new ArrayList<>();
    private Handler mCheckHandler = new Handler(Looper.getMainLooper());
    private int DEFAULT_CHECK_TIME = 5000;
    private boolean handleTimeout = false;
    private AudioLoadCallback mAudioLoadCallback;

    public void load(HashMap<AudioEntity, File> entities, @NotNull AudioLoadCallback audioLoadCallback) {
        if (entities == null) {
            audioLoadCallback.onLoadError(AudioLoadCallback.TYPE_NULL);
            return;
        }
        try {
            mEntities = entities;
            mAudioLoadCallback = audioLoadCallback;
            handleTimeout = false;
            mAudioDataSources = new ArrayList<>();
            createSoundPoolIfNeeded();
            mCheckHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!handleTimeout) {
                        mAudioLoadCallback.onLoadError(AudioLoadCallback.TYPE_TIME_OUT);
                    }
                }
            }, DEFAULT_CHECK_TIME);
            for (Map.Entry<AudioEntity, File> entry : entities.entrySet()) {
                AudioEntity audioEntity = entry.getKey();
                File musicFile = entry.getValue();
                FileInputStream fis = new FileInputStream(musicFile);
                long totalTime = 0;
                long startTime = 0;
                if (audioEntity != null) {
                    if (audioEntity.totalTime != null) {
                        totalTime = audioEntity.totalTime;
                    }
                    if (audioEntity.startTime != null) {
                        startTime = audioEntity.startTime;
                    }
                }
                if (startTime != 0 && totalTime != 0) {
                    int soundId = mSoundPool.load(fis.getFD(), startTime / totalTime * fis.available(), fis.available(), 1);
                    AudioDataSource source = new AudioDataSource();
                    source.soundId = soundId;
                    source.isLoadSuccess = false;
                    source.svgaAudioEntity = new SVGAAudioEntity(audioEntity);
                    source.svgaAudioEntity.setSoundID(soundId);
                    mAudioDataSources.add(source);
                } else {
                    int soundId = mSoundPool.load(fis.getFD(), 0, fis.available(), 1);
                    AudioDataSource source = new AudioDataSource();
                    source.soundId = soundId;
                    source.isLoadSuccess = false;
                    source.svgaAudioEntity = new SVGAAudioEntity(audioEntity);
                    source.svgaAudioEntity.setSoundID(soundId);
                    mAudioDataSources.add(source);
                }
                fis.close();
            }
        } catch (Exception e) {
            handleTimeout = true;
            mAudioLoadCallback.onLoadError(AudioLoadCallback.TYPE_EX);
            e.printStackTrace();
        }
    }

    /**
     * 创建SoundPool ，注意 api 等级
     */
    private void createSoundPoolIfNeeded() {
        if (mSoundPool == null) {
            // 5.0 及 之后
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                mSoundPool = new SoundPool.Builder()
                        .setMaxStreams(16)
                        .setAudioAttributes(audioAttributes)
                        .build();
            } else { // 5.0 以前
                //创建SoundPool
                mSoundPool = new SoundPool(16, AudioManager.STREAM_MUSIC, 0);

            }
            mSoundPool.setOnLoadCompleteListener(this); // 设置加载完成监听
        }
    }


    @Override
    public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
        boolean isLoadAllComplete = true;
        for (AudioDataSource item : mAudioDataSources) {
            if (item.soundId == sampleId && status == 0) {
                item.isLoadSuccess = true;
            }
            if (item.isLoadSuccess) {
                isLoadAllComplete &= item.isLoadSuccess;
            }
        }
        if (isLoadAllComplete) {
            handleTimeout = true;
            mAudioLoadCallback.onLoadSuccess(mAudioDataSources);
        }
    }

    public int play(int soundId) {
        for (AudioDataSource item : mAudioDataSources) {
            if (item.soundId == soundId) {
                return mSoundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
            }
        }
        return 0;
    }

    public void stop(int playId) {
        mSoundPool.stop(playId);
    }

    /**
     * 释放资源
     */
    public void releaseSoundPool() {
        if (mSoundPool != null) {
            mSoundPool.autoPause();
            for (AudioDataSource item : mAudioDataSources) {
                mSoundPool.unload(item.soundId);
            }
//            mSoundPool.release();
//            mSoundPool = null;
        }
    }

}
