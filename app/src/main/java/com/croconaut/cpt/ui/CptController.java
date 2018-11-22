package com.croconaut.cpt.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import com.croconaut.cpt.link.PreferenceHelper;

public class CptController {
    private static final String TAG = "link";

    private final Context mContext;
    private final PreferenceHelper mPreferenceHelper;

    public CptController(Context context) {
        mContext = context;
        mPreferenceHelper = new PreferenceHelper(mContext, "cpt_ui");
    }

    public void setMode(@LinkLayerMode.CptMode int mode) {
        BootstrapReceiver.startActionMode(mContext, mode);
    }

    public void setTrackingEnabled(boolean enable) {
        mPreferenceHelper.setTrackingEnabled(enable);
        mContext.sendBroadcast(BootstrapReceiver.getRefreshIntent(mContext));
    }
    public boolean getTrackingEnabled() {
        return mPreferenceHelper.getTrackingEnabled();
    }

    public void setLocalNetworkOnlyEnabled(boolean enable) {
        mPreferenceHelper.setLocalNetworkOnlyEnabled(enable);
        mContext.sendBroadcast(BootstrapReceiver.getRefreshIntent(mContext));
    }
    public boolean getLocalNetworkOnlyEnabled() {
        return mPreferenceHelper.getLocalNetworkOnlyEnabled();
    }


    boolean getReverseModeEnabled() {
        return mPreferenceHelper.getReverseConnectionModeEnabled();
    }

    void setReverseModeEnabled(boolean enable) {
        mPreferenceHelper.setReverseConnectionModeEnabled(enable);
        mContext.sendBroadcast(BootstrapReceiver.getRefreshIntent(mContext));
    }

    boolean getNewApiEnabled() {
        return mPreferenceHelper.getNewApiCallsEnabled();
    }

    void setNewApiEnabled(boolean enable) {
        mPreferenceHelper.setNewApiCallsEnabled(enable);
        mContext.sendBroadcast(BootstrapReceiver.getRefreshIntent(mContext));
    }

    boolean isNewApiSupported() {
        return Build.VERSION.SDK_INT >= 18;
    }

    public boolean getDimScreenWorkaroundEnabled() {
        return mPreferenceHelper.getWakeUpOnFormedGroupEnabled();
    }

    public void setDimScreenWorkaroundEnabled(boolean enable) {
        mPreferenceHelper.setWakeUpOnFormedGroupEnabled(enable);
        mContext.sendBroadcast(BootstrapReceiver.getRefreshIntent(mContext));
    }

    public boolean isDimScreenWorkaroundRecommended() {
        // TODO: if changed here, change in preference helper, too
        return Build.MANUFACTURER.toLowerCase().contains("samsung") || Build.BRAND.toLowerCase().contains("google");
    }

    private static final String WIFI_SUSPEND_OPTIMIZATIONS_ENABLED = "wifi_suspend_optimizations_enabled";

    public boolean getBatteryOptimizationEnabled() {
        ContentResolver cr = mContext.getContentResolver();
        // TODO: uncover hidden API
        // enabled by default
        if (Build.VERSION.SDK_INT <= 16) {
            //noinspection deprecation
            return Settings.System.getInt(cr, /*Settings.System.*/WIFI_SUSPEND_OPTIMIZATIONS_ENABLED, -1) != 0;
        } else {
            return Settings.Global.getInt(cr, /*Settings.Global.*/WIFI_SUSPEND_OPTIMIZATIONS_ENABLED, -1) != 0;
        }
    }

    public void setBatteryOptimizationEnabled(boolean enable) {
        if (Build.VERSION.SDK_INT <= 16) {
            ContentResolver cr = mContext.getContentResolver();
            // TODO: uncover hidden API
            //noinspection deprecation
            Settings.System.putInt(cr, /*Settings.System.*/WIFI_SUSPEND_OPTIMIZATIONS_ENABLED, enable ? 1 : 0);
        }
    }

    public boolean isBatteryOptimizationModifiable() {
        return Build.VERSION.SDK_INT <= 16;
    }

    public enum SleepPolicy {
        WIFI_SLEEP_POLICY_DEFAULT,
        WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED,
        WIFI_SLEEP_POLICY_NEVER
    }

    @SuppressWarnings("deprecation")
    public SleepPolicy getWifiSleepPolicy() {
        ContentResolver cr = mContext.getContentResolver();
        if (Build.VERSION.SDK_INT <= 16) {
            switch (Settings.System.getInt(cr, Settings.System.WIFI_SLEEP_POLICY, -1)) {
                case Settings.System.WIFI_SLEEP_POLICY_NEVER:
                    return SleepPolicy.WIFI_SLEEP_POLICY_NEVER;
                case Settings.System.WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED:
                    return SleepPolicy.WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED;
                default:
                    // on PAP3540 it is not set and says "Always" on screen, i.e. NEVER but better
                    // report it as DEFAULT = "Never" and let the user change it manually to be sure
                    return SleepPolicy.WIFI_SLEEP_POLICY_DEFAULT;
            }
        } else {
            switch (Settings.Global.getInt(cr, Settings.Global.WIFI_SLEEP_POLICY, -1)) {
                case Settings.Global.WIFI_SLEEP_POLICY_NEVER:
                    return SleepPolicy.WIFI_SLEEP_POLICY_NEVER;
                case Settings.Global.WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED:
                    return SleepPolicy.WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED;
                case Settings.Global.WIFI_SLEEP_POLICY_DEFAULT:
                default:
                    return SleepPolicy.WIFI_SLEEP_POLICY_DEFAULT;
            }
        }
    }

    @SuppressWarnings("deprecation")
    public void setWifiSleepPolicy(SleepPolicy policy) {
        if (Build.VERSION.SDK_INT <= 16) {
            int policyInt;
            switch (policy) {
                case WIFI_SLEEP_POLICY_NEVER:
                    policyInt = Settings.System.WIFI_SLEEP_POLICY_NEVER;
                    break;
                case WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED:
                    policyInt = Settings.System.WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED;
                    break;
                default:
                    policyInt = Settings.System.WIFI_SLEEP_POLICY_DEFAULT;
            }

            ContentResolver cr = mContext.getContentResolver();
            Settings.System.putInt(cr, Settings.System.WIFI_SLEEP_POLICY, policyInt);
        }
    }

    public boolean isWifiSleepPolicyModifiable() {
        return Build.VERSION.SDK_INT <= 16;
    }

    public Intent getAdvancedWifiIntent() {
        return new Intent(Settings.ACTION_WIFI_IP_SETTINGS);
    }
}
