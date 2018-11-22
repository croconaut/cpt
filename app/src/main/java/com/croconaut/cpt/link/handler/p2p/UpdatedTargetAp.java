package com.croconaut.cpt.link.handler.p2p;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntent;

public class UpdatedTargetAp extends LocalIntent {
    private static final String EXTRA_TARGET_AP = "target_ap";

    private static String mLastSetTargetAp;

    public interface Receiver {
        void onUpdatedTargetAp(Context context, String targetAp);
    }

    static String getLastSetValue() {
        return mLastSetTargetAp;
    }

    static void resetLastSetValue() {
        mLastSetTargetAp = null;
    }

    public void send(Context context, String targetAp) {
        mLastSetTargetAp = targetAp;

        super.send(context,
                getIntent()
                    .putExtra(EXTRA_TARGET_AP, targetAp)
        );
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        String targetAp = intent.getStringExtra(EXTRA_TARGET_AP);
        ((Receiver) targetReceiver).onUpdatedTargetAp(context, targetAp);
    }
}
