package com.croconaut.cpt.common.intent;

import android.content.Context;

public abstract class LocalIntentNoArg extends LocalIntent {
    public final void send(Context context) {
        send(context, getIntent());
    }
}
