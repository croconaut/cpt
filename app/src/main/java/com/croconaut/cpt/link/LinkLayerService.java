package com.croconaut.cpt.link;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.util.Log;

import com.croconaut.cpt.common.CptServiceStarter;
import com.croconaut.cpt.common.intent.LinkLayerServiceIntent;
import com.croconaut.cpt.data.DatabaseManager;
import com.croconaut.cpt.link.handler.Handler;
import com.croconaut.cpt.link.handler.main.GcmSyncRequest;
import com.croconaut.cpt.link.handler.main.MainHandler;
import com.croconaut.cpt.link.handler.main.Start;
import com.croconaut.cpt.link.handler.main.Stop;
import com.croconaut.cpt.link.handler.notification.NotificationHandler;
import com.croconaut.cpt.link.handler.p2p.P2pHandler;
import com.croconaut.cpt.ui.BootstrapReceiver;
import com.croconaut.cpt.ui.LinkLayerMode;

public class LinkLayerService extends Service {
    private static final String TAG = "link";

    private PowerManager mPowerManager;
    private MainHandler mMainHandler;
    private NotificationHandler mNotificationHandler;
    private PreferenceHelper mPreferenceHelper;
    private int mStartId;
    private boolean mIsScreenReceiverRegistered;
    private PowerManager.WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;

