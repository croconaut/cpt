package com.croconaut.cpt.common.intent;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

public abstract class LocalIntent extends ImplicitIntent {
    protected final void send(Context context, Intent intent) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
