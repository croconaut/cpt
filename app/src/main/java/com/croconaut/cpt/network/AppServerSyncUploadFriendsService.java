package com.croconaut.cpt.network;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.croconaut.cpt.data.DatabaseManager;
import com.croconaut.cpt.link.handler.main.GcmSyncRequest;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.util.Set;

public class AppServerSyncUploadFriendsService extends AppServerSyncService {
    private static final String TAG = "network.gcm.up.friends";

    private class AppServerSyncUploadFriendsRunnable extends AppServerSyncRunnable {
        public AppServerSyncUploadFriendsRunnable(String TAG, Context context, String crocoId, InetSocketAddress socketAddress, boolean fullSync) {
            super(TAG, context, crocoId, socketAddress, fullSync);
        }

        @Override
        public void run() {
            Log.v(TAG, getClass().getSimpleName() + ".run");

            if (!isGcmSyncNeeded(GcmSyncRequest.UPLOAD_FRIENDS)) {
                Log.v(TAG, "GCM sync not needed, skipping");
                return;
            }

            try {
                connect();
                initializeDataStreams();

                sendCommand(GcmSyncRequest.UPLOAD_FRIENDS, true);

                sendCrocoIdAndUsername(true);

                sendBlockedCrocoIds();

                sendTrustedCrocoIds();

                shutdown();

                // now we're sure both parties have the same content

                cancelCommunicationErrorNotification(false);

                gcmSyncDone();
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

        private void sendTrustedCrocoIds() throws IOException {
            Log.v(TAG, getClass().getSimpleName() + ".sendTrustedCrocoIds");

            // this is not entirely accurate, it marks people from whom we want to download everything
            // but they may be not our friends (minor issue)
            Set<String> friends = DatabaseManager.getTrustedDevices(context);

            ObjectOutputStream oos = new ObjectOutputStream(dos);
            oos.writeObject(friends);
            oos.flush();

            Log.d(TAG, "Sent " + friends.size() + " friends to the app server");
        }
    }

    public AppServerSyncUploadFriendsService() {
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
                sync(startId, crocoId, new AppServerSyncUploadFriendsRunnable(TAG, this, crocoId, socketAddress, fullSync));
            } else {
                // schedule a task when internet connectivity is available
                scheduleOneOffTask(this, null);
            }
        }

        return START_REDELIVER_INTENT;
    }

    public static void sync(Context context, boolean isInternetConnectivityAvailable, boolean fullSync) {
        sync(context, AppServerSyncUploadFriendsService.class, isInternetConnectivityAvailable, fullSync);
    }
}
