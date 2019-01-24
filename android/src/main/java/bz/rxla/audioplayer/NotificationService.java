package bz.rxla.audioplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class NotificationService extends Service {

    private static final String TAG = NotificationService.class.getSimpleName();
    private static final int NOTIFICSTIO_ID = 32324;
    private static final String CHANNEL_ID = "bz.rxla.flutter/channelID";
    private static final String NOTIFICATION_PLAY_BTN = "NOTIFICATION_PLAY_BTN";
    private static final String NOTIFICATION_STOP_BTN = "NOTIFICATION_STOP_BTN";
    private static final String NOTIFICATION_TIMER_BTN = "NOTIFICATION_TIMER_BTN";

    // This is the object that receives interactions from clients.
    private final IBinder mBinder = new LocalBinder();
    private NotificationCompat.Builder builder = null;
    private NotificationManager manager = null;
    private RemoteViews contentView = null;
    private boolean isSoundPlayed = false;

    private MediaSessionCompat mSession;
    private MediaNotificationManager mMediaNotificationManager;
    private MediaSessionCallback mCallback;

//    private NotificationListener notificationListener = null;

    public class LocalBinder extends Binder {

        NotificationService getService() {
            return NotificationService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        init();
//        Notification n = buildNotification();
//        startForeground(NOTIFICSTIO_ID, n);

        start();

        isSoundPlayed = true;
    }

    private void start() {
        Notification notification = mMediaNotificationManager.getNotification(mSession.getSessionToken());
            ContextCompat.startForegroundService(
                    NotificationService.this,
                    new Intent(NotificationService.this, NotificationService.class));
        startForeground(MediaNotificationManager.NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        mMediaNotificationManager.onDestroy();
        mSession.release();
        Log.d(TAG, "onDestroy: MediaPlayerAdapter stopped, and MediaSession released");
    }

    private void init() {
        // Create a new MediaSession.
        mSession = new MediaSessionCompat(this, "MusicService");
        mCallback = new MediaSessionCallback();
        mSession.setCallback(mCallback);
        mMediaNotificationManager = new MediaNotificationManager(this);
        manager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        String action = intent.getAction();
        if (action != null) {
            switch (action) {
                case NOTIFICATION_PLAY_BTN: {
                    Log.i("click", "NOTIFICATION_PLAY_BTN");
//                    if (notificationListener != null) {
//                        notificationListener.onPlayClick();
//                    }
                    onPlayPauseClick();
                }
                break;
                case NOTIFICATION_STOP_BTN: {
                    Log.i("click", "NOTIFICATION_STOP_BTN");
                }
                break;
            }
            Toast.makeText(getApplicationContext(), "Action name: ${name}", Toast.LENGTH_LONG).show();
        }

        return START_NOT_STICKY;
    }

    private void onPlayPauseClick() {
        if (contentView != null) {
            contentView.setImageViewResource(R.id.btn_notification_play, isSoundPlayed ? R.drawable.ic_pause : R.drawable.ic_play);
            manager.notify(NOTIFICSTIO_ID, builder.build());
        }
        isSoundPlayed = !isSoundPlayed;
    }

    private Notification buildNotification() {
        contentView = new RemoteViews(getApplicationContext().getPackageName(), R.layout.notification_view);
        contentView.setOnClickPendingIntent(R.id.btn_notification_play, getPendingSelfIntent(getApplicationContext(), NOTIFICATION_PLAY_BTN));
        contentView.setOnClickPendingIntent(R.id.btn_notification_stop, getPendingSelfIntent(getApplicationContext(), NOTIFICATION_STOP_BTN));
        contentView.setOnClickPendingIntent(R.id.btn_notification_timer, getPendingSelfIntent(getApplicationContext(), NOTIFICATION_TIMER_BTN));
        contentView.setImageViewResource(R.id.btn_notification_play, R.drawable.ic_pause);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel();
        }

//        builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);

//        builder.setSmallIcon(android.R.mipmap.sym_def_app_icon)
//                .setDefaults(Notification.DEFAULT_ALL)
//                .setVibrate(null)
//                .setSound(null)
//                .setContent(contentView);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
        notificationBuilder
                .setStyle(
                        new MediaStyle()
                                .setMediaSession(mSession.getSessionToken())
                                .setShowCancelButton(true)
                                .setCancelButtonIntent(
                                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                                                getApplicationContext(), PlaybackStateCompat.ACTION_STOP)))
                                .setColor(ContextCompat.getColor(getApplicationContext(), android.R.color.darker_gray))
                                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                                .setOnlyAlertOnce(true)
//                .setContentIntent(createContentIntent())
                                .setContentTitle("album")
                                .setContentText("artist")
                                .setSubText("song name");
//                .setLargeIcon(MusicLibrary.getAlbumBitmap(getApplicationContext(), description.getMediaId()))
//                .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(
//                        mService, PlaybackStateCompat.ACTION_STOP));
        Notification n = notificationBuilder.build();

        return n;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createChannel() {
        NotificationManager
                mNotificationManager =
                (NotificationManager) getApplicationContext()
                        .getSystemService(Context.NOTIFICATION_SERVICE);

        // The user-visible name of the channel.
        CharSequence name = "Media playback";
        // The user-visible description of the channel.
        String description = "Media playback controls";
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
        // Configure the notification channel.
        mChannel.setDescription(description);
        mChannel.setShowBadge(false);
        mChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        if (mNotificationManager != null) {
            mNotificationManager.createNotificationChannel(mChannel);
        }
    }

    private PendingIntent getPendingSelfIntent(Context context, String action) {
        Intent intent = new Intent(context, NotificationService.class);
        intent.setAction(action);
        return PendingIntent.getService(context, 0, intent, 0);
    }

//    public void setNotificationListener(NotificationListener notificationListener) {
//        this.notificationListener = notificationListener;
//    }

    // MediaSession Callback: Transport Controls -> MediaPlayerAdapter
    public class MediaSessionCallback extends MediaSessionCompat.Callback {
//        private final List<MediaSessionCompat.QueueItem> mPlaylist = new ArrayList<>();
//        private int mQueueIndex = -1;
//        private MediaMetadataCompat mPreparedMedia;

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
//            mPlaylist.add(new MediaSessionCompat.QueueItem(description, description.hashCode()));
//            mQueueIndex = (mQueueIndex == -1) ? 0 : mQueueIndex;
//            mSession.setQueue(mPlaylist);

            Toast.makeText(getApplicationContext(), "onAddQueueItem", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description) {
//            mPlaylist.remove(new MediaSessionCompat.QueueItem(description, description.hashCode()));
//            mQueueIndex = (mPlaylist.isEmpty()) ? -1 : mQueueIndex;
//            mSession.setQueue(mPlaylist);

            Toast.makeText(getApplicationContext(), "onRemoveQueueItem", Toast.LENGTH_SHORT).show();
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

            Toast.makeText(getApplicationContext(), "onPrepare", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPlay() {
//            if (!isReadyToPlay()) {
//                // Nothing to play.
//                return;
//            }
//
//            if (mPreparedMedia == null) {
//                onPrepare();
//            }
//
//            mPlayback.playFromMedia(mPreparedMedia);
            Log.d(TAG, "onPlayFromMediaId: MediaSession active");

            Toast.makeText(getApplicationContext(), "onPlay", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPause() {
//            mPlayback.pause();

            Toast.makeText(getApplicationContext(), "onPause", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStop() {
//            mPlayback.stop();
//            mSession.setActive(false);

            Toast.makeText(getApplicationContext(), "onStop", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onSkipToNext() {
//            mQueueIndex = (++mQueueIndex % mPlaylist.size());
//            mPreparedMedia = null;
//            onPlay();

            Toast.makeText(getApplicationContext(), "onSkipToNext", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onSkipToPrevious() {
//            mQueueIndex = mQueueIndex > 0 ? mQueueIndex - 1 : mPlaylist.size() - 1;
//            mPreparedMedia = null;
//            onPlay();

            Toast.makeText(getApplicationContext(), "onSkipToPrevious", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onSeekTo(long pos) {
//            mPlayback.seekTo(pos);

            Toast.makeText(getApplicationContext(), "onSeekTo", Toast.LENGTH_SHORT).show();
        }

        private boolean isReadyToPlay() {
//            return (!mPlaylist.isEmpty());
            Toast.makeText(getApplicationContext(), "isReadyToPlay", Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
