package com.pili.pldroid.streaming.camera.demo.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;

import com.pili.pldroid.streaming.camera.demo.R;
import com.pili.pldroid.streaming.ui.FocusIndicator;

// A view that indicates the focus area or the metering area.

/**
 * 显示或者指示一个焦点区域或者计量区域
 */
public class FocusIndicatorRotateLayout extends RotateLayout implements FocusIndicator {

    private static final String TAG = "FocusIndicatorLayout";

    // Sometimes continuous autofucus starts and stops several times quickly.
    // These states are used to make sure the animation is run for at least some
    // time.
    private int mState;
    //空闲的
    private static final int STATE_IDLE = 0;
    //持有焦点
    private static final int STATE_FOCUSING = 1;
    //结束
    private static final int STATE_FINISHING = 2;

    //结束 置为空闲状态
    private Runnable mDisappear = new Disappear();
    //结束活动 刷新界面
    private Runnable mEndAction = new EndAction();

    //时长
    private static final int SCALING_UP_TIME = 1000;
    //时长
    private static final int SCALING_DOWN_TIME = 200;
    //多长时间消失、超时
    private static final int DISAPPEAR_TIMEOUT = 200;

    public FocusIndicatorRotateLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private void setDrawable(int resid) {
        mChild.setBackgroundDrawable(getResources().getDrawable(resid));
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void showStart() {
        Log.i(TAG, "showStart");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return;
        }
        if (mState == STATE_IDLE) {
            setDrawable(R.drawable.ic_focus_focusing);

            /*
                animate()的返回值是ViewPropertyAnimator
                这个类允许自动和优化选择属性视图对象的动画
             */
            animate().withLayer().setDuration(SCALING_UP_TIME)
                    .scaleX(1.5f).scaleY(1.5f);
            mState = STATE_FOCUSING;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void showSuccess(boolean timeout) {
        Log.i(TAG, "showSuccess");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return;
        }
        if (mState == STATE_FOCUSING) {
            setDrawable(R.drawable.ic_focus_focused);
            animate().withLayer().setDuration(SCALING_DOWN_TIME).scaleX(1f)
                    .scaleY(1f).withEndAction(timeout ? mEndAction : null);
            mState = STATE_FINISHING;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void showFail(boolean timeout) {
        Log.i(TAG, "showFail");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return;
        }
        if (mState == STATE_FOCUSING) {
            setDrawable(R.drawable.ic_focus_failed);
            animate().withLayer().setDuration(SCALING_DOWN_TIME).scaleX(1f)
                    .scaleY(1f).withEndAction(timeout ? mEndAction : null);
            mState = STATE_FINISHING;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void clear() {
        Log.i(TAG, "clear");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return;
        }
        animate().cancel();
        removeCallbacks(mDisappear);
        mDisappear.run();
        setScaleX(1f);
        setScaleY(1f);
    }

    private class EndAction implements Runnable {
        @Override
        public void run() {
            // Keep the focus indicator for some time.
            postDelayed(mDisappear, DISAPPEAR_TIMEOUT);
        }
    }

    private class Disappear implements Runnable {
        @Override
        public void run() {
            mChild.setBackgroundDrawable(null);
            mState = STATE_IDLE;
        }
    }
}