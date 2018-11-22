package com.croconaut.cpt.link.handler.p2p;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntentNoArg;

public class TimerExpired extends LocalIntentNoArg {
    public interface Receiver {
        void onTimerExpired(Context context);
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        ((Receiver) targetReceiver).onTimerExpired(context);
    }
}