    private final BroadcastReceiver mScreenChangedReceiver = new BroadcastReceiver() {
        // this receiver is active only in Settings.backgroundMode (and active Wi-Fi/AP, of course)
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "mScreenChangedReceiver.onReceive: " + intent);

            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                if (mPreferenceHelper.getMode() == LinkLayerMode.BACKGROUND) {
                    new Stop().send(LinkLayerService.this, false, mStartId);
                }
            } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                if (mPreferenceHelper.getMode() == LinkLayerMode.BACKGROUND) {
                    new Start().send(LinkLayerService.this, mStartId);
                }
            }
        }
    };

    @Override
    public void onCreate() {
        Log.v(TAG, getClass().getSimpleName() + ".onCreate");

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);

        // tiny little hacks...
        Handler.firstRunInitialization(this);
        P2pHandler.firstRunInitialization();

        // TODO: it's possible Handler's wifi p2p channel initialization wont be caught
        // because the receiver is just about to be registered one line below
        mMainHandler = new MainHandler(this);
        mMainHandler.start();

        mNotificationHandler = new NotificationHandler(this);
        mNotificationHandler.start();

        mPreferenceHelper = new PreferenceHelper(this);
        // we're OFF by default; the client app is supposed to start CPT with CptControler.setMode()
        mPreferenceHelper.setMode(LinkLayerMode.OFF);

        mWifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, getClass().getSimpleName());
        mWifiLock.acquire();

        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getSimpleName());
        mWakeLock.acquire();
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, getClass().getSimpleName() + ".onDestroy");

        unregisterForScreenChanges();

        mNotificationHandler.stop();
        mMainHandler.stop();

        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }

        if (mWifiLock.isHeld()) {
            mWifiLock.release();
        }
    }

    // TODO: ACTION_DEVICE_STORAGE_LOW, ACTION_BATTERY_LOW, ACTION_SHUTDOWN

    @Override
    public void onTrimMemory(int level) {
        Log.w(TAG, getClass().getSimpleName() + ".onTrimMemory: " + level);

        // TODO: TRIM_MEMORY_COMPLETE, TRIM_MEMORY_MODERATE, TRIM_MEMORY_BACKGROUND, TRIM_MEMORY_UI_HIDDEN, TRIM_MEMORY_RUNNING_CRITICAL, TRIM_MEMORY_RUNNING_LOW, or TRIM_MEMORY_RUNNING_MODERATE
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.w(TAG, getClass().getSimpleName() + ".onTaskRemoved");

        // terrible hack to remove "waitingToKill=remove task"
        // https://code.google.com/p/android/issues/detail?id=63618
        // https://code.google.com/p/android/issues/detail?id=104308
        // https://code.google.com/p/android/issues/detail?id=53313
        Intent activityIntent = new Intent(this, DummyActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(activityIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, getClass().getSimpleName() + ".onBind");

        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, getClass().getSimpleName() + ".onStartCommand: " + intent);

        mStartId = startId;

        int oldMode = mPreferenceHelper.getMode();
        boolean oldUseInternet = mPreferenceHelper.getInternetEnabled();
        boolean oldAllowTracking = mPreferenceHelper.getTrackingEnabled();
        if (BootstrapReceiver.ACTION_SETTINGS.equals(intent.getAction())) {
            mPreferenceHelper.setReverseConnectionModeEnabled(intent.getBooleanExtra(BootstrapReceiver.EXTRA_SETTINGS_REVERSE_MODE, false));
            mPreferenceHelper.setNewApiCallsEnabled(intent.getBooleanExtra(BootstrapReceiver.EXTRA_SETTINGS_NEW_API, false));
            mPreferenceHelper.setMode(intent.getIntExtra(BootstrapReceiver.EXTRA_SETTINGS_MODE, -1));
            mPreferenceHelper.setWakeUpOnFormedGroupEnabled(intent.getBooleanExtra(BootstrapReceiver.EXTRA_SETTINGS_WAKE_UP_ON_FORMED_GROUP, true));
            mPreferenceHelper.setInternetEnabled(intent.getBooleanExtra(BootstrapReceiver.EXTRA_SETTINGS_INTERNET, true));
            mPreferenceHelper.setLocalNetworkOnlyEnabled(intent.getBooleanExtra(BootstrapReceiver.EXTRA_SETTINGS_LOCAL_ONLY, false));
            mPreferenceHelper.setTrackingEnabled(intent.getBooleanExtra(BootstrapReceiver.EXTRA_SETTINGS_TRACKING, true));

            Log.e(TAG, "New settings");
            Log.e(TAG, "============");
            Log.e(TAG, "reverseConnectionMode: " + mPreferenceHelper.getReverseConnectionModeEnabled());
            Log.e(TAG, "useNewApi: " + mPreferenceHelper.getNewApiCallsEnabled() + " (available API: " + Build.VERSION.SDK_INT + ")");
            Log.e(TAG, "mode: " + mPreferenceHelper.getMode());
            Log.e(TAG, "wakeUpOnFormedGroup: " + mPreferenceHelper.getWakeUpOnFormedGroupEnabled());
            Log.e(TAG, "useInternet: " + mPreferenceHelper.getInternetEnabled());
            Log.e(TAG, "useLocalOnly: " + mPreferenceHelper.getLocalNetworkOnlyEnabled());
            Log.e(TAG, "allowTracking: " + mPreferenceHelper.getTrackingEnabled());

            // watch screen changes if necessary
            if (mPreferenceHelper.getMode() == LinkLayerMode.BACKGROUND) {
                registerForScreenChanges();
            } else {
                unregisterForScreenChanges();
            }

            // if re-enabled tracking, get location ASAP
            if (mPreferenceHelper.getTrackingEnabled() && !oldAllowTracking) {
                DatabaseManager.obtainLocation(this);
            }

            // XXX: there used to be a 'force' parameter but it was always set within ACTION_SETTINGS
//            if (mPreferenceHelper.getMode() != oldMode) {
                //noinspection deprecation
                if (mPreferenceHelper.getMode() == LinkLayerMode.FOREGROUND
                        || (mPreferenceHelper.getMode() == LinkLayerMode.BACKGROUND && !mPowerManager.isScreenOn())) {
                    new Start().send(this, startId);
                } else {
                    new Stop().send(this, mPreferenceHelper.getMode() == LinkLayerMode.OFF, startId);
                }
//            } else {
//                Log.d(TAG, "Same mode, not starting/stopping");
//                // don't sync anything actually, just store pass startId on
//                Intent syncIntent = new GcmSyncRequest().getPlainIntent(0);
//                syncIntent.putExtra(LinkLayerServiceIntent.EXTRA_START_ID, startId);
//                mMainHandler.getReceiver().onReceive(this, syncIntent);
//            }

            if (mPreferenceHelper.getInternetEnabled() != oldUseInternet && mPreferenceHelper.getInternetEnabled()) {
                // if we enabled useInternet, sync everything
                Intent syncIntent = new GcmSyncRequest().getPlainIntent(GcmSyncRequest.DOWNLOAD_AND_UPLOAD_EVERYTHING | GcmSyncRequest.UPLOAD_TOKEN_AND_NAME);
                syncIntent.putExtra(LinkLayerServiceIntent.EXTRA_START_ID, startId);
                mMainHandler.getReceiver().onReceive(this, syncIntent);
            }
        } else if ((flags & android.app.Service.START_FLAG_REDELIVERY) != 0) {
            int what = DatabaseManager.getGcmTransactionRequestsMask(this);
            Log.w(TAG, "Redelivery, pending gcm sync request(s): " + what);
            Intent syncIntent = new GcmSyncRequest().getPlainIntent(what);
            syncIntent.putExtra(LinkLayerServiceIntent.EXTRA_START_ID, startId);
            mMainHandler.getReceiver().onReceive(this, syncIntent);
        }

        // mWakeLock should be in charge now
        CptServiceStarter.finish(this, intent);

        if (!BootstrapReceiver.ACTION_SETTINGS.equals(intent.getAction())) {
            intent.putExtra(LinkLayerServiceIntent.EXTRA_START_ID, startId);
            mMainHandler.getReceiver().onReceive(this, intent);
        }

        return START_REDELIVER_INTENT;
    }

    private void registerForScreenChanges() {
        Log.v(TAG, getClass().getSimpleName() + ".registerForScreenChanges");

        if (!mIsScreenReceiverRegistered) {
            mIsScreenReceiverRegistered = true;
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_SCREEN_ON);
            intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
            registerReceiver(mScreenChangedReceiver, intentFilter);
        }
    }

    private void unregisterForScreenChanges() {
        Log.v(TAG, getClass().getSimpleName() + ".unregisterForScreenChanges");

        if (mIsScreenReceiverRegistered) {
            mIsScreenReceiverRegistered = false;
            unregisterReceiver(mScreenChangedReceiver);
        }
    }
}
