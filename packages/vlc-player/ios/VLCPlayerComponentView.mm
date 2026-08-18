#import "VLCPlayerComponentView.h"
#if TARGET_OS_TV
#import <TVVLCKit/TVVLCKit.h>
#else
#import <MobileVLCKit/MobileVLCKit.h>
#endif
#import "RCTVLCPlayerModule-Swift.h"

#import <react/renderer/components/VLCPlayerSpec/RCTComponentViewHelpers.h>
#import <react/renderer/components/VLCPlayerSpec/ComponentDescriptors.h>
#import <react/renderer/components/VLCPlayerSpec/EventEmitters.h>
#import <react/renderer/components/VLCPlayerSpec/Props.h>

using namespace facebook::react;

@interface VLCPlayerComponentView () <RCTVLCPlayerViewProtocol>
@end

@implementation VLCPlayerComponentView {
  VLCPlayerView *_playerView;
  NSDictionary *_lastSource;
}

- (instancetype)init
{
  if (self = [super init]) {
    _playerView = [VLCPlayerView new];

    __weak __typeof__(self) weakSelf = self;
    _playerView.onEvent = ^(NSString *type, NSDictionary *payload) {
      [weakSelf emitEvent:type payload:payload];
    };

    self.contentView = _playerView;
  }
  return self;
}

#pragma mark - Events

- (const VLCPlayerEventEmitter &)eventEmitter
{
  return static_cast<const VLCPlayerEventEmitter &>(*_eventEmitter);
}

- (void)emitEvent:(NSString *)type payload:(NSDictionary *)payload
{
  // Events may fire synchronously from within `updateProps:` (e.g. the first
  // `setSource`) and from libvlc background threads. Dispatching on the main
  // queue guarantees the Fabric event `eventDispatcher` is connected before we
  // emit, and is safe to call from any thread. Sending synchronously during a
  // pending mount would dereference a not-yet-attached dispatcher and crash.
  __weak __typeof__(self) weakSelf = self;
  dispatch_async(dispatch_get_main_queue(), ^{
    __strong __typeof__(weakSelf) self = weakSelf;
    if (self == nil) {
      return;
    }
    // The Fabric component may have been recycled or invalidated while this
    // block was queued, in which case `prepareForRecycle`/`invalidate` reset
    // `_eventEmitter`. Dereferencing it here would crash, so guard it.
    if (!self->_eventEmitter) {
      return;
    }
    const auto &emitter = self.eventEmitter;

    if ([type isEqualToString:@"LoadStart"]) {
      VLCPlayerEventEmitter::OnLoadStart e = {"", 0};
      emitter.onLoadStart(e);
      return;
    }

    if ([type isEqualToString:@"Progress"]) {
      VLCPlayerEventEmitter::OnProgress e = {
          .currentTime = [payload[@"currentTime"] doubleValue],
          .duration = [payload[@"duration"] doubleValue],
      };
      emitter.onProgress(e);
      return;
    }

    if ([type isEqualToString:@"Seek"]) {
      VLCPlayerEventEmitter::OnSeek e = {
          .currentTime = [payload[@"currentTime"] doubleValue],
          .duration = [payload[@"duration"] doubleValue],
      };
      emitter.onSeek(e);
      return;
    }

    if ([type isEqualToString:@"Snapshot"]) {
      VLCPlayerEventEmitter::OnSnapshot e = {.isSuccess = [payload[@"isSuccess"] intValue]};
      emitter.onSnapshot(e);
      return;
    }

    if ([type isEqualToString:@"Metadata"]) {
      VLCPlayerEventEmitter::OnMetadata e = {
          .type = [self stdString:payload[@"type"]],
          .title = [self stdString:payload[@"title"]],
          .artist = [self stdString:payload[@"artist"]],
          .genre = [self stdString:payload[@"genre"]],
          .copyright = [self stdString:payload[@"copyright"]],
          .album = [self stdString:payload[@"album"]],
          .tracknumber = [self stdString:payload[@"tracknumber"]],
          .description = [self stdString:payload[@"description"]],
          .rating = [self stdString:payload[@"rating"]],
          .date = [self stdString:payload[@"date"]],
          .language = [self stdString:payload[@"language"]],
          .publisher = [self stdString:payload[@"publisher"]],
          .encodedby = [self stdString:payload[@"encodedby"]],
          .artwork = [self stdString:payload[@"artwork"]],
          .trackid = [self stdString:payload[@"trackid"]],
          .tracktotal = [self stdString:payload[@"tracktotal"]],
          .director = [self stdString:payload[@"director"]],
          .season = [self stdString:payload[@"season"]],
          .episode = [self stdString:payload[@"episode"]],
          .showname = [self stdString:payload[@"showname"]],
          .albumartist = [self stdString:payload[@"albumartist"]],
          .discnumber = [self stdString:payload[@"discnumber"]],
      };
      emitter.onMetadata(e);
      return;
    }

    if ([type isEqualToString:@"Tracks"]) {
      VLCPlayerEventEmitter::OnTracks e = {
          .audio = [self stdString:payload[@"audio"]],
          .audioIndex = [payload[@"audioIndex"] intValue],
          .subtitle = [self stdString:payload[@"subtitle"]],
          .subtitleIndex = [payload[@"subtitleIndex"] intValue],
      };
      emitter.onTracks(e);
      return;
    }

    if ([type isEqualToString:@"RequestNext"]) {
      VLCPlayerEventEmitter::OnRequestNext e = {};
      emitter.onRequestNext(e);
      return;
    }

    if ([type isEqualToString:@"RequestPrevious"]) {
      VLCPlayerEventEmitter::OnRequestPrevious e = {};
      emitter.onRequestPrevious(e);
      return;
    }

    if ([type isEqualToString:@"Opening"]) {
      VLCPlayerEventEmitter::OnLoad loadEvent = {
          .currentTime = [payload[@"currentTime"] doubleValue],
          .duration = [payload[@"duration"] doubleValue],
      };
      emitter.onLoad(loadEvent);
      return;
    }

    // All remaining state events share the same payload shape (they are each
    // separate codegen'd structs, so fill via a generic lambda).
    auto withState = [&](auto &event) {
      event.type = [self stdString:type];
      event.currentTime = [payload[@"currentTime"] doubleValue];
      event.duration = [payload[@"duration"] doubleValue];
      event.isPlaying = [payload[@"isPlaying"] boolValue];
      event.hasVideoOut = [payload[@"hasVideoOut"] boolValue];
      event.isBuffering = [payload[@"isBuffering"] boolValue];
      event.bufferRate = [payload[@"bufferRate"] doubleValue];
    };

    if ([type isEqualToString:@"Playing"]) {
      VLCPlayerEventEmitter::OnPlaying e = {};
      withState(e);
      emitter.onPlaying(e);
    } else if ([type isEqualToString:@"Paused"]) {
      VLCPlayerEventEmitter::OnPaused e = {};
      withState(e);
      emitter.onPaused(e);
    } else if ([type isEqualToString:@"Ended"]) {
      VLCPlayerEventEmitter::OnEnd e = {};
      withState(e);
      emitter.onEnd(e);
    } else if ([type isEqualToString:@"Error"]) {
      VLCPlayerEventEmitter::OnError e = {};
      withState(e);
      emitter.onError(e);
    } else if ([type isEqualToString:@"Stopped"]) {
      VLCPlayerEventEmitter::OnStopped e = {};
      withState(e);
      emitter.onStopped(e);
    } else if ([type isEqualToString:@"Buffering"]) {
      VLCPlayerEventEmitter::OnBuffer e = {};
      withState(e);
      emitter.onBuffer(e);
    }
  });
}

