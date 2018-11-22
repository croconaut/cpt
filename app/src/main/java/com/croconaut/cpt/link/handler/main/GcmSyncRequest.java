package com.croconaut.cpt.link.handler.main;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LinkLayerServiceIntent;

public class GcmSyncRequest extends LinkLayerServiceIntent {
    // initiated by the app server
    public static final int DOWNLOAD_MESSAGES_AND_ATTACHMENTS_DELIVERY = 0x01;  // stored in the gcm transactions table
    // initiated by the device
    public static final int                      UPLOAD_TOKEN_AND_NAME = 0x02;  // stored in the gcm transactions table
    public static final int                  UPLOAD_NON_LOCAL_MESSAGES = 0x04;
    public static final int     UPLOAD_LOCAL_MESSAGES_WITH_ATTACHMENTS = 0x08;
    public static final int                       DOWNLOAD_ATTACHMENTS = 0x10;
    public static final int                             UPLOAD_FRIENDS = 0x20;  // stored in the gcm transactions table
    public static final int                        PERSISTENT_REQUESTS = DOWNLOAD_MESSAGES_AND_ATTACHMENTS_DELIVERY
            | UPLOAD_TOKEN_AND_NAME | UPLOAD_FRIENDS;
    public static final int             DOWNLOAD_AND_UPLOAD_EVERYTHING = DOWNLOAD_MESSAGES_AND_ATTACHMENTS_DELIVERY
            | UPLOAD_NON_LOCAL_MESSAGES | UPLOAD_LOCAL_MESSAGES_WITH_ATTACHMENTS | DOWNLOAD_ATTACHMENTS | UPLOAD_FRIENDS;

    private static final String EXTRA_WHAT = "what";

    public interface Receiver {
        void onGcmSyncRequest(Context context, int startId, int what);
    }

    public void send(Context context, int what) {
        send(context,
                getIntent(context)
                    .putExtra(EXTRA_WHAT, what)
        );
    }

    public Intent getPlainIntent(int what) {
        return new Intent(getAction())
                .putExtra(EXTRA_WHAT, what)
        ;
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        int startId = intent.getIntExtra(EXTRA_START_ID, -1);
        int what = intent.getIntExtra(EXTRA_WHAT, 0);
        ((Receiver) targetReceiver).onGcmSyncRequest(context, startId, what);
    }
}
