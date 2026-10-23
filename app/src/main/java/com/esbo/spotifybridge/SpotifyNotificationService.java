
package com.esbo.spotifybridge;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class SpotifyNotificationService
        extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!"com.spotify.music".equals(sbn.getPackageName())) {
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) {
            return;
        }

        Bundle extras = notification.extras;

        CharSequence titleValue =
                extras.getCharSequence(Notification.EXTRA_TITLE);

        CharSequence artistValue =
                extras.getCharSequence(Notification.EXTRA_TEXT);

        String title = titleValue == null
                ? "" : titleValue.toString();

        String artist = artistValue == null
                ? "" : artistValue.toString();

        String state = "PLAYING";

        getSharedPreferences("esbo_music", MODE_PRIVATE)
                .edit()
                .putString("title", title)
                .putString("artist", artist)
                .putString("state", state)
                .apply();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if ("com.spotify.music".equals(sbn.getPackageName())) {
            getSharedPreferences("esbo_music", MODE_PRIVATE)
                    .edit()
                    .putString("state", "STOPPED")
                    .apply();
        }
    }
        }
