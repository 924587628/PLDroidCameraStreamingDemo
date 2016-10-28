package com.pili.pldroid.streaming.camera.demo;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.pili.pldroid.streaming.CameraStreamingManager;
import com.pili.pldroid.streaming.CameraStreamingSetting;
import com.pili.pldroid.streaming.FrameCapturedCallback;
import com.pili.pldroid.streaming.MicrophoneStreamingSetting;
import com.pili.pldroid.streaming.StreamStatusCallback;
import com.pili.pldroid.streaming.StreamingPreviewCallback;
import com.pili.pldroid.streaming.StreamingProfile;
import com.pili.pldroid.streaming.SurfaceTextureCallback;
import com.pili.pldroid.streaming.camera.demo.gles.FBO;
import com.pili.pldroid.streaming.camera.demo.ui.RotateLayout;
import com.qiniu.android.dns.DnsManager;
import com.qiniu.android.dns.IResolver;
import com.qiniu.android.dns.NetworkInfo;
import com.qiniu.android.dns.http.DnspodFree;
import com.qiniu.android.dns.local.AndroidDnsServer;
import com.qiniu.android.dns.local.Resolver;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

/**
 * Created by jerikc on 15/7/6.
 */
public class StreamingBaseActivity extends Activity implements
        View.OnLayoutChangeListener,//当View的大小等发生改变时
        StreamStatusCallback,//通知流状态改变时
        StreamingPreviewCallback,//预览
        SurfaceTextureCallback,//SurfaceView 渲染回调
        CameraPreviewFrameView.Listener,//
        CameraStreamingManager.StreamingSessionListener,//一次会话
        CameraStreamingManager.StreamingStateListener//流状态改变
{

    private static final String TAG = "StreamingBaseActivity";

    private static final int ZOOM_MINIMUM_WAIT_MILLIS = 33; //ms

    private Context mContext;

    //录制视屏的按钮
    protected Button mShutterButton;

    //是否有声音
    private Button mMuteButton;

    //灯光是否打开
    private Button mTorchBtn;

    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
    }

    //前置摄像头和后置摄像头切换
    private Button mCameraSwitchBtn;

    //捕获当前帧（图片）
    private Button mCaptureFrameBtn;

    //方向的切换  横竖屏的切换
    private Button mEncodingOrientationSwitcherBtn;

    //自定义的带旋转的View
    private RotateLayout mRotateLayout;

    //显示状态
    protected TextView mSatusTextView;
    private TextView mLogTextView;
    private TextView mStreamStatus;

    //录像快门是否按下
    protected boolean mShutterButtonPressed = false;

    //灯光是否打开
    private boolean mIsTorchOn = false;
    //是否有声音
    private boolean mIsNeedMute = false;
    //    private boolean mIsNeedFB = false;
    //方向是否是垂直的
    private boolean isEncOrientationPort = true;

    protected static final int MSG_START_STREAMING = 0;
    protected static final int MSG_STOP_STREAMING = 1;
    private static final int MSG_SET_ZOOM = 2;
    private static final int MSG_MUTE = 3;

    protected String mStatusMsgContent;

    protected String mLogContent = "\n";

    private View mRootView;

    //摄像头流媒体管理类
    protected CameraStreamingManager mCameraStreamingManager;
    protected CameraStreamingSetting mCameraStreamingSetting;
    //扩音器，麦克风
    protected MicrophoneStreamingSetting mMicrophoneStreamingSetting;

    //流媒体资源
    protected StreamingProfile mProfile;
    protected JSONObject mJSONObject;

    //是否准备好
    protected boolean mIsReady = false;

    //当前的缩放值
    private int mCurrentZoom = 0;
    //    缩放的最大值
    private int mMaxZoom = 0;

    private FBO mFBO = new FBO();

    /**
     * 屏幕截图
     */
    private Screenshooter mScreenshooter = new Screenshooter();

    /**
     * 方向开关
     */
    private EncodingOrientationSwitcher mEncodingOrientationSwitcher = new EncodingOrientationSwitcher();

    protected Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_START_STREAMING://0 开始录像
                    new Thread(new Runnable() {
                        @Override
                        protected Object clone() throws CloneNotSupportedException {
                            return super.clone();
                        }

                        @Override
                        public void run() {
                            // disable the shutter button before startStreaming
                            setShutterButtonEnabled(false);
                            boolean res = mCameraStreamingManager.startStreaming();
                            mShutterButtonPressed = true;
                            Log.i(TAG, "res:" + res);
                            if (!res) {
                                mShutterButtonPressed = false;
                                setShutterButtonEnabled(true);
                            }
                            setShutterButtonPressed(mShutterButtonPressed);
                        }
                    }).start();
                    break;
                case MSG_STOP_STREAMING://1.停止录像
                    //按下结束按钮(快门)之前停止
                    // disable the shutter button before stopStreaming
                    setShutterButtonEnabled(false);
                    boolean res = mCameraStreamingManager.stopStreaming();
                    if (!res) {
                        mShutterButtonPressed = true;
                        setShutterButtonEnabled(true);
                    }
                    setShutterButtonPressed(mShutterButtonPressed);
                    break;
                //设置聚焦
                case MSG_SET_ZOOM:
                    mCameraStreamingManager.setZoomValue(mCurrentZoom);
                    break;
                case MSG_MUTE:
                    //
                    mIsNeedMute = !mIsNeedMute;
                    mCameraStreamingManager.mute(mIsNeedMute);
                    updateMuteButtonText();
                    break;
                default:
                    Log.e(TAG, "Invalid message");
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        } else {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }

        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (Config.SCREEN_ORIENTATION == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            isEncOrientationPort = true;
        } else if (Config.SCREEN_ORIENTATION == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            isEncOrientationPort = false;
        }
        //不触发onConfigurationChanged
        setRequestedOrientation(Config.SCREEN_ORIENTATION);

        setContentView(R.layout.activity_camera_streaming);
