package com.croconaut.cpt.network;

import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;

import com.croconaut.cpt.data.DatabaseManager;
import com.croconaut.cpt.gcm.SyncTaskService;
import com.croconaut.cpt.link.PreferenceHelper;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.gcm.Task;

abstract class AppServerSyncService extends ClientSyncService {
    protected static final String EXTRA_SYNC_INTERNET = "internet_available";
    protected static final String     EXTRA_SYNC_FULL = "full_sync";

    private PowerManager.WakeLock mWakeLock;

    protected AppServerSyncService(String TAG) {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        DatabaseManager.obtainLocation(this);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getSimpleName());
        mWakeLock.acquire();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    protected void scheduleOneOffTask(Context context, Bundle extras) {
        Log.v(TAG, getClass().getSimpleName() + ".scheduleOneOffTask");

        OneoffTask oneoffTask = new OneoffTask.Builder()
                .setService(SyncTaskService.class)
                .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                .setPersisted(true)
                .setUpdateCurrent(true)
                .setTag(getClass().getName())
                .setExecutionWindow(0, 30)  // no need to have this window too small, we're supposed to use direct connection whenever possible else it's a bug
                .setExtras(extras)
                .build();
        GcmNetworkManager.getInstance(context).schedule(oneoffTask);
    }

    protected void cancelOneOffTask(Context context) {
        Log.v(TAG, getClass().getSimpleName() + ".cancelOneOffTask");

        GcmNetworkManager.getInstance(context).cancelTask(getClass().getName(), SyncTaskService.class);
    }

    protected static void sync(Context context, Class<? extends AppServerSyncService> clsService, boolean isInternetConnectivityAvailable, boolean fullSync) {
        PreferenceHelper helper = new PreferenceHelper(context);
        if (helper.getInternetEnabled()) {
            Bundle bundle = new Bundle();
            bundle.putBoolean(EXTRA_SYNC_INTERNET, isInternetConnectivityAvailable);
            bundle.putBoolean(EXTRA_SYNC_FULL, fullSync);
            sync(context, clsService, null, null, bundle);
        } else {
            Log.w("gcm", "Ignoring sync request for " + clsService.getName());
        }
    }

    protected static void cancelSync(Context context, Class<? extends AppServerSyncService> clsService) {
        cancelSync(context, clsService, null);
    }
}
