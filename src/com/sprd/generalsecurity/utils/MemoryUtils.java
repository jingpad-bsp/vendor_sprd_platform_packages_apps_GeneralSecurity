package com.sprd.generalsecurity.utils;

import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;

import java.text.NumberFormat;
import java.text.ParseException;

public final class MemoryUtils {
    private static final String TAG = "Utils";
    private static final String[] mPersistentServiceProcesses = {"com.greenpoint.android.mc10086.activity"};
    /** UNISOC: Bug 1162010 memory management list shows 2 browsers @{ */
    private static final String[] mBlackListProcesses = {"com.android.webview:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0"};
    /** @} */
    public static void handleLoadingContainer(View loading,
            View doneLoading, boolean done, boolean animate) {
        setViewShown(loading, !done, animate);
        setViewShown(doneLoading, done, animate);
    }

    private static void setViewShown(final View view, boolean shown,
            boolean animate) {
        Log.i(TAG," view="+view + " ; shown="+shown+" ;animate="+animate);
        if (animate) {
            Animation animation = AnimationUtils.loadAnimation(view
                    .getContext(), shown ? android.R.anim.fade_in
                    : android.R.anim.fade_out);
            if (shown) {
                view.setVisibility(View.VISIBLE);
            } else {
                animation.setAnimationListener(new AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        view.setVisibility(View.INVISIBLE);
                    }
                });
            }
            view.startAnimation(animation);
        } else {
            view.clearAnimation();
            view.setVisibility(shown ? View.VISIBLE : View.INVISIBLE);
        }
    }
    /** Formats the ratio of amount/total as a percentage. */
    public static String formatPercentage(long amount, long total) {
        return formatPercentage(((double) amount) / total);
    }

    /** Formats an integer from 0..100 as a percentage. */
    public static String formatPercentage(int percentage) {
        return formatPercentage(((double) percentage) / 100.0);
    }

    /** Formats a double from 0.0..1.0 as a percentage. */
    private static String formatPercentage(double percentage) {
      return NumberFormat.getPercentInstance().format(percentage);
    }

    public static int getPicId(int picCount, String per) {
        String p = per.substring(0, per.length() - 1);
        Log.i(TAG, "percent string:"+p);

        /**UNISOC: modify for bug1056871  @{*/
        double percent = 0;
        try {
            percent = NumberFormat.getInstance().parse(p).doubleValue()/100.0;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        /** @} */

        Log.i(TAG, "percent:"+percent+"    picCount:"+picCount);
        int id = (int) Math.floor(percent * picCount);
        Log.i(TAG, "\t\tid:"+id);
        if (percent >= 1) {
            return picCount - 1;
        } else if (percent <= 0) {
            return 0;
        } else if (id == picCount-1) {
            return id - 1;
        } else {
            return id;
        }
    }

    /* UNISOC: Bug 674774 keep mc10086 from killing and cleaning @{ */
    public static boolean isPersistentServiceProcess(String process) {
        Log.i(TAG, "process = " + process);
        if (process != null) {
            for (int i = 0; i < mPersistentServiceProcesses.length; i++) {
                if (process.contains(mPersistentServiceProcesses[i])) {
                    return true;
                }
            }
        }
        return false;
    }
    /* @} */

    /** UNISOC: Bug 1162010 memory management list shows 2 browsers @{ */
    public static boolean isBlackListProcess(String process) {
        Log.i(TAG, "isBlackListProcess = " + process);
        if (process != null) {
            for (int i = 0; i < mBlackListProcesses.length; i++) {
                if (process.contains(mBlackListProcesses[i])) {
                    return true;
                }
            }
        }
        return false;
    }
    /** @} */

}

