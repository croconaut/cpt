package com.croconaut.cpt.link.handler.p2p;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntent;
import com.croconaut.cpt.link.PreferenceHelper;

public class UpdatedUsername extends LocalIntent {
    private static final String EXTRA_USERNAME = "username";

    public interface Receiver {
        void onUpdatedUsername(Context context, String username);
    }

    static String getLastSetValue(Context context) {
        PreferenceHelper helper = new PreferenceHelper(context);
        return helper.getUsername();
    }

    public void send(Context context, String username){
        PreferenceHelper helper = new PreferenceHelper(context);
        helper.setUsername(username);   // used by HOP, P2P NSD, Send by email, app server

        super.send(context,
                getIntent()
                    .putExtra(EXTRA_USERNAME, username)
        );
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        String username = intent.getStringExtra(EXTRA_USERNAME);
        ((Receiver) targetReceiver).onUpdatedUsername(context, username);
    }
}
