package com.croconaut.cpt.link.handler.main;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntent;

public class UpdatedIgnoredDevices extends LocalIntent {
    private static final String EXTRA_CROCO_ID = "croco_id";

    public interface Receiver {
        void onUpdatedIgnoredDevices(Context context, String crocoIdToCancel);
    }

    public void send(Context context, String crocoIdToCancel) {
        super.send(context,
                getIntent()
                    .putExtra(EXTRA_CROCO_ID, crocoIdToCancel)
        );
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        String crocoIdToCancel = intent.getStringExtra(EXTRA_CROCO_ID);
        ((Receiver) targetReceiver).onUpdatedIgnoredDevices(context, crocoIdToCancel);
    }
}
