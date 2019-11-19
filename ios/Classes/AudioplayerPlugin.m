#import "AudioplayerPlugin.h"
#import <UIKit/UIKit.h>
#import <AVKit/AVKit.h>
#import <AVFoundation/AVFoundation.h>
#import <MediaPlayer/MediaPlayer.h>

static NSString *const CHANNEL_NAME = @"bz.rxla.flutter/audio";
static FlutterMethodChannel *channel;
static AVPlayer *player;
static AVPlayerItem *playerItem;

@interface AudioplayerPlugin()
-(void)pause;
-(void)stop;
-(void)mute:(BOOL)muted;
-(void)seek:(CMTime)time;
-(void)onStart;
-(void)onTimeInterval:(CMTime)time;
-(void)setInfo:(NSString*)name author:(NSString*)author imageUrl:(NSString*)imageUrl duration:(int)duration;
-(void)setPlaybackInfo:(int)duration;
-(void)setDuration:(int)duration;
@end

@implementation AudioplayerPlugin

CMTime position;
NSString *lastUrl;
BOOL isPlaying = false;
NSMutableSet *observers;
NSMutableSet *timeobservers;
FlutterMethodChannel *_channel;
MPRemoteCommandCenter *commandCenter;

+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
    FlutterMethodChannel* channel = [FlutterMethodChannel
                                     methodChannelWithName:CHANNEL_NAME
                                     binaryMessenger:[registrar messenger]];
    AudioplayerPlugin* instance = [[AudioplayerPlugin alloc] init];
    [registrar addMethodCallDelegate:instance channel:channel];
    _channel = channel;
    
    //ADDED START
    NSError *error = nil;
    [[UIApplication sharedApplication] beginReceivingRemoteControlEvents];
    
    BOOL success = [[AVAudioSession sharedInstance]
                    setCategory:AVAudioSessionCategoryPlayback
                    error:&error];
    if (!success) {
        NSLog(@"Error setting speaker");
        // Handle error here, as appropriate
    }
    
    commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    
    [commandCenter.pauseCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *event) {

        [player pause];
        isPlaying = false;
        [_channel invokeMethod:@"audio.onSELPause" arguments:nil];
        
        return MPRemoteCommandHandlerStatusSuccess;
    } ];
    
    [commandCenter.playCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *event) {
        CMTime duration = [[player currentItem] duration];
        [[player currentItem] addObserver:self
                               forKeyPath:@"player.currentItem.status"
                                  options:0
                                  context:nil];
        
        if (CMTimeGetSeconds(duration) > 0) {
            int mseconds= CMTimeGetSeconds(duration)*1000;
            [_channel invokeMethod:@"audio.onStart" arguments:@(mseconds)];
        }
        
        [player play];
        isPlaying = true;
        [_channel invokeMethod:@"audio.onContinue" arguments:nil];

        return MPRemoteCommandHandlerStatusSuccess;
    } ];
    
    [commandCenter.nextTrackCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *event) {
        [player pause];
        isPlaying = false;
        
        [playerItem seekToTime:CMTimeMake(0, 1)];
        [_channel invokeMethod:@"audio.onComplete" arguments:nil];
        
        return MPRemoteCommandHandlerStatusSuccess;
    } ];
    
    [commandCenter.previousTrackCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *event) {
        [player pause];
        isPlaying = false;
        
        [playerItem seekToTime:CMTimeMake(0, 1)];
        [_channel invokeMethod:@"audio.onPrevious" arguments:nil];
        return MPRemoteCommandHandlerStatusSuccess;
    } ];
    
    [commandCenter.previousTrackCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *event) {
        [player pause];
        isPlaying = false;
        
        [playerItem seekToTime:CMTimeMake(0, 1)];
        [_channel invokeMethod:@"audio.onPause" arguments:nil];
        return MPRemoteCommandHandlerStatusSuccess;
    } ];
    
    if (@available(iOS 9.1, *)) {
        [commandCenter.changePlaybackPositionCommand setEnabled:YES];
        [commandCenter.changePlaybackPositionCommand addTarget:instance action:@selector(changedThumbSliderOnLockScreen:)];
    } else {
        // Fallback on earlier versions
    }
    
}

