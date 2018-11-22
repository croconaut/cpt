package com.croconaut.cpt.gcm;

import android.os.Bundle;
import android.util.Log;

import com.croconaut.cpt.link.PreferenceHelper;
import com.croconaut.cpt.link.handler.main.GcmSyncRequest;
import com.google.android.gms.gcm.GcmListenerService;

public class ListenerService extends GcmListenerService {
    private static final String TAG = "gcm";

    public ListenerService() {
        super();

        Log.v(TAG, getClass().getSimpleName() + ".ListenerService");
    }

    @Override
    public void onMessageReceived(String from, Bundle data) {
        Log.v(TAG, getClass().getSimpleName() + ".onMessageReceived");

        String message = data.getString("message");
        Log.d(TAG, "From: " + from);
        Log.d(TAG, "Message: " + message);

        PreferenceHelper helper = new PreferenceHelper(this);
        if (helper.getAppId() != null && helper.getClassId() != null) {
            new GcmSyncRequest().send(this, GcmSyncRequest.DOWNLOAD_MESSAGES_AND_ATTACHMENTS_DELIVERY);
        } else {
            // the app server will initiate the same request again when registering
            Log.w(TAG, "GCM sync request before registration, ignoring");
        }
    }
}
