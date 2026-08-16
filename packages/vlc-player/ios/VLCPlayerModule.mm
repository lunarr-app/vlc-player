#import "VLCPlayerModule.h"
#import <VideoToolbox/VideoToolbox.h>

@implementation VLCPlayerModule

+ (NSString *)moduleName
{
  return @"VLCPlayerModule";
}

- (NSNumber *)supportsHardwareCodecs
{
  // Advise whether the platform has a hardware decoder for typical profiles.
  bool hasH264 = (bool)VTIsHardwareDecodeSupported(kCMVideoCodecType_H264);
  bool hasHEVC = (bool)VTIsHardwareDecodeSupported(kCMVideoCodecType_HEVC);
  return @(hasH264 || hasHEVC);
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativeVlcPlayerModuleSpecJSI>(params);
}

@end
