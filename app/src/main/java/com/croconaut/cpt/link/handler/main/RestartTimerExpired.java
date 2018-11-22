package com.croconaut.cpt.link.handler.main;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LinkLayerServiceIntent;

public class RestartTimerExpired extends LinkLayerServiceIntent {
    private static final String EXTRA_FORCE = "force";

    public interface Receiver {
        void onRestartTimerExpired(Context context, int startId, boolean force);
    }

    public Intent getIntent(Context context, boolean force) {
        return getIntent(context)
                .putExtra(EXTRA_FORCE, force)
        ;
    }

    public void send(Context context, boolean force){
        send(context, getIntent(context, force));
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        int startId = intent.getIntExtra(EXTRA_START_ID, -1);
        boolean force = intent.getBooleanExtra(EXTRA_FORCE, false);
        ((Receiver) targetReceiver).onRestartTimerExpired(context, startId, force);
    }
}