- (std::string)stdString:(id)value
{
  if (![value isKindOfClass:[NSString class]]) {
    return "";
  }
  return std::string([value UTF8String]);
}

#pragma mark - RCTComponentViewProtocol

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<VLCPlayerComponentDescriptor>();
}

- (void)prepareForRecycle
{
  // Fabric may move a removed component view into a recycling pool. Release
  // playback here and in the Swift `deinit`/`invalidate` so the player stops
  // when the JS component unmounts, even if the view stays alive.
  [_playerView releasePlayer];
  _lastSource = nil;
  [super prepareForRecycle];
}

- (void)invalidate
{
  // Unmounted component that will not be recycled. Release resources.
  [_playerView releasePlayer];
  _lastSource = nil;
  [super invalidate];
}

- (void)handleCommand:(const NSString *)commandName args:(const NSArray *)args
{
  RCTVLCPlayerHandleCommand(self, commandName, args);
}

#pragma mark - RCTVLCPlayerViewProtocol (commands)

- (void)updateProps:(Props::Shared const &)props oldProps:(Props::Shared const &)oldProps
{
  const auto &newProps = *std::static_pointer_cast<VLCPlayerProps const>(props);

  [_playerView setPaused:newProps.paused];
  [_playerView setMuted:newProps.muted];
  [_playerView setRepeat:newProps.repeat];
  [_playerView setRate:newProps.rate];
  [_playerView setVolume:(int)newProps.volume];
  [_playerView setAutoAspectRatio:newProps.autoAspectRatio];
  [_playerView setProgressUpdateInterval:newProps.progressUpdateInterval];
  [_playerView setContinueAudioInBackground:newProps.continueAudioInBackground];
  [_playerView setShowNowPlaying:newProps.showNowPlaying];
  [_playerView setNextTrackEnabled:newProps.nextTrackEnabled];
  [_playerView setPreviousTrackEnabled:newProps.previousTrackEnabled];

  const auto &meta = newProps.nowPlayingMetadata;
  NSMutableDictionary *md = [NSMutableDictionary dictionary];
  if (!meta.title.empty()) md[@"title"] = [NSString stringWithUTF8String:meta.title.c_str()];
  if (!meta.artist.empty()) md[@"artist"] = [NSString stringWithUTF8String:meta.artist.c_str()];
  if (!meta.album.empty()) md[@"album"] = [NSString stringWithUTF8String:meta.album.c_str()];
  if (!meta.albumArtist.empty()) md[@"albumArtist"] = [NSString stringWithUTF8String:meta.albumArtist.c_str()];
  if (!meta.genre.empty()) md[@"genre"] = [NSString stringWithUTF8String:meta.genre.c_str()];
  if (meta.trackNumber > 0) md[@"trackNumber"] = @(meta.trackNumber);
  if (meta.discNumber > 0) md[@"discNumber"] = @(meta.discNumber);
  if (!meta.artwork.empty()) md[@"artwork"] = [NSString stringWithUTF8String:meta.artwork.c_str()];
  [_playerView setNowPlayingMetadata:md.count > 0 ? md : nil];

  if (!newProps.resizeMode.empty()) {
    [_playerView setResizeMode:[NSString stringWithUTF8String:newProps.resizeMode.c_str()]];
  }
  if (!newProps.videoAspectRatio.empty()) {
    [_playerView setVideoAspectRatio:[NSString stringWithUTF8String:newProps.videoAspectRatio.c_str()]];
  }

  const auto &src = newProps.source;
  if (!src.uri.empty()) {
    NSMutableArray *mediaOptions = [NSMutableArray array];
    for (const auto &s : src.mediaOptions) {
      [mediaOptions addObject:[NSString stringWithUTF8String:s.c_str()]];
    }
    NSMutableArray *initOptions = [NSMutableArray array];
    for (const auto &s : src.initOptions) {
      [initOptions addObject:[NSString stringWithUTF8String:s.c_str()]];
    }
    NSDictionary *srcDict = @{
      @"uri" : [NSString stringWithUTF8String:src.uri.c_str()],
      @"mediaOptions" : mediaOptions,
      @"initOptions" : initOptions,
      @"isNetwork" : @(src.isNetwork),
      @"autoplay" : @(src.autoplay),
      @"hwDecoderEnabled" : @(src.hwDecoderEnabled),
    };
    // `updateProps:` fires on every prop change. Only rebuild the player when
    // the source actually changed, so changing `resizeMode`/`paused`/etc. does
    // not restart playback.
    if (![_lastSource isEqualToDictionary:srcDict]) {
      [_playerView setSource:srcDict];
      _lastSource = [srcDict copy];
    }
  }

  [super updateProps:props oldProps:oldProps];
}

