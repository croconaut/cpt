package com.croconaut.cpt.link.handler.notification;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.net.wifi.p2p.WifiP2pManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.croconaut.cpt.R;
import com.croconaut.cpt.common.NotificationId;
import com.croconaut.cpt.common.State;
import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.data.CptClientCommunication;
import com.croconaut.cpt.link.PreferenceHelper;
import com.croconaut.cpt.link.handler.WifiP2pDiscoveryChanged;
import com.croconaut.cpt.link.handler.main.RestartTimerExpired;
import com.croconaut.cpt.ui.LinkLayerMode;

public class NotificationHandlerReceiver extends CptBroadcastReceiver implements
        PublishProgress.Receiver,
        WifiP2pDiscoveryChanged.Receiver {
    private final Service service;

    private NotificationCompat.Builder mNotificationBuilder;
    private String lastSetConnectionInfoText;
    private ConnectionProgress lastSetProgress;
    private int mWifiP2pDiscoveryState = -1;

    public NotificationHandlerReceiver(Service service, String tag) {
        super(service, tag);
        this.service = service;

        // local intents
        addIntent(new PublishProgress());
        // global intents
        addIntent(new WifiP2pDiscoveryChanged());

        mNotificationBuilder = new NotificationCompat.Builder(service);
        // FLAG_CANCEL_CURRENT seems to be needed only for devices 4.4 - 4.4.2, see https://code.google.com/p/android/issues/detail?id=61850
        PendingIntent piWifiRestart = PendingIntent.getService(service, NotificationId.WIFI_RESTART_ACTION, new RestartTimerExpired().getIntent(service, true), PendingIntent.FLAG_CANCEL_CURRENT);
        mNotificationBuilder
                .addAction(R.drawable.ic_settings_white_24dp, service.getResources().getString(R.string.cpt_notif_settings),
                        CptClientCommunication.getCptNotificationPendingIntent(service, new PreferenceHelper(service)))
                .addAction(android.R.drawable.stat_notify_sync, service.getResources().getString(R.string.cpt_notif_restart), piWifiRestart)
                .setContentIntent(CptClientCommunication.getCptModeRequestPendingIntent(service, new PreferenceHelper(service), LinkLayerMode.OFF))
                .setSmallIcon(R.drawable.ic_wifon)
        ;
    }

    @Override
    protected State getState() {
        return null;
    }


    @Override
    public void onPublishProgress(Context context, ConnectionProgress progress, String connectionInfoText) {
        Log.v(TAG, getClass().getSimpleName() + ".publishProgress: " + progress);

        lastSetProgress = progress;
        if (connectionInfoText != null) {
            lastSetConnectionInfoText = connectionInfoText;
        } else {
            connectionInfoText = lastSetConnectionInfoText;
        }

        mNotificationBuilder
                .setContentTitle(progress.getDesc(context));

        if (progress != ConnectionProgress.BACKGROUND_MODE) {
            mNotificationBuilder
                    .setProgress(progress.getMax(), progress.getStep(), progress.getMax() == 0)
                    .setContentText((mWifiP2pDiscoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED ? "[DE]" : "[DD]") + (connectionInfoText != null ? connectionInfoText : ""))
            ;
        } else {
            mNotificationBuilder
                    .setProgress(0, 0, false)
                    .setContentText("")
            ;
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NotificationId.WIFI_P2P_OFF);
        service.startForeground(NotificationId.FOREGROUND_SERVICE, mNotificationBuilder.build());
    }

    @Override
    public void onWifiP2pDiscoveryChanged(Context context, int wifiP2pDiscoveryState) {
        mWifiP2pDiscoveryState = wifiP2pDiscoveryState;
        if (lastSetProgress != null) {
            onPublishProgress(context, lastSetProgress, null);
        }
    }
}
