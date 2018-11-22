package com.croconaut.cpt.common.intent;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.CptServiceStarter;
import com.croconaut.cpt.link.LinkLayerService;

public abstract class LinkLayerServiceIntent extends ExplicitIntent {
    public static final String EXTRA_START_ID = "start_id";

    protected Intent getIntent(Context context) {
        return getIntent(context, LinkLayerService.class);
    }

    protected void send(Context context, Intent serviceIntent) {
        CptServiceStarter.startService(context, serviceIntent, false);
    }
}
