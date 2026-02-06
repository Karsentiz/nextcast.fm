package de.danoeh.antennapod.ads;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import android.util.Log;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.admanager.AdManagerAdView;

import de.danoeh.antennapod.ads.config.AdsConfig;
import de.danoeh.antennapod.ads.ima.AudioAdManager;
import de.danoeh.antennapod.ads.manager.BannerAdManager;
import de.danoeh.antennapod.ads.manager.InterstitialAdManager;
import de.danoeh.antennapod.ads.policy.AdsPolicyManager;

/**
 * Main facade for the ads system.
 *
 * This is the primary entry point for all ad-related operations.
 * UI and playback code should use this class instead of directly
 * interacting with individual ad managers.
 *
 * Usage:
 * 1. Initialize in Application.onCreate(): AdsManager.getInstance(context).initialize()
 * 2. Load banners: adsManager.loadBanner(container, BannerAdManager.Screen.HOME, null, null)
 * 3. Show interstitials: adsManager.tryShowInterstitial(activity, "screen_name", null)
 * 4. Audio ads: adsManager.getAudioAdManager() for integration with PlaybackService
 */
public class AdsManager implements DefaultLifecycleObserver {

    private static final String TAG = "AdsManager";
    private static AdsManager instance;

    private final Context context;
    private final AdsConfig config;
    private final AdsPolicyManager policy;
    private BannerAdManager bannerManager;
    private InterstitialAdManager interstitialManager;
    private AudioAdManager audioAdManager;

    private boolean isInitialized = false;
    private boolean initializationFailed = false;

    private AdsManager(Context context) {
        this.context = context.getApplicationContext();
        this.config = AdsConfig.getInstance(context);
        this.policy = AdsPolicyManager.getInstance(context);

        // Create managers defensively - they don't do heavy init in constructors anymore
        try {
            this.bannerManager = new BannerAdManager(context);
            this.interstitialManager = new InterstitialAdManager(context);
            this.audioAdManager = new AudioAdManager(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create ad managers", e);
            initializationFailed = true;
        }
    }

    /**
     * Get the singleton instance of AdsManager.
     */
    public static synchronized AdsManager getInstance(Context context) {
        if (instance == null) {
            instance = new AdsManager(context);
        }
        return instance;
    }

    /**
     * Initialize the ads system.
     * Call this in Application.onCreate().
     */
    public void initialize() {
        if (isInitialized) {
            return;
        }

        if (initializationFailed) {
            // Manager creation failed, skip SDK init
            Log.w(TAG, "Skipping ads initialization due to earlier failure");
            isInitialized = true;
            return;
        }

        if (!config.isAdsEnabled()) {
            // Ads are disabled, don't initialize SDKs
            isInitialized = true;
            return;
        }

        try {
            // Initialize Mobile Ads SDK
            MobileAds.initialize(context, initializationStatus -> {
                Log.d(TAG, "Mobile Ads SDK initialized");
            });

            // Register lifecycle observer for session management
            ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Mobile Ads SDK", e);
            initializationFailed = true;
        }

        isInitialized = true;
    }

    // ============================================
    // LIFECYCLE CALLBACKS
    // ============================================

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        policy.onAppForegrounded();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        policy.onAppBackgrounded();
    }

    // ============================================
    // CONFIGURATION
    // ============================================

    /**
     * Get the ads configuration.
     */
    public AdsConfig getConfig() {
        return config;
    }

    /**
     * Get the policy manager.
     */
    public AdsPolicyManager getPolicy() {
        return policy;
    }

    /**
     * Check if ads are enabled.
     */
    public boolean isAdsEnabled() {
        return config.isAdsEnabled();
    }

    /**
     * Enable or disable all ads (master kill switch).
     */
    public void setAdsEnabled(boolean enabled) {
        config.setAdsEnabled(enabled);
    }

    // ============================================
    // BANNER ADS
    // ============================================

    /**
     * Load a banner ad into the provided container.
     *
     * @param container The ViewGroup to hold the banner
     * @param screen    The screen where the banner is displayed
     * @param episodeId Optional episode ID for logging
     * @param callback  Optional callback for events
     * @return The AdManagerAdView, or null if ads are disabled
     */
    @Nullable
    public AdManagerAdView loadBanner(@NonNull ViewGroup container,
                                       @NonNull BannerAdManager.Screen screen,
                                       @Nullable Long episodeId,
                                       @Nullable BannerAdManager.BannerCallback callback) {
        if (!config.isAdsEnabled() || bannerManager == null) {
            return null;
        }
        return bannerManager.loadBanner(container, screen, episodeId, callback);
    }

    /**
     * Destroy a banner ad.
     * Call this when leaving the screen.
     */
    public void destroyBanner(@Nullable AdManagerAdView adView) {
        if (bannerManager != null) {
            bannerManager.destroyBanner(adView);
        }
    }

    // ============================================
    // INTERSTITIAL ADS
    // ============================================

    /**
     * Preload an interstitial ad.
     * Call this early so the ad is ready when needed.
     *
     * @param screenContext The screen/context for logging
     */
    public void preloadInterstitial(String screenContext) {
        if (!config.isAdsEnabled() || interstitialManager == null) {
            return;
        }
        interstitialManager.preload(screenContext);
    }

    /**
     * Try to show an interstitial ad.
     *
     * @param activity      The activity to show the ad in
     * @param screenContext The screen/context for logging
     * @param callback      Optional callback for events
     * @return true if ad will be shown
     */
    public boolean tryShowInterstitial(@NonNull Activity activity,
                                        @NonNull String screenContext,
                                        @Nullable InterstitialAdManager.InterstitialCallback callback) {
        if (!config.isAdsEnabled() || interstitialManager == null) {
            return false;
        }
        return interstitialManager.tryShow(activity, screenContext, callback);
    }

    /**
     * Check if an interstitial is ready to show.
     */
    public boolean isInterstitialReady() {
        return interstitialManager != null && interstitialManager.isReady();
    }

    /**
     * Check if interstitial should be triggered on episode start.
     */
    public boolean shouldShowInterstitialOnEpisodeStart() {
        return interstitialManager != null && interstitialManager.shouldShowOnEpisodeStart();
    }

    // ============================================
    // AUDIO ADS
    // ============================================

    /**
     * Get the audio ad manager for integration with PlaybackService.
     * May return null if initialization failed.
     */
    @Nullable
    public AudioAdManager getAudioAdManager() {
        return audioAdManager;
    }

    /**
     * Check if an audio ad should play for this episode.
     */
    public boolean shouldPlayAudioAd() {
        if (!config.isAdsEnabled() || audioAdManager == null) {
            return false;
        }
        return audioAdManager.shouldPlayAd();
    }

    // ============================================
    // EPISODE TRACKING
    // ============================================

    /**
     * Record that an episode playback started.
     * Call this when playback begins for frequency calculations.
     */
    public void recordEpisodeStart() {
        policy.recordEpisodeStart();
    }

    // ============================================
    // CLEANUP
    // ============================================

    /**
     * Release all resources.
     * Call this when the app is being destroyed.
     */
    public void release() {
        if (interstitialManager != null) {
            interstitialManager.clear();
        }
        if (audioAdManager != null) {
            audioAdManager.release();
        }
        try {
            ProcessLifecycleOwner.get().getLifecycle().removeObserver(this);
        } catch (Exception e) {
            Log.e(TAG, "Error removing lifecycle observer", e);
        }
    }
}
