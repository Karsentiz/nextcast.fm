package de.danoeh.antennapod.ui.screen.preferences;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.ads.AdsManager;
import de.danoeh.antennapod.ads.config.AdsConfig;
import de.danoeh.antennapod.ads.policy.AdsPolicyManager;
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment;

/**
 * Debug preferences for ads - only visible in debug builds.
 */
public class AdsDebugPreferencesFragment extends AnimatedPreferenceFragment {

    private static final String PREF_ADS_ENABLED = "pref_ads_enabled";
    private static final String PREF_TEST_MODE = "pref_ads_test_mode";
    private static final String PREF_DEBUG_LOGGING = "pref_ads_debug_logging";
    private static final String PREF_BANNERS_ENABLED = "pref_banners_enabled";
    private static final String PREF_INTERSTITIALS_ENABLED = "pref_interstitials_enabled";
    private static final String PREF_AUDIO_ADS_ENABLED = "pref_audio_ads_enabled";
    private static final String PREF_INTERSTITIAL_FREQ = "pref_interstitial_episode_freq";
    private static final String PREF_AUDIO_AD_FREQ = "pref_audio_ad_episode_freq";
    private static final String PREF_RESET_COUNTERS = "pref_reset_ad_counters";
    private static final String PREF_TEST_BANNER = "pref_trigger_test_banner";
    private static final String PREF_TEST_INTERSTITIAL = "pref_trigger_test_interstitial";
    private static final String PREF_STATUS_INFO = "ads_status_info";

