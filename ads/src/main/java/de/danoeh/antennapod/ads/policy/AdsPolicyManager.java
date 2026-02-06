package de.danoeh.antennapod.ads.policy;

import android.content.Context;
import android.content.SharedPreferences;

import de.danoeh.antennapod.ads.config.AdsConfig;
import de.danoeh.antennapod.ads.logging.AdType;
import de.danoeh.antennapod.ads.logging.AdsLogger;

/**
 * Manages ad policy decisions including frequency caps and when ads are allowed.
 *
 * This is the central decision-maker for:
 * - Whether an ad should be shown based on frequency caps
 * - Tracking session-level ad counts
 * - Episode-based ad triggers
 */
public class AdsPolicyManager {

    private static final String PREFS_NAME = "nextcast_ads_policy";

    // Session state keys
    private static final String KEY_SESSION_START_TIME = "session_start_time";
    private static final String KEY_SESSION_INTERSTITIAL_COUNT = "session_interstitial_count";
    private static final String KEY_LAST_INTERSTITIAL_TIME = "last_interstitial_time";
    private static final String KEY_EPISODE_START_COUNT = "episode_start_count";
    private static final String KEY_LAST_AUDIO_AD_EPISODE = "last_audio_ad_episode";

    // Session timeout (if app is in background for this long, reset session)
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

    private static AdsPolicyManager instance;
    private final AdsConfig config;
    private final AdsLogger logger;
    private final SharedPreferences prefs;

    private long sessionStartTime;
    private int sessionInterstitialCount;
    private long lastInterstitialTime;
    private int episodeStartCount;
    private int lastAudioAdEpisode;

    private AdsPolicyManager(Context context) {
        this.config = AdsConfig.getInstance(context);
        this.logger = AdsLogger.getInstance(context);
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadState();
    }

    public static synchronized AdsPolicyManager getInstance(Context context) {
        if (instance == null) {
            instance = new AdsPolicyManager(context);
        }
        return instance;
    }

    /**
     * Called when the app starts or returns to foreground.
     * Resets session if timed out.
     */
    public void onAppForegrounded() {
        long now = System.currentTimeMillis();
        long lastActive = prefs.getLong("last_active_time", 0);

        if (now - lastActive > SESSION_TIMEOUT_MS) {
            // Session timed out, reset session counters
            resetSession();
        }

        prefs.edit().putLong("last_active_time", now).apply();
    }

    /**
     * Called when app goes to background.
     */
    public void onAppBackgrounded() {
        prefs.edit().putLong("last_active_time", System.currentTimeMillis()).apply();
        saveState();
    }

    // ============================================
    // BANNER POLICY
    // ============================================

    /**
     * Check if a banner ad should be shown on the given screen.
     *
     * @param screen The screen identifier
     * @return true if banner should be shown
     */
    public boolean shouldShowBanner(String screen) {
        if (!config.isBannersEnabled()) {
            return false;
        }

        // Banners are screen-scoped, no frequency caps needed
        // Just check if ads are enabled
        return true;
    }

    // ============================================
    // INTERSTITIAL POLICY
    // ============================================

    /**
     * Check if an interstitial ad can be shown based on frequency caps.
     *
     * Rules:
     * - Max 1 per 10 minutes
     * - Max 2 per session
     * - Every 3 episode starts (or after episode completion)
     *
     * @return true if interstitial is allowed
     */
    public boolean canShowInterstitial() {
        if (!config.isInterstitialsEnabled()) {
            return false;
        }

        long now = System.currentTimeMillis();

        // Check session cap
        if (sessionInterstitialCount >= config.getInterstitialMaxPerSession()) {
            return false;
        }

        // Check time-based cap
        long minInterval = config.getInterstitialMinIntervalMs();
        if (now - lastInterstitialTime < minInterval) {
            return false;
        }

        return true;
    }

    /**
     * Check if interstitial should be triggered based on episode count.
     *
     * @return true if episode count threshold is met
     */
    public boolean shouldTriggerInterstitialByEpisodeCount() {
        if (!canShowInterstitial()) {
            return false;
        }

        int frequency = config.getInterstitialEpisodeFrequency();
        return episodeStartCount > 0 && episodeStartCount % frequency == 0;
    }

