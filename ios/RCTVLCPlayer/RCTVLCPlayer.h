#import "React/RCTView.h"

@class RCTEventDispatcher;

@interface RCTVLCPlayer : UIView

@property (nonatomic, copy) RCTDirectEventBlock onVideoProgress;
@property (nonatomic, copy) RCTDirectEventBlock onVideoPaused;
@property (nonatomic, copy) RCTDirectEventBlock onVideoStopped;
@property (nonatomic, copy) RCTDirectEventBlock onVideoBuffering;
@property (nonatomic, copy) RCTDirectEventBlock onVideoPlaying;
@property (nonatomic, copy) RCTDirectEventBlock onVideoEnded;
@property (nonatomic, copy) RCTDirectEventBlock onVideoError;
@property (nonatomic, copy) RCTDirectEventBlock onVideoOpen;
@property (nonatomic, copy) RCTDirectEventBlock onVideoLoadStart;
@property (nonatomic, copy) RCTDirectEventBlock onSnapshot;
@property (nonatomic, copy) RCTDirectEventBlock onVideoStateChange;

- (instancetype)initWithEventDispatcher:(RCTEventDispatcher *)eventDispatcher NS_DESIGNATED_INITIALIZER;

@end
