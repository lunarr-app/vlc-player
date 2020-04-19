import React from "react";
import { StyleSheet, View, Platform } from "react-native";
import resolveAssetSource from "react-native/Libraries/Image/resolveAssetSource";
import PropTypes from "prop-types";
import Player from "./VLCPlayer";

export default class VLCPlayer extends React.PureComponent {
  constructor(props, context) {
    super(props, context);
    this.seek = this.seek.bind(this);
    this.resume = this.resume.bind(this);
    this.play = this.play.bind(this);
    this.snapshot = this.snapshot.bind(this);
    this._onProgress = this._onProgress.bind(this);
    this._onLoadStart = this._onLoadStart.bind(this);
    this._onSnapshot = this._onSnapshot.bind(this);
    this._onVideoStateChange = this._onVideoStateChange.bind(this);
    this.clear = this.clear.bind(this);
    this.changeVideoAspectRatio = this.changeVideoAspectRatio.bind(this);
  }

  static defaultProps = {
    autoplay: true,
    initType: 1,
    initOptions: [],
    style: {}
  };

  setNativeProps(nativeProps) {
    this._video.setNativeProps(nativeProps);
  }

  clear() {
    this.setNativeProps({ clear: true });
  }

  seek(timeSec) {
    if (Platform.OS === "ios") {
      this.setNativeProps({ seekTime: timeSec });
    } else {
      this.setNativeProps({ seek: timeSec });
    }
  }

  autoAspectRatio(isAuto) {
    this.setNativeProps({ autoAspectRatio: isAuto });
  }

  changeVideoAspectRatio(ratio) {
    this.setNativeProps({ videoAspectRatio: ratio });
  }

  play(paused) {
    this.setNativeProps({ paused: paused });
  }

  position(position) {
    this.setNativeProps({ position: position });
  }

  resume(isResume) {
    this.setNativeProps({ resume: isResume });
  }

  snapshot(path) {
    this.setNativeProps({ snapshotPath: path });
  }

  _onVideoStateChange({ nativeEvent }) {
    switch (nativeEvent.type) {
      case "Opening":
        this.props.onLoad && this.props.onLoad(nativeEvent);
        break;
      case "Stopped":
        this.props.onStopped && this.props.onStopped(nativeEvent);
        break;
      case "Ended":
        this.props.onEnd && this.props.onEnd(nativeEvent);
        break;
      case "Buffering":
        this.props.onBuffer && this.props.onBuffer(nativeEvent);
        break;
      case "Error":
        this.props.onError && this.props.onError(nativeEvent);
        break;
      default:
        this.props.onVideoStateChange &&
          this.props.onVideoStateChange(nativeEvent);
    }
  }

  _onLoadStart(event) {
    if (this.props.onLoadStart) {
      this.props.onLoadStart(event.nativeEvent);
    }
  }

  _onProgress(event) {
    if (this.props.onProgress) {
      this.props.onProgress(event.nativeEvent);
    }
  }

  _onSnapshot(event) {
    if (this.props.onSnapshot) {
      this.props.onSnapshot(event.nativeEvent);
    }
  }

  render() {
    const source = resolveAssetSource({ ...this.props.source }) || {};
    let uri = source.uri || "";
    let isNetwork = !!(uri && uri.match(/^https?:/));
    const isAsset = !!(
      uri && uri.match(/^(assets-library|file|content|ms-appx|ms-appdata):/)
    );
    if (!isAsset) {
      isNetwork = true;
    }
    if (uri && uri.match(/^\//)) {
      isNetwork = false;
    }
    if (Platform.OS === "ios") {
      source.mediaOptions = this.props.mediaOptions || {};
    } else {
      let mediaOptionsList = [];
      let mediaOptions = this.props.mediaOptions || {};
      let keys = Object.keys(mediaOptions);
      for (let i = 0; i < keys.length - 1; i++) {
        let optionKey = keys[i];
        let optionValue = mediaOptions[optionKey];
        mediaOptionsList.push(optionKey + "=" + optionValue);
      }
      source.mediaOptions = mediaOptionsList;
    }
    source.initOptions = this.props.initOptions;
    source.isNetwork = isNetwork;
    source.autoplay = this.props.autoplay;
    if (
      !isNaN(this.props.hwDecoderEnabled) &&
      !isNaN(this.props.hwDecoderForced)
    ) {
      source.hwDecoderEnabled = this.props.hwDecoderEnabled;
      source.hwDecoderForced = this.props.hwDecoderForced;
    }
    source.initType = this.props.initType;

    //repeat the input media
    //source.initOptions.push('--input-repeat=1000');
    const nativeProps = {
      ...this.props,
      source,
      style: [styles.base, this.props.style],
      onVideoLoadStart: this._onLoadStart,
      onVideoProgress: this._onProgress,
      onVideoStateChange: this._onVideoStateChange,
      onSnapshot: this._onSnapshot
    };

    return (
      <Player
        ref={ref => {
          this._video = ref;
        }}
        {...nativeProps}
      />
    );
  }
}

VLCPlayer.propTypes = {
  /* Native only */
  rate: PropTypes.number,
  seek: PropTypes.number,
  resume: PropTypes.bool,
  position: PropTypes.number,
  snapshotPath: PropTypes.string,
  paused: PropTypes.bool,
  autoAspectRatio: PropTypes.bool,
  videoAspectRatio: PropTypes.string,

  /* Volume: 0-200 */
  volume: PropTypes.number,
  volumeUp: PropTypes.number,
  volumeDown: PropTypes.number,
  repeat: PropTypes.bool,
  muted: PropTypes.bool,

  hwDecoderEnabled: PropTypes.number,
  hwDecoderForced: PropTypes.number,

  onVideoLoadStart: PropTypes.func,
  onVideoStateChange: PropTypes.func,
  onVideoProgress: PropTypes.func,
  onSnapshot: PropTypes.func,
  onOpen: PropTypes.func,
  onLoadStart: PropTypes.func,

  /* Wrapper component */
  source: PropTypes.oneOfType([PropTypes.object, PropTypes.number]),
  play: PropTypes.func,
  snapshot: PropTypes.func,
  onError: PropTypes.func,
  onProgress: PropTypes.func,
  onBuffer: PropTypes.func,
  onEnd: PropTypes.func,
  onStopped: PropTypes.func,

  /* Required by react-native */
  scaleX: PropTypes.number,
  scaleY: PropTypes.number,
  translateX: PropTypes.number,
  translateY: PropTypes.number,
  rotation: PropTypes.number,
  ...View.propTypes
};

const styles = StyleSheet.create({
  base: {
    overflow: "hidden"
  }
});
