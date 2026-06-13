#import "React/RCTConvert.h"
#import "RCTVLCPlayer.h"
#import "React/RCTBridgeModule.h"
#import "React/UIView+React.h"
#import <MobileVLCKit/MobileVLCKit.h>
#import <AVFoundation/AVFoundation.h>
static NSString *const statusKeyPath = @"status";
static NSString *const playbackLikelyToKeepUpKeyPath = @"playbackLikelyToKeepUp";
static NSString *const playbackBufferEmptyKeyPath = @"playbackBufferEmpty";
static NSString *const readyForDisplayKeyPath = @"readyForDisplay";
static NSString *const playbackRate = @"rate";

@interface RCTVLCPlayer () <VLCMediaPlayerDelegate,VLCMediaDelegate>
@end

@implementation RCTVLCPlayer
{

    VLCMediaPlayer *_player;

    NSDictionary * _source;
    BOOL _paused;
    BOOL _started;
    BOOL _autoAspectRatio;
    BOOL _repeat;

}

- (instancetype)init
{
    if ((self = [super init])) {

        [[NSNotificationCenter defaultCenter] addObserver:self
                                                 selector:@selector(applicationWillResignActive:)
                                                     name:UIApplicationWillResignActiveNotification
                                                   object:nil];

        [[NSNotificationCenter defaultCenter] addObserver:self
                                                 selector:@selector(applicationWillEnterForeground:)
                                                     name:UIApplicationWillEnterForegroundNotification
                                                   object:nil];

    }

    return self;
}


- (void)applicationWillResignActive:(NSNotification *)notification
{
    if (!_paused) {
        [self setPaused:_paused];
    }
}

- (void)applicationWillEnterForeground:(NSNotification *)notification
{
    [self applyModifiers];
}

- (void)applyModifiers
{
    if(!_paused)
        [self play];
}

- (void)layoutSubviews
{
    [super layoutSubviews];
    if (_autoAspectRatio && _player && self.bounds.size.width > 0 && self.bounds.size.height > 0) {
        NSString *ratio = [NSString stringWithFormat:@"%d:%d",
                           (int)self.bounds.size.width,
                           (int)self.bounds.size.height];
        char *char_content = [ratio cStringUsingEncoding:NSASCIIStringEncoding];
        [_player setVideoAspectRatio:char_content];
    }
}

- (void)applyHWDecoderOptions:(VLCMedia *)media fromSource:(NSDictionary *)source
{
    NSNumber *hwDecoderEnabled = [source objectForKey:@"hwDecoderEnabled"];
    if (!hwDecoderEnabled) {
        return;
    }
    if ([hwDecoderEnabled intValue] >= 1) {
        [media addOption:@":avcodec-hw=videotoolbox"];
    } else {
        [media addOption:@":avcodec-hw=none"];
    }
}

- (void)setPaused:(BOOL)paused
{
    if(_player){
        if(!paused){
            [self play];
        }else {
            [_player pause];
            _paused =  YES;
            _started = NO;
        }
    }
}

-(void)play
{
    if(_player){
        [_player play];
        _paused = NO;
        _started = YES;
    }
}

-(void)setResume:(BOOL)autoplay
{
    @try{
        char * videoRatio = nil;
        if(_player){
            videoRatio = _player.videoAspectRatio;
            [_player stop];
            _player = nil;
        }
        NSMutableDictionary* mediaOptions = [_source objectForKey:@"mediaOptions"];
        NSArray* options = [_source objectForKey:@"initOptions"];
        NSString* uri    = [_source objectForKey:@"uri"];
        NSInteger initType = [RCTConvert NSInteger:[_source objectForKey:@"initType"]];
        BOOL autoplay = [RCTConvert BOOL:[_source objectForKey:@"autoplay"]];
        BOOL isNetWork   = [RCTConvert BOOL:[_source objectForKey:@"isNetwork"]];
        NSURL* _uri    = [NSURL URLWithString:uri];
        if(uri && uri.length > 0){
            // init player && play
            if(initType == 2){
                _player = [[VLCMediaPlayer alloc] initWithOptions:options];
            }else{
                _player = [[VLCMediaPlayer alloc] init];
            }
            [_player setDrawable:self];
            _player.delegate = self;
            _player.scaleFactor = 0;
            //设置缓存多少毫秒
            // [mediaDictonary setObject:@"1500" forKey:@"network-caching"];
            VLCMedia *media = nil;
            if(isNetWork){
                media = [VLCMedia mediaWithURL:_uri];
            }else{
                media = [VLCMedia mediaWithPath: uri];
            }
            media.delegate = self;
            if(mediaOptions){
                [media addOptions:mediaOptions];
            }
            [self applyHWDecoderOptions:media fromSource:_source];
            /*if(videoRatio){
                _player.videoAspectRatio = videoRatio;
            }*/
            [media parseWithOptions:VLCMediaParseLocal|VLCMediaFetchLocal|VLCMediaParseNetwork|VLCMediaFetchNetwork];
            _player.media = media;
            if(autoplay)
                [self play];
            if(self.onVideoLoadStart){
                self.onVideoLoadStart(@{
                                        @"target": self.reactTag
                                        });
            }
        }
    }
    @catch(NSException *exception){
        NSLog(@"%@", exception);
    }
}

