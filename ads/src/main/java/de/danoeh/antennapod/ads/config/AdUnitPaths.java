package de.danoeh.antennapod.ads.config;

/**
 * Canonical Ad Unit Paths for Google Ad Manager (GAM).
 *
 * IMPORTANT: This is the ONLY place where ad unit strings should be defined.
 * All ad requests MUST use these constants. Do not hardcode ad unit paths elsewhere.
 */
public final class AdUnitPaths {

    private AdUnitPaths() {
        // Prevent instantiation
    }

    // ============================================
    // BANNER AD UNITS
    // ============================================

    /**
     * Banner ad for Home/Discover screen - sticky bottom placement
     */
    public static final String BANNER_HOME_BOTTOM = "/173142088/nc_banner_home_bottom";

    /**
     * Banner ad for Search screen - inline list placement
     */
    public static final String BANNER_SEARCH_INLINE = "/173142088/nc_banner_search_inline";

    /**
     * Banner ad for Episode Details screen - bottom placement
     */
    public static final String BANNER_EPISODE_DETAILS_BOTTOM = "/173142088/nc_banner_episode_details_bottom";

    /**
     * Banner ad for Library/Downloads screen - sticky bottom placement
     */
    public static final String BANNER_LIBRARY_BOTTOM = "/173142088/nc_banner_library_bottom";

    /**
     * Companion banner for Now Playing screen (optional)
     * Only shown if UI has reserved space
     */
    public static final String BANNER_NOW_PLAYING_COMPANION = "/173142088/nc_banner_now_playing_companion";

    // ============================================
    // VIDEO INTERSTITIAL AD UNIT
    // ============================================

    /**
     * Video interstitial ad - triggered via navigation events
     * Not tied to playback. Respects frequency caps.
     */
    public static final String VIDEO_INTERSTITIAL = "/173142088/nc_video_interstitial";

    // ============================================
    // AUDIO AD UNIT (IMA)
    // ============================================

    /**
     * Audio pre-roll ad unit for IMA SDK
     * Triggered once every 2 episode starts
     */
    public static final String AUDIO_PREROLL = "/173142088/nc_audio_preroll";

    /**
     * Build the VAST tag URL for IMA audio pre-roll
     * @param customParams Optional custom targeting parameters
     * @return Full VAST tag URL for IMA request
     */
    public static String buildAudioPrerollVastUrl(String customParams) {
        StringBuilder url = new StringBuilder();
        url.append("https://pubads.g.doubleclick.net/gampad/ads?");
        url.append("iu=").append(AUDIO_PREROLL);
        url.append("&sz=audio");
        url.append("&gdfp_req=1");
        url.append("&output=vast");
        url.append("&unviewed_position_start=1");
        url.append("&env=vp");
        url.append("&impl=s");
        url.append("&correlator=").append(System.currentTimeMillis());

        if (customParams != null && !customParams.isEmpty()) {
            url.append("&cust_params=").append(customParams);
        }

        return url.toString();
    }
}