- (MPRemoteCommandHandlerStatus)changedThumbSliderOnLockScreen:(MPChangePlaybackPositionCommandEvent *)event
{
    // change position
    CMTime seekingCM = CMTimeMakeWithSeconds(event.positionTime, 1000000);
    [playerItem seekToTime:seekingCM];
    [self setPlaybackInfo:(int)event.positionTime];
    
    return MPRemoteCommandHandlerStatusSuccess;
}

- (void)handleMethodCall:(FlutterMethodCall*)call result:(FlutterResult)result {
    typedef void (^CaseBlock)(void);
    // Squint and this looks like a proper switch!
    NSDictionary *methods = @{
                              @"play":
                                  ^{
                                      NSString *url = call.arguments[@"url"];
                                      int isLocal = [call.arguments[@"isLocal"] intValue];
                                      [self play:url isLocal:isLocal];
                                      result(nil);
                                  },
                              @"pause":
                                  ^{
                                      [self pause];
                                      result(nil);
                                  },
                              @"stop":
                                  ^{
                                      [self stop];
                                      result(nil);
                                  },
                              @"setInfo":
                                  ^{
                                      NSString *author = call.arguments[@"author"];
                                      NSString *name = call.arguments[@"name"];
                                      NSString *imageUrl = call.arguments[@"imageUrl"];
                                      int duration = [call.arguments[@"duration"] intValue];
                                      [self setInfo:name author:author imageUrl:imageUrl duration:duration];
                                      result(nil);
                                  },
                              @"setPlaybackInfo":
                                  ^{
                                      int duration = [call.arguments[@"duration"] intValue];
                                      [self setPlaybackInfo:duration];
                                      result(nil);
                                  },
                              @"setDuration":
                                  ^{
                                      int duration = [call.arguments[@"duration"] intValue];
                                      [self setDuration:duration];
                                      result(nil);
                                  },
                              @"mute":
                                  ^{
                                      [self mute:[call.arguments boolValue]];
                                      result(nil);
                                  },
                              @"seek":
                                  ^{
                                      [self seek:CMTimeMakeWithSeconds([call.arguments doubleValue], 1)];
                                      result(nil);
                                  }
                              };
    CaseBlock c = methods[call.method];
    if (c) {
        c();
    } else {
        result(FlutterMethodNotImplemented);
    }
}

- (void)setInfo:(NSString*)name author:(NSString*)author imageUrl:(NSString*)imageUrl duration:(int)duration {
    NSMutableDictionary *songInfo = [[NSMutableDictionary alloc] init];
    
    [songInfo setObject:name forKey:MPMediaItemPropertyTitle];
    [songInfo setObject:author forKey:MPMediaItemPropertyArtist];
    [songInfo setObject:[NSNumber numberWithDouble:duration] forKey:MPMediaItemPropertyPlaybackDuration];
    UIImage *artworkImage = [UIImage imageWithData:[NSData dataWithContentsOfURL:[NSURL URLWithString:imageUrl]]];
    if(artworkImage) {
        MPMediaItemArtwork *albumArt = [[MPMediaItemArtwork alloc] initWithImage: artworkImage];
        [songInfo setValue:albumArt forKey:MPMediaItemPropertyArtwork];
    }

    NSLog(@"setInfoSUPER %d", duration);
    [[MPNowPlayingInfoCenter defaultCenter] setNowPlayingInfo:songInfo];
}

-(void)setPlaybackInfo:(int)duration {
    NSMutableDictionary *songInfo = [[NSMutableDictionary alloc] initWithDictionary: [[MPNowPlayingInfoCenter defaultCenter] nowPlayingInfo]];
    NSLog(@"setPlaybackInfoSUPER %d", duration);
    [songInfo setObject:[NSNumber numberWithDouble:duration] forKey:MPNowPlayingInfoPropertyElapsedPlaybackTime];
    
    [[MPNowPlayingInfoCenter defaultCenter] setNowPlayingInfo:songInfo];
}

-(void)setDuration:(int)duration {
    NSMutableDictionary *songInfo = [[NSMutableDictionary alloc] initWithDictionary: [[MPNowPlayingInfoCenter defaultCenter] nowPlayingInfo]];
    NSLog(@"setDurationSUPER %d", duration);
    [songInfo setObject:[NSNumber numberWithDouble:duration] forKey:MPMediaItemPropertyPlaybackDuration];
    
    [[MPNowPlayingInfoCenter defaultCenter] setNowPlayingInfo:songInfo];
}

