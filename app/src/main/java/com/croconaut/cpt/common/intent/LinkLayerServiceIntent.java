package com.croconaut.cpt.common.intent;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.CptServiceStarter;
import com.croconaut.cpt.link.LinkLayerService;
import com.croconaut.cpt.link.Settings;

public abstract class LinkLayerServiceIntent extends ExplicitIntent {
    public static final String EXTRA_START_ID = "start_id";
    public static final String EXTRA_CPT_SETTINGS = "settings";

    protected Intent getIntent(Context context) {
        return getIntent(context, LinkLayerService.class)
                // make a backup in case the service is killed
                .putExtra(EXTRA_CPT_SETTINGS, Settings.getInstance())
        ;
    }

    protected void send(Context context, Intent serviceIntent) {
        CptServiceStarter.startService(context, serviceIntent, false);
    }
}
