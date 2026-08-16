#import <Foundation/Foundation.h>
#import <ReactCommon/RCTTurboModule.h>
#import <VLCPlayerSpec/VLCPlayerSpec.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * TurboModule provider for `VLCPlayerModule` (see codegenConfig `ios.modules`).
 *
 * Implemented in Objective-C++ because TurboModules cannot be written purely in
 * Swift, the provider is instantiated by the generated RCTModuleProviders and
 * must respond to `getTurboModule:`.
 */
@interface VLCPlayerModule : NativeVlcPlayerModuleSpecBase <NativeVlcPlayerModuleSpec>
@end

NS_ASSUME_NONNULL_END
