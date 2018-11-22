package com.croconaut.cpt.common;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.PowerManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.croconaut.cpt.link.PreferenceHelper;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

// fortunately, it seems this receiver doesn't need a Wi-Fi (P2P) permission but does need android.Manifest.permission.WAKE_LOCK
public class CptServiceStarter extends WakefulBroadcastReceiver {
    private static final String TAG = "link";

    private static final String EXTRA_DEVICE_ADDRESS = "com.croconaut.cpt.common.device_address";
    private static final String EXTRA_DEVICE_NAME    = "com.croconaut.cpt.common.device_name";

    // slow-ish but simpler than Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static Set<CptServiceStarter> mInstances = Collections.synchronizedSet(new HashSet<CptServiceStarter>());
    private final Intent serviceIntent;
    private final boolean isIntentService;
    private final PowerManager.WakeLock mWakeLock;

    public static void startService(Context context, Intent serviceIntent, boolean skipCrocoId) {
        if (skipCrocoId) {
            doStartService(context, serviceIntent);
        } else {
            // ensure it doesn't get garbage collected
            mInstances.add(new CptServiceStarter(context, serviceIntent, false));
        }
    }

    public static void startIntentService(Context context, Intent serviceIntent, boolean skipCrocoId) {
        if (skipCrocoId) {
            doStartIntentService(context, serviceIntent);
        } else {
            // ensure it doesn't get garbage collected
            mInstances.add(new CptServiceStarter(context, serviceIntent, true));
        }
    }

    // expected to run in ":cpt" process!
    public static void finish(Service service, Intent intent) {
        String deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS);
        String deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME);

        if (deviceAddress != null && deviceName != null) {
            // these comparisons (and writes) are not atomic, of course. but this doesn't pose a huge
            // problem, the worst that can happen is to write the same croco id/username twice
            PreferenceHelper preferenceHelper = new PreferenceHelper(service);
            if (preferenceHelper.getCrocoId() != null) {
                if (!deviceAddress.equals(preferenceHelper.getCrocoId())) {
                    throw new IllegalStateException("Fatal error, the old P2P MAC address: '" + preferenceHelper.getCrocoId() + "' has been changed to '"
                            + deviceAddress + "' !!!");
                }
            } else {
                preferenceHelper.setCrocoId(deviceAddress);
            }

            if (preferenceHelper.getUsername() == null) {
                // set username to device name, better than nothing
                preferenceHelper.setUsername(deviceName);
            }
        }

        if (!(service instanceof WakefulIntentService)) {
            // note: it's possible the lock wont be released because the service was started in different process
            // but this is OK, it will get released eventually, after one minute, no problem
            completeWakefulIntent(intent);
        }
    }

    private static void doStartService(Context context, Intent serviceIntent) {
        ComponentName componentName = startWakefulService(context, serviceIntent);
        if (componentName == null) {
            Log.e(TAG, "Service does not exist: " + serviceIntent);
        }
    }

    private static void doStartIntentService(Context context, Intent serviceIntent) {
        WakefulIntentService.sendWakefulWork(context, serviceIntent);
    }

    private CptServiceStarter(Context context, Intent serviceIntent, boolean isIntentService) {
        this.serviceIntent = serviceIntent;
        this.isIntentService = isIntentService;

        // hold the lock even here -- the system may kill us while waiting for the intent
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getSimpleName());
        mWakeLock.acquire();

        // use application context to avoid ReceiverCallNotAllowedException
        context.getApplicationContext().registerReceiver(this, new IntentFilter(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v(TAG, getClass().getSimpleName() + ".onReceive");

        context.getApplicationContext().unregisterReceiver(this);
        mWakeLock.release();
        mInstances.remove(this);   // allow garbage collection

        WifiP2pDevice wifiP2pDevice = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
        serviceIntent
                .putExtra(EXTRA_DEVICE_ADDRESS, wifiP2pDevice.deviceAddress)
                .putExtra(EXTRA_DEVICE_NAME, wifiP2pDevice.deviceName)
        ;
        if (isIntentService) {
            doStartIntentService(context, serviceIntent);
        } else {
            doStartService(context, serviceIntent);
        }
    }
}
