package com.pili.pldroid.streaming.camera.demo;

import android.os.Bundle;

import com.pili.pldroid.streaming.CameraStreamingManager;
import com.pili.pldroid.streaming.CameraStreamingManager.EncodingType;
import com.pili.pldroid.streaming.widget.AspectFrameLayout;

/**
 * Created by jerikc on 15/10/29.
 * 硬件
 */
public class HWCodecCameraStreamingActivity extends StreamingBaseActivity {
    private static final String TAG = "HWCodecCameraStreaming";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AspectFrameLayout afl = (AspectFrameLayout) findViewById(R.id.cameraPreview_afl);
        afl.setShowMode(AspectFrameLayout.SHOW_MODE.REAL);
        //摄像预览View
        CameraPreviewFrameView cameraPreviewFrameView =
                (CameraPreviewFrameView) findViewById(R.id.cameraPreview_surfaceView);
        cameraPreviewFrameView.setListener(this);

        mCameraStreamingManager = new CameraStreamingManager(this, afl, cameraPreviewFrameView,
                EncodingType.HW_VIDEO_WITH_HW_AUDIO_CODEC); // hw codec

        //初始化
        mCameraStreamingManager.prepare(mCameraStreamingSetting, mProfile);

        //设置流媒体状态监听器
        mCameraStreamingManager.setStreamingStateListener(this);
        //SurfaceView监听器
        mCameraStreamingManager.setSurfaceTextureCallback(this);
        //流媒体回话监听器
        mCameraStreamingManager.setStreamingSessionListener(this);
//        mCameraStreamingManager.setNativeLoggingEnabled(false);
        //流媒体状态回调
        mCameraStreamingManager.setStreamStatusCallback(this);
        // update the StreamingProfile
//        mProfile.setStream(new Stream(mJSONObject1));
//        mCameraStreamingManager.setStreamingProfile(mProfile);

//        设置焦点指示器 在点击的地方 焦点在哪里
        setFocusAreaIndicator();

    }
}
