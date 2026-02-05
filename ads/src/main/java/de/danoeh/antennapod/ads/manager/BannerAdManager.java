package de.danoeh.antennapod.ads.manager;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.admanager.AdManagerAdRequest;
import com.google.android.gms.ads.admanager.AdManagerAdView;

import de.danoeh.antennapod.ads.config.AdUnitPaths;
import de.danoeh.antennapod.ads.config.AdsConfig;
import de.danoeh.antennapod.ads.logging.AdType;
import de.danoeh.antennapod.ads.logging.AdsLogger;
import de.danoeh.antennapod.ads.logging.AdEvent;
import de.danoeh.antennapod.ads.policy.AdsPolicyManager;

/**
 * Manages banner ad loading and display.
 *
 * Placements:
 * - Home/Discover → sticky bottom banner
 * - Search → inline list banner (deterministic index, max 1 per screen)
 * - Episode Details → bottom banner
 * - Library/Downloads → sticky bottom banner
 * - Now Playing → companion banner only if UI has reserved space
 *
 * Rules:
 * - Screen-scoped only (load on enter, destroy on exit)
 * - No overlays on controls
 * - Collapse container completely on no-fill
 */
public class BannerAdManager {

    private static final String TAG = "BannerAdManager";

    private final Context context;
    private final AdsConfig config;
    private final AdsLogger logger;
    private final AdsPolicyManager policy;

    public BannerAdManager(Context context) {
        this.context = context.getApplicationContext();
        this.config = AdsConfig.getInstance(context);
        this.logger = AdsLogger.getInstance(context);
        this.policy = AdsPolicyManager.getInstance(context);
    }

    /**
     * Screen identifiers for banner placements.
     */
    public enum Screen {
        HOME("home", AdUnitPaths.BANNER_HOME_BOTTOM),
        SEARCH("search", AdUnitPaths.BANNER_SEARCH_INLINE),
        EPISODE_DETAILS("episode_details", AdUnitPaths.BANNER_EPISODE_DETAILS_BOTTOM),
        LIBRARY("library", AdUnitPaths.BANNER_LIBRARY_BOTTOM),
        NOW_PLAYING("now_playing", AdUnitPaths.BANNER_NOW_PLAYING_COMPANION);

        private final String name;
        private final String adUnitPath;

        Screen(String name, String adUnitPath) {
            this.name = name;
            this.adUnitPath = adUnitPath;
        }

        public String getName() {
            return name;
        }

        public String getAdUnitPath() {
            return adUnitPath;
        }
    }

    /**
     * Load a banner ad into the provided container.
     *
     * @param container    The ViewGroup to hold the banner
     * @param screen       The screen where the banner is displayed
     * @param episodeId    Optional episode ID for logging context
     * @param callback     Optional callback for load events
     * @return The created AdManagerAdView, or null if ads are disabled
     */
    @Nullable
    public AdManagerAdView loadBanner(@NonNull ViewGroup container,
                                       @NonNull Screen screen,
                                       @Nullable Long episodeId,
                                       @Nullable BannerCallback callback) {

        // Check if banners are enabled
        if (!policy.shouldShowBanner(screen.getName())) {
            collapseContainer(container);
            return null;
        }

        String adUnitPath = screen.getAdUnitPath();
        logger.logOpportunity(AdType.BANNER, adUnitPath, screen.getName(), episodeId);

        // Create the ad view
        AdManagerAdView adView = new AdManagerAdView(context);
        adView.setAdUnitId(adUnitPath);
        adView.setAdSize(getAdSizeForScreen(screen));

        // Set up listener
        adView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                logger.logFill(AdType.BANNER, adUnitPath, screen.getName());
                showContainer(container);
                if (callback != null) {
                    callback.onBannerLoaded();
                }
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                logger.logError(AdType.BANNER, adUnitPath, screen.getName(),
                        error.getCode() + ": " + error.getMessage());
                collapseContainer(container);
                if (callback != null) {
                    callback.onBannerFailed(error);
                }
            }

            @Override
            public void onAdOpened() {
                // Ad opened fullscreen overlay
            }

            @Override
            public void onAdClicked() {
                logger.logClick(AdType.BANNER, adUnitPath, screen.getName());
                if (callback != null) {
                    callback.onBannerClicked();
                }
            }

            @Override
            public void onAdClosed() {
                logger.logClose(AdType.BANNER, adUnitPath, screen.getName());
            }

            @Override
            public void onAdImpression() {
                logger.logImpression(AdType.BANNER, adUnitPath, screen.getName(), episodeId);
            }
        });

        // Add to container and load
        container.removeAllViews();
        container.addView(adView);

        // Build and send ad request
        logger.logRequest(AdType.BANNER, adUnitPath, screen.getName());
        AdManagerAdRequest adRequest = new AdManagerAdRequest.Builder().build();
        adView.loadAd(adRequest);

        return adView;
    }

    /**
     * Destroy a banner ad view.
     * Call this when leaving the screen.
     *
     * @param adView The ad view to destroy
     */
    public void destroyBanner(@Nullable AdManagerAdView adView) {
        if (adView != null) {
            adView.destroy();
        }
    }

    /**
     * Get the appropriate ad size for the screen.
     */
    private AdSize getAdSizeForScreen(Screen screen) {
        switch (screen) {
            case SEARCH:
                // Inline banner - medium rectangle works well in lists
                return AdSize.MEDIUM_RECTANGLE;
            case NOW_PLAYING:
                // Smaller companion banner
                return AdSize.BANNER;
            default:
                // Anchored adaptive banner for bottom placement
                return AdSize.BANNER;
        }
    }

    /**
     * Collapse the container completely (no space taken).
     */
    private void collapseContainer(ViewGroup container) {
        container.setVisibility(View.GONE);
        container.removeAllViews();
    }

    /**
     * Show the container.
     */
    private void showContainer(ViewGroup container) {
        container.setVisibility(View.VISIBLE);
    }

    /**
     * Callback interface for banner events.
     */
    public interface BannerCallback {
        void onBannerLoaded();
        void onBannerFailed(LoadAdError error);
        void onBannerClicked();
    }
}
