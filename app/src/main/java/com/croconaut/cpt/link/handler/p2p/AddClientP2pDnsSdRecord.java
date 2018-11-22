package com.croconaut.cpt.link.handler.p2p;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntent;

public class AddClientP2pDnsSdRecord extends LocalIntent {
    private static final String EXTRA_INSTANCE_NAME = "instance_name";
    private static final String EXTRA_RECORD = "record";

    public interface Receiver {
        void onAddClientP2pDnsSdRecord(Context context, String instanceName, Bundle record);
    }

    public void send(Context context, String instanceName, Bundle record) {
        super.send(context,
                getIntent()
                        .putExtra(EXTRA_INSTANCE_NAME, instanceName)
                        .putExtra(EXTRA_RECORD, record)
        );
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        String instanceName = intent.getStringExtra(EXTRA_INSTANCE_NAME);
        Bundle record = intent.getBundleExtra(EXTRA_RECORD);
        ((Receiver) targetReceiver).onAddClientP2pDnsSdRecord(context, instanceName, record);
    }
}
