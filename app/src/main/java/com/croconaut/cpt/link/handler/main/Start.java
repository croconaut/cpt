package com.croconaut.cpt.link.handler.main;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntent;

public class Start extends LocalIntent {
    private static final String EXTRA_START_ID = "start_id";

    public interface Receiver {
        void onStart(Context context, int startId);
    }

    public void send(Context context, int startId) {
        super.send(context,
                getIntent()
                        .putExtra(EXTRA_START_ID, startId)
        );
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        int startId = intent.getIntExtra(EXTRA_START_ID, -1);
        ((Receiver) targetReceiver).onStart(context, startId);
    }
}
