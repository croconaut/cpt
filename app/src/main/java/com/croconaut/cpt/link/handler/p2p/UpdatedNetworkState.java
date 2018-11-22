package com.croconaut.cpt.link.handler.p2p;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntent;

import java.net.InetAddress;

public class UpdatedNetworkState extends LocalIntent {
    private static final String EXTRA_NETWORK_STATE = "network_state";

    private static InetAddress mLastSetNetworkAddress;

    public interface Receiver {
        void onUpdatedNetworkState(Context context, InetAddress networkAddress);
    }

    static InetAddress getLastSetValue() {
        return mLastSetNetworkAddress;
    }

    static void resetLastSetValue() {
        mLastSetNetworkAddress = null;
    }

    public void send(Context context, InetAddress networkAddress) {
        mLastSetNetworkAddress = networkAddress;

        super.send(context,
                getIntent()
                    .putExtra(EXTRA_NETWORK_STATE, networkAddress)
        );
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        InetAddress networkAddress = (InetAddress) intent.getSerializableExtra(EXTRA_NETWORK_STATE);
        ((Receiver) targetReceiver).onUpdatedNetworkState(context, networkAddress);
    }
}
