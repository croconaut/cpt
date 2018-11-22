package com.croconaut.cpt.network;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.croconaut.cpt.R;
import com.croconaut.cpt.link.handler.main.GcmSyncRequest;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

import java.io.IOException;
import java.net.InetSocketAddress;

public class AppServerSyncUploadTokenService extends AppServerSyncService {
    private static final String TAG = "network.gcm.up.token";

    private class AppServerSyncUploadTokenRunnable extends AppServerSyncRunnable {
        private static final String TOKEN_SENT_TO_APP_SERVER = "token_sent";
        private static final String TOKEN = "token";

        private SharedPreferences sharedPreferences;
        private String token;

        public AppServerSyncUploadTokenRunnable(String TAG, Context context, String crocoId, InetSocketAddress socketAddress, boolean fullSync) {
            super(TAG, context, crocoId, socketAddress, fullSync);
        }

        @Override
        public void run() {
            Log.v(TAG, getClass().getSimpleName() + ".run");

            if (!isGcmSyncNeeded(GcmSyncRequest.UPLOAD_TOKEN_AND_NAME)) {
                Log.v(TAG, "GCM sync not needed, skipping");
                return;
            }

            try {
                if (checkApiAvailability() && isTokenChanged()) {
                    connect();
                    initializeDataStreams();

                    sendCommand(GcmSyncRequest.UPLOAD_TOKEN_AND_NAME, true);

                    sendCrocoIdAndUsername(true);

                    sendDeviceInfo(true);

                    sendToken();

                    shutdown();

                    // now we're sure both parties have the same content

                    cancelCommunicationErrorNotification(false);

                    gcmSyncDone();

                    markTokenAsSent();
                }
            } catch (InterruptedException e) {
                Log.w(TAG, "connect() has been interrupted", e);
                // try again immediately, it's an user-initiated interrupt
                sync(context, true, fullSync);
            } catch (IOException e) {
                if (e.getMessage() == null) {
                    Log.e(TAG, "exception", e);
                } else {
                    Log.e(TAG, e.getMessage());
                }
                if (!isConnectionErrorNotificationShown) {
                    showCommunicationErrorNotification(false);
                }

                // if there was an communication error, don't try immediately again, use the built-in backoff algorithm
                scheduleOneOffTask(context, null);
            } finally {
                close();
            }
        }

        private boolean checkApiAvailability() {
            Log.v(TAG, getClass().getSimpleName() + ".checkApiAvailability");

            /**
             * Check the device to make sure it has the Google Play Services APK. If
             * it doesn't, display a dialog that allows users to download the APK from
             * the Google Play Store or enable it in the device's system settings.
             */
            GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
            int resultCode = apiAvailability.isGooglePlayServicesAvailable(context);
            if (resultCode != ConnectionResult.SUCCESS) {
                if (apiAvailability.isUserResolvableError(resultCode)) {
                    Log.w(TAG, "Google Play Services is too old, showing a notification");
                    apiAvailability.showErrorNotification(context, resultCode);
                } else {
                    Log.e(TAG, "Google Play Services is too old, this device is not supported");
                }
            }

            return resultCode == ConnectionResult.SUCCESS;
        }

        private boolean isTokenChanged() throws IOException {
            Log.v(TAG, getClass().getSimpleName() + ".isTokenChanged");

            // Initially this call goes out to the network to retrieve the token, subsequent calls are local
            InstanceID instanceID = InstanceID.getInstance(context);
            token = instanceID.getToken(getString(R.string.gcm_defaultSenderId), GoogleCloudMessaging.INSTANCE_ID_SCOPE);

            sharedPreferences = getSharedPreferences(getClass().getSimpleName() + "_preferences", MODE_PRIVATE);

            if (!token.equals(sharedPreferences.getString(TOKEN, "")) || !sharedPreferences.getBoolean(TOKEN_SENT_TO_APP_SERVER, false)) {
                sharedPreferences.edit().putBoolean(TOKEN_SENT_TO_APP_SERVER, false).apply();
                sharedPreferences.edit().putString(TOKEN, token).apply();
                return true;
            } else {
                Log.i(TAG, "Token is up to date and had been sent to the app server");
                //return false;
                // XXX: force anyway
                return true;
            }
        }

        private void sendToken() throws IOException {
            Log.v(TAG, getClass().getSimpleName() + ".sendToken");

            dos.writeUTF(token);
            dos.flush();
        }

        private void markTokenAsSent() {
            Log.v(TAG, getClass().getSimpleName() + ".markTokenAsSent");

            sharedPreferences.edit().putBoolean(TOKEN_SENT_TO_APP_SERVER, true).apply();
        }
    }

    public AppServerSyncUploadTokenService() {
        super(TAG);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, getClass().getSimpleName() + ".onStartCommand: " + startId + " (" + intent.getAction() + ")");

        if (ACTION_SYNC.equals(intent.getAction())) {
            String crocoId = intent.getStringExtra(EXTRA_CROCO_ID);
            InetSocketAddress socketAddress = (InetSocketAddress) intent.getSerializableExtra(EXTRA_SOCKET_ADDRESS);
            boolean isInternetAvailable = intent.getBooleanExtra(EXTRA_SYNC_INTERNET, false);
            boolean fullSync = intent.getBooleanExtra(EXTRA_SYNC_FULL, false);

            if (isInternetAvailable) {
                // first, cancel a pending task since we don't want to fire off any pending task
                // (this expects SyncTaskService detects the network later than this one)
                cancelOneOffTask(this);
                sync(startId, crocoId, new AppServerSyncUploadTokenRunnable(TAG, this, crocoId, socketAddress, fullSync));
            } else {
                // schedule a task when internet connectivity is available
                scheduleOneOffTask(this, null);
            }
        }

        return START_REDELIVER_INTENT;
    }

    public static void sync(Context context, boolean isInternetConnectivityAvailable, boolean fullSync) {
        sync(context, AppServerSyncUploadTokenService.class, isInternetConnectivityAvailable, fullSync);
    }
}
