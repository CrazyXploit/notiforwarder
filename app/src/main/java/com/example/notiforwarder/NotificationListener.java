package com.example.notiforwarder;

import android.app.Notification;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotificationListener extends NotificationListenerService {

    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        android.util.Log.d("NotiFwd", "Listener created");
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        android.util.Log.d("NotiFwd", "Listener connected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null) return;
            if (sbn.isOngoing()) return;
            if (sbn.getPackageName().equals(getPackageName())) return;

            Bundle extras = sbn.getNotification().extras;
            if (extras == null) return;

            CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence textCs  = extras.getCharSequence(Notification.EXTRA_TEXT);
            String title = titleCs == null ? "" : titleCs.toString();
            String text  = textCs  == null ? "" : textCs.toString();
            String appName = getAppName(sbn.getPackageName());

            if (title.isEmpty() && text.isEmpty()) return;

            String message = "\uD83D\uDCF1 " + appName + "\n" +
                             "\uD83D\uDC64 " + title + "\n" +
                             "\uD83D\uDCAC " + text;

            String botToken = prefs.getString("bot_token", "");
            String chatId   = prefs.getString("chat_id", "");

            if (botToken.isEmpty() || chatId.isEmpty()) {
                android.util.Log.w("NotiFwd", "Credentials empty, skipping");
                return;
            }

            android.util.Log.d("NotiFwd", "Forwarding: " + appName + " / " + title);
            MainActivity.telegramSend(botToken, chatId, message);

        } catch (Exception e) {
            android.util.Log.e("NotiFwd", "onNotificationPosted error", e);
        }
    }

    private String getAppName(String packageName) {
        try {
            return getPackageManager()
                .getApplicationLabel(getPackageManager().getApplicationInfo(packageName, 0))
                .toString();
        } catch (Exception e) {
            return packageName;
        }
    }
        }
