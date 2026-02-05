package de.danoeh.antennapod.ads.ui;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.ads.admanager.AdManagerAdView;

import de.danoeh.antennapod.ads.AdsManager;
import de.danoeh.antennapod.ads.manager.BannerAdManager;

/**
 * Helper class for integrating banner ads in fragments.
 *
 * Usage:
 * 1. In your fragment's onViewCreated:
 *    BannerAdHelper helper = new BannerAdHelper(this, bannerContainer, BannerAdManager.Screen.HOME);
 *
 * 2. The helper automatically:
 *    - Loads the banner when the fragment starts
 *    - Destroys the banner when the fragment stops
 *    - Handles the full lifecycle
 */
public class BannerAdHelper implements DefaultLifecycleObserver {

    private final Fragment fragment;
    private final ViewGroup container;
    private final BannerAdManager.Screen screen;
    private final Long episodeId;
    private final BannerAdManager.BannerCallback callback;

    private AdManagerAdView adView;

    /**
     * Create a banner ad helper.
     *
     * @param fragment  The fragment hosting the banner
     * @param container The container ViewGroup for the banner
     * @param screen    The screen type for this banner
     */
    public BannerAdHelper(@NonNull Fragment fragment,
                          @NonNull ViewGroup container,
                          @NonNull BannerAdManager.Screen screen) {
        this(fragment, container, screen, null, null);
    }

    /**
     * Create a banner ad helper with episode context and callback.
     *
     * @param fragment  The fragment hosting the banner
     * @param container The container ViewGroup for the banner
     * @param screen    The screen type for this banner
     * @param episodeId Optional episode ID for logging
     * @param callback  Optional callback for banner events
     */
    public BannerAdHelper(@NonNull Fragment fragment,
                          @NonNull ViewGroup container,
                          @NonNull BannerAdManager.Screen screen,
                          @Nullable Long episodeId,
                          @Nullable BannerAdManager.BannerCallback callback) {
        this.fragment = fragment;
        this.container = container;
        this.screen = screen;
        this.episodeId = episodeId;
        this.callback = callback;

        // Register lifecycle observer
        fragment.getViewLifecycleOwner().getLifecycle().addObserver(this);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        loadBanner();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        destroyBanner();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        destroyBanner();
        owner.getLifecycle().removeObserver(this);
    }

    /**
     * Manually load the banner.
     * Usually not needed as the helper loads automatically on start.
     */
    public void loadBanner() {
        if (fragment.getContext() == null) {
            return;
        }

        AdsManager adsManager = AdsManager.getInstance(fragment.requireContext());
        adView = adsManager.loadBanner(container, screen, episodeId, callback);
    }

    /**
     * Manually destroy the banner.
     * Usually not needed as the helper destroys automatically on stop.
     */
    public void destroyBanner() {
        if (adView != null && fragment.getContext() != null) {
            AdsManager.getInstance(fragment.requireContext()).destroyBanner(adView);
            adView = null;
        }
    }

    /**
     * Get the current ad view.
     */
    @Nullable
    public AdManagerAdView getAdView() {
        return adView;
    }
}
