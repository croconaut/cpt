package com.croconaut.cpt.common.intent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.croconaut.cpt.common.State;

import java.util.HashMap;
import java.util.Map;

public abstract class CptBroadcastReceiver extends BroadcastReceiver {
    protected final String TAG;
    protected final Context context;
    private final Map<String, CptIntent> intentHandlerMap = new HashMap<>();

    public CptBroadcastReceiver(Context context, String tag) {
        this.context = context;
        this.TAG = tag;
    }

    public void register() {
        IntentFilter globalIntentFilter = new IntentFilter();
        IntentFilter localIntentFilter = new IntentFilter();
        for (CptIntent cptIntent : intentHandlerMap.values()) {
            if (cptIntent instanceof LocalIntent) {
                localIntentFilter.addAction(cptIntent.getAction());
            } else if (cptIntent instanceof GlobalIntent) {
                globalIntentFilter.addAction(cptIntent.getAction());
            } else if (cptIntent instanceof LinkLayerServiceIntent) {
                Log.w(TAG, "This intent must be caught in its service: " + cptIntent.getClass().getSimpleName());
            } else {
                throw new IllegalArgumentException("Illegal cpt intent to register: " + cptIntent);
            }
        }
        context.registerReceiver(this, globalIntentFilter);
        LocalBroadcastManager.getInstance(context).registerReceiver(this, localIntentFilter);
    }

    public void unregister() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
        context.unregisterReceiver(this);
    }

    protected void addIntent(CptIntent cptIntent) {
        intentHandlerMap.put(cptIntent.getAction(), cptIntent);
    }

    protected void removeIntent(CptIntent cptIntent) {
        intentHandlerMap.remove(cptIntent.getAction());
    }

    @Override
    public final void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getAction() != null) {
            Log.v(TAG, PrettyState.log(intent) + " in state: " + getState());

            CptIntent cptIntent = intentHandlerMap.get(intent.getAction());
            if (cptIntent != null) {
                cptIntent.onReceive(context, intent, this);
            } else {
                throw new IllegalArgumentException("Unknown intent: " + cptIntent);
            }
        }
    }

    protected abstract State getState();
}
