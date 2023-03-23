package com.lunarr.vlc.vlcplayer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import java.util.ArrayList;

@SuppressLint("ViewConstructor")
class ReactVlcPlayerView extends TextureView implements
        LifecycleEventListener,
        TextureView.SurfaceTextureListener,
        AudioManager.OnAudioFocusChangeListener{

    private static final String TAG = "ReactVlcPlayerView";
    private final String tag = "ReactVlcPlayerView";

    private final VideoEventEmitter eventEmitter;
    private LibVLC libvlc;
    private MediaPlayer mMediaPlayer = null;
    private TextureView surfaceView;
    private Surface surfaceVideo;//视频画布
    private boolean isSurfaceViewDestory;
    private int surfaceW, surfaceH;
    //资源路径
    private String src;
    //是否网络资源
    private  boolean netStrTag;
    private ReadableMap srcMap;


    //private Handler mainHandler;
   /* private static final int SURFACE_BEST_FIT = 0;
    private static final int SURFACE_FIT_HORIZONTAL = 1;
    private static final int SURFACE_FIT_VERTICAL = 2;
    private static final int SURFACE_FILL = 3;
    private static final int SURFACE_16_9 = 4;
    private static final int SURFACE_4_3 = 5;
    private static final int SURFACE_ORIGINAL = 6;
    private int mCurrentSize = SURFACE_BEST_FIT;*/
    // Props from React
    /* private Uri srcUri;
    private String extension;
    private boolean repeat;
    private boolean disableFocus;
    private boolean playInBackground = false;*/
    // \ End props

    private int mVideoHeight = 0;
    private int mVideoWidth = 0;
    private int mVideoVisibleHeight = 0;
    private int mVideoVisibleWidth = 0;
    private int mSarNum = 0;
    private int mSarDen = 0;
    private int screenWidth = 0;
    private int screenHeight = 0;
    private boolean isPaused = true;
    private boolean isHostPaused = false;
    private int preVolume = 100;
    private boolean autoAspectRatio = false;

    // React
    private final ThemedReactContext themedReactContext;
    private final AudioManager audioManager;




    public ReactVlcPlayerView(ThemedReactContext context) {
        super(context);
        this.eventEmitter = new VideoEventEmitter(context);
        this.themedReactContext = context;
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        //themedReactContext.addLifecycleEventListener(this);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenHeight = dm.heightPixels;
        screenWidth = dm.widthPixels;
        this.setSurfaceTextureListener(this);
        //surfaceView = this;
        //surfaceView.setZOrderOnTop(false);
        //surfaceView.setZOrderMediaOverlay(false);
       // this.setZOrderOnTop(true);
       // this.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        //this.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
       //this.setZOrderMediaOverlay(true);
        //
        //不过中间那句是OpenGl的，视情况使用，无用可注释掉了，也能实现了透明，但是GLSurfaceView就必须使用

       // this.setZOrderMediaOverlay(false);
        this.addOnLayoutChangeListener(onLayoutChangeListener);
    }


    @Override
    public void setId(int id) {
        super.setId(id);
        eventEmitter.setViewId(id);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        //createPlayer();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopPlayback();
    }

    // LifecycleEventListener implementation

    @Override
    public void onHostResume() {
        if(mMediaPlayer != null && isSurfaceViewDestory && isHostPaused){
            IVLCVout vlcOut =  mMediaPlayer.getVLCVout();
            if(!vlcOut.areViewsAttached()){
               // vlcOut.setVideoSurface(this.getHolder().getSurface(), this.getHolder());
                vlcOut.attachViews(onNewVideoLayoutListener);
                isSurfaceViewDestory = false;
                isPaused = false;
               // this.getHolder().setKeepScreenOn(true);
                mMediaPlayer.play();
            }
        }
    }


    @Override
    public void onHostPause() {
        if(!isPaused && mMediaPlayer != null){
            isPaused = true;
            isHostPaused = true;
            mMediaPlayer.pause();
           // this.getHolder().setKeepScreenOn(false);
            WritableMap map = Arguments.createMap();
            map.putString("type","Paused");
            eventEmitter.onVideoStateChange(map);
        }
        Log.i("onHostPause","---------onHostPause------------>");
    }



    @Override
    public void onHostDestroy() {
        stopPlayback();
    }


    // AudioManager.OnAudioFocusChangeListener implementation
    @Override
    public void onAudioFocusChange(int focusChange) {
    }


    /*************
     * Events  Listener
     *************/

    private View.OnLayoutChangeListener onLayoutChangeListener = new View.OnLayoutChangeListener(){

        @Override
        public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
            if(view.getWidth() > 0 && view.getHeight() > 0 ){
                mVideoWidth = view.getWidth(); // 获取宽度
                mVideoHeight = view.getHeight(); // 获取高度
                if(mMediaPlayer != null) {
                    IVLCVout vlcOut = mMediaPlayer.getVLCVout();
                    vlcOut.setWindowSize(mVideoWidth, mVideoHeight);
                    if(autoAspectRatio){
                        mMediaPlayer.setAspectRatio(mVideoWidth + ":" + mVideoHeight);
                    }
                }
            }
        }
    };

    /**
     * 播放过程中的时间事件监听
     */
    private MediaPlayer.EventListener mPlayerListener = new MediaPlayer.EventListener(){
        @Override
        public void onEvent(MediaPlayer.Event event) {
            WritableMap map = Arguments.createMap();
            // float position = mMediaPlayer.getPosition();
            // map.putDouble("position",position);
            switch (event.type) {
              case MediaPlayer.Event.TimeChanged:
                  map.putDouble("currentTime",mMediaPlayer.getTime());
                  map.putDouble("duration",mMediaPlayer.getLength());
                  eventEmitter.progressChanged(map);
                  break;
                case MediaPlayer.Event.EndReached:
                    map.putString("type","Ended");
                    eventEmitter.onVideoStateChange(map);
                    break;
                case MediaPlayer.Event.Playing:
                    map.putString("type","Playing");
                    eventEmitter.onVideoStateChange(map);
                    break;
                case MediaPlayer.Event.Opening:
                    map.putString("type","Opening");
                    map.putDouble("currentTime",mMediaPlayer.getTime());
                    map.putDouble("duration",mMediaPlayer.getLength());
                    eventEmitter.onVideoStateChange(map);
                    break;
                case MediaPlayer.Event.Paused:
                    map.putString("type","Paused");
                    eventEmitter.onVideoStateChange(map);
                    break;
                case MediaPlayer.Event.Buffering:
                    map.putDouble("bufferRate",event.getBuffering());
                    map.putBoolean("isBuffering",!mMediaPlayer.isPlaying());
                    map.putString("type","Buffering");
                    eventEmitter.onVideoStateChange(map);
                    break;
                case MediaPlayer.Event.Stopped:
                    map.putString("type","Stopped");
                    eventEmitter.onVideoStateChange(map);
                    break;
                case MediaPlayer.Event.EncounteredError:
                    map.putString("type","Error");
                    eventEmitter.onVideoStateChange(map);
                    break;
            }
        }
    };

    private IVLCVout.OnNewVideoLayoutListener onNewVideoLayoutListener = new IVLCVout.OnNewVideoLayoutListener(){
        @Override
        public void onNewVideoLayout(IVLCVout vout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
            if (width * height == 0)
                return;
            // store video size
            mVideoWidth = width;
            mVideoHeight = height;
            mVideoVisibleWidth  = visibleWidth;
            mVideoVisibleHeight = visibleHeight;
            mSarNum = sarNum;
            mSarDen = sarDen;
            WritableMap map = Arguments.createMap();
            map.putInt("mVideoWidth",mVideoWidth);
            map.putInt("mVideoHeight",mVideoHeight);
            map.putInt("mVideoVisibleWidth",mVideoVisibleWidth);
            map.putInt("mVideoVisibleHeight",mVideoVisibleHeight);
            map.putInt("mSarNum",mSarNum);
            map.putInt("mSarDen",mSarDen);
            map.putString("type","onNewVideoLayout");
            eventEmitter.onVideoStateChange(map);
        }
    };

    IVLCVout.Callback callback = new IVLCVout.Callback() {
        @Override
        public void onSurfacesCreated(IVLCVout ivlcVout) {
            isSurfaceViewDestory = false;
        }

        @Override
        public void onSurfacesDestroyed(IVLCVout ivlcVout) {
            isSurfaceViewDestory = true;
        }

    };



    /*************
     * MediaPlayer
     *************/


    private void stopPlayback() {
        onStopPlayback();
        releasePlayer();
    }

    private void onStopPlayback() {
        // setKeepScreenOn(false);
        audioManager.abandonAudioFocus(this);
    }

    private void createPlayer(boolean autoplayResume, boolean isResume) {
        releasePlayer();
        if(this.getSurfaceTexture() == null){
            return;
        }
        try {
            final ArrayList<String> cOptions = new ArrayList<>();
            String uriString = srcMap.hasKey("uri") ? srcMap.getString("uri") : null;
            //String extension = srcMap.hasKey("type") ? srcMap.getString("type") : null;
            boolean isNetwork = srcMap.hasKey("isNetwork") ? srcMap.getBoolean("isNetwork") : false;
            boolean autoplay = srcMap.hasKey("autoplay") ? srcMap.getBoolean("autoplay") : true;
            int initType =     srcMap.hasKey("initType") ? srcMap.getInt("initType") : 1;
            ReadableArray mediaOptions =     srcMap.hasKey("mediaOptions") ? srcMap.getArray("mediaOptions") : null;
            ReadableArray initOptions = srcMap.hasKey("initOptions") ? srcMap.getArray("initOptions") : null;
            Integer hwDecoderEnabled = srcMap.hasKey("hwDecoderEnabled") ? srcMap.getInt("hwDecoderEnabled") : null;
            Integer hwDecoderForced = srcMap.hasKey("hwDecoderForced") ? srcMap.getInt("hwDecoderForced") : null;

            if(initOptions != null){
                ArrayList options = initOptions.toArrayList();
                for(int i=0; i < options.size() - 1 ; i++){
                    String option = (String)options.get(i);
                    cOptions.add(option);
                }
            }
            // Create LibVLC
            if(initType == 1){
                libvlc = new LibVLC(getContext());
            }else{
                libvlc = new LibVLC(getContext(), cOptions);
            }
            // Create media player
            mMediaPlayer = new MediaPlayer(libvlc);
            mMediaPlayer.setEventListener(mPlayerListener);
            //this.getHolder().setKeepScreenOn(true);
            IVLCVout vlcOut =  mMediaPlayer.getVLCVout();
            if(mVideoWidth > 0 && mVideoHeight > 0){
                vlcOut.setWindowSize(mVideoWidth,mVideoHeight);
                if(autoAspectRatio){
                    mMediaPlayer.setAspectRatio(mVideoWidth + ":" + mVideoHeight);
                }
                //mMediaPlayer.setAspectRatio(mVideoWidth+":"+mVideoHeight);
            }
            DisplayMetrics dm = getResources().getDisplayMetrics();
            Media m = null;
            if(isNetwork){
                Uri uri = Uri.parse(uriString);
                m = new Media(libvlc, uri);
            }else{
                m = new Media(libvlc, uriString);
            }
            // m.setEventListener(mMediaListener);
            if(hwDecoderEnabled != null && hwDecoderForced != null){
                boolean hmEnabled = false;
                boolean hmForced  = false;
                if(hwDecoderEnabled >= 1){
                    hmEnabled = true;
                }
                if(hwDecoderForced >= 1){
                    hmForced = true;
                }
                m.setHWDecoderEnabled(hmEnabled, hmForced);
            }
            //添加media  option
            if(mediaOptions != null){
                ArrayList options = mediaOptions.toArrayList();
                for(int i=0; i < options.size() - 1 ; i++){
                    String option = (String)options.get(i);
                     m.addOption(option);
                }
            }
            mMediaPlayer.setMedia(m);
            mMediaPlayer.setScale(0);

            if (!vlcOut.areViewsAttached()) {
                vlcOut.addCallback(callback);
               // vlcOut.setVideoSurface(this.getSurfaceTexture());
                //vlcOut.setVideoSurface(this.getHolder().getSurface(), this.getHolder());
                //vlcOut.attachViews(onNewVideoLayoutListener);
                vlcOut.setVideoSurface(this.getSurfaceTexture());
                vlcOut.attachViews(onNewVideoLayoutListener);
               // vlcOut.attachSurfaceSlave(surfaceVideo,null,onNewVideoLayoutListener);
                //vlcOut.setVideoView(this);
                //vlcOut.attachViews(onNewVideoLayoutListener);
            }
            if(isResume){
                if(autoplayResume){
                    mMediaPlayer.play();
                }
            }else{
                if(autoplay){
                    isPaused = false;
                    mMediaPlayer.play();
                }
            }
            eventEmitter.loadStart();
        } catch (Exception e) {
           e.printStackTrace();
            //Toast.makeText(getContext(), "Error creating player!", Toast.LENGTH_LONG).show();
        }
    }

    private void releasePlayer() {
        if (libvlc == null)
            return;
        mMediaPlayer.stop();
        final IVLCVout vout = mMediaPlayer.getVLCVout();
        vout.removeCallback(callback);
        vout.detachViews();
        //surfaceView.removeOnLayoutChangeListener(onLayoutChangeListener);
        libvlc.release();
        libvlc = null;
    }

    /**
     *  视频进度调整
     * @param time
     */
    public void seekTo(long time) {
        if(mMediaPlayer != null){
            mMediaPlayer.setTime(time);

            WritableMap map = Arguments.createMap();
            map.putDouble("currentTime",mMediaPlayer.getTime());
            map.putDouble("duration",mMediaPlayer.getLength());
            eventEmitter.onVideoSeek(map);
        }
    }

    public void  setPosition(float position){
        if(mMediaPlayer != null) {
            if(position >= 0 && position <= 1){
                mMediaPlayer.setPosition(position);
            }
        }
    }

    /**
     * 设置资源路径
     * @param uri
     * @param isNetStr
     */
    public void setSrc(String uri, boolean isNetStr, boolean autoplay) {
        this.src = uri;
        this.netStrTag = isNetStr;
        createPlayer(autoplay,false);
    }

    public void setSrc(ReadableMap src){
        this.srcMap = src;
        createPlayer(true,false);
    }


    /**
     * 改变播放速率
     * @param rateModifier
     */
    public void setRateModifier(float rateModifier) {
        if(mMediaPlayer != null){
            mMediaPlayer.setRate(rateModifier);
        }
    }


    /**
     * 改变声音大小
     * @param volumeModifier
     */
    public void setVolumeModifier(int volumeModifier) {
        if(mMediaPlayer != null){
            mMediaPlayer.setVolume(volumeModifier);
        }
    }

    /**
     * 改变静音状态
     * @param muted
     */
    public void setMutedModifier(boolean muted) {
        if(mMediaPlayer != null){
            if(muted){
                this.preVolume = mMediaPlayer.getVolume();
                mMediaPlayer.setVolume(0);
            }else{
                mMediaPlayer.setVolume(this.preVolume);
            }
        }
    }

    /**
     * 改变播放状态
     * @param paused
     */
    public void setPausedModifier(boolean paused){
        Log.i("paused:",""+paused+":"+mMediaPlayer);
        if(mMediaPlayer != null){
            if(paused){
                isPaused = true;
                mMediaPlayer.pause();
            }else{
                isPaused = false;
                mMediaPlayer.play();
                Log.i("do play:",true + "");
            }
        }else{
            createPlayer(!paused,false);
        }
    }


    /**
     * 截图
     * @param path
     */
    public void doSnapshot(String path){
       return;
    }


    /**
     * 重新加载视频
     * @param autoplay
     */
    public void doResume(boolean autoplay){
        createPlayer(autoplay,true);
    }


    public void setRepeatModifier(boolean repeat){
    }


    /**
     * 改变宽高比
     * @param aspectRatio
     */
    public void setAspectRatio(String aspectRatio){
        if(!autoAspectRatio && mMediaPlayer != null){
            mMediaPlayer.setAspectRatio(aspectRatio);
        }
    }

    public void setAutoAspectRatio(boolean auto){
        autoAspectRatio = auto;
    }


    public void cleanUpResources() {
        if(surfaceView != null){
            surfaceView.removeOnLayoutChangeListener(onLayoutChangeListener);
        }
        stopPlayback();
    }

    public void getMetadata() {
        if(mMediaPlayer != null){
          WritableMap map = Arguments.createMap();
          Media media = mMediaPlayer.getMedia();
          String artwork = media.getMeta(Media.Meta.ArtworkURL);

          map.putString("type","Metadata");
          map.putString("title",media.getMeta(Media.Meta.Title));
          map.putString("artist",media.getMeta(Media.Meta.Artist));
          map.putString("genre",media.getMeta(Media.Meta.Genre));
          map.putString("copyright",media.getMeta(Media.Meta.Copyright));
          map.putString("album",media.getMeta(Media.Meta.Album));
          map.putString("tracknumber",media.getMeta(Media.Meta.TrackNumber));
          map.putString("description",media.getMeta(Media.Meta.Description));
          map.putString("rating",media.getMeta(Media.Meta.Rating));
          map.putString("date",media.getMeta(Media.Meta.Date));
          map.putString("language",media.getMeta(Media.Meta.Language));
          map.putString("publisher",media.getMeta(Media.Meta.Publisher));
          map.putString("encodedby",media.getMeta(Media.Meta.EncodedBy));
          if (artwork != null) {
            map.putString("artwork",artwork.toString());
          };
          map.putString("trackid",media.getMeta(Media.Meta.TrackID));
          map.putString("tracktotal",media.getMeta(Media.Meta.TrackTotal));
          map.putString("director",media.getMeta(Media.Meta.Director));
          map.putString("season",media.getMeta(Media.Meta.Season));
          map.putString("episode",media.getMeta(Media.Meta.Episode));
          map.putString("showname",media.getMeta(Media.Meta.ShowName));
          map.putString("albumartist",media.getMeta(Media.Meta.AlbumArtist));
          map.putString("discnumber",media.getMeta(Media.Meta.DiscNumber));
          eventEmitter.onVideoStateChange(map);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mVideoWidth = width;
        mVideoHeight = height;
        surfaceVideo = new Surface(surface);
        createPlayer(true,false);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        Log.i("onSurfaceTextureUpdated","onSurfaceTextureUpdated");
    }

    // private final Media.EventListener mMediaListener = new Media.EventListener() {
    //     @Override
    //     public void onEvent(Media.Event event) {
    //         switch (event.type) {
    //             case Media.Event.MetaChanged:
    //                 // New metadata received
    //                 Log.i(tag, "Media.Event.MetaChanged:  =" + event.getMetaId());
    //                 break;
    //             case Media.Event.ParsedChanged:
    //                 Log.i(tag, "Media.Event.ParsedChanged  =" + event.getMetaId());
    //                 break;
    //             case Media.Event.StateChanged:
    //                 Log.i(tag, "StateChanged   =" + event.getMetaId());
    //                 break;
    //             default:
    //                 Log.i(tag, "Media.Event.type=" + event.type + "   eventgetParsedStatus=" + event.getParsedStatus());
    //                 break;
    //
    //         }
    //     }
    // };

    /*private void changeSurfaceSize(boolean message) {

        if (mMediaPlayer != null) {
            final IVLCVout vlcVout = mMediaPlayer.getVLCVout();
            vlcVout.setWindowSize(screenWidth, screenHeight);
        }

        double displayWidth = screenWidth, displayHeight = screenHeight;

        if (screenWidth < screenHeight) {
            displayWidth = screenHeight;
            displayHeight = screenWidth;
        }

        // sanity check
        if (displayWidth * displayHeight <= 1 || mVideoWidth * mVideoHeight <= 1) {
            return;
        }

        // compute the aspect ratio
        double aspectRatio, visibleWidth;
        if (mSarDen == mSarNum) {
            *//* No indication about the density, assuming 1:1 *//*
            visibleWidth = mVideoVisibleWidth;
            aspectRatio = (double) mVideoVisibleWidth / (double) mVideoVisibleHeight;
        } else {
            *//* Use the specified aspect ratio *//*
            visibleWidth = mVideoVisibleWidth * (double) mSarNum / mSarDen;
            aspectRatio = visibleWidth / mVideoVisibleHeight;
        }

        // compute the display aspect ratio
        double displayAspectRatio = displayWidth / displayHeight;

        counter ++;

        switch (mCurrentSize) {
            case SURFACE_BEST_FIT:
                if(counter > 2)
                    Toast.makeText(getContext(), "Best Fit", Toast.LENGTH_SHORT).show();
                if (displayAspectRatio < aspectRatio)
                    displayHeight = displayWidth / aspectRatio;
                else
                    displayWidth = displayHeight * aspectRatio;
                break;
            case SURFACE_FIT_HORIZONTAL:
                Toast.makeText(getContext(), "Fit Horizontal", Toast.LENGTH_SHORT).show();
                displayHeight = displayWidth / aspectRatio;
                break;
            case SURFACE_FIT_VERTICAL:
                Toast.makeText(getContext(), "Fit Horizontal", Toast.LENGTH_SHORT).show();
                displayWidth = displayHeight * aspectRatio;
                break;
            case SURFACE_FILL:
                Toast.makeText(getContext(), "Fill", Toast.LENGTH_SHORT).show();
                break;
            case SURFACE_16_9:
                Toast.makeText(getContext(), "16:9", Toast.LENGTH_SHORT).show();
                aspectRatio = 16.0 / 9.0;
                if (displayAspectRatio < aspectRatio)
                    displayHeight = displayWidth / aspectRatio;
                else
                    displayWidth = displayHeight * aspectRatio;
                break;
            case SURFACE_4_3:
                Toast.makeText(getContext(), "4:3", Toast.LENGTH_SHORT).show();
                aspectRatio = 4.0 / 3.0;
                if (displayAspectRatio < aspectRatio)
                    displayHeight = displayWidth / aspectRatio;
                else
                    displayWidth = displayHeight * aspectRatio;
                break;
            case SURFACE_ORIGINAL:
                Toast.makeText(getContext(), "Original", Toast.LENGTH_SHORT).show();
                displayHeight = mVideoVisibleHeight;
                displayWidth = visibleWidth;
                break;
        }

        // set display size
        int finalWidth = (int) Math.ceil(displayWidth * mVideoWidth / mVideoVisibleWidth);
        int finalHeight = (int) Math.ceil(displayHeight * mVideoHeight / mVideoVisibleHeight);

        SurfaceHolder holder = this.getHolder();
        holder.setFixedSize(finalWidth, finalHeight);

        ViewGroup.LayoutParams lp = this.getLayoutParams();
        lp.width = finalWidth;
        lp.height = finalHeight;
        this.setLayoutParams(lp);
        this.invalidate();
    }*/
}
