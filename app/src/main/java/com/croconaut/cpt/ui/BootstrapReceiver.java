package com.croconaut.cpt.ui;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import com.croconaut.cpt.common.CptServiceStarter;
import com.croconaut.cpt.common.intent.PrettyState;
import com.croconaut.cpt.link.LinkLayerService;
import com.croconaut.cpt.link.PreferenceHelper;

public class BootstrapReceiver extends BroadcastReceiver {
    private static final String TAG = "link.bootstrap";

    private static final String ACTION_REFRESH = "com.croconaut.cpt.link.action.REFRESH";

    private static final String ACTION_MODE = "com.croconaut.cpt.link.action.MODE";
    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_FORCE = "force";

    public static final String ACTION_SETTINGS = "com.croconaut.cpt.link.action.SETTINGS";
    public static final String EXTRA_SETTINGS_NEW_API = "new_api";
    public static final String EXTRA_SETTINGS_REVERSE_MODE = "reverse_mode";
    public static final String EXTRA_SETTINGS_MODE = "mode";
    public static final String EXTRA_SETTINGS_FORCE = "force";
    public static final String EXTRA_SETTINGS_WAKE_UP_ON_FORMED_GROUP = "wake_up";
    public static final String EXTRA_SETTINGS_TRACKING = "tracking";
    public static final String EXTRA_SETTINGS_LOCAL_ONLY = "local_only";

    public static void startActionMode(Context context, int mode) {
        Log.v(TAG, BootstrapReceiver.class.getSimpleName() + ".startActionMode");

        if (isEnabled(context)) {
            context.sendBroadcast(getModeIntent(context, mode));
        } else if (mode != LinkLayerMode.OFF) {
            enable(context);
            context.sendBroadcast(getModeIntent(context, mode));
        } else {
            Log.w(TAG, "CPT is disabled and OFF, not starting explicitly");
        }
    }

    private static Intent getModeIntent(Context context, int mode) {
        return new Intent(context, BootstrapReceiver.class)
                .setAction(ACTION_MODE)
                .putExtra(EXTRA_MODE, mode)
        ;
    }

    public static Intent getRefreshIntent(Context context) {
        return getRefreshIntent(context, true);
    }

    public static Intent getRefreshIntent(Context context, boolean force) {
        return new Intent(context, BootstrapReceiver.class)
                .setAction(ACTION_REFRESH)
                .putExtra(EXTRA_FORCE, force)
        ;
    }

    private static boolean isEnabled(Context context) {
        PackageManager packageManager = context.getPackageManager();
        ComponentName receiver = new ComponentName(context, BootstrapReceiver.class);
        return packageManager.getComponentEnabledSetting(receiver) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    private static void enable(Context context) {
        Log.v(TAG, BootstrapReceiver.class.getSimpleName() + ".enable");

        PackageManager packageManager = context.getPackageManager();
        ComponentName receiver = new ComponentName(context, BootstrapReceiver.class);
        if (packageManager.getComponentEnabledSetting(receiver) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            packageManager.setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        }
    }

    public static void disable(Context context) {
        Log.v(TAG, BootstrapReceiver.class.getSimpleName() + ".disable");

        PackageManager packageManager = context.getPackageManager();
        ComponentName receiver = new ComponentName(context, BootstrapReceiver.class);
        if (packageManager.getComponentEnabledSetting(receiver) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            packageManager.setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    /*
     * The idea is this:
     * - if enabled, the service is started, p2p receiver registered and then decided whether to shutdown or not
     * - if background mode, the service continues to run and manages screen states on its own
     * - if disabled, the service should be running (as we're disabling this receiver otherwise) and therefore the passed value should stop the service (and disable this receiver)
     * - if wifi (p2p) disabled, the service will catch it and disable itself
     * - if wifi (p2p) enabled,  the service is started and p2p receiver will setup everything
     * - if wifi (p2p) disabled and the service killed, this receiver does nothing, all handing is done in the service
     */

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v(TAG, getClass().getSimpleName() + ".onReceive: " + PrettyState.log(intent));

        PreferenceHelper preferenceHelper = new PreferenceHelper(context, "cpt_ui");
        String action = intent.getAction();

        int mode = -1;
        boolean force = false;
        if (ACTION_MODE.equals(action)) {
            mode = intent.getIntExtra(EXTRA_MODE, -1);
            preferenceHelper.setMode(mode);
        } else if ((Intent.ACTION_BOOT_COMPLETED.equals(action) || ACTION_REFRESH.equals(action))
                || (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action) && intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) == WifiP2pManager.WIFI_P2P_STATE_ENABLED)) {
            mode = preferenceHelper.getMode();
            force = intent.getBooleanExtra(EXTRA_FORCE, true);
        }

        if (mode != -1) {
            startCpt(context, mode, force, preferenceHelper);
        } else {
            Log.v(TAG, "mode == -1 (not starting cpt)");
        }
    }

    private void startCpt(Context context, final int mode, final boolean force, final PreferenceHelper preferenceHelper) {
        CptServiceStarter.startService(context,
                new Intent(context, LinkLayerService.class)
                        .setAction(ACTION_SETTINGS)
                        .putExtra(EXTRA_SETTINGS_NEW_API, preferenceHelper.getNewApiCallsEnabled())
                        .putExtra(EXTRA_SETTINGS_REVERSE_MODE, preferenceHelper.getReverseConnectionModeEnabled())
                        .putExtra(EXTRA_SETTINGS_MODE, mode)
                        .putExtra(EXTRA_SETTINGS_FORCE, force)
                        .putExtra(EXTRA_SETTINGS_WAKE_UP_ON_FORMED_GROUP, preferenceHelper.getWakeUpOnFormedGroupEnabled())
                        .putExtra(EXTRA_SETTINGS_TRACKING, preferenceHelper.getTrackingEnabled())
                        .putExtra(EXTRA_SETTINGS_LOCAL_ONLY, preferenceHelper.getLocalNetworkOnlyEnabled()),
                false
        );
    }
}