- (void)play
{
  [_playerView play];
}

- (void)pause
{
  [_playerView pause];
}

- (void)seekTo:(double)timeSeconds
{
  [_playerView seekTo:timeSeconds];
}

- (void)snapshot:(NSString *)path
{
  [_playerView snapshotTo:path];
}

- (void)getMetadata
{
  [_playerView getMetadata];
}

- (void)clear
{
  [_playerView releasePlayer];
  _lastSource = nil;
}

- (void)changeVideoAspectRatio:(NSString *)ratio
{
  [_playerView setVideoAspectRatio:ratio];
}

- (void)getTracks
{
  [_playerView getTracks];
}

- (void)selectAudioTrack:(NSInteger)index
{
  [_playerView selectAudioTrack:index];
}

- (void)selectSubtitleTrack:(NSInteger)index
{
  [_playerView selectSubtitleTrack:index];
}

- (void)setSubtitleFile:(NSString *)path
{
  [_playerView setSubtitleFile:path];
}

- (void)setAudioDelay:(double)micros
{
  [_playerView setAudioDelay:(int64_t)micros];
}

- (void)setSubtitleDelay:(double)micros
{
  [_playerView setSubtitleDelay:(int64_t)micros];
}

- (void)setEqualizerEnabled:(BOOL)enabled
{
  [_playerView setEqualizerEnabled:enabled];
}

- (void)setEqualizerPreset:(NSInteger)index
{
  [_playerView setEqualizerPreset:index];
}

- (void)setEqualizerPreamp:(double)value
{
  [_playerView setEqualizerPreamp:value];
}

- (void)setEqualizerBandGains:(NSString *)gains
{
  [_playerView setEqualizerBandGains:gains];
}

@end
