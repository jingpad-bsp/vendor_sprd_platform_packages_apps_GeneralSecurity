package com.sprd.generalsecurity.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import com.sprd.generalsecurity.R;

public class NotificationChannelsUtil {
    public static String DEFAULT_CHANNEL = "GS_DEFAULT_CHANNEL";

    private NotificationChannelsUtil() {}

    public static void createDefaultChannel(Context context) {
        final NotificationManager nm = context.getSystemService(NotificationManager.class);
        final NotificationChannel channel = new NotificationChannel(DEFAULT_CHANNEL,
                context.getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(channel);
    }
}