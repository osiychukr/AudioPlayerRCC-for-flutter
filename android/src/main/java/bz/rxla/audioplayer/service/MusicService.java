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
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.List;

import bz.rxla.audioplayer.models.AudioInfo;
import bz.rxla.audioplayer.service.contentcatalogs.MusicLibrary;
import bz.rxla.audioplayer.service.notifications.MediaNotificationManager;
import bz.rxla.audioplayer.service.players.MediaPlayerAdapter;

public class MusicService extends MediaBrowserServiceCompat {

    private static final String TAG = MusicService.class.getSimpleName();

    public static final String SKIP_NEXT_ACTION = "SKIP_NEXT_ACTION";
    public static final String SKIP_PREVIOUS_ACTION = "SKIP_PREVIOUS_ACTION";
    public static final String SET_INFO_ACTION = "SKIP_PREVIOUS_ACTION";
    public static final String AUDIO_INFO_KEY = "AUDIO_INFO_KEY";

    private MediaSessionCompat mSession;
    private PlayerAdapter mPlayback;
    private MediaNotificationManager mMediaNotificationManager;
    private MediaSessionCallback mCallback;
    private boolean mServiceInStartedState;
    private Uri uri = null;

    private AudioInfo audioInfo = null;

    private BroadcastReceiver setInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "setInfoReceiver onReceive");
            audioInfo = intent.getParcelableExtra(AUDIO_INFO_KEY);
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
        return new BrowserRoot(MusicLibrary.getRoot(), null);
    }

    @Override
    public void onLoadChildren(
            @NonNull final String parentMediaId,
            @NonNull final Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(MusicLibrary.getMediaItems());
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
//            if (mQueueIndex < 0 && mPlaylist.isEmpty()) {
//                // Nothing to play.
//                return;
//            }
//
//            final String mediaId = mPlaylist.get(mQueueIndex).getDescription().getMediaId();
//            mPreparedMedia = MusicLibrary.getMetadata(MusicService.this, mediaId);
//            mSession.setMetadata(mPreparedMedia);
//
//            if (!mSession.isActive()) {
//                mSession.setActive(true);
//            }

            if (audioInfo == null) {
                return;
            }
//            Glide.with(MusicService.this)
//                    .asBitmap()
//                    .load(audioInfo.imageUrl)
//                    .into(new SimpleTarget<Bitmap>(50, 50) {
//                        @Override
//                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
//                            audioInfo.bitmap = resource;
//                            mPreparedMedia = MusicLibrary.getMetadata(MusicService.this, audioInfo);
//                            mSession.setMetadata(mPreparedMedia);
//
//                            if (!mSession.isActive()) {
//                                mSession.setActive(true);
//                            }
//                        }
//                    });

//            audioInfo.bitmap = resource;
            mPreparedMedia = MusicLibrary.getMetadata(MusicService.this, audioInfo);
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
//
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
            mPlayback.pause();
        }

        @Override
        public void onStop() {
            mPlayback.stop();
            mSession.setActive(false);
        }

        @Override
        public void onSkipToNext() {
//            mQueueIndex = (++mQueueIndex % mPlaylist.size());
//            mPreparedMedia = null;
//            onPlay();

            Intent intent = new Intent(SKIP_NEXT_ACTION);
//            intent.putExtra(SKIP_NEXT_ACTION, args[0].toString());
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        }

        @Override
        public void onSkipToPrevious() {
//            mQueueIndex = mQueueIndex > 0 ? mQueueIndex - 1 : mPlaylist.size() - 1;
//            mPreparedMedia = null;
//            onPlay();

            Intent intent = new Intent(SKIP_PREVIOUS_ACTION);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

        }

        @Override
        public void onSeekTo(long pos) {
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

        class ServiceManager {

            private void moveServiceToStartedState(PlaybackStateCompat state) {
//                Notification notification =
//                        mMediaNotificationManager.getNotification(
//                                mPlayback.getCurrentMedia(), state, getSessionToken());
                MediaMetadataCompat mData = MusicLibrary.getMetadata(MusicService.this, "test_id");
                Notification notification =
                        mMediaNotificationManager.getNotification(
                                mPlayback.getCurrentMedia(), state, getSessionToken());

                if (!mServiceInStartedState) {
                    ContextCompat.startForegroundService(
                            MusicService.this,
                            new Intent(MusicService.this, MusicService.class));
                    mServiceInStartedState = true;
                }

                startForeground(MediaNotificationManager.NOTIFICATION_ID, notification);
            }

            private void updateNotificationForPause(PlaybackStateCompat state) {
                stopForeground(false);
//                Notification notification =
//                        mMediaNotificationManager.getNotification(
//                                mPlayback.getCurrentMedia(), state, getSessionToken());
                MediaMetadataCompat mData = MusicLibrary.getMetadata(MusicService.this, "test_id");
                Notification notification =
                        mMediaNotificationManager.getNotification(
                                mPlayback.getCurrentMedia(), state, getSessionToken());
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