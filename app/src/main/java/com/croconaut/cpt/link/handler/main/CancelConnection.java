package com.croconaut.cpt.link.handler.main;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LinkLayerServiceIntent;

public class CancelConnection extends LinkLayerServiceIntent {
    private static final String EXTRA_CROCO_ID = "croco_id";

    public interface Receiver {
        void onCancelConnection(Context context, int startId, String cancelledCrocoId);
    }

    public void send(Context context, String cancelledCrocoId) {
        super.send(context,
                getIntent(context)
                    .putExtra(EXTRA_CROCO_ID, cancelledCrocoId)
        );
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        int startId = intent.getIntExtra(EXTRA_START_ID, -1);
        String cancelledCrocoId = intent.getStringExtra(EXTRA_CROCO_ID);
        ((Receiver) targetReceiver).onCancelConnection(context, startId, cancelledCrocoId);
    }
}