    /**
     * Record that an interstitial was shown.
     */
    public void recordInterstitialShown() {
        sessionInterstitialCount++;
        lastInterstitialTime = System.currentTimeMillis();
        saveState();
    }

    // ============================================
    // AUDIO AD POLICY
    // ============================================

    /**
     * Check if an audio pre-roll ad should play before the current episode.
     *
     * Rule: Play every 2 episode starts
     *
     * @return true if audio ad should play
     */
    public boolean shouldPlayAudioAd() {
        if (!config.isAudioAdsEnabled()) {
            return false;
        }

        int frequency = config.getAudioAdEpisodeFrequency();
        int episodesSinceLastAd = episodeStartCount - lastAudioAdEpisode;

        return episodesSinceLastAd >= frequency;
    }

    /**
     * Record that an audio ad was played.
     */
    public void recordAudioAdPlayed() {
        lastAudioAdEpisode = episodeStartCount;
        saveState();
    }

    // ============================================
    // EPISODE TRACKING
    // ============================================

    /**
     * Record that an episode playback started.
     * This is used for frequency calculations.
     */
    public void recordEpisodeStart() {
        episodeStartCount++;
        saveState();
    }

    /**
     * Get the current episode start count (for this session/period).
     */
    public int getEpisodeStartCount() {
        return episodeStartCount;
    }

    /**
     * Get session episode count (alias for getEpisodeStartCount).
     */
    public int getSessionEpisodeCount() {
        return episodeStartCount;
    }

    /**
     * Get the number of interstitials shown this session.
     */
    public int getSessionInterstitialCount() {
        return sessionInterstitialCount;
    }

    // ============================================
    // SESSION MANAGEMENT
    // ============================================

    /**
     * Reset session counters.
     */
    public void resetSession() {
        sessionStartTime = System.currentTimeMillis();
        sessionInterstitialCount = 0;
        lastInterstitialTime = 0;
        // Note: episode count persists across sessions
        saveState();
    }

    /**
     * Reset all counters including episode counts.
     */
    public void resetAll() {
        sessionStartTime = System.currentTimeMillis();
        sessionInterstitialCount = 0;
        lastInterstitialTime = 0;
        episodeStartCount = 0;
        lastAudioAdEpisode = 0;
        saveState();
    }

    // ============================================
    // PERSISTENCE
    // ============================================

    private void loadState() {
        sessionStartTime = prefs.getLong(KEY_SESSION_START_TIME, System.currentTimeMillis());
        sessionInterstitialCount = prefs.getInt(KEY_SESSION_INTERSTITIAL_COUNT, 0);
        lastInterstitialTime = prefs.getLong(KEY_LAST_INTERSTITIAL_TIME, 0);
        episodeStartCount = prefs.getInt(KEY_EPISODE_START_COUNT, 0);
        lastAudioAdEpisode = prefs.getInt(KEY_LAST_AUDIO_AD_EPISODE, 0);
    }

    private void saveState() {
        prefs.edit()
                .putLong(KEY_SESSION_START_TIME, sessionStartTime)
                .putInt(KEY_SESSION_INTERSTITIAL_COUNT, sessionInterstitialCount)
                .putLong(KEY_LAST_INTERSTITIAL_TIME, lastInterstitialTime)
                .putInt(KEY_EPISODE_START_COUNT, episodeStartCount)
                .putInt(KEY_LAST_AUDIO_AD_EPISODE, lastAudioAdEpisode)
                .apply();
    }

    // ============================================
    // DEBUG INFO
    // ============================================

    /**
     * Get debug info about current policy state.
     */
    public String getDebugInfo() {
        return "AdsPolicyManager State:\n" +
                "  Session Interstitials: " + sessionInterstitialCount + "/" + config.getInterstitialMaxPerSession() + "\n" +
                "  Last Interstitial: " + (System.currentTimeMillis() - lastInterstitialTime) / 1000 + "s ago\n" +
                "  Episode Count: " + episodeStartCount + "\n" +
                "  Last Audio Ad Episode: " + lastAudioAdEpisode + "\n" +
                "  Next Audio Ad In: " + (config.getAudioAdEpisodeFrequency() - (episodeStartCount - lastAudioAdEpisode)) + " episodes";
    }
}
