package com.croconaut.cpt.link.handler.main;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntent;

public class Stop extends LocalIntent {
    private static final String EXTRA_DISABLE = "disable";
    private static final String EXTRA_START_ID = "start_id";

    public interface Receiver {
        void onStop(Context context, int startId, boolean disable);
    }

    public void send(Context context, boolean disable, int startId) {
        super.send(context,
                getIntent()
                    .putExtra(EXTRA_DISABLE, disable)
                    .putExtra(EXTRA_START_ID, startId)
        );
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        int startId = intent.getIntExtra(EXTRA_START_ID, -1);
        boolean disable = intent.getBooleanExtra(EXTRA_DISABLE, false);
        ((Receiver) targetReceiver).onStop(context, startId, disable);
    }
}
