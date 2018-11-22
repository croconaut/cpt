package com.croconaut.cpt.link.handler.main;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LinkLayerServiceIntent;

import java.util.ArrayList;

public class DiscoveryResults extends LinkLayerServiceIntent {
    private static final String EXTRA_USERS = "users";

    public interface Receiver {
        void onDiscoveryResults(Context context, int startId, ArrayList<User> users);
    }

    public void send(Context context, ArrayList<User> users) {
        super.send(context,
                getIntent(context)
                    .putParcelableArrayListExtra(EXTRA_USERS, users)
        );
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        int startId = intent.getIntExtra(EXTRA_START_ID, -1);
        ArrayList<User> users = intent.getParcelableArrayListExtra(EXTRA_USERS);
        ((Receiver) targetReceiver).onDiscoveryResults(context, startId, users);
    }
}
