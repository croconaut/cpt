package com.croconaut.cpt.link.handler.p2p;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntentNoArg;

class Start extends LocalIntentNoArg {
    public interface Receiver {
        void onStart(Context context);
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        ((Receiver) targetReceiver).onStart(context);
    }
}