    private AdsConfig adsConfig;
    private AdsPolicyManager policyManager;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_ads_debug);

        adsConfig = AdsConfig.getInstance(requireContext());
        policyManager = AdsPolicyManager.getInstance(requireContext());

        setupPreferences();
        updateStatusInfo();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((PreferenceActivity) getActivity()).getSupportActionBar().setTitle("Ads Debug");
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStatusInfo();
    }

    private void setupPreferences() {
        // Master ads switch
        SwitchPreferenceCompat adsEnabled = findPreference(PREF_ADS_ENABLED);
        adsEnabled.setChecked(adsConfig.isAdsEnabled());
        adsEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
            adsConfig.setAdsEnabled((Boolean) newValue);
            updateStatusInfo();
            return true;
        });

        // Test mode switch
        SwitchPreferenceCompat testMode = findPreference(PREF_TEST_MODE);
        testMode.setChecked(adsConfig.isTestMode());
        testMode.setOnPreferenceChangeListener((preference, newValue) -> {
            adsConfig.setTestMode((Boolean) newValue);
            Toast.makeText(getContext(),
                (Boolean) newValue ? "Test mode enabled - using test ad units" : "Test mode disabled - using production ad units",
                Toast.LENGTH_SHORT).show();
            updateStatusInfo();
            return true;
        });

        // Debug logging switch
        SwitchPreferenceCompat debugLogging = findPreference(PREF_DEBUG_LOGGING);
        debugLogging.setChecked(adsConfig.isDebugLogging());
        debugLogging.setOnPreferenceChangeListener((preference, newValue) -> {
            adsConfig.setDebugLogging((Boolean) newValue);
            return true;
        });

        // Banner ads switch
        SwitchPreferenceCompat bannersEnabled = findPreference(PREF_BANNERS_ENABLED);
        bannersEnabled.setChecked(adsConfig.isBannersEnabled());
        bannersEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
            adsConfig.setBannersEnabled((Boolean) newValue);
            updateStatusInfo();
            return true;
        });

        // Interstitials switch
        SwitchPreferenceCompat interstitialsEnabled = findPreference(PREF_INTERSTITIALS_ENABLED);
        interstitialsEnabled.setChecked(adsConfig.isInterstitialsEnabled());
        interstitialsEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
            adsConfig.setInterstitialsEnabled((Boolean) newValue);
            updateStatusInfo();
            return true;
        });

        // Audio ads switch
        SwitchPreferenceCompat audioAdsEnabled = findPreference(PREF_AUDIO_ADS_ENABLED);
        audioAdsEnabled.setChecked(adsConfig.isAudioAdsEnabled());
        audioAdsEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
            adsConfig.setAudioAdsEnabled((Boolean) newValue);
            updateStatusInfo();
            return true;
        });

        // Interstitial frequency
        Preference interstitialFreq = findPreference(PREF_INTERSTITIAL_FREQ);
        updateFrequencySummary(interstitialFreq, adsConfig.getInterstitialEpisodeFrequency());
        interstitialFreq.setOnPreferenceClickListener(preference -> {
            showFrequencyDialog("Interstitial Episode Frequency",
                adsConfig.getInterstitialEpisodeFrequency(),
                value -> {
                    adsConfig.setInterstitialEpisodeFrequency(value);
                    updateFrequencySummary(preference, value);
                });
            return true;
        });

        // Audio ad frequency
        Preference audioAdFreq = findPreference(PREF_AUDIO_AD_FREQ);
        updateFrequencySummary(audioAdFreq, adsConfig.getAudioAdEpisodeFrequency());
        audioAdFreq.setOnPreferenceClickListener(preference -> {
            showFrequencyDialog("Audio Ad Episode Frequency",
                adsConfig.getAudioAdEpisodeFrequency(),
                value -> {
                    adsConfig.setAudioAdEpisodeFrequency(value);
                    updateFrequencySummary(preference, value);
                });
            return true;
        });

        // Reset counters
        findPreference(PREF_RESET_COUNTERS).setOnPreferenceClickListener(preference -> {
            policyManager.resetSession();
            Toast.makeText(getContext(), "Ad counters reset", Toast.LENGTH_SHORT).show();
            updateStatusInfo();
            return true;
        });

        // Test banner
        findPreference(PREF_TEST_BANNER).setOnPreferenceClickListener(preference -> {
            Toast.makeText(getContext(), "Banner ads display on main screens", Toast.LENGTH_SHORT).show();
            return true;
        });

        // Test interstitial
        findPreference(PREF_TEST_INTERSTITIAL).setOnPreferenceClickListener(preference -> {
            AdsManager adsManager = AdsManager.getInstance(requireContext());
            if (adsManager.isInterstitialReady()) {
                adsManager.tryShowInterstitial(requireActivity(), "debug_test", null);
            } else {
                adsManager.preloadInterstitial("debug_test");
                Toast.makeText(getContext(), "Preloading interstitial... try again in a few seconds", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    private void updateStatusInfo() {
        Preference statusInfo = findPreference(PREF_STATUS_INFO);
        if (statusInfo != null) {
            StringBuilder status = new StringBuilder();
            status.append("Ads: ").append(adsConfig.isAdsEnabled() ? "ON" : "OFF");
            status.append(" | Test: ").append(adsConfig.isTestMode() ? "YES" : "NO");
            status.append("\nBanners: ").append(adsConfig.isBannersEnabled() ? "ON" : "OFF");
            status.append(" | Interstitials: ").append(adsConfig.isInterstitialsEnabled() ? "ON" : "OFF");
            status.append(" | Audio: ").append(adsConfig.isAudioAdsEnabled() ? "ON" : "OFF");
            status.append("\nEpisodes this session: ").append(policyManager.getSessionEpisodeCount());
            status.append("\nInterstitials shown: ").append(policyManager.getSessionInterstitialCount());
            statusInfo.setSummary(status.toString());
        }
    }

    private void updateFrequencySummary(Preference preference, int value) {
        preference.setSummary("Every " + value + " episodes");
    }

    private void showFrequencyDialog(String title, int currentValue, FrequencyCallback callback) {
        String[] options = {"1", "2", "3", "4", "5", "10"};
        int[] values = {1, 2, 3, 4, 5, 10};

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(options, (dialog, which) -> {
                callback.onValueSelected(values[which]);
            })
            .show();
    }

    private interface FrequencyCallback {
        void onValueSelected(int value);
    }
}
