package com.croconaut.cpt.link.handler;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pManager;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.GlobalIntent;

public class WifiP2pDiscoveryChanged extends GlobalIntent {
    public interface Receiver {
        void onWifiP2pDiscoveryChanged(Context context, int wifiP2pDiscoveryState);
    }

    public WifiP2pDiscoveryChanged() {
        super(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        int wifiP2pDiscoveryState = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1);
        ((Receiver) targetReceiver).onWifiP2pDiscoveryChanged(context, wifiP2pDiscoveryState);
    }
}
