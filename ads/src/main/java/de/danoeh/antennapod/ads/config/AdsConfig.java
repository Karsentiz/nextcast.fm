package de.danoeh.antennapod.ads.config;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Global configuration for the ads system.
 *
 * Provides:
 * - Master kill switch (adsEnabled)
 * - Per-format enable flags
 * - Frequency caps
 * - Test/debug mode settings
 *
 * If adsEnabled is false, the app behaves normally with no SDK calls.
 */
public class AdsConfig {

    private static final String PREFS_NAME = "nextcast_ads_config";

    // Preference keys
    private static final String KEY_ADS_ENABLED = "ads_enabled";
    private static final String KEY_BANNERS_ENABLED = "banners_enabled";
    private static final String KEY_INTERSTITIALS_ENABLED = "interstitials_enabled";
    private static final String KEY_AUDIO_ADS_ENABLED = "audio_ads_enabled";
    private static final String KEY_TEST_MODE = "test_mode";
    private static final String KEY_DEBUG_LOGGING = "debug_logging";

    // Frequency cap keys
    private static final String KEY_INTERSTITIAL_MIN_INTERVAL_MS = "interstitial_min_interval_ms";
    private static final String KEY_INTERSTITIAL_MAX_PER_SESSION = "interstitial_max_per_session";
    private static final String KEY_INTERSTITIAL_EPISODE_FREQUENCY = "interstitial_episode_frequency";
    private static final String KEY_AUDIO_AD_EPISODE_FREQUENCY = "audio_ad_episode_frequency";
    private static final String KEY_AUDIO_AD_LOAD_TIMEOUT_MS = "audio_ad_load_timeout_ms";

    // Default values
    private static final boolean DEFAULT_ADS_ENABLED = true;
    private static final boolean DEFAULT_BANNERS_ENABLED = true;
    private static final boolean DEFAULT_INTERSTITIALS_ENABLED = true;
    private static final boolean DEFAULT_AUDIO_ADS_ENABLED = true;
    private static final boolean DEFAULT_TEST_MODE = false;
    private static final boolean DEFAULT_DEBUG_LOGGING = false;

    // Default frequency caps
    private static final long DEFAULT_INTERSTITIAL_MIN_INTERVAL_MS = 10 * 60 * 1000; // 10 minutes
    private static final int DEFAULT_INTERSTITIAL_MAX_PER_SESSION = 2;
    private static final int DEFAULT_INTERSTITIAL_EPISODE_FREQUENCY = 3; // Every 3 episodes
    private static final int DEFAULT_AUDIO_AD_EPISODE_FREQUENCY = 2; // Every 2 episodes
    private static final long DEFAULT_AUDIO_AD_LOAD_TIMEOUT_MS = 5000; // 5 seconds

    private static AdsConfig instance;
    private final SharedPreferences prefs;

