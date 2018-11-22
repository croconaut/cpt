package com.croconaut.cpt.link.handler;

import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.GlobalIntent;
import com.croconaut.cpt.link.PreferenceHelper;

public class WifiP2pConnectionChanged extends GlobalIntent {
    public interface Receiver {
        void onWifiP2pConnectionChanged(Context context, WifiP2pInfo wifiP2pInfo, NetworkInfo networkInfo, WifiP2pGroup wifiP2pGroup);
    }

    public WifiP2pConnectionChanged() {
        super(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        WifiP2pInfo wifiP2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
        NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
        WifiP2pGroup wifiP2pGroup;
        PreferenceHelper helper = new PreferenceHelper(context);
        if (Build.VERSION.SDK_INT >= 18 && helper.getNewApiCallsEnabled()) {
            wifiP2pGroup = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
        } else {
            wifiP2pGroup = null;
        }
        ((Receiver) targetReceiver).onWifiP2pConnectionChanged(context, wifiP2pInfo, networkInfo, wifiP2pGroup);
    }
}
