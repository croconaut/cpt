package com.croconaut.cpt.link.handler.main;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntent;

public class NetworkSyncServiceFinished extends LocalIntent {
    public static final int    CLIENT_MESSAGES = 0x01;
    public static final int CLIENT_ATTACHMENTS = 0x02;
    public static final int             SERVER = 0x04;
    public static final int  EVERYTHING = CLIENT_MESSAGES | CLIENT_ATTACHMENTS | SERVER;

    private static final String EXTRA_CROCO_ID = "croco_id";
    private static final String EXTRA_WHAT = "what";
    private static final String EXTRA_TIMESTAMP = "timestamp";

    public interface Receiver {
        void onNetworkSyncServiceFinished(Context context, String crocoId, int what, long timeStampSuccess);
    }

    public void send(Context context, String crocoId, int what, long timeStampSuccess) {
        super.send(context,
                getIntent()
                        .putExtra(EXTRA_CROCO_ID, crocoId)
                        .putExtra(EXTRA_WHAT, what)
                        .putExtra(EXTRA_TIMESTAMP, timeStampSuccess)
        );
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        String crocoId = intent.getStringExtra(EXTRA_CROCO_ID);
        int what = intent.getIntExtra(EXTRA_WHAT, -1);
        long timeStampSuccess = intent.getLongExtra(EXTRA_TIMESTAMP, -1);
        ((Receiver) targetReceiver).onNetworkSyncServiceFinished(context, crocoId, what, timeStampSuccess);
    }
}
