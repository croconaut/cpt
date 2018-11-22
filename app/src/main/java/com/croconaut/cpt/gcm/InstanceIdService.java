package com.croconaut.cpt.gcm;

import android.util.Log;

import com.croconaut.cpt.link.handler.main.GcmSyncRequest;
import com.google.android.gms.iid.InstanceIDListenerService;

public class InstanceIdService extends InstanceIDListenerService {
    private static final String TAG = "gcm";

    @Override
    public void onTokenRefresh() {
        Log.v(TAG, getClass().getSimpleName() + ".onTokenRefresh");

        new GcmSyncRequest().send(this, GcmSyncRequest.UPLOAD_TOKEN_AND_NAME);
    }
}
