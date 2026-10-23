
package com.esbo.spotifybridge;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;

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

        String title = extras.getCharSequence(
                Notification.EXTRA_TITLE) == null
                ? ""
                : extras.getCharSequence(
                        Notification.EXTRA_TITLE).toString();

        String artist = extras.getCharSequence(
                Notification.EXTRA_TEXT) == null
                ? ""
                : extras.getCharSequence(
                        Notification.EXTRA_TEXT).toString();

        String state = "PLAYING";

        if ((notification.flags & Notification.FLAG_ONGOING_EVENT) == 0) {
            state = "PAUSED";
        }

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
