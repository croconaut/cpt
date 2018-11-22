package com.croconaut.cpt.network;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.net.InetSocketAddress;

abstract class ClientSyncService extends NetworkSyncService {
    protected static final String          ACTION_SYNC = "com.croconaut.cpt.network.action.SYNC";
    protected static final String        ACTION_CANCEL = "com.croconaut.cpt.network.action.CANCEL";
    protected static final String       EXTRA_CROCO_ID = "croco_id";
    protected static final String EXTRA_SOCKET_ADDRESS = "socket_address";

    protected ClientSyncService(String TAG) {
        super(TAG);
    }

    protected static void sync(Context context, Class<? extends ClientSyncService> clsService, String crocoId, InetSocketAddress socketAddress) {
        sync(context, clsService, crocoId, socketAddress, new Bundle());
    }

    protected static void sync(Context context, Class<? extends ClientSyncService> clsService, String crocoId, InetSocketAddress socketAddress, Bundle extras) {
        context.startService(
                new Intent(context, clsService)
                        .setAction(ACTION_SYNC)
                        .putExtra(EXTRA_CROCO_ID, crocoId)
                        .putExtra(EXTRA_SOCKET_ADDRESS, socketAddress)
                        .putExtras(extras)
        );
    }

    protected static void cancelSync(Context context, Class<? extends ClientSyncService> clsService, String crocoId) {
        cancelSync(context, clsService, crocoId, new Bundle());
    }

    protected static void cancelSync(Context context, Class<? extends ClientSyncService> clsService, String crocoId, Bundle extras) {
        context.startService(
                new Intent(context, clsService)
                        .setAction(ACTION_CANCEL)
                        .putExtra(EXTRA_CROCO_ID, crocoId)
                        .putExtras(extras)
        );
    }
}
