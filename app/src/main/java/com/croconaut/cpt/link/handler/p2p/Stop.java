package com.croconaut.cpt.link.handler.p2p;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntent;

class Stop extends LocalIntent {
    private static final String EXTRA_WAIT = "wait";

    public interface Receiver {
        void onStop(Context context, boolean waitForDnsTxtRecordChange);
    }

    public void send(Context context, boolean waitForDnsTxtRecordChange){
        super.send(context,
                getIntent()
                        .putExtra(EXTRA_WAIT, waitForDnsTxtRecordChange)
        );
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        boolean waitForDnsTxtRecordChange = intent.getBooleanExtra(EXTRA_WAIT, false);
        ((Receiver) targetReceiver).onStop(context, waitForDnsTxtRecordChange);
    }
}
