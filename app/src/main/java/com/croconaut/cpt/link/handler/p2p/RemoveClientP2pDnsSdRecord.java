package com.croconaut.cpt.link.handler.p2p;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntent;

public class RemoveClientP2pDnsSdRecord extends LocalIntent {
    private static final String EXTRA_INSTANCE_NAME = "instance_name";

    public interface Receiver {
        void onRemoveClientP2pDnsSdRecord(Context context, String instanceName);
    }

    public void send(Context context, String instanceName) {
        super.send(context,
                getIntent()
                        .putExtra(EXTRA_INSTANCE_NAME, instanceName)
        );
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        String instanceName = intent.getStringExtra(EXTRA_INSTANCE_NAME);
        ((Receiver) targetReceiver).onRemoveClientP2pDnsSdRecord(context, instanceName);
    }
}
