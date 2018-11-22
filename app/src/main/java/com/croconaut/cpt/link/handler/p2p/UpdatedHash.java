package com.croconaut.cpt.link.handler.p2p;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntent;
import com.croconaut.cpt.link.PreferenceHelper;

public class UpdatedHash extends LocalIntent {
    private static final String EXTRA_HASH = "hash";

    public interface Receiver {
        void onUpdatedHash(Context context, String hash);
    }

    static String getLastSetValue(Context context) {
        PreferenceHelper helper = new PreferenceHelper(context);
        return helper.getHash();
    }

    public void send(Context context, String hash){
        PreferenceHelper helper = new PreferenceHelper(context);
        helper.setHash(hash);

        super.send(context,
                getIntent()
                    .putExtra(EXTRA_HASH, hash)
        );
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        String hash = intent.getStringExtra(EXTRA_HASH);
        ((Receiver) targetReceiver).onUpdatedHash(context, hash);
    }
}