-(void)setSource:(NSDictionary *)source
{
    @try{
        if(_player){
            [_player stop];
            _player = nil;
        }
        _source = source;
        NSMutableDictionary* mediaOptions = [source objectForKey:@"mediaOptions"];
        NSArray* options = [source objectForKey:@"initOptions"];
        NSString* uri    = [source objectForKey:@"uri"];
        NSInteger initType = [RCTConvert NSInteger:[source objectForKey:@"initType"]];
        BOOL autoplay = [RCTConvert BOOL:[source objectForKey:@"autoplay"]];
        BOOL isNetWork   = [RCTConvert BOOL:[source objectForKey:@"isNetwork"]];
        NSURL* _uri    = [NSURL URLWithString:uri];
        if(uri && uri.length > 0){
            // init player && play
            if(initType == 2){
                _player = [[VLCMediaPlayer alloc] initWithOptions:options];
            }else{
                _player = [[VLCMediaPlayer alloc] init];
            }
            [_player setDrawable:self];
            _player.delegate = self;
            _player.scaleFactor = 0;
            //设置缓存多少毫秒
            // [mediaDictonary setObject:@"1500" forKey:@"network-caching"];
            VLCMedia *media = nil;
            if(isNetWork){
                media = [VLCMedia mediaWithURL:_uri];
            }else{
                media = [VLCMedia mediaWithPath: uri];
            }
            if(media){
                media.delegate = self;
                if(mediaOptions){
                    [media addOptions:mediaOptions];
                }
                [self applyHWDecoderOptions:media fromSource:source];
                [media parseWithOptions:VLCMediaParseLocal|VLCMediaFetchLocal|VLCMediaParseNetwork|VLCMediaFetchNetwork];
                 _player.media = media;
            }
            if(autoplay)
                [self play];
            if(self.onVideoLoadStart){
                self.onVideoLoadStart(@{
                                       @"target": self.reactTag
                                     });
            }
        }
    }
    @catch(NSException *exception){
          NSLog(@"%@", exception);
    }
}

- (void)mediaPlayerSnapshot:(NSNotification *)aNotification{
     NSLog(@"userInfo %@",[aNotification userInfo]);
    self.onSnapshot(@{
                      @"target": self.reactTag,
                      @"success": [NSNumber numberWithInt:1],
                    });
}


- (void)mediaMetaDataDidChange:(VLCMedia *)aMedia{
    NSLog(@"mediaMetaDataDidChange");
    NSInteger readBytes = aMedia.numberOfReadBytesOnInput;
    NSLog(@"readBytes %zd", readBytes);
    BOOL isPlaying = _player.isPlaying;
    BOOL hasVideoOut = _player.hasVideoOut;
    self.onVideoStateChange(@{
                              @"target": self.reactTag,
                              @"isPlaying": [NSNumber numberWithBool: isPlaying],
                              @"hasVideoOut": [NSNumber numberWithBool: hasVideoOut],
                              @"type": @"mediaMetaDataDidChange",
                            });
}

- (void)mediaDidFinishParsing:(VLCMedia *)aMedia
{
    NSLog(@"mediaDidFinishParsing");
    BOOL isPlaying = _player.isPlaying;
    BOOL hasVideoOut = _player.hasVideoOut;
    self.onVideoStateChange(@{
                              @"target": self.reactTag,
                              @"isPlaying": [NSNumber numberWithBool: isPlaying],
                              @"hasVideoOut": [NSNumber numberWithBool: hasVideoOut],
                              @"type": @"mediaDidFinishParsing",
                            });
    //NSLog(@"readBytes %zd", readBytes);
}

