package com.croconaut.cpt.link.handler;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntentNoArg;

public class DropConnection extends LocalIntentNoArg {
    public interface Receiver {
        void onDropConnection(Context context);
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        ((Receiver) targetReceiver).onDropConnection(context);
    }
}
