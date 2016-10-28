package com.pili.pldroid.streaming.camera.demo;

import android.os.Bundle;

import com.pili.pldroid.streaming.CameraStreamingManager;
import com.pili.pldroid.streaming.CameraStreamingManager.EncodingType;
import com.pili.pldroid.streaming.widget.AspectFrameLayout;

/**
 * Created by jerikc on 15/10/29.
 * 软件
 */
public class SWCodecCameraStreamingActivity extends StreamingBaseActivity {
    private static final String TAG = "SWCodecCameraStreaming";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AspectFrameLayout afl = (AspectFrameLayout) findViewById(R.id.cameraPreview_afl);
        afl.setShowMode(AspectFrameLayout.SHOW_MODE.REAL);
        //摄像预览View
        CameraPreviewFrameView cameraPreviewFrameView =
                (CameraPreviewFrameView) findViewById(R.id.cameraPreview_surfaceView);
        //设置点击屏幕时，聚焦点改变，。
        // 焦点值改变时
        cameraPreviewFrameView.setListener(this);

        //摄像头的管理类
        mCameraStreamingManager = new CameraStreamingManager(this, afl, cameraPreviewFrameView,
                EncodingType.SW_VIDEO_WITH_SW_AUDIO_CODEC);  // soft codec

        //初始化 摄像头流媒体的设置，麦克风、扩音器的设置，流媒体对象的设置
        mCameraStreamingManager.prepare(mCameraStreamingSetting, mMicrophoneStreamingSetting, mProfile);
        mCameraStreamingManager.setStreamingStateListener(this);
        mCameraStreamingManager.setStreamingPreviewCallback(this);
        mCameraStreamingManager.setSurfaceTextureCallback(this);
        mCameraStreamingManager.setStreamingSessionListener(this);
        mCameraStreamingManager.setStreamStatusCallback(this);

        // update the StreamingProfile
//        mProfile.setStream(new Stream(mJSONObject1));
//        mCameraStreamingManager.setStreamingProfile(mProfile);
//        mCameraStreamingManager.setNativeLoggingEnabled(false);

//        设置焦点指示器 在点击的地方 焦点在哪里
        setFocusAreaIndicator();
    }
}
