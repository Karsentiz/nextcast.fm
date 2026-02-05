package de.danoeh.antennapod.ads.logging;

/**
 * Types of ad events for structured logging.
 */
public enum AdEvent {
    /**
     * An ad opportunity was identified (e.g., user navigated to a screen with ad placement)
     */
    OPPORTUNITY,

    /**
     * An ad request was made to the ad server
     */
    REQUEST,

    /**
     * An ad was successfully loaded (fill)
     */
    FILL,

    /**
     * No ad was returned (no-fill)
     */
    NO_FILL,

    /**
     * An ad impression was recorded (ad was displayed/played)
     */
    IMPRESSION,

    /**
     * User clicked on the ad
     */
    CLICK,

    /**
     * Ad was closed/dismissed
     */
    CLOSE,

    /**
     * An error occurred during ad loading or playback
     */
    ERROR,

    /**
     * Ad was skipped (e.g., due to frequency cap or timeout)
     */
    SKIPPED,

    /**
     * Ad playback started (for audio/video)
     */
    STARTED,

    /**
     * Ad playback completed (for audio/video)
     */
    COMPLETED,

    /**
     * Ad playback paused
     */
    PAUSED,

    /**
     * Ad playback resumed
     */
    RESUMED
}
