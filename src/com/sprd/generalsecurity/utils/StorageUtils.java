package com.sprd.generalsecurity.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;

import java.io.File;
import java.util.List;

public class StorageUtils {

    private static String TAG = "StorageUtils";

    public static String[] convertTotalSize(Context context, long totalSize) {
        String size_lable = Formatter.formatFileSize(context, totalSize);
        String[] sizeLableArray;
        if (size_lable.contains(" ")) {
            sizeLableArray = size_lable.split(" ");
        } else {
            StringBuffer s1 = new StringBuffer(size_lable);
            for (int i = 0; i < size_lable.length(); i++) {
                if ((size_lable.charAt(i) + "").getBytes().length > 1) {
                    s1.insert(i, " ");
                    break;
                }
            }
            sizeLableArray = s1.toString().split(" ");
        }
        return sizeLableArray;
    }

    public static boolean isLauncherApp(PackageManager pm, String packageName) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> allResolveInfoList = pm.queryIntentActivities(intent, 0);
        for (ResolveInfo resolveInfo : allResolveInfoList) {
            final ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (activityInfo == null) {
                continue;
            }
            if (packageName.equals(activityInfo.packageName)) {
                return true;
            }
        }
        return false;
    }

    public static void notifyMediaScanDir(Context context, File dir) {
        Log.i(TAG, "send broadcast to scan dir = " + dir);
        String path = dir.getPath();
        // UNISOC: Bug768591 Change 'android' to 'sprd' due to cts error
        Intent intent = new Intent("sprd.intent.action.MEDIA_SCANNER_SCAN_DIR");
        // UNISOC: Bug852451 background apps can not receive implicit broadcast
        intent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        Bundle bundle = new Bundle();
        bundle.putString("scan_dir_path", path);
        intent.putExtras(bundle);
        context.sendBroadcast(intent);
    }
}