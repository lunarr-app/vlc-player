import { forwardRef } from 'react';
import type { VLCPlayerProps, VLCPlayerRef } from './types';

export {
  VLCPlayerSource,
  VLCPlayerProps,
  VLCPlayerRef,
  VLCProgressEvent,
  VLCStateChangeEvent,
  VLCMetaEvent,
  VLCMetadataEvent,
  VLCSnapshotEvent,
} from './types';

const NoopView = forwardRef<VLCPlayerRef, VLCPlayerProps>(function NoopView() {
  return null;
});

export default NoopView;
