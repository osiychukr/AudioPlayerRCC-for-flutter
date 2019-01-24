package bz.rxla.audioplayer;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * Android implementation for AudioPlayerPlugin.
 */
public class AudioplayerPlugin implements MethodCallHandler {
    private static final String ID = "bz.rxla.flutter/audio";

    private final MethodChannel channel;
    private final AudioManager am;
    private final Handler handler = new Handler();
    private MediaPlayer mediaPlayer;
    private Context context;
    private boolean mIsBound = false;

    private NotificationService mBoundService;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has
            // been established, giving us the service object we can use
            // to interact with the service.  Because we have bound to a
            // explicit service that we know is running in our own
            // process, we can cast its IBinder to a concrete class and
            // directly access it.
            mBoundService = ((NotificationService.LocalBinder) service).getService();

            // Tell the user about this for our demo.
            Toast.makeText(context,
                    "local_service_connected",
                    Toast.LENGTH_SHORT).show();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has
            // been unexpectedly disconnected -- that is, its process
            // crashed. Because it is running in our same process, we
            // should never see this happen.
            mBoundService = null;
            Toast.makeText(context,
                    "local_service_disconnected",
                    Toast.LENGTH_SHORT).show();
        }
    };

    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation
        // that we know will be running in our own process (and thus
        // won't be supporting component replacement by other
        // applications).
        context.bindService(new Intent(context, NotificationService.class),
                mConnection,
                Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            context.unbindService(mConnection);
            mIsBound = false;
        }
    }

    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), ID);
        channel.setMethodCallHandler(new AudioplayerPlugin(registrar, channel));
    }

    private AudioplayerPlugin(Registrar registrar, MethodChannel channel) {
        this.channel = channel;
        channel.setMethodCallHandler(this);
        context = registrar.context().getApplicationContext();
        this.am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        ((Application) context.getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                Toast.makeText(context,
                        "onActivityCreated",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onActivityStarted(Activity activity) {

            }

            @Override
            public void onActivityResumed(Activity activity) {

            }

            @Override
            public void onActivityPaused(Activity activity) {

            }

            @Override
            public void onActivityStopped(Activity activity) {

            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                doUnbindService();
                Toast.makeText(context,
                        "onActivityDestroyed",
                        Toast.LENGTH_SHORT).show();
            }
        });
        doBindService();
    }

    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result response) {
        switch (call.method) {
            case "play":
                play(call.argument("url").toString());
                response.success(null);
                break;
            case "pause":
                pause();
                response.success(null);
                break;
            case "stop":
                stop();
                response.success(null);
                break;
            case "seek":
                double position = call.arguments();
                seek(position);
                response.success(null);
                break;
            case "mute":
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
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            channel.invokeMethod("audio.onStop", null);
        }
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
}
