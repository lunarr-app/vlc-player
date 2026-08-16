import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  /**
   * Whether the underlying VLC stack can decode hardware-accelerated video.
   * iOS probes VideoToolbox (H.264/HEVC). Android always reports true because
   * libvlc auto-selects a hardware-accelerated decoder (MediaCodec) where
   * available. Used to decide direct-play advertising.
   */
  supportsHardwareCodecs(): boolean;
}

export default TurboModuleRegistry.getEnforcing<Spec>('VLCPlayerModule');