- (void)mediaPlayerTimeChanged:(NSNotification *)aNotification
{
    [self updateVideoProgress];
}

- (void)mediaPlayerStateChanged:(NSNotification *)aNotification
{
    @try{
        if(_player){

            /*
            NSInteger numberOfReadBytesOnInput = _player.media.numberOfReadBytesOnInput;
            NSInteger numberOfPlayedAudioBuffers =  _player.media.numberOfPlayedAudioBuffers;
            NSInteger numberOfSentBytes = _player.media.numberOfSentBytes;
            NSInteger numberOfReadBytesOnDemux =  _player.media.numberOfReadBytesOnDemux;
            NSInteger numberOfSentPackets =  _player.media.numberOfSentPackets;
            NSInteger numberOfCorruptedDataPackets =  _player.media.numberOfCorruptedDataPackets;
            NSInteger numberOfDisplayedPictures =  _player.media.numberOfDisplayedPictures;
            NSInteger numberOfDecodedVideoBlocks =  _player.media.numberOfDecodedVideoBlocks;
            */
            VLCMediaPlayerState state = _player.state;
            switch (state) {
                case VLCMediaPlayerStateOpening:
                    self.onVideoStateChange(@{
                                              @"target": self.reactTag,
                                              @"type": @"Opening",
                                              @"currentTime": [NSNumber numberWithInt:[[_player time] intValue]],
                                              @"duration": [NSNumber numberWithInt:[_player.media.length intValue]]
                                            });
                    break;
                case VLCMediaPlayerStatePaused:
                    _paused = YES;
                    self.onVideoStateChange(@{
                                              @"target": self.reactTag,
                                              @"type": @"Paused",
                                            });
                    break;
                case VLCMediaPlayerStateStopped:
                    self.onVideoStateChange(@{
                                              @"target": self.reactTag,
                                              @"type": @"Stopped",
                                            });
                    break;
                case VLCMediaPlayerStateBuffering:
                    self.onVideoStateChange(@{
                                              @"target": self.reactTag,
                                              @"isBuffering": [NSNumber numberWithBool: !_player.isPlaying],
                                              @"type": @"Buffering",
                                            });
                    break;
                case VLCMediaPlayerStatePlaying:
                    _paused = NO;
                    self.onVideoStateChange(@{
                                              @"target": self.reactTag,
                                              @"type": @"Playing",
                                            });
                    break;
                case VLCMediaPlayerStateESAdded:
                    self.onVideoStateChange(@{
                                              @"target": self.reactTag,
                                              @"type": @"ESAdded",
                                            });
                    break;
                case VLCMediaPlayerStateEnded:
                    if (_repeat) {
                        [_player setTime:[VLCTime timeWithInt:0]];
                        [self play];
                    } else {
                        self.onVideoStateChange(@{
                                                  @"target": self.reactTag,
                                                  @"type": @"Ended",
                                                });
                    }
                    break;
                case VLCMediaPlayerStateError:
                    self.onVideoStateChange(@{
                                              @"target": self.reactTag,
                                              @"type": @"Error",
                                            });
                    [self _release];
                    break;
            }
        }
    }@catch(NSException *exception){
        NSLog(@"%@", exception);
    }
}

-(void)updateVideoProgress
{   @try{
        if(_player){
            int currentTime  = [[_player time] intValue];
            int duration     = [_player.media.length intValue];

            if( currentTime >= 0 && currentTime < duration) {
                self.onVideoProgress(@{
                                       @"target": self.reactTag,
                                       @"currentTime": [NSNumber numberWithInt:currentTime],
                                       @"duration": [NSNumber numberWithInt:duration],
                                     });
            }
        }
    }
    @catch(NSException *exception){
        NSLog(@"%@", exception);
    }
}

- (void)jumpBackward:(int)interval
{
    if(interval>=0 && interval <= [_player.media.length intValue])
        [_player jumpBackward:interval];
}

- (void)jumpForward:(int)interval
{
    if(interval>=0 && interval <= [_player.media.length intValue])
        [_player jumpForward:interval];
}

/**
 * audio  -----> start
 */
- (void)setMuted:(BOOL)muted
{
    if(_player){
        VLCAudio *audio = _player.audio;
        [audio setMuted: muted];
    }
}

-(void)setVolume:(int)interval
{
    if(_player){
        VLCAudio *audio = _player.audio;
        if(interval >= 0){
            audio.volume = interval;
        }
    }
}

