#import <React/RCTViewComponentView.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * Fabric native component view for `VLCPlayer`.
 *
 * Must be written in Objective-C++ per React Native's Fabric requirements.
 * It owns a content view (a Swift `VLCPlayerView`) where all libvlc logic
 * lives, forwards props/commands in, and emits events out via the codegen'd
 * event emitter.
 */
@interface VLCPlayerComponentView : RCTViewComponentView

@end

NS_ASSUME_NONNULL_END
