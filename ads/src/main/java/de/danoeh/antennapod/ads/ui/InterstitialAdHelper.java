package de.danoeh.antennapod.ads.ui;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import de.danoeh.antennapod.ads.AdsManager;
import de.danoeh.antennapod.ads.manager.InterstitialAdManager;

/**
 * Helper class for integrating interstitial ads in activities/fragments.
 *
 * Usage in Activity:
 *   InterstitialAdHelper helper = new InterstitialAdHelper(this, "main_screen");
 *   // Call when navigation event occurs:
 *   helper.tryShowOnNavigation();
 *
 * Usage in Fragment:
 *   InterstitialAdHelper helper = new InterstitialAdHelper(this, "feed_screen");
 *   // The helper preloads automatically when the fragment starts
 */
public class InterstitialAdHelper implements DefaultLifecycleObserver {

    private final String screenContext;
    private final LifecycleOwner lifecycleOwner;
    private AdsManager adsManager;

    /**
     * Create an interstitial helper for an Activity.
     */
    public InterstitialAdHelper(@NonNull Activity activity, @NonNull String screenContext) {
        this.screenContext = screenContext;
        this.lifecycleOwner = null;
        this.adsManager = AdsManager.getInstance(activity);
        // Preload immediately
        adsManager.preloadInterstitial(screenContext);
    }

    /**
     * Create an interstitial helper for a Fragment.
     */
    public InterstitialAdHelper(@NonNull Fragment fragment, @NonNull String screenContext) {
        this.screenContext = screenContext;
        this.lifecycleOwner = fragment.getViewLifecycleOwner();
        this.adsManager = AdsManager.getInstance(fragment.requireContext());

        // Register lifecycle observer
        lifecycleOwner.getLifecycle().addObserver(this);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        // Preload when fragment starts
        adsManager.preloadInterstitial(screenContext);
    }

    /**
     * Try to show an interstitial ad on a navigation event.
     * The ad will only show if frequency caps allow.
     *
     * @param activity The activity to show the ad in
     * @param callback Optional callback for events
     * @return true if ad will be shown
     */
    public boolean tryShowOnNavigation(@NonNull Activity activity,
                                       @Nullable InterstitialAdManager.InterstitialCallback callback) {
        return adsManager.tryShowInterstitial(activity, screenContext, callback);
    }

    /**
     * Try to show an interstitial if the episode count threshold is met.
     *
     * @param activity The activity to show the ad in
     * @param callback Optional callback for events
     * @return true if ad will be shown
     */
    public boolean tryShowOnEpisodeStart(@NonNull Activity activity,
                                         @Nullable InterstitialAdManager.InterstitialCallback callback) {
        if (adsManager.shouldShowInterstitialOnEpisodeStart()) {
            return adsManager.tryShowInterstitial(activity, screenContext, callback);
        }
        return false;
    }

    /**
     * Check if an interstitial is ready to show.
     */
    public boolean isReady() {
        return adsManager.isInterstitialReady();
    }
}
