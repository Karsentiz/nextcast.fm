package de.danoeh.antennapod.ads.playback;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import de.danoeh.antennapod.ads.AdsManager;
import de.danoeh.antennapod.ads.ima.AudioAdManager;

/**
 * Handler for integrating audio ads with the playback service.
 *
 * Usage:
 * 1. Create instance: new AudioAdPlaybackHandler(context, callback)
 * 2. Before starting content playback, call: checkAndPlayPreroll(episodeId)
 * 3. Wait for callback.onProceedWithContent() before starting content
 *
 * This handler:
 * - Checks if an audio pre-roll should play
 * - Manages ad playback state
 * - Ensures content starts at correct position after ad
 * - Handles errors gracefully (always proceeds to content)
 */
public class AudioAdPlaybackHandler implements AudioAdManager.AudioAdCallback {

    /**
     * Callback for playback events.
     */
    public interface PlaybackCallback {
        /**
         * Called when ready to proceed with content playback.
         * This is called either after the ad completes or if no ad should play.
         */
        void onProceedWithContent();

        /**
         * Called when content should be paused for an ad.
         * Save current position and pause content.
         */
        void onPauseContentForAd();

        /**
         * Called when ad playback is in progress.
         * Can be used to update UI.
         */
        void onAdPlaybackStarted();

        /**
         * Called when ad playback ends (success or failure).
         */
        void onAdPlaybackEnded();
    }

    private final Context context;
    private final PlaybackCallback callback;
    private final Handler mainHandler;

    private AdsManager adsManager;
    private AudioAdManager audioAdManager; // May be null if initialization failed
    private boolean isWaitingForAd = false;
    private long pendingEpisodeId = -1;

    public AudioAdPlaybackHandler(@NonNull Context context, @NonNull PlaybackCallback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());

        this.adsManager = AdsManager.getInstance(context);
        this.audioAdManager = adsManager.getAudioAdManager(); // May return null
    }

    /**
     * Check if an audio pre-roll should play and handle it.
     *
     * Call this BEFORE starting content playback.
     * Wait for onProceedWithContent() callback before playing content.
     *
     * @param episodeId The episode ID about to play
     */
    public void checkAndPlayPreroll(long episodeId) {
        // Record episode start for frequency tracking
        adsManager.recordEpisodeStart();

        // Check if audio ad manager is available
        if (audioAdManager == null) {
            callback.onProceedWithContent();
            return;
        }

        // Check if we should play an audio ad
        if (!adsManager.shouldPlayAudioAd()) {
            // No ad needed, proceed immediately
            callback.onProceedWithContent();
            return;
        }

        // Request ad
        this.pendingEpisodeId = episodeId;
        this.isWaitingForAd = true;

        audioAdManager.requestAd(episodeId, this);
    }

    /**
     * Cancel any pending ad request.
     * Use this if user navigates away before ad loads.
     */
    public void cancelPendingAd() {
        if (isWaitingForAd && audioAdManager != null) {
            isWaitingForAd = false;
            audioAdManager.skipAd();
        }
    }

    /**
     * Check if an ad is currently playing.
     */
    public boolean isAdPlaying() {
        return audioAdManager != null && audioAdManager.isAdPlaying();
    }

    /**
     * Pause the current ad (e.g., when phone call comes in).
     */
    public void pauseAd() {
        if (audioAdManager != null) {
            audioAdManager.pauseAd();
        }
    }

    /**
     * Resume the paused ad.
     */
    public void resumeAd() {
        if (audioAdManager != null) {
            audioAdManager.resumeAd();
        }
    }

    /**
     * Skip the current ad and proceed to content.
     */
    public void skipAd() {
        if (audioAdManager != null) {
            audioAdManager.skipAd();
        }
        isWaitingForAd = false;
        callback.onProceedWithContent();
    }

    /**
     * Release resources.
     */
    public void release() {
        if (audioAdManager != null) {
            audioAdManager.release();
        }
    }

    // ============================================
    // AUDIO AD CALLBACKS
    // ============================================

    @Override
    public void onAdLoaded() {
        // Ad is loaded, start playback
        mainHandler.post(() -> {
            callback.onAdPlaybackStarted();
            audioAdManager.playAd();
        });
    }

    @Override
    public void onAdSkipped(String reason) {
        // No ad to show, proceed with content
        mainHandler.post(() -> {
            isWaitingForAd = false;
            callback.onProceedWithContent();
        });
    }

    @Override
    public void onAdError(String error) {
        // Error occurred, proceed with content (fail gracefully)
        mainHandler.post(() -> {
            isWaitingForAd = false;
            callback.onAdPlaybackEnded();
            callback.onProceedWithContent();
        });
    }

    @Override
    public void onContentPauseRequested() {
        // Content should be paused for ad
        mainHandler.post(() -> {
            callback.onPauseContentForAd();
        });
    }

    @Override
    public void onContentResumeRequested() {
        // Ad is done, resume content
        mainHandler.post(() -> {
            isWaitingForAd = false;
            callback.onAdPlaybackEnded();
            callback.onProceedWithContent();
        });
    }
}