//
//        SharedLibraryNameHelper.getInstance().renameSharedLibrary(
//                SharedLibraryNameHelper.PLSharedLibraryType.PL_SO_TYPE_AAC,
//                getApplicationInfo().nativeLibraryDir + "/libpldroid_streaming_aac_encoder_v7a.so");
//
//        SharedLibraryNameHelper.getInstance().renameSharedLibrary(
//                SharedLibraryNameHelper.PLSharedLibraryType.PL_SO_TYPE_CORE, "pldroid_streaming_core");
//
//        SharedLibraryNameHelper.getInstance().renameSharedLibrary(
//                SharedLibraryNameHelper.PLSharedLibraryType.PL_SO_TYPE_H264, "pldroid_streaming_h264_encoder_v7a");

        String streamJsonStrFromServer = getIntent().getStringExtra(Config.EXTRA_KEY_STREAM_JSON);
        Log.i(TAG, "streamJsonStrFromServer:" + streamJsonStrFromServer);

        try {
            mJSONObject = new JSONObject(streamJsonStrFromServer);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mContext = this;

        //音频的
        StreamingProfile.AudioProfile aProfile = new StreamingProfile.AudioProfile(44100, 96 * 1024);
        //视频的
        StreamingProfile.VideoProfile vProfile = new StreamingProfile.VideoProfile(30, 1000 * 1024, 48);
        //从一个源代码重新创建。IntelliJ IDEA的类文件
        StreamingProfile.AVProfile avProfile = new StreamingProfile.AVProfile(vProfile, aProfile);

        mProfile = new StreamingProfile();

        StreamingProfile.Stream stream = new StreamingProfile.Stream(mJSONObject);
        mProfile.setStream(stream);
        mProfile.setVideoQuality(StreamingProfile.VIDEO_QUALITY_HIGH3)
                .setAudioQuality(StreamingProfile.AUDIO_QUALITY_MEDIUM2)
//                .setPreferredVideoEncodingSize(960, 544)
                .setEncodingSizeLevel(Config.ENCODING_LEVEL)
                .setEncoderRCMode(StreamingProfile.EncoderRCModes.QUALITY_PRIORITY)
                .setAVProfile(avProfile)
                .setDnsManager(getMyDnsManager())
                .setStreamStatusConfig(new StreamingProfile.StreamStatusConfig(3))
//                .setEncodingOrientation(StreamingProfile.ENCODING_ORIENTATION.PORT)
                .setSendingBufferProfile(new StreamingProfile.SendingBufferProfile(0.2f, 0.8f, 3.0f, 20 * 1000));

        mCameraStreamingSetting = new CameraStreamingSetting();
        mCameraStreamingSetting.setCameraId(Camera.CameraInfo.CAMERA_FACING_BACK)
                .setContinuousFocusModeEnabled(true)
                .setRecordingHint(false)
                .setResetTouchFocusDelayInMs(3000)
//                .setFocusMode(CameraStreamingSetting.FOCUS_MODE_CONTINUOUS_PICTURE)
                .setCameraPrvSizeLevel(CameraStreamingSetting.PREVIEW_SIZE_LEVEL.MEDIUM)
                .setCameraPrvSizeRatio(CameraStreamingSetting.PREVIEW_SIZE_RATIO.RATIO_16_9);
//        mIsNeedFB = true;
        mMicrophoneStreamingSetting = new MicrophoneStreamingSetting();
        mMicrophoneStreamingSetting.setBluetoothSCOEnabled(false);

        initUIs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "tid:" + Thread.currentThread().getId());
        try {
            mCameraStreamingManager.resume();
        } catch (Exception e) {
            Toast.makeText(StreamingBaseActivity.this, "Device open error!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        mIsReady = false;
        mShutterButtonPressed = false;
        mCameraStreamingManager.pause();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraStreamingManager.destroy();
    }

    /**
     * 档快门按下的时候
     *
     * @param pressed
     */
    protected void setShutterButtonPressed(final boolean pressed) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mShutterButtonPressed = pressed;
                mShutterButton.setPressed(pressed);
            }
        });
    }

    /**
     * 设置快门按钮是否可操作
     *
     * @param enable
     */
    protected void setShutterButtonEnabled(final boolean enable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mShutterButton.setFocusable(enable);
                mShutterButton.setClickable(enable);
                mShutterButton.setEnabled(enable);
            }
        });
    }

    /**
     * 开始
     */
    protected void startStreaming() {
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_START_STREAMING), 50);
    }

    /**
     * 结束
     */
    protected void stopStreaming() {
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_STOP_STREAMING), 50);
    }

    /**
     * 录音失败
     *
     * @param err
     * @return
     */
    @Override
    public boolean onRecordAudioFailedHandled(int err) {
        mCameraStreamingManager.updateEncodingType(CameraStreamingManager.EncodingType.SW_VIDEO_CODEC);
        mCameraStreamingManager.startStreaming();
        return true;
    }

    /**
     * 重新开始
     *
     * @param err
     * @return
     */
    @Override
    public boolean onRestartStreamingHandled(int err) {
        Log.i(TAG, "onRestartStreamingHandled");
        return mCameraStreamingManager.startStreaming();
    }

    /**
     * 选择的预览大小
     *
     * @param list
     * @return
     */
    @Override
    public Camera.Size onPreviewSizeSelected(List<Camera.Size> list) {
        Camera.Size size = null;
//        if (list != null) {
//            for (Camera.Size s : list) {
//                size = s;
//                Log.i(TAG, "w:" + s.width + ", h:" + s.height);
//                break;
//                if (s.height < 480) {
//                    continue;
//                } else {
//                    size = s;
//                    break;
//                }
//            }
//        }
//        Log.e(TAG, "selected size :" + size.width + "x" + size.height);
        return size;
    }

    /**
     * 点击单次时回调
     *
     * @param e
     * @return
     */
    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        Log.i(TAG, "onSingleTapUp X:" + e.getX() + ",Y:" + e.getY());

        if (mIsReady) {
            setFocusAreaIndicator();
            //点击的位置
            mCameraStreamingManager.doSingleTapUp((int) e.getX(), (int) e.getY());
            return true;
        }
        return false;
    }

    /**
     * 当焦点（缩放比例） 变化时
     *
     * @param factor
     * @return
     */
    @Override
    public boolean onZoomValueChanged(float factor) {
        if (mIsReady && mCameraStreamingManager.isZoomSupported()) {
            mCurrentZoom = (int) (mMaxZoom * factor);
            mCurrentZoom = Math.min(mCurrentZoom, mMaxZoom);
            mCurrentZoom = Math.max(0, mCurrentZoom);

            Log.d(TAG, "zoom ongoing, scale: " + mCurrentZoom + ",factor:" + factor + ",maxZoom:" + mMaxZoom);
            if (!mHandler.hasMessages(MSG_SET_ZOOM)) {
                mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_SET_ZOOM), ZOOM_MINIMUM_WAIT_MILLIS);
                return true;
            }
        }
        return false;
    }

    /**
     * 换相机
     */
    private Switcher mSwitcher = new Switcher();

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        Log.i(TAG, "view!!!!:" + v);
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {

    }

    @Override
    public boolean onPreviewFrame(byte[] bytes, int width, int height) {
//        deal with the yuv data.
//        long start = System.currentTimeMillis();
//        for (int i = 0; i < bytes.length; i++) {
//            bytes[i] = 0x00;
//        }
//        Log.i(TAG, "old onPreviewFrame cost :" + (System.currentTimeMillis() - start));
        return true;
    }

    //SurfaceView 创建
    @Override
    public void onSurfaceCreated() {
        Log.i(TAG, "onSurfaceCreated");
        mFBO.initialize(this);
    }

    //SurfaceView 改变
    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged width:" + width + ",height:" + height);
        mFBO.updateSurfaceSize(width, height);
    }

    //SurfaceView 销毁
    @Override
    public void onSurfaceDestroyed() {
        Log.i(TAG, "onSurfaceDestroyed");
        mFBO.release();
    }

    //画每一帧
    @Override
    public int onDrawFrame(int texId, int texWidth, int texHeight) {
        // newTexId should not equal with texId. texId is from the SurfaceTexture.
        // Otherwise, there is no filter effect.
        int newTexId = mFBO.drawFrame(texId, texWidth, texHeight);
//        Log.i(TAG, "onDrawFrame texId:" + texId + ",newTexId:" + newTexId + ",texWidth:" + texWidth + ",texHeight:" + texHeight);
        return newTexId;
    }

    /**
     * 通知流媒体的状态改变
     *
     * @param streamStatus
     */
    @Override
    public void notifyStreamStatusChanged(final StreamingProfile.StreamStatus streamStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStreamStatus.setText("bitrate:" + streamStatus.totalAVBitrate / 1024 + " kbps"//每秒传输的帧速
                        + "\naudio:" + streamStatus.audioFps + " fps"
                        + "\nvideo:" + streamStatus.videoFps + " fps");
            }
        });
    }

    //切换摄像头
    private class Switcher implements Runnable {
        @Override
        public void run() {
            mCameraStreamingManager.switchCamera();
        }
    }

    /**
     * 方向开关
     */
    private class EncodingOrientationSwitcher implements Runnable {

        @Override
        public void run() {
            Log.i(TAG, "isEncOrientationPort:" + isEncOrientationPort);
            isEncOrientationPort = !isEncOrientationPort;
            //流媒体的编码方向
            mProfile.setEncodingOrientation(isEncOrientationPort ? StreamingProfile.ENCODING_ORIENTATION.PORT : StreamingProfile.ENCODING_ORIENTATION.LAND);
            //给管理类设置需要操作的流媒体对象
            mCameraStreamingManager.setStreamingProfile(mProfile);
            /*
            *   这一活动的期望的方向变化。如果活动目前在前台或以其他方式影响屏幕的方向,屏幕将立即被改变(可能导致活动重新启动)。
            *   否则,这将用于下次的活动是visibleChange所需的方向活动。
            *   如果活动目前在前台或以其他方式影响屏幕的方向,屏幕将立即被改变(可能导致活动重新启动)。
            *   否则,这将是下一次使用活动是可见的
             */
            setRequestedOrientation(isEncOrientationPort ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            //通知Activity的方向改变，刷新界面
            mCameraStreamingManager.notifyActivityOrientationChanged();
            //更新放心按钮上的文字
            updateOrientationBtnText();
            Toast.makeText(StreamingBaseActivity.this, Config.HINT_ENCODING_ORIENTATION_CHANGED,
                    Toast.LENGTH_SHORT).show();
            Log.i(TAG, "EncodingOrientationSwitcher -");
        }
    }

    /**
     * 截图
     */
    private class Screenshooter implements Runnable {
        @Override
        public void run() {
            final String fileName = "PLStreaming_" + System.currentTimeMillis() + ".jpg";

            /**
             * 在 Camera 正常预览之后，可以正常进行截帧功能。

             * 在调用 captureFrame 的时候，您需要传入 width 和 height，
             * 以及 FrameCapturedCallback，如果传入的 width 或者 height 小于等于 0，SDK 返回的 Bitmap
             * 将会是预览的尺寸 。SDK 完成截帧之后，会回调 onFrameCaptured，并将结果以参数的形式返回给调用者。
             */
            mCameraStreamingManager.captureFrame(0, 0, new FrameCapturedCallback() {
                private Bitmap bitmap;

                @Override
                public void onFrameCaptured(Bitmap bmp) {
                    bitmap = bmp;

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                saveToSDCard(fileName, bitmap);
                            } catch (IOException e) {
                                e.printStackTrace();
                            } finally {
                                if (bitmap != null) {
                                    bitmap.recycle();
                                    bitmap = null;
                                }
                            }
                        }
                    }).start();
                }
            });
        }
    }

    /**
     * 手电筒
     * 设置灯光是否可操作(打开还是关闭)
     *
     * @param enabled
     */
    private void setTorchEnabled(final boolean enabled) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String flashlight = enabled ? getString(R.string.flash_light_off) : getString(R.string.flash_light_on);
                mTorchBtn.setText(flashlight);
            }
        });
    }

    /**
     * 当状态改变时
     *
     * @param state
     * @param extra
     */
    @Override
    public void onStateChanged(final int state, Object extra) {

        switch (state) {
            case CameraStreamingManager.STATE.PREPARING://正在准备
                mStatusMsgContent = getString(R.string.string_state_preparing);
                break;
            case CameraStreamingManager.STATE.READY://准备好
                mIsReady = true;
                //获取最大的缩放的最大值
                mMaxZoom = mCameraStreamingManager.getMaxZoom();
                mStatusMsgContent = getString(R.string.string_state_ready);
                // start streaming when READY
                startStreaming();
                break;
            case CameraStreamingManager.STATE.CONNECTING://正在连接
                mStatusMsgContent = getString(R.string.string_state_connecting);
                break;
            case CameraStreamingManager.STATE.STREAMING://开始录制
                mStatusMsgContent = getString(R.string.string_state_streaming);
                setShutterButtonEnabled(true);
                setShutterButtonPressed(true);
                break;
            case CameraStreamingManager.STATE.SHUTDOWN://按下快门键
                mStatusMsgContent = getString(R.string.string_state_ready);
                setShutterButtonEnabled(true);
                setShutterButtonPressed(false);
                break;
            case CameraStreamingManager.STATE.IOERROR://传输错误
                mLogContent += "IOERROR\n";
                mStatusMsgContent = getString(R.string.string_state_ready);
                setShutterButtonEnabled(true);
                break;
            case CameraStreamingManager.STATE.UNKNOWN:
                mStatusMsgContent = getString(R.string.string_state_ready);
                break;
            case CameraStreamingManager.STATE.SENDING_BUFFER_EMPTY:
                break;
            case CameraStreamingManager.STATE.SENDING_BUFFER_FULL:
                break;
            case CameraStreamingManager.STATE.AUDIO_RECORDING_FAIL:
                break;
            case CameraStreamingManager.STATE.OPEN_CAMERA_FAIL:
                Log.e(TAG, "Open Camera Fail. id:" + extra);
                break;
            case CameraStreamingManager.STATE.DISCONNECTED:
                mLogContent += "DISCONNECTED\n";
                break;
            case CameraStreamingManager.STATE.INVALID_STREAMING_URL:
                Log.e(TAG, "Invalid streaming url:" + extra);
                break;
            case CameraStreamingManager.STATE.CAMERA_SWITCHED://相机改变
//                mShutterButtonPressed = false;
                if (extra != null) {
                    Log.i(TAG, "current camera id:" + (Integer) extra);
                }
                Log.i(TAG, "camera switched");
                final int currentCamId = (Integer) extra;
                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateCameraSwitcherButtonText(currentCamId);
                    }
                });
                break;
            case CameraStreamingManager.STATE.TORCH_INFO://灯光状态
                if (extra != null) {
                    final boolean isSupportedTorch = (Boolean) extra;
                    Log.i(TAG, "isSupportedTorch=" + isSupportedTorch);
                    this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isSupportedTorch) {
                                mTorchBtn.setVisibility(View.VISIBLE);
                            } else {
                                mTorchBtn.setVisibility(View.GONE);
                            }
                        }
                    });
                }
                break;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mLogTextView != null) {
                    mLogTextView.setText(mLogContent);
                }
                mSatusTextView.setText(mStatusMsgContent);
            }
        });
    }

    @Override
    public boolean onStateHandled(final int state, Object extra) {
        switch (state) {
            case CameraStreamingManager.STATE.SENDING_BUFFER_HAS_FEW_ITEMS:
                return false;
            case CameraStreamingManager.STATE.SENDING_BUFFER_HAS_MANY_ITEMS:
                return false;
        }
        return false;
    }

    private void initUIs() {
        mRootView = findViewById(R.id.content);
        mRootView.addOnLayoutChangeListener(this);

        mMuteButton = (Button) findViewById(R.id.mute_btn);
        mShutterButton = (Button) findViewById(R.id.toggleRecording_button);
        mTorchBtn = (Button) findViewById(R.id.torch_btn);
        mCameraSwitchBtn = (Button) findViewById(R.id.camera_switch_btn);
        mCaptureFrameBtn = (Button) findViewById(R.id.capture_btn);
        mSatusTextView = (TextView) findViewById(R.id.streamingStatus);

        mLogTextView = (TextView) findViewById(R.id.log_info);
        mStreamStatus = (TextView) findViewById(R.id.stream_status);

        mMuteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mHandler.hasMessages(MSG_MUTE)) {
                    mHandler.sendEmptyMessage(MSG_MUTE);
                }
            }
        });

        mShutterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mShutterButtonPressed) {
                    stopStreaming();
                } else {
                    startStreaming();
                }
            }
        });

        mTorchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (!mIsTorchOn) {
                            mIsTorchOn = true;
                            mCameraStreamingManager.turnLightOn();
                        } else {
                            mIsTorchOn = false;
                            mCameraStreamingManager.turnLightOff();
                        }
                        setTorchEnabled(mIsTorchOn);
                    }
                }).start();
            }
        });

        //切换摄像头
        mCameraSwitchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mHandler.removeCallbacks(mSwitcher);
                mHandler.postDelayed(mSwitcher, 100);
            }
        });

        //抓图
        mCaptureFrameBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mHandler.removeCallbacks(mScreenshooter);
                mHandler.postDelayed(mScreenshooter, 100);
            }
        });


        mEncodingOrientationSwitcherBtn = (Button) findViewById(R.id.orientation_btn);
        mEncodingOrientationSwitcherBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.removeCallbacks(mEncodingOrientationSwitcher);
                mHandler.post(mEncodingOrientationSwitcher);
            }
        });
        initButtonText();
    }

    private void initButtonText() {
        updateCameraSwitcherButtonText(mCameraStreamingSetting.getReqCameraId());
        mCaptureFrameBtn.setText("Capture");
        updateMuteButtonText();
        updateOrientationBtnText();
    }

    /**
     * 更新方向按钮上的文字，横屏或者竖屏
     */
    private void updateOrientationBtnText() {
        if (isEncOrientationPort) {
            mEncodingOrientationSwitcherBtn.setText("Land");
        } else {
            mEncodingOrientationSwitcherBtn.setText("Port");
        }
    }

    /**
     * 设置焦点指示器 在点击的地方
     */
    protected void setFocusAreaIndicator() {
        if (mRotateLayout == null) {
            mRotateLayout = (RotateLayout) findViewById(R.id.focus_indicator_rotate_layout);
            mCameraStreamingManager.setFocusAreaIndicator(mRotateLayout,
                    mRotateLayout.findViewById(R.id.focus_indicator));
        }
    }

    /**
     * 是否静音
     */
    private void updateMuteButtonText() {
        if (mMuteButton != null) {
            mMuteButton.setText(mIsNeedMute ? "Unmute" : "Mute");
        }
    }

    /**
     * 更新（是前置摄像头或是后置摄像头）上的文字
     *
     * @param camId
     */
    private void updateCameraSwitcherButtonText(int camId) {
        if (mCameraSwitchBtn == null) {
            return;
        }
        if (camId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            mCameraSwitchBtn.setText("Back");
        } else {
            mCameraSwitchBtn.setText("Front");
        }
    }

    /**
     * 爆粗抓取的图片到SD卡
     *
     * @param filename
     * @param bmp
     * @throws IOException
     */
    private void saveToSDCard(String filename, Bitmap bmp) throws IOException {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File file = new File(Environment.getExternalStorageDirectory(), filename);
            BufferedOutputStream bos = null;
            try {
                bos = new BufferedOutputStream(new FileOutputStream(file));
                bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
                bmp.recycle();
                bmp = null;
                Log.d("sunxy","保存成功 ！");
                Log.d("sunxy",""+file.getAbsolutePath());
            } finally {
                if (bos != null) bos.close();
            }

            final String info = "Save frame to:" + Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + filename;

            Log.d("TAG", "info  = " + info);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, info, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    /**
     * 接入的网络频段
     *
     * @return
     */
    private static DnsManager getMyDnsManager() {
        IResolver r0 = new DnspodFree();
        IResolver r1 = AndroidDnsServer.defaultResolver();
        IResolver r2 = null;
        try {
            r2 = new Resolver(InetAddress.getByName("119.29.29.29"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return new DnsManager(NetworkInfo.normal, new IResolver[]{r0, r1, r2});
    }
}