- (void)play:(NSString*)url isLocal:(int)isLocal {
    
    if (![url isEqualToString:lastUrl]) {
        [playerItem removeObserver:self
                        forKeyPath:@"player.currentItem.status"];
        
        for (id ob in observers) {
            [[NSNotificationCenter defaultCenter] removeObserver:ob];
        }
        observers = nil;
        
        if (isLocal) {
            playerItem = [[AVPlayerItem alloc] initWithURL:[NSURL fileURLWithPath:url]];
        } else {
            playerItem = [[AVPlayerItem alloc] initWithURL:[NSURL URLWithString:url]];
        }
        lastUrl = url;
        
        id anobserver = [[NSNotificationCenter defaultCenter] addObserverForName:AVPlayerItemDidPlayToEndTimeNotification
                                                                          object:playerItem
                                                                           queue:nil
                                                                      usingBlock:^(NSNotification* note){
                                                                          [self stop];
                                                                          [_channel invokeMethod:@"audio.onComplete" arguments:nil];
                                                                      }];
        [observers addObject:anobserver];

        if (player) {
//            [player replaceCurrentItemWithPlayerItem:playerItem];
            player = [[AVPlayer alloc] initWithPlayerItem:playerItem];
        } else {
            player = [[AVPlayer alloc] initWithPlayerItem:playerItem];
            // Stream player position.
            // This call is only active when the player is active so there's no need to
            // remove it when player is paused or stopped.
            CMTime interval = CMTimeMakeWithSeconds(0.2, NSEC_PER_SEC);
            id timeObserver = [player addPeriodicTimeObserverForInterval:interval queue:nil usingBlock:^(CMTime time){
                [self onTimeInterval:time];
            }];
            [timeobservers addObject:timeObserver];
        }
        
        // is sound ready
        [[player currentItem] addObserver:self
                               forKeyPath:@"player.currentItem.status"
                                  options:0
                                  context:nil];
    }
    [self onStart];
    [player play];
    isPlaying = true;
}

- (void)onStart {
    CMTime duration = [[player currentItem] duration];
    if (CMTimeGetSeconds(duration) > 0) {
        int mseconds= CMTimeGetSeconds(duration)*1000;
        [_channel invokeMethod:@"audio.onStart" arguments:@(mseconds)];
    }
}

- (void)onTimeInterval:(CMTime)time {
    int mseconds =  CMTimeGetSeconds(time)*1000;
    [_channel invokeMethod:@"audio.onCurrentPosition" arguments:@(mseconds)];
}

- (void)pause {
    [player pause];
    isPlaying = false;
    [_channel invokeMethod:@"audio.onPause" arguments:nil];
}

- (void)stop {
    if (isPlaying) {
        [player pause];
        isPlaying = false;
    }
    [playerItem seekToTime:CMTimeMake(0, 1)];
    [_channel invokeMethod:@"audio.onStop" arguments:nil];
}

- (void)mute:(BOOL)muted {
    player.muted = muted;
}

- (void)seek:(CMTime)time {
    [playerItem seekToTime:time];
    [self setPlaybackInfo:(int)time.value];
}

- (void)observeValueForKeyPath:(NSString *)keyPath
                      ofObject:(id)object
                        change:(NSDictionary *)change
                       context:(void *)context {
    if ([keyPath isEqualToString:@"player.currentItem.status"]) {
        if ([[player currentItem] status] == AVPlayerItemStatusReadyToPlay) {
            [self onStart];
        } else if ([[player currentItem] status] == AVPlayerItemStatusFailed) {
            [_channel invokeMethod:@"audio.onError" arguments:@[(player.currentItem.error.localizedDescription)]];
        }
    } else {
        // Any unrecognized context must belong to super
        [super observeValueForKeyPath:keyPath
                             ofObject:object
                               change:change
                              context:context];
    }
}

- (void)dealloc {
    for (id ob in timeobservers) {
        [player removeTimeObserver:ob];
    }
    timeobservers = nil;
    
    for (id ob in observers) {
        [[NSNotificationCenter defaultCenter] removeObserver:ob];
    }
    observers = nil;
}

@end
