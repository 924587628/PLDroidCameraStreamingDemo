package com.pili.pldroid.streaming.camera.demo.gles;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import com.pili.pldroid.streaming.camera.demo.Config;
import com.pili.pldroid.streaming.camera.demo.R;
import com.pili.pldroid.streaming.camera.demo.filter.CameraFilterBeauty;

/**
 * Created by jerikc on 16/2/23.
 */
public class FBO {
    private static final String TAG = "FBO";

    //false
    private boolean mEnable = Config.FILTER_ENABLED;

    private int mSurfaceWidth;
    private int mSurfaceHeight;

    /*
            ACV文件扩展名信息
                 一种图像处理软体把曲线存档了 (奥多比)文件。
     */
    private static int[] mCurveArrays = new int[]{
            R.raw.cross_1, R.raw.cross_2, R.raw.cross_3, R.raw.cross_4, R.raw.cross_5,
            R.raw.cross_6, R.raw.cross_7, R.raw.cross_8, R.raw.cross_9, R.raw.cross_10,
            R.raw.cross_11,
    };
    private int mCurveIndex = 10;

    // Used for off-screen rendering(渲染).
    private int mOffscreenTexture;
    //帧缓冲
    private int mFramebuffer;

    //全景区域
    private FullFrameRect mFullScreen;

    /**
     * 更新SurfaceView的大小
     * @param width
     * @param height
     */
    public void updateSurfaceSize(int width, int height) {
        if (!mEnable) {
            return;
        }

        mSurfaceWidth = width;
        mSurfaceHeight = height;
    }

    /**
     * 初始化
     * @param context
     */
    public void initialize(Context context) {
        if (!mEnable) {
            return;
        }

        if (mFullScreen != null) {
            mFullScreen.release(false);
        }

        /**
         * Create a new full frame renderer with beauty filter.
         * There are two another filter, you can have a try.
         */
//        mFullScreen = new FullFrameRect(new CameraFilterToneCurve(context,
//                context.getResources().openRawResource(mCurveArrays[mCurveIndex])));
//        mFullScreen = new FullFrameRect(new CameraFilterMosaic(context));
        mFullScreen = new FullFrameRect(new CameraFilterBeauty(context));

        mOffscreenTexture = 0;
    }

    /**
     * 释放
     * 1. 全屏渲染
     * 2. 释放应用（摄像头）
     */
    public void release() {
        if (!mEnable) {
            return;
        }
        mFullScreen.release(true);
    }

    /**
     * Prepares the off-screen framebuffer.
     *
     * 准备帧缓冲
     */
    private void prepareFramebuffer(int width, int height) {
        //检查看 开始 open GLES 是否有错误
        GlUtil.checkGlError("start");
        int[] values = new int[1];

        // Create a texture(渲染) object and bind(绑定) it.  This will be the color buffer.
        GLES20.glGenTextures(1, values, 0);
        GlUtil.checkGlError("glGenTextures");
        mOffscreenTexture = values[0];   // expected > 0
        Log.i(TAG, "prepareFramebuffer mOffscreenTexture:" + mOffscreenTexture);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mOffscreenTexture);
        GlUtil.checkGlError("glBindTexture");

        // Create texture storage.
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GlUtil.checkGlError("glTexParameter");

        // Create framebuffer object and bind it.
        GLES20.glGenFramebuffers(1, values, 0);
        GlUtil.checkGlError("glGenFramebuffers");
        mFramebuffer = values[0];    // expected > 0

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFramebuffer);
        GlUtil.checkGlError("glBindFramebuffer " + mFramebuffer);

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mOffscreenTexture, 0);

        // See if GLES is happy with all this.
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer not complete, status=" + status);
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        // Switch back to the default framebuffer.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GlUtil.checkGlError("glBindFramebuffer");
    }

    /**
     * 绘制 帧
     * @param texId
     * @param texWidth
     * @param texHeight
     * @return
     */
    public int drawFrame(int texId, int texWidth, int texHeight) {
        if (!mEnable) {
            return texId;
        }
        GLES20.glViewport(0, 0, texWidth, texHeight);
        if (mOffscreenTexture == 0) {
            prepareFramebuffer(texWidth, texHeight);
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFramebuffer);

        mFullScreen.getFilter().setTextureSize(texWidth, texHeight);
        mFullScreen.drawFrame(texId);

        // Blit to display.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);
        return mOffscreenTexture;
    }
}
