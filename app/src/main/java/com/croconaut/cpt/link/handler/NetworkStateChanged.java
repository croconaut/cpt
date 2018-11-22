package com.croconaut.cpt.link.handler;

import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.GlobalIntent;

public class NetworkStateChanged extends GlobalIntent {
    public interface Receiver {
        void onNetworkStateChanged(Context context, NetworkInfo networkInfo);
    }

    public NetworkStateChanged() {
        super(WifiManager.NETWORK_STATE_CHANGED_ACTION);
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
        ((Receiver) targetReceiver).onNetworkStateChanged(context, networkInfo);
    }
}
