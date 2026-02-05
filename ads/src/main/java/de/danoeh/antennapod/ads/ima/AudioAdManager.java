package de.danoeh.antennapod.ads.ima;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.player.ContentProgressProvider;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;

import de.danoeh.antennapod.ads.config.AdUnitPaths;
import de.danoeh.antennapod.ads.config.AdsConfig;
import de.danoeh.antennapod.ads.logging.AdType;
import de.danoeh.antennapod.ads.logging.AdsLogger;
import de.danoeh.antennapod.ads.policy.AdsPolicyManager;

/**
 * Manages audio pre-roll ads using the IMA SDK.
 *
 * Behavior:
 * - Triggered once every 2 episode starts
 * - Insert before content playback begins or immediately after buffering resolves
 * - Works while playback runs in background/foreground service
 *
 * Audio Rules:
 * - Pause content → play ad → resume content at correct position
 * - Do NOT play during seeking/scrubbing
 * - Apply short load timeout
 * - On no-fill/error/timeout → resume content immediately
 * - Never retry in a loop
 */
public class AudioAdManager implements AdsLoader.AdsLoadedListener,
        AdErrorEvent.AdErrorListener, AdEvent.AdEventListener {

    private static final String TAG = "AudioAdManager";
    private static final String AD_UNIT = AdUnitPaths.AUDIO_PREROLL;

    public enum State {
        IDLE,
        LOADING,
        READY,
        PLAYING,
        COMPLETED,
        ERROR
    }

    private final Context context;
    private final AdsConfig config;
    private final AdsLogger logger;
    private final AdsPolicyManager policy;
    private final Handler mainHandler;

    private ImaSdkFactory sdkFactory;
    private AdsLoader adsLoader;
    private AdsManager adsManager;
    private AdDisplayContainer adDisplayContainer;

    private State state = State.IDLE;
    private AudioAdCallback callback;
    private Long currentEpisodeId;
    private Runnable timeoutRunnable;

    public AudioAdManager(Context context) {
        this.context = context.getApplicationContext();
        this.config = AdsConfig.getInstance(context);
        this.logger = AdsLogger.getInstance(context);
        this.policy = AdsPolicyManager.getInstance(context);
        this.mainHandler = new Handler(Looper.getMainLooper());

        initIma();
    }

    private void initIma() {
        sdkFactory = ImaSdkFactory.getInstance();

        ImaSdkSettings settings = sdkFactory.createImaSdkSettings();
        settings.setDebugMode(config.isDebugLogging());

        // Create audio-only ad display container
        adDisplayContainer = ImaSdkFactory.createAudioAdDisplayContainer(context, null);

        adsLoader = sdkFactory.createAdsLoader(context, settings, adDisplayContainer);
        adsLoader.addAdsLoadedListener(this);
        adsLoader.addAdErrorListener(this);
    }

    /**
     * Check if an audio ad should play for this episode start.
     *
     * @return true if ad should play
     */
    public boolean shouldPlayAd() {
        return policy.shouldPlayAudioAd();
    }

    /**
     * Request an audio pre-roll ad.
     *
     * @param episodeId The episode ID for context
     * @param callback  Callback for ad events
     */
    public void requestAd(long episodeId, @NonNull AudioAdCallback callback) {
        if (!config.isAudioAdsEnabled()) {
            callback.onAdSkipped("Audio ads disabled");
            return;
        }

        if (!policy.shouldPlayAudioAd()) {
            callback.onAdSkipped("Frequency cap not met");
            return;
        }

        this.currentEpisodeId = episodeId;
        this.callback = callback;
        this.state = State.LOADING;

        logger.logOpportunity(AdType.AUDIO_PREROLL, AD_UNIT, "playback", episodeId);
        logger.logRequest(AdType.AUDIO_PREROLL, AD_UNIT, "playback");

        // Build VAST URL
        String vastUrl = AdUnitPaths.buildAudioPrerollVastUrl(null);

        // Create ads request
        AdsRequest request = sdkFactory.createAdsRequest();
        request.setAdTagUrl(vastUrl);

        // Set content progress provider (for audio, we use a simple one)
        request.setContentProgressProvider(new ContentProgressProvider() {
            @Override
            public VideoProgressUpdate getContentProgress() {
                return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
            }
        });

        // Start timeout
        startLoadTimeout();

        // Request ads
        adsLoader.requestAds(request);
    }

    /**
     * Start playing the loaded ad.
     * Call this when content is ready to play.
     */
    public void playAd() {
        if (state != State.READY || adsManager == null) {
            if (callback != null) {
                callback.onAdSkipped("Ad not ready");
            }
            return;
        }

        state = State.PLAYING;

        AdsRenderingSettings renderingSettings = sdkFactory.createAdsRenderingSettings();
        adsManager.init(renderingSettings);
    }

    /**
     * Pause the current ad.
     */
    public void pauseAd() {
        if (adsManager != null && state == State.PLAYING) {
            adsManager.pause();
            logger.log(de.danoeh.antennapod.ads.logging.AdEvent.PAUSED,
                    AdType.AUDIO_PREROLL, AD_UNIT, "playback", currentEpisodeId, null);
        }
    }

    /**
     * Resume the paused ad.
     */
    public void resumeAd() {
        if (adsManager != null && state == State.PLAYING) {
            adsManager.resume();
            logger.log(de.danoeh.antennapod.ads.logging.AdEvent.RESUMED,
                    AdType.AUDIO_PREROLL, AD_UNIT, "playback", currentEpisodeId, null);
        }
    }

    /**
     * Skip/cancel the current ad.
     */
    public void skipAd() {
        cancelTimeout();
        if (adsManager != null) {
            adsManager.destroy();
            adsManager = null;
        }
        state = State.IDLE;
        if (callback != null) {
            callback.onAdSkipped("User skipped");
        }
    }

    /**
     * Get current state.
     */
    public State getState() {
        return state;
    }

    /**
     * Check if an ad is currently playing.
     */
    public boolean isAdPlaying() {
        return state == State.PLAYING;
    }

    /**
     * Release resources.
     */
    public void release() {
        cancelTimeout();
        if (adsManager != null) {
            adsManager.destroy();
            adsManager = null;
        }
        if (adsLoader != null) {
            adsLoader.removeAdsLoadedListener(this);
            adsLoader.removeAdErrorListener(this);
        }
        state = State.IDLE;
    }

    // ============================================
    // ADS LOADER CALLBACKS
    // ============================================

    @Override
    public void onAdsManagerLoaded(AdsManagerLoadedEvent event) {
        cancelTimeout();
        adsManager = event.getAdsManager();
        adsManager.addAdErrorListener(this);
        adsManager.addAdEventListener(this);

        state = State.READY;
        logger.logFill(AdType.AUDIO_PREROLL, AD_UNIT, "playback");

        if (callback != null) {
            callback.onAdLoaded();
        }
    }

    // ============================================
    // AD ERROR CALLBACKS
    // ============================================

    @Override
    public void onAdError(AdErrorEvent event) {
        cancelTimeout();
        String errorMsg = event.getError().getMessage();
        logger.logError(AdType.AUDIO_PREROLL, AD_UNIT, "playback", currentEpisodeId, errorMsg);

        state = State.ERROR;
        cleanupAdsManager();

        if (callback != null) {
            callback.onAdError(errorMsg);
        }
    }

    // ============================================
    // AD EVENT CALLBACKS
    // ============================================

    @Override
    public void onAdEvent(AdEvent event) {
        switch (event.getType()) {
            case LOADED:
                // Ad is loaded and ready
                break;

            case CONTENT_PAUSE_REQUESTED:
                // Content should pause for ad
                if (callback != null) {
                    callback.onContentPauseRequested();
                }
                break;

            case STARTED:
                logger.log(de.danoeh.antennapod.ads.logging.AdEvent.STARTED,
                        AdType.AUDIO_PREROLL, AD_UNIT, "playback", currentEpisodeId, null);
                logger.logImpression(AdType.AUDIO_PREROLL, AD_UNIT, "playback", currentEpisodeId);
                policy.recordAudioAdPlayed();
                break;

            case CLICKED:
                logger.logClick(AdType.AUDIO_PREROLL, AD_UNIT, "playback");
                break;

            case COMPLETED:
                logger.log(de.danoeh.antennapod.ads.logging.AdEvent.COMPLETED,
                        AdType.AUDIO_PREROLL, AD_UNIT, "playback", currentEpisodeId, null);
                break;

            case CONTENT_RESUME_REQUESTED:
                // Ad finished, content should resume
                state = State.COMPLETED;
                cleanupAdsManager();
                if (callback != null) {
                    callback.onContentResumeRequested();
                }
                break;

            case ALL_ADS_COMPLETED:
                // All ads in the ad break are done
                state = State.COMPLETED;
                cleanupAdsManager();
                break;

            case SKIPPED:
                logger.log(de.danoeh.antennapod.ads.logging.AdEvent.SKIPPED,
                        AdType.AUDIO_PREROLL, AD_UNIT, "playback", currentEpisodeId, null);
                break;

            default:
                break;
        }
    }

    // ============================================
    // TIMEOUT HANDLING
    // ============================================

    private void startLoadTimeout() {
        cancelTimeout();
        timeoutRunnable = () -> {
            if (state == State.LOADING) {
                logger.logError(AdType.AUDIO_PREROLL, AD_UNIT, "playback",
                        currentEpisodeId, "Load timeout");
                state = State.ERROR;
                cleanupAdsManager();
                if (callback != null) {
                    callback.onAdError("Load timeout");
                }
            }
        };
        mainHandler.postDelayed(timeoutRunnable, config.getAudioAdLoadTimeoutMs());
    }

    private void cancelTimeout() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    private void cleanupAdsManager() {
        if (adsManager != null) {
            adsManager.destroy();
            adsManager = null;
        }
    }

    // ============================================
    // CALLBACK INTERFACE
    // ============================================

    /**
     * Callback interface for audio ad events.
     */
    public interface AudioAdCallback {
        /**
         * Called when an ad is loaded and ready to play.
         */
        void onAdLoaded();

        /**
         * Called when ad was skipped (not shown).
         *
         * @param reason Why the ad was skipped
         */
        void onAdSkipped(String reason);

        /**
         * Called when an error occurred.
         *
         * @param error Error message
         */
        void onAdError(String error);

        /**
         * Called when content playback should pause for the ad.
         */
        void onContentPauseRequested();

        /**
         * Called when content playback should resume after the ad.
         */
        void onContentResumeRequested();
    }
}
