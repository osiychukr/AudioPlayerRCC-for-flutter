package bz.rxla.audioplayer;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import java.io.IOException;
import java.util.List;

import bz.rxla.audioplayer.client.MediaBrowserHelper;
import bz.rxla.audioplayer.models.AudioInfo;
import bz.rxla.audioplayer.service.MusicService;
import bz.rxla.audioplayer.service.contentcatalogs.MusicLibrary;
import bz.rxla.audioplayer.service.players.MediaPlayerAdapter;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * Android implementation for AudioPlayerPlugin.
 */
public class AudioplayerPlugin implements MethodCallHandler {
    private static final String ID = "bz.rxla.flutter/audio";
    private static final String TAG = AudioplayerPlugin.class.getSimpleName();

    private final MethodChannel channel;
    private final AudioManager am;
    private final Handler handler = new Handler();
    private MediaPlayer mediaPlayer;
    private Context context;

    private MediaBrowserHelper mMediaBrowserHelper;
    private boolean mIsPlaying;
    private boolean needToSeek = false;
    private long seek = 0;
    private AudioInfo audioInfo = null;

    private BroadcastReceiver skipNextReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "skipNextReceiver onReceive");
            mMediaBrowserHelper.getTransportControls().pause();
            channel.invokeMethod("audio.onComplete", null);
        }
    };

    private BroadcastReceiver skipPreviousReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "skipPreviousReceiver onReceive");
            channel.invokeMethod("audio.onPrevious", null);
        }
    };

    private BroadcastReceiver currentPositionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "currentPositionReceiver onReceive");
            int time = intent.getIntExtra(MediaPlayerAdapter.CURRENT_POS_KEY, 0);
            channel.invokeMethod("audio.onCurrentPosition", time);
        }
    };

    private BroadcastReceiver onCompleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onCompleteReceiver onReceive");
            channel.invokeMethod("audio.onComplete", null);
        }
    };

    private BroadcastReceiver onSeekReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onSeekReceiver onReceive");
            needToSeek = false;
            seek = 0;
        }
    };

    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), ID);
        channel.setMethodCallHandler(new AudioplayerPlugin(registrar, channel));
    }

    private AudioplayerPlugin(Registrar registrar, MethodChannel channel) {
        this.channel = channel;
        channel.setMethodCallHandler(this);
        context = registrar.context().getApplicationContext();
        this.am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        mMediaBrowserHelper = new MediaBrowserConnection(context);
        mMediaBrowserHelper.registerCallback(new MediaBrowserListener());

        ((Application) context.getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                Toast.makeText(context,
                        "onActivityCreated",
                        Toast.LENGTH_SHORT).show();

                Log.d(TAG, "onActivityCreated");
            }

            @Override
            public void onActivityStarted(Activity activity) {
                mMediaBrowserHelper.onStart();

                LocalBroadcastManager.getInstance(context).registerReceiver(
                        skipNextReceiver, new IntentFilter(MusicService.SKIP_NEXT_ACTION));

                LocalBroadcastManager.getInstance(context).registerReceiver(
                        skipPreviousReceiver, new IntentFilter(MusicService.SKIP_PREVIOUS_ACTION));

                LocalBroadcastManager.getInstance(context).registerReceiver(
                        currentPositionReceiver, new IntentFilter(MediaPlayerAdapter.CURRENT_POS_ACTION));

                LocalBroadcastManager.getInstance(context).registerReceiver(
                        onSeekReceiver, new IntentFilter(MediaPlayerAdapter.SEEK_ACTION));

                LocalBroadcastManager.getInstance(context).registerReceiver(
                        onCompleteReceiver, new IntentFilter(MusicService.ON_COMPLETE_ACTION));

                Log.d(TAG, "onActivityStarted");
            }

            @Override
            public void onActivityResumed(Activity activity) {
//                if (mIsPlaying) {
//                    mMediaBrowserHelper.getTransportControls().pause();
//                } else {
//                    mMediaBrowserHelper.getTransportControls().play();
//                }
                Log.d(TAG, "onActivityResumed");
            }

            @Override
            public void onActivityPaused(Activity activity) {

                Log.d(TAG, "onActivityPaused");
            }

            @Override
            public void onActivityStopped(Activity activity) {
//                mMediaBrowserHelper.onStop();
//
//                LocalBroadcastManager.getInstance(context).unregisterReceiver(
//                        skipNextReceiver);
//
//                LocalBroadcastManager.getInstance(context).unregisterReceiver(
//                        skipPreviousReceiver);
//
//                LocalBroadcastManager.getInstance(context).unregisterReceiver(
//                        currentPositionReceiver);

                Log.d(TAG, "onActivityStopped");
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

                Log.d(TAG, "onActivitySaveInstanceState");
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                Toast.makeText(context,
                        "onActivityDestroyed",
                        Toast.LENGTH_SHORT).show();
                Log.d(TAG, "onActivityDestroyed");

                mMediaBrowserHelper.onStop();

                LocalBroadcastManager.getInstance(context).unregisterReceiver(
                        skipNextReceiver);

                LocalBroadcastManager.getInstance(context).unregisterReceiver(
                        skipPreviousReceiver);

                LocalBroadcastManager.getInstance(context).unregisterReceiver(
                        currentPositionReceiver);

                LocalBroadcastManager.getInstance(context).unregisterReceiver(
                        onCompleteReceiver);

                LocalBroadcastManager.getInstance(context).unregisterReceiver(
                        onSeekReceiver);
            }
        });
    }

    private void setTestInfo() {
        audioInfo = new AudioInfo("name", "author", "https://wallpaperbrowse.com/media/images/3848765-wallpaper-images-download.jpg", 160);
        Glide.with(context)
                .asBitmap()
                .load(audioInfo.imageUrl)
                .into(new SimpleTarget<Bitmap>(200, 200) {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        audioInfo.bitmap = resource;
                        MusicLibrary.getInstance().setAudioInfo(audioInfo);

                        Intent intent = new Intent(MusicService.SET_INFO_ACTION);
                        intent.putExtra(MusicService.AUDIO_INFO_KEY, audioInfo);
                        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                    }
                });

    }

    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result response) {
        String method = call.method;
        Log.i(TAG, "call.method = " + method);
        switch (method) {
            case "play":
                Log.d(TAG, "play");
//                play(call.argument("url").toString());

//                if (mIsPlaying) {
//                    mMediaBrowserHelper.getTransportControls().pause();
//                } else {
//                    mMediaBrowserHelper.getTransportControls().play();
//                }

                mMediaBrowserHelper.getTransportControls().playFromUri(Uri.parse(call.argument("url").toString()), null);


                response.success(null);
                break;
            case "pause":
                Log.d(TAG, "pause");
//                pause();

                mMediaBrowserHelper.getTransportControls().pause();

                response.success(null);
                break;
            case "setInfo":
                Log.d(TAG, "setInfo");

                String author = call.argument("author");
                String name = call.argument("name");
                String imageUrl = call.argument("imageUrl");
                int duration = call.argument("duration");
                audioInfo = new AudioInfo(name, author, imageUrl, duration);
                MusicLibrary.getInstance().setAudioInfo(audioInfo);

                Glide.with(context)
                        .asBitmap()
                        .load(audioInfo.imageUrl)
                        .into(new SimpleTarget<Bitmap>((int) Utils.toDp(100, context), (int) Utils.toDp(100, context)) {
                            @Override
                            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                                audioInfo.bitmap = resource;

                                Log.d(TAG, "setInfo onResourceReady");
                                MusicLibrary.getInstance().setAudioInfo(audioInfo);
                                Intent intent = new Intent(MusicService.SET_INFO_ACTION);
//                                intent.putExtra(MusicService.AUDIO_INFO_KEY, audioInfo);
                                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                            }
                        });

//                Intent intent = new Intent(MusicService.SET_INFO_ACTION);
//                intent.putExtra(MusicService.AUDIO_INFO_KEY, audioInfo);
//                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

                Log.d(TAG, "setInfo " + author + name + imageUrl + duration);

                response.success(null);
                break;
            case "setPlaybackInfo":
                int pos = call.argument("duration");

                Log.d(TAG, "setPlaybackInfo " + pos);

                response.success(null);
                break;
            case "setDuration":
                Log.d(TAG, "setDuration");
                int duration2 = call.argument("duration");
                response.success(null);
                break;
            case "stop":
                Log.d(TAG, "stop");
                stop();
                response.success(null);
                break;
            case "seek":
                double position = call.arguments();
//                seek(position);
                needToSeek = true;
                seek = (long) (position * 1000);
                mMediaBrowserHelper.getTransportControls().seekTo(seek);
                response.success(null);

                Log.d(TAG, "seek " + seek);
                break;
            case "mute":
                Log.d(TAG, "mute");
                Boolean muted = call.arguments();
                mute(muted);
                response.success(null);
                break;
            default:
                response.notImplemented();
        }
    }

    private void mute(Boolean muted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, muted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
        } else {
            am.setStreamMute(AudioManager.STREAM_MUSIC, muted);
        }
    }

    private void seek(double position) {
        mediaPlayer.seekTo((int) (position * 1000));
    }

    private void stop() {
        handler.removeCallbacks(sendData);
//        if (mediaPlayer != null) {
//            mediaPlayer.stop();
//            mediaPlayer.release();
//            mediaPlayer = null;
        channel.invokeMethod("audio.onStop", null);
//        }
    }

    private void pause() {
        handler.removeCallbacks(sendData);
        if (mediaPlayer != null) {
            mediaPlayer.pause();
            channel.invokeMethod("audio.onPause", true);
        }
    }

    private void play(String url) {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

            try {
                mediaPlayer.setDataSource(url);
            } catch (IOException e) {
                Log.w(ID, "Invalid DataSource", e);
                channel.invokeMethod("audio.onError", "Invalid Datasource");
                return;
            }

            mediaPlayer.prepareAsync();

            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mediaPlayer.start();
                    channel.invokeMethod("audio.onStart", mediaPlayer.getDuration());
                }
            });

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stop();
                    channel.invokeMethod("audio.onComplete", null);
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    channel.invokeMethod("audio.onError", String.format("{\"what\":%d,\"extra\":%d}", what, extra));
                    return true;
                }
            });
        } else {
            mediaPlayer.start();
            channel.invokeMethod("audio.onStart", mediaPlayer.getDuration());
        }
        handler.post(sendData);
    }

    private final Runnable sendData = new Runnable() {
        public void run() {
            try {
                if (!mediaPlayer.isPlaying()) {
                    handler.removeCallbacks(sendData);
                }
                int time = mediaPlayer.getCurrentPosition();
                channel.invokeMethod("audio.onCurrentPosition", time);
                handler.postDelayed(this, 200);
            } catch (Exception e) {
                Log.w(ID, "When running handler", e);
            }
        }
    };

    /**
     * Implementation of the {@link MediaControllerCompat.Callback} methods we're interested in.
     * <p>
     * Here would also be where one could override
     * {@code onQueueChanged(List<MediaSessionCompat.QueueItem> queue)} to get informed when items
     * are added or removed from the queue. We don't do this here in order to keep the UI
     * simple.
     */
    private class MediaBrowserListener extends MediaControllerCompat.Callback {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat playbackState) {
            Log.d(TAG, "onPlaybackStateChanged");
//            mMediaControlsImage.setPressed(mIsPlaying);

            if (playbackState != null) {
                switch (playbackState.getState()) {
                    case PlaybackStateCompat.STATE_PLAYING:
                        Log.d(TAG, "STATE_PLAYING");
                        mIsPlaying = true;
                        channel.invokeMethod("audio.onStart", 0);
                        Log.d(TAG, "audio.onStart");

                        if (needToSeek) {
                            mMediaBrowserHelper.getTransportControls().seekTo(seek);
                        }
                        break;
                    case PlaybackStateCompat.STATE_PAUSED:
                        Log.d(TAG, "STATE_PAUSED");
                        mIsPlaying = false;
                        channel.invokeMethod("audio.onPause", true);
                        Log.d(TAG, "audio.onPause");
                        break;
                    case PlaybackStateCompat.STATE_STOPPED:
                        Log.d(TAG, "STATE_STOPPED");
                        mIsPlaying = false;
                        stop();
                        break;
                }
            }

        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat mediaMetadata) {
            Log.d(TAG, "onMetadataChanged");
            if (mediaMetadata == null) {
                return;
            }
//            mTitleTextView.setText(
//                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
//            mArtistTextView.setText(
//                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
//            mAlbumArt.setImageBitmap(MusicLibrary.getAlbumBitmap(
//                    context,
//                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)));
        }

        @Override
        public void onSessionDestroyed() {
            Log.d(TAG, "onSessionDestroyed");
            super.onSessionDestroyed();
        }

        @Override
        public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
            Log.d(TAG, "onQueueChanged");
            super.onQueueChanged(queue);
        }
    }

    /**
     * Customize the connection to our {@link android.support.v4.media.MediaBrowserServiceCompat}
     * and implement our app specific desires.
     */
    class MediaBrowserConnection extends MediaBrowserHelper {

        private final String TAG = MediaBrowserConnection.class.getSimpleName();

        public MediaBrowserConnection(Context context) {
            super(context, MusicService.class);
        }

        @Override
        protected void onConnected(@NonNull MediaControllerCompat mediaController) {
//            mSeekBarAudio.setMediaController(mediaController);
            Log.d(TAG, "onConnected");
//            setTestInfo();
        }

        @Override
        protected void onChildrenLoaded(@NonNull String parentId,
                                        @NonNull List<MediaBrowserCompat.MediaItem> children) {
            super.onChildrenLoaded(parentId, children);
            Log.d(TAG, "onChildrenLoaded");

            final MediaControllerCompat mediaController = getMediaController();

            // Queue up all media items for this simple sample.
            for (final MediaBrowserCompat.MediaItem mediaItem : children) {
                mediaController.addQueueItem(mediaItem.getDescription());
            }

            // Call prepare now so pressing play just works.
            mediaController.getTransportControls().prepare();
        }
    }
}
