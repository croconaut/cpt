package com.croconaut.cpt.link.handler.main;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LinkLayerServiceIntent;

public class NewMessage extends LinkLayerServiceIntent {
    private static final String EXTRA_TO = "to";

    public interface Receiver {
        void onNewMessage(Context context, int startId, String to);
    }

    public void send(Context context, String to){
        send(context,
                getIntent(context)
                    .putExtra(EXTRA_TO, to)
        );
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        int startId = intent.getIntExtra(EXTRA_START_ID, -1);
        String to = intent.getStringExtra(EXTRA_TO);
        ((Receiver) targetReceiver).onNewMessage(context, startId, to);
    }
}
