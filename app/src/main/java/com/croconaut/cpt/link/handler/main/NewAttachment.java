package com.croconaut.cpt.link.handler.main;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LinkLayerServiceIntent;

public class NewAttachment extends LinkLayerServiceIntent {
    private static final String EXTRA_CROCO_ID = "croco_id";

    public interface Receiver {
        void onNewAttachment(Context context, int startId, String from);
    }

    public void send(Context context, String from) {
        super.send(context,
                getIntent(context)
                        .putExtra(EXTRA_CROCO_ID, from)
        );
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        int startId = intent.getIntExtra(EXTRA_START_ID, -1);
        String from = intent.getStringExtra(EXTRA_CROCO_ID);
        ((Receiver) targetReceiver).onNewAttachment(context, startId, from);
    }
}
