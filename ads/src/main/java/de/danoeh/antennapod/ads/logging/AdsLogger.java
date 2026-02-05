package de.danoeh.antennapod.ads.logging;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import de.danoeh.antennapod.ads.config.AdsConfig;

/**
 * Structured logging for ad events.
 *
 * Logs include:
 * - Ad unit path
 * - Screen/context
 * - Episode ID (if applicable)
 * - Timestamp
 * - App version
 */
public class AdsLogger {

    private static final String TAG = "AdsLogger";

    private static AdsLogger instance;
    private final AdsConfig config;
    private final String appVersion;

    private AdsLogger(Context context) {
        this.config = AdsConfig.getInstance(context);
        this.appVersion = getAppVersion(context);
    }

    public static synchronized AdsLogger getInstance(Context context) {
        if (instance == null) {
            instance = new AdsLogger(context);
        }
        return instance;
    }

    /**
     * Log an ad event with full context.
     *
     * @param event      The type of ad event
     * @param adType     The type of ad (banner, interstitial, audio)
     * @param adUnitPath The GAM ad unit path
     * @param screen     The screen/context where the event occurred
     * @param episodeId  The episode ID if applicable, null otherwise
     * @param errorMsg   Error message if event is ERROR, null otherwise
     */
    public void log(@NonNull AdEvent event,
                    @NonNull AdType adType,
                    @NonNull String adUnitPath,
                    @NonNull String screen,
                    @Nullable Long episodeId,
                    @Nullable String errorMsg) {

        if (!config.isDebugLogging() && event != AdEvent.ERROR) {
            // In non-debug mode, only log errors to avoid noise
            return;
        }

        try {
            JSONObject logEntry = new JSONObject();
            logEntry.put("event", event.name());
            logEntry.put("ad_type", adType.name());
            logEntry.put("ad_unit", adUnitPath);
            logEntry.put("screen", screen);
            logEntry.put("timestamp", System.currentTimeMillis());
            logEntry.put("app_version", appVersion);

            if (episodeId != null) {
                logEntry.put("episode_id", episodeId);
            }

            if (errorMsg != null) {
                logEntry.put("error", errorMsg);
            }

            String logMessage = logEntry.toString();

            switch (event) {
                case ERROR:
                    Log.e(TAG, logMessage);
                    break;
                case NO_FILL:
                case SKIPPED:
                    Log.w(TAG, logMessage);
                    break;
                default:
                    Log.d(TAG, logMessage);
                    break;
            }

            // TODO: Send to analytics service if needed
            // Analytics.logAdEvent(logEntry);

        } catch (JSONException e) {
            Log.e(TAG, "Failed to create log entry", e);
        }
    }

    /**
     * Convenience method for logging without episode context.
     */
    public void log(@NonNull AdEvent event,
                    @NonNull AdType adType,
                    @NonNull String adUnitPath,
                    @NonNull String screen) {
        log(event, adType, adUnitPath, screen, null, null);
    }

    /**
     * Convenience method for logging errors.
     */
    public void logError(@NonNull AdType adType,
                         @NonNull String adUnitPath,
                         @NonNull String screen,
                         @NonNull String errorMsg) {
        log(AdEvent.ERROR, adType, adUnitPath, screen, null, errorMsg);
    }

    /**
     * Convenience method for logging errors with episode context.
     */
    public void logError(@NonNull AdType adType,
                         @NonNull String adUnitPath,
                         @NonNull String screen,
                         @Nullable Long episodeId,
                         @NonNull String errorMsg) {
        log(AdEvent.ERROR, adType, adUnitPath, screen, episodeId, errorMsg);
    }

    /**
     * Log an ad opportunity.
     */
    public void logOpportunity(@NonNull AdType adType,
                               @NonNull String adUnitPath,
                               @NonNull String screen,
                               @Nullable Long episodeId) {
        log(AdEvent.OPPORTUNITY, adType, adUnitPath, screen, episodeId, null);
    }

    /**
     * Log an ad request.
     */
    public void logRequest(@NonNull AdType adType,
                           @NonNull String adUnitPath,
                           @NonNull String screen) {
        log(AdEvent.REQUEST, adType, adUnitPath, screen, null, null);
    }

    /**
     * Log an ad fill.
     */
    public void logFill(@NonNull AdType adType,
                        @NonNull String adUnitPath,
                        @NonNull String screen) {
        log(AdEvent.FILL, adType, adUnitPath, screen, null, null);
    }

    /**
     * Log a no-fill.
     */
    public void logNoFill(@NonNull AdType adType,
                          @NonNull String adUnitPath,
                          @NonNull String screen) {
        log(AdEvent.NO_FILL, adType, adUnitPath, screen, null, null);
    }

    /**
     * Log an impression.
     */
    public void logImpression(@NonNull AdType adType,
                              @NonNull String adUnitPath,
                              @NonNull String screen,
                              @Nullable Long episodeId) {
        log(AdEvent.IMPRESSION, adType, adUnitPath, screen, episodeId, null);
    }

    /**
     * Log a click.
     */
    public void logClick(@NonNull AdType adType,
                         @NonNull String adUnitPath,
                         @NonNull String screen) {
        log(AdEvent.CLICK, adType, adUnitPath, screen, null, null);
    }

    /**
     * Log an ad close.
     */
    public void logClose(@NonNull AdType adType,
                         @NonNull String adUnitPath,
                         @NonNull String screen) {
        log(AdEvent.CLOSE, adType, adUnitPath, screen, null, null);
    }

    private String getAppVersion(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