    private AdsConfig(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized AdsConfig getInstance(Context context) {
        if (instance == null) {
            instance = new AdsConfig(context);
        }
        return instance;
    }

    // ============================================
    // MASTER KILL SWITCH
    // ============================================

    /**
     * Master kill switch for all ads.
     * When false, no ad SDK calls are made and the app behaves normally.
     */
    public boolean isAdsEnabled() {
        return prefs.getBoolean(KEY_ADS_ENABLED, DEFAULT_ADS_ENABLED);
    }

    public void setAdsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ADS_ENABLED, enabled).apply();
    }

    // ============================================
    // PER-FORMAT ENABLE FLAGS
    // ============================================

    public boolean isBannersEnabled() {
        return isAdsEnabled() && prefs.getBoolean(KEY_BANNERS_ENABLED, DEFAULT_BANNERS_ENABLED);
    }

    public void setBannersEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BANNERS_ENABLED, enabled).apply();
    }

    public boolean isInterstitialsEnabled() {
        return isAdsEnabled() && prefs.getBoolean(KEY_INTERSTITIALS_ENABLED, DEFAULT_INTERSTITIALS_ENABLED);
    }

    public void setInterstitialsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_INTERSTITIALS_ENABLED, enabled).apply();
    }

    public boolean isAudioAdsEnabled() {
        return isAdsEnabled() && prefs.getBoolean(KEY_AUDIO_ADS_ENABLED, DEFAULT_AUDIO_ADS_ENABLED);
    }

    public void setAudioAdsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUDIO_ADS_ENABLED, enabled).apply();
    }

    // ============================================
    // TEST/DEBUG MODE
    // ============================================

    /**
     * When true, uses test ad units instead of production.
     */
    public boolean isTestMode() {
        return prefs.getBoolean(KEY_TEST_MODE, DEFAULT_TEST_MODE);
    }

    public void setTestMode(boolean testMode) {
        prefs.edit().putBoolean(KEY_TEST_MODE, testMode).apply();
    }

    /**
     * When true, enables verbose logging for ad events.
     */
    public boolean isDebugLogging() {
        return prefs.getBoolean(KEY_DEBUG_LOGGING, DEFAULT_DEBUG_LOGGING);
    }

    public void setDebugLogging(boolean enabled) {
        prefs.edit().putBoolean(KEY_DEBUG_LOGGING, enabled).apply();
    }

    // ============================================
    // FREQUENCY CAPS - INTERSTITIALS
    // ============================================

    /**
     * Minimum time between interstitial ads in milliseconds.
     * Default: 10 minutes
     */
    public long getInterstitialMinIntervalMs() {
        return prefs.getLong(KEY_INTERSTITIAL_MIN_INTERVAL_MS, DEFAULT_INTERSTITIAL_MIN_INTERVAL_MS);
    }

    public void setInterstitialMinIntervalMs(long intervalMs) {
        prefs.edit().putLong(KEY_INTERSTITIAL_MIN_INTERVAL_MS, intervalMs).apply();
    }

    /**
     * Maximum number of interstitial ads per session.
     * Default: 2
     */
    public int getInterstitialMaxPerSession() {
        return prefs.getInt(KEY_INTERSTITIAL_MAX_PER_SESSION, DEFAULT_INTERSTITIAL_MAX_PER_SESSION);
    }

    public void setInterstitialMaxPerSession(int max) {
        prefs.edit().putInt(KEY_INTERSTITIAL_MAX_PER_SESSION, max).apply();
    }

    /**
     * Show interstitial after every N episode starts.
     * Default: 3
     */
    public int getInterstitialEpisodeFrequency() {
        return prefs.getInt(KEY_INTERSTITIAL_EPISODE_FREQUENCY, DEFAULT_INTERSTITIAL_EPISODE_FREQUENCY);
    }

    public void setInterstitialEpisodeFrequency(int frequency) {
        prefs.edit().putInt(KEY_INTERSTITIAL_EPISODE_FREQUENCY, frequency).apply();
    }

    // ============================================
    // FREQUENCY CAPS - AUDIO ADS
    // ============================================

    /**
     * Show audio pre-roll ad every N episode starts.
     * Default: 2
     */
    public int getAudioAdEpisodeFrequency() {
        return prefs.getInt(KEY_AUDIO_AD_EPISODE_FREQUENCY, DEFAULT_AUDIO_AD_EPISODE_FREQUENCY);
    }

    public void setAudioAdEpisodeFrequency(int frequency) {
        prefs.edit().putInt(KEY_AUDIO_AD_EPISODE_FREQUENCY, frequency).apply();
    }

    /**
     * Maximum time to wait for audio ad to load in milliseconds.
     * On timeout, content playback resumes immediately.
     * Default: 5 seconds
     */
    public long getAudioAdLoadTimeoutMs() {
        return prefs.getLong(KEY_AUDIO_AD_LOAD_TIMEOUT_MS, DEFAULT_AUDIO_AD_LOAD_TIMEOUT_MS);
    }

    public void setAudioAdLoadTimeoutMs(long timeoutMs) {
        prefs.edit().putLong(KEY_AUDIO_AD_LOAD_TIMEOUT_MS, timeoutMs).apply();
    }

    // ============================================
    // UTILITY METHODS
    // ============================================

    /**
     * Reset all settings to defaults.
     */
    public void resetToDefaults() {
        prefs.edit().clear().apply();
    }

    /**
     * Check if any ad format is enabled.
     */
    public boolean hasAnyAdsEnabled() {
        return isBannersEnabled() || isInterstitialsEnabled() || isAudioAdsEnabled();
    }
}
