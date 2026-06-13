## @lunarr/vlc-player

VLC Player for react-native based on `xuyuanzhou/react-native-yz-vlcplayer`, `delia-m/react-native-yz-vlcplayer` and `react-native-vlc-media-player` to be used on Lunarr.

Uses **MobileVLCKit 3.7.3** (iOS) and **libvlc-all 3.6.5** (Android).

## Installation

#### React Native 0.60+

`yarn add @lunarr/vlc-player`

#### Additional step for iOS

Set `Enable Bitcode` to `NO`

Build Settings ---> search Bitcode

![disable bitcode](https://raw.githubusercontent.com/xuyuanzhou/react-native-yz-vlcplayer/master/images/4.png)

After installing, run `pod install --repo-update` in your app's `ios` directory.

For Android, no extra Maven repository is required — `libvlc-all` is resolved from Maven Central. Run `./gradlew clean` after upgrading.

## Example

```js
import Video from "@lunarr/vlc-player";

<Video
  ref={videoRef}
  source={{ uri: url }}
  style={styles.video}
  onProgress={({ currentTime, duration }) => {
    setState({
      currentTime, // seconds
      duration, // seconds
    });
  }}
  onSeek={({ currentTime, duration }) => {
    setState({
      currentTime, // seconds
      duration, // seconds
    });
  }}
  onEnd={() => {
    setState({ play: false });
  }}
  onError={() => {
    setState({
      play: false,
      error: {
        error: "Oops!",
        message:
          "There was an error playing this video, please try again later.",
      },
    });
  }}
  paused={!state.play}
/>;
```
