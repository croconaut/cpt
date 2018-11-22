package com.croconaut.cpt.link.handler;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pManager;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.GlobalIntent;

public class WifiP2pStateChanged extends GlobalIntent {
    public interface Receiver {
        void onWifiP2pStateChanged(Context context, int wifiP2pState);
    }

    public WifiP2pStateChanged() {
        super(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        int wifiP2pState = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
        ((Receiver) targetReceiver).onWifiP2pStateChanged(context, wifiP2pState);
    }
}