-(void)setVolumeDown:(int)volume
{
    if(_player){

        VLCAudio *audio = _player.audio;
        [audio volumeDown];
    }
}



-(void)setVolumeUp:(int)volume
{
    if(_player){
        VLCAudio *audio = _player.audio;
        [audio volumeUp];
    }
}

//audio  -----> end


-(void)setSeekTime:(int)time{
    if(_player){
        float ms = time * 1000;
        [_player setTime:[VLCTime timeWithInt:ms]];

        int currentTime  = [[_player time] intValue];
        int duration     = [_player.media.length intValue];
        self.onVideoSeek(@{
                               @"target": self.reactTag,
                               @"currentTime": [NSNumber numberWithInt:currentTime],
                               @"duration": [NSNumber numberWithInt:duration],
                             });
    }
}

-(void)setSnapshotPath:(NSString*)path
{
    if(_player)
        [_player saveVideoSnapshotAt:path withWidth:0 andHeight:0];
}

-(void)setRate:(float)rate
{
    [_player setRate:rate];
}

-(void)setClear:(float)clear
{
    [self _release];
}


-(void)setVideoAspectRatio:(NSString *)ratio{
    if(!_autoAspectRatio && ratio != nil && ratio.length > 0 && _player){
        char *char_content = [ratio cStringUsingEncoding:NSASCIIStringEncoding];
        [_player setVideoAspectRatio:char_content];
    }
}

-(void)setAutoAspectRatio:(BOOL)autoAspectRatio
{
    _autoAspectRatio = autoAspectRatio;
    [self setNeedsLayout];
}

-(void)setPosition:(float)position
{
    if(_player && position >= 0 && position <= 1){
        _player.position = position;
    }
}

-(void)setRepeat:(BOOL)repeat
{
    _repeat = repeat;
}

-(void)setMetadata:(BOOL)metadata
{
    if(!metadata || !_player || !_player.media){
        return;
    }
    VLCMediaMetaData *meta = _player.media.metaData;
    if(!meta){
        return;
    }
    NSMutableDictionary *payload = [NSMutableDictionary dictionaryWithDictionary:@{
        @"target": self.reactTag,
        @"type": @"Metadata",
    }];
    if (meta.title) payload[@"title"] = meta.title;
    if (meta.artist) payload[@"artist"] = meta.artist;
    if (meta.genre) payload[@"genre"] = meta.genre;
    if (meta.copyright) payload[@"copyright"] = meta.copyright;
    if (meta.album) payload[@"album"] = meta.album;
    if (meta.trackNumber) payload[@"tracknumber"] = [NSString stringWithFormat:@"%u", meta.trackNumber];
    if (meta.metaDescription) payload[@"description"] = meta.metaDescription;
    if (meta.rating) payload[@"rating"] = meta.rating;
    if (meta.date) payload[@"date"] = meta.date;
    if (meta.language) payload[@"language"] = meta.language;
    if (meta.publisher) payload[@"publisher"] = meta.publisher;
    if (meta.encodedBy) payload[@"encodedby"] = meta.encodedBy;
    if (meta.artworkURL) payload[@"artwork"] = meta.artworkURL.absoluteString;
    if (meta.trackID) payload[@"trackid"] = [NSString stringWithFormat:@"%u", meta.trackID];
    if (meta.trackTotal) payload[@"tracktotal"] = [NSString stringWithFormat:@"%u", meta.trackTotal];
    if (meta.director) payload[@"director"] = meta.director;
    if (meta.season) payload[@"season"] = [NSString stringWithFormat:@"%u", meta.season];
    if (meta.episode) payload[@"episode"] = [NSString stringWithFormat:@"%u", meta.episode];
    if (meta.showName) payload[@"showname"] = meta.showName;
    if (meta.albumArtist) payload[@"albumartist"] = meta.albumArtist;
    if (meta.discNumber) payload[@"discnumber"] = [NSString stringWithFormat:@"%u", meta.discNumber];
    self.onVideoStateChange(payload);
}

- (void)_release
{
    if(_player){
        [_player stop];
        _player = nil;
    }
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

- (void)dealloc{
     [self _release];
}
#pragma mark - Lifecycle

//- (void)willMoveToSuperview:(UIView *)newSuperview
//- (void)didMoveToSuperview

//- (void)willRemoveSubview:(UIView *)subview


- (void)removeFromSuperview
{
    NSLog(@"removeFromSuperview");
    [self _release];
    [super removeFromSuperview];
}

@end
