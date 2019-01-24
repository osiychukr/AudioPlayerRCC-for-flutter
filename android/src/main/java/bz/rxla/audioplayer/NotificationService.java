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
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

public class NotificationService extends Service {

    private static final String CHANNEL_ID = "bz.rxla.flutter/channelID";
    private static final String NOTIFICATION_PLAY_BTN = "NOTIFICATION_PLAY_BTN";
    private static final String NOTIFICATION_STOP_BTN = "NOTIFICATION_STOP_BTN";
    private static final String NOTIFICATION_TIMER_BTN = "NOTIFICATION_TIMER_BTN";

    // This is the object that receives interactions from clients.
    private final IBinder mBinder = new LocalBinder();
    private NotificationCompat.Builder builder = null;
    private NotificationManager manager = null;
    private RemoteViews contentView = null;

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
        Notification n = buildNotification();
        startForeground(32, n);
    }

    private void init() {
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

    private Notification buildNotification() {
        contentView = new RemoteViews(getApplicationContext().getPackageName(), R.layout.notification_view);
        contentView.setOnClickPendingIntent(R.id.btn_notification_play, getPendingSelfIntent(getApplicationContext(), NOTIFICATION_PLAY_BTN));
        contentView.setOnClickPendingIntent(R.id.btn_notification_stop, getPendingSelfIntent(getApplicationContext(), NOTIFICATION_STOP_BTN));
        contentView.setOnClickPendingIntent(R.id.btn_notification_timer, getPendingSelfIntent(getApplicationContext(), NOTIFICATION_TIMER_BTN));
        contentView.setImageViewResource(R.id.btn_notification_play, R.drawable.ic_pause);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
            builder.setChannelId(CHANNEL_ID);

            String name = "name";
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(mChannel);
        } else {
            builder = new NotificationCompat.Builder(getApplicationContext());
        }

        builder.setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setContent(contentView);
        Notification n = builder.build();

        return n;
    }

    private PendingIntent getPendingSelfIntent(Context context, String action) {
        Intent intent = new Intent(context, NotificationService.class);
        intent.setAction(action);
        return PendingIntent.getService(context, 0, intent, 0);
    }

}
