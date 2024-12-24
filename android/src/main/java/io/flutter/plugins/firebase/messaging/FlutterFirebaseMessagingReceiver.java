// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.firebase.messaging;

import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.util.Log;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.firebase.messaging.RemoteMessage;
import java.util.HashMap;
import java.util.Map;

public class FlutterFirebaseMessagingReceiver extends BroadcastReceiver {
  private static final String TAG = "FLTFireMsgReceiver";
  static HashMap<String, RemoteMessage> notifications = new HashMap<>();

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(TAG, "broadcast received for message");
    if (ContextHolder.getApplicationContext() == null) {
      ContextHolder.setApplicationContext(context.getApplicationContext());
    }

    RemoteMessage remoteMessage = new RemoteMessage(intent.getExtras());

    // Store the RemoteMessage if the message contains a notification payload.
    if (remoteMessage.getNotification() != null) {
      notifications.put(remoteMessage.getMessageId(), remoteMessage);
      FlutterFirebaseMessagingStore.getInstance().storeFirebaseMessage(remoteMessage);
    }

    playSoundFromRawResource(context, remoteMessage);

    //  |-> ---------------------
    //      App in Foreground
    //   ------------------------
    if (FlutterFirebaseMessagingUtils.isApplicationForeground(context)) {
      Intent onMessageIntent = new Intent(FlutterFirebaseMessagingUtils.ACTION_REMOTE_MESSAGE);
      onMessageIntent.putExtra(FlutterFirebaseMessagingUtils.EXTRA_REMOTE_MESSAGE, remoteMessage);
      LocalBroadcastManager.getInstance(context).sendBroadcast(onMessageIntent);
      return;
    }

    //  |-> ---------------------
    //    App in Background/Quit
    //   ------------------------
    Intent onBackgroundMessageIntent =
        new Intent(context, FlutterFirebaseMessagingBackgroundService.class);
    onBackgroundMessageIntent.putExtra(
        FlutterFirebaseMessagingUtils.EXTRA_REMOTE_MESSAGE, remoteMessage);
    FlutterFirebaseMessagingBackgroundService.enqueueMessageProcessing(
        context, onBackgroundMessageIntent);
  }

  private void playSoundFromRawResource(Context context, RemoteMessage remoteMessage) {
    try {
      // Extract custom data (key-value pairs)
      final Map<String, String> data = remoteMessage.getData();

      // Access specific data from the map
      final String event = data.get("event");
      if (event == null) return;
      final boolean isProd = "production".equals(data.get("topicPrefix"));
      if (event.hashCode() != "SDD_ORDER_TO_PICK".hashCode()) return;
      final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
      if (audioManager == null) return;
      final int ringerMode = audioManager.getRingerMode();
      if (ringerMode == AudioManager.RINGER_MODE_NORMAL) return;

      // Replace "com.example.othermodule" with the actual package name of the other module
      final String externalPackageName = isProd
              ? "id.segari.whflutter"
              : "id.segari.whflutter.local";

      // Get the context of the external package
      final Context externalContext = context.createPackageContext(externalPackageName, Context.CONTEXT_IGNORE_SECURITY);

      // Retrieve the resource ID dynamically
      final int soundResId = externalContext.getResources().getIdentifier(
              "ready_to_pick", // Resource name
              "raw",                // Resource type
              externalPackageName   // Package name
      );

      if (soundResId != 0) { // Ensure resource exists
        final MediaPlayer mediaPlayer = MediaPlayer.create(context, Uri.parse("android.resource://" + externalPackageName + "/" + soundResId));
        if (mediaPlayer != null){
          mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
              mp.release(); // Release resources after playing
            }
          });
          mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
              mp.start(); // Start playback
            }
          });
          mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
              mp.reset();
              return true;
            }
          });
          mediaPlayer.prepareAsync();
        }
      } else {
        throw new Resources.NotFoundException("Resource not found in external package");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
