/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bz.rxla.audioplayer.service;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import bz.rxla.audioplayer.service.contentcatalogs.MusicLibrary;
import bz.rxla.audioplayer.service.notifications.MediaNotificationManager;
import bz.rxla.audioplayer.service.players.MediaPlayerAdapter;

public class MusicService extends MediaBrowserServiceCompat {

    private static final String TAG = MusicService.class.getSimpleName();

    public static final String SKIP_NEXT_ACTION = "SKIP_NEXT_ACTION";
    public static final String SKIP_PREVIOUS_ACTION = "SKIP_PREVIOUS_ACTION";
    public static final String SET_INFO_ACTION = "SET_INFO_ACTION";
    public static final String AUDIO_INFO_KEY = "AUDIO_INFO_KEY";
    public static final String ON_COMPLETE_ACTION = "ON_COMPLETE_ACTION";
    public static final int UPDATE_IMAGE_STATE = -12;

    private MediaSessionCompat mSession;
    private PlayerAdapter mPlayback;
    private MediaNotificationManager mMediaNotificationManager;
    private MediaSessionCallback mCallback;
    private boolean mServiceInStartedState;
    private boolean needToUpdate = false;
    private Uri uri = null;


    private BroadcastReceiver setInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "setInfoReceiver onReceive");
            if (mPlayback != null && mPlayback.isPlaying() && needToUpdate) {
                ((MediaPlayerAdapter) mPlayback).setNewState(UPDATE_IMAGE_STATE);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        // Create a new MediaSession.
        mSession = new MediaSessionCompat(this, "MusicService");
        mCallback = new MediaSessionCallback();
        mSession.setCallback(mCallback);
        mSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                        MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS |
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        setSessionToken(mSession.getSessionToken());

        mMediaNotificationManager = new MediaNotificationManager(this);

        mPlayback = new MediaPlayerAdapter(this, new MediaPlayerListener());

        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(
                setInfoReceiver, new IntentFilter(SET_INFO_ACTION));

        Log.d(TAG, "onCreate: MusicService creating MediaSession, and MediaNotificationManager");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        mMediaNotificationManager.onDestroy();
        mPlayback.stop();
        mSession.release();

        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(
                setInfoReceiver);

        Log.d(TAG, "onDestroy: MediaPlayerAdapter stopped, and MediaSession released");
    }

    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName,
                                 int clientUid,
                                 Bundle rootHints) {
        return new BrowserRoot(MusicLibrary.getInstance().getRoot(), null);
    }

    @Override
    public void onLoadChildren(
            @NonNull final String parentMediaId,
            @NonNull final Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(MusicLibrary.getInstance().getMediaItems());
    }

    // MediaSession Callback: Transport Controls -> MediaPlayerAdapter
    public class MediaSessionCallback extends MediaSessionCompat.Callback {
        private final List<MediaSessionCompat.QueueItem> mPlaylist = new ArrayList<>();
        private int mQueueIndex = -1;
        private MediaMetadataCompat mPreparedMedia;

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            mPlaylist.add(new MediaSessionCompat.QueueItem(description, description.hashCode()));
            mQueueIndex = (mQueueIndex == -1) ? 0 : mQueueIndex;
            mSession.setQueue(mPlaylist);
        }

        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description) {
            mPlaylist.remove(new MediaSessionCompat.QueueItem(description, description.hashCode()));
            mQueueIndex = (mPlaylist.isEmpty()) ? -1 : mQueueIndex;
            mSession.setQueue(mPlaylist);
        }

        @Override
        public void onPrepare() {
            mPreparedMedia = MusicLibrary.getInstance().getMetadata();
            mSession.setMetadata(mPreparedMedia);

            if (!mSession.isActive()) {
                mSession.setActive(true);
            }
        }

        @Override
        public void onPlay() {
            if (!isReadyToPlay()) {
                // Nothing to play.
                return;
            }

            if (mPreparedMedia == null) {
                onPrepare();
            }
//            mPlayback.playFromMedia(mPreparedMedia);

            if (uri != null) {
                mPlayback.playFromUrl(uri.toString(), mPreparedMedia);
            }
            Log.d(TAG, "onPlayFromMediaId: MediaSession active");
        }

        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {
            MusicService.this.uri = uri;
            if (!isReadyToPlay()) {
                // Nothing to play.
                return;
            }

            if (mPreparedMedia == null) {
                onPrepare();
            }

            mPlayback.playFromUrl(uri.toString(), mPreparedMedia);
            Log.d(TAG, "onPlayFromUri: MediaSession active - " + uri.toString());
        }

        @Override
        public void onPause() {
            Log.i(TAG, "onPause");
            mPlayback.pause();
        }

        @Override
        public void onStop() {
            Log.i(TAG, "onStop");
            mPlayback.stop();
            mSession.setActive(false);
        }

        @Override
        public void onSkipToNext() {
            Log.i(TAG, "onSkipToNext");

            Intent intent = new Intent(SKIP_NEXT_ACTION);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        }

        @Override
        public void onSkipToPrevious() {
            Log.i(TAG, "onSkipToPrevious");

            Intent intent = new Intent(SKIP_PREVIOUS_ACTION);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

        }

        @Override
        public void onSeekTo(long pos) {
            Log.i(TAG, "onSeekTo");
            mPlayback.seekTo(pos);
        }

        private boolean isReadyToPlay() {
//            return (!mPlaylist.isEmpty());
            return (uri != null);
        }
    }

    // MediaPlayerAdapter Callback: MediaPlayerAdapter state -> MusicService.
    public class MediaPlayerListener extends PlaybackInfoListener {

        private final ServiceManager mServiceManager;

        MediaPlayerListener() {
            mServiceManager = new ServiceManager();
        }

        @Override
        public void onPlaybackStateChange(PlaybackStateCompat state) {
            // Report the state to the MediaSession.
            mSession.setPlaybackState(state);

            // Manage the started state of this service.
            switch (state.getState()) {
                case PlaybackStateCompat.STATE_PLAYING:
                    mServiceManager.moveServiceToStartedState(state);
                    break;
                case PlaybackStateCompat.STATE_PAUSED:
                    mServiceManager.updateNotificationForPause(state);
                    break;
                case PlaybackStateCompat.STATE_STOPPED:
                    mServiceManager.moveServiceOutOfStartedState(state);
                    break;
            }
        }

        @Override
        public void onPlaybackCompleted() {
            super.onPlaybackCompleted();
            Intent intent = new Intent(ON_COMPLETE_ACTION);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        }

        class ServiceManager {

            private void moveServiceToStartedState(PlaybackStateCompat state) {
                MediaMetadataCompat metadata = MusicLibrary.getInstance().getMetadata();
                Notification notification =
                        mMediaNotificationManager.getNotification(
                                metadata, state, getSessionToken());

                if (!mServiceInStartedState) {
                    ContextCompat.startForegroundService(
                            MusicService.this,
                            new Intent(MusicService.this, MusicService.class));
                    mServiceInStartedState = true;
                }

                startForeground(MediaNotificationManager.NOTIFICATION_ID, notification);

                needToUpdate = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART) == null;
            }

            private void updateNotificationForPause(PlaybackStateCompat state) {
                stopForeground(false);
                Notification notification =
                        mMediaNotificationManager.getNotification(
                                MusicLibrary.getInstance().getMetadata(), state, getSessionToken());
                mMediaNotificationManager.getNotificationManager()
                        .notify(MediaNotificationManager.NOTIFICATION_ID, notification);
            }

            private void moveServiceOutOfStartedState(PlaybackStateCompat state) {
                stopForeground(true);
                stopSelf();
                mServiceInStartedState = false;
            }
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }
}