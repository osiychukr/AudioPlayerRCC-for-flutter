package bz.rxla.audioplayer;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;

import java.util.List;

import bz.rxla.audioplayer.client.MediaBrowserHelper;
import bz.rxla.audioplayer.service.MusicService;

/**
 * Customize the connection to our {@link android.support.v4.media.MediaBrowserServiceCompat}
 * and implement our app specific desires.
 */
class MediaBrowserConnection extends MediaBrowserHelper {
    public MediaBrowserConnection(Context context) {
        super(context, MusicService.class);
    }

    @Override
    protected void onConnected(@NonNull MediaControllerCompat mediaController) {
//            mSeekBarAudio.setMediaController(mediaController);
    }

    @Override
    protected void onChildrenLoaded(@NonNull String parentId,
                                    @NonNull List<MediaBrowserCompat.MediaItem> children) {
        super.onChildrenLoaded(parentId, children);

        final MediaControllerCompat mediaController = getMediaController();

        // Queue up all media items for this simple sample.
        for (final MediaBrowserCompat.MediaItem mediaItem : children) {
            mediaController.addQueueItem(mediaItem.getDescription());
        }

        // Call prepare now so pressing play just works.
        mediaController.getTransportControls().prepare();
    }
}