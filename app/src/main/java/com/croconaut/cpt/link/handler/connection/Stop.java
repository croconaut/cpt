package com.croconaut.cpt.link.handler.connection;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntentNoArg;

class Stop extends LocalIntentNoArg {
    public interface Receiver {
        void onStop(Context context);
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        ((Receiver) targetReceiver).onStop(context);
    }
}
