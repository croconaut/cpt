package com.croconaut.cpt.link.handler.main;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntentNoArg;

public class HandlerFinished extends LocalIntentNoArg {
    public interface Receiver {
        void onHandlerFinished(Context context);
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        ((Receiver) targetReceiver).onHandlerFinished(context);
    }
}
