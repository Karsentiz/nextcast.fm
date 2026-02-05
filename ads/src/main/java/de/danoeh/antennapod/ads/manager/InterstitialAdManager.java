package de.danoeh.antennapod.ads.manager;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.admanager.AdManagerAdRequest;
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd;
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback;

import de.danoeh.antennapod.ads.config.AdUnitPaths;
import de.danoeh.antennapod.ads.config.AdsConfig;
import de.danoeh.antennapod.ads.logging.AdType;
import de.danoeh.antennapod.ads.logging.AdsLogger;
import de.danoeh.antennapod.ads.logging.AdEvent;
import de.danoeh.antennapod.ads.policy.AdsPolicyManager;

/**
 * Manages video interstitial ad loading and display.
 *
 * Behavior:
 * - Triggered via navigation (not tied to playback)
 * - After every 3 episode starts OR after episode completion
 *
 * Frequency caps:
 * - Max 1 per 10 minutes
 * - Max 2 per session
 *
 * Rules:
 * - Never block navigation or playback
 * - Preload opportunistically
 * - Skip silently if not ready
 */
public class InterstitialAdManager {

    private static final String TAG = "InterstitialAdManager";
    private static final String AD_UNIT = AdUnitPaths.VIDEO_INTERSTITIAL;

    private final Context context;
    private final AdsConfig config;
    private final AdsLogger logger;
    private final AdsPolicyManager policy;

    @Nullable
    private AdManagerInterstitialAd interstitialAd;
    private boolean isLoading = false;
    private String currentContext = "unknown";

    public InterstitialAdManager(Context context) {
        this.context = context.getApplicationContext();
        this.config = AdsConfig.getInstance(context);
        this.logger = AdsLogger.getInstance(context);
        this.policy = AdsPolicyManager.getInstance(context);
    }

    /**
     * Preload an interstitial ad opportunistically.
     * Call this early so the ad is ready when needed.
     *
     * @param screenContext The screen/context for logging
     */
    public void preload(String screenContext) {
        if (!config.isInterstitialsEnabled()) {
            return;
        }

        if (interstitialAd != null || isLoading) {
            // Already loaded or loading
            return;
        }

        if (!policy.canShowInterstitial()) {
            // Don't preload if we can't show anyway
            return;
        }

        this.currentContext = screenContext;
        isLoading = true;

        logger.logRequest(AdType.VIDEO_INTERSTITIAL, AD_UNIT, screenContext);

        AdManagerAdRequest adRequest = new AdManagerAdRequest.Builder().build();

        AdManagerInterstitialAd.load(context, AD_UNIT, adRequest,
                new AdManagerInterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull AdManagerInterstitialAd ad) {
                        isLoading = false;
                        interstitialAd = ad;
                        logger.logFill(AdType.VIDEO_INTERSTITIAL, AD_UNIT, currentContext);
                        setupFullScreenCallback(ad);
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        isLoading = false;
                        interstitialAd = null;
                        logger.logError(AdType.VIDEO_INTERSTITIAL, AD_UNIT, currentContext,
                                error.getCode() + ": " + error.getMessage());
                    }
                });
    }

    /**
     * Check if an interstitial should be shown based on episode count.
     *
     * @return true if interstitial should trigger
     */
    public boolean shouldShowOnEpisodeStart() {
        return policy.shouldTriggerInterstitialByEpisodeCount();
    }

    /**
     * Try to show the interstitial ad.
     *
     * @param activity The activity to show the ad in
     * @param screenContext The screen/context for logging
     * @param callback Optional callback for show events
     * @return true if ad will be shown, false if skipped
     */
    public boolean tryShow(@NonNull Activity activity,
                           @NonNull String screenContext,
                           @Nullable InterstitialCallback callback) {

        if (!config.isInterstitialsEnabled()) {
            if (callback != null) {
                callback.onAdSkipped("Interstitials disabled");
            }
            return false;
        }

        if (!policy.canShowInterstitial()) {
            logger.log(AdEvent.SKIPPED, AdType.VIDEO_INTERSTITIAL, AD_UNIT, screenContext);
            if (callback != null) {
                callback.onAdSkipped("Frequency cap");
            }
            return false;
        }

        if (interstitialAd == null) {
            logger.log(AdEvent.SKIPPED, AdType.VIDEO_INTERSTITIAL, AD_UNIT, screenContext);
            if (callback != null) {
                callback.onAdSkipped("Not loaded");
            }
            // Try to preload for next time
            preload(screenContext);
            return false;
        }

        this.currentContext = screenContext;
        logger.logOpportunity(AdType.VIDEO_INTERSTITIAL, AD_UNIT, screenContext, null);

        // Set up callback for this show
        final InterstitialCallback showCallback = callback;
        interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                logger.logImpression(AdType.VIDEO_INTERSTITIAL, AD_UNIT, currentContext, null);
                policy.recordInterstitialShown();
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                logger.logClose(AdType.VIDEO_INTERSTITIAL, AD_UNIT, currentContext);
                interstitialAd = null;
                if (showCallback != null) {
                    showCallback.onAdClosed();
                }
                // Preload next
                preload(currentContext);
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                logger.logError(AdType.VIDEO_INTERSTITIAL, AD_UNIT, currentContext,
                        error.getCode() + ": " + error.getMessage());
                interstitialAd = null;
                if (showCallback != null) {
                    showCallback.onAdShowFailed(error);
                }
            }

            @Override
            public void onAdClicked() {
                logger.logClick(AdType.VIDEO_INTERSTITIAL, AD_UNIT, currentContext);
            }
        });

        // Show the ad
        interstitialAd.show(activity);
        return true;
    }

    /**
     * Check if an interstitial is ready to show.
     */
    public boolean isReady() {
        return interstitialAd != null && policy.canShowInterstitial();
    }

    /**
     * Clear any loaded ad.
     */
    public void clear() {
        interstitialAd = null;
        isLoading = false;
    }

    private void setupFullScreenCallback(AdManagerInterstitialAd ad) {
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                interstitialAd = null;
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                interstitialAd = null;
            }
        });
    }

    /**
     * Callback interface for interstitial events.
     */
    public interface InterstitialCallback {
        void onAdSkipped(String reason);
        void onAdClosed();
        void onAdShowFailed(AdError error);
    }
}
