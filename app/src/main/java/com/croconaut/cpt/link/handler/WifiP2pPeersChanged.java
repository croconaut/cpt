package com.croconaut.cpt.link.handler;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.GlobalIntent;
import com.croconaut.cpt.link.Settings;

public class WifiP2pPeersChanged extends GlobalIntent {
    public interface Receiver {
        void onWifiP2pPeersChanged(Context context, WifiP2pDeviceList wifiP2pDeviceList);
    }

    public WifiP2pPeersChanged() {
        super(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        WifiP2pDeviceList wifiP2pDeviceList;
        if (Build.VERSION.SDK_INT >= 18 && Settings.getInstance().useNewApi) {
            wifiP2pDeviceList = intent.getParcelableExtra(WifiP2pManager.EXTRA_P2P_DEVICE_LIST);
        } else {
            wifiP2pDeviceList = null;
        }
        ((Receiver) targetReceiver).onWifiP2pPeersChanged(context, wifiP2pDeviceList);
    }
}
