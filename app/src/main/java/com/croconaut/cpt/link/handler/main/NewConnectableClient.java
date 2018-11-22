package com.croconaut.cpt.link.handler.main;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntent;

import java.net.InetSocketAddress;

public class NewConnectableClient extends LocalIntent {
    private static final String EXTRA_CROCO_ID = "croco_id";
    private static final String EXTRA_SOCKET_ADDRESS = "socket_address";
    private static final String EXTRA_HASH = "hash";
    private static final String EXTRA_P2P_CLIENT = "p2p_client";

    public interface Receiver {
        void onNewConnectableClient(Context context, String crocoId, InetSocketAddress socketAddress, String hash, boolean isP2pClient);
    }

    public void send(Context context, String crocoId, InetSocketAddress socketAddress, String hash, boolean isP2pClient) {
        super.send(context,
                getIntent()
                        .putExtra(EXTRA_CROCO_ID, crocoId)
                        .putExtra(EXTRA_SOCKET_ADDRESS, socketAddress)
                        .putExtra(EXTRA_HASH, hash)
                        .putExtra(EXTRA_P2P_CLIENT, isP2pClient)
        );
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        String crocoId = intent.getStringExtra(EXTRA_CROCO_ID);
        InetSocketAddress socketAddress = (InetSocketAddress) intent.getSerializableExtra(EXTRA_SOCKET_ADDRESS);
        String hash = intent.getStringExtra(EXTRA_HASH);
        boolean isP2pClient = intent.getBooleanExtra(EXTRA_P2P_CLIENT, false);
        ((Receiver) targetReceiver).onNewConnectableClient(context, crocoId, socketAddress, hash, isP2pClient);
    }
}
