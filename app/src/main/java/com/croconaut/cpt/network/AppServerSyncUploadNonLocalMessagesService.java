package com.croconaut.cpt.network;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.croconaut.cpt.link.handler.main.GcmSyncRequest;
import com.croconaut.cpt.link.handler.main.NetworkSyncServiceFinished;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.TreeSet;

public class AppServerSyncUploadNonLocalMessagesService extends AppServerSyncService {
    private static final String TAG = "network.gcm.up.msg";

    private class AppServerSyncUploadNonLocalMessagesRunnable extends AppServerSyncRunnable {
        private MessagesHelper messagesHelper;
        private int counter;
        private TreeSet<NetworkHeader> sentHeaders;
        private List<NetworkHeader> receivedHeaders;

        public AppServerSyncUploadNonLocalMessagesRunnable(String TAG, Context context, String crocoId, InetSocketAddress socketAddress, boolean fullSync) {
            super(TAG, context, crocoId, socketAddress, fullSync);
        }

        @Override
        public void run() {
            Log.v(TAG, getClass().getSimpleName() + ".run");

            if (!isGcmSyncNeeded(GcmSyncRequest.UPLOAD_NON_LOCAL_MESSAGES)) {
                Log.v(TAG, "GCM sync not needed, skipping");
                return;
            }

            try {
                connect();
                initializeDataStreams();
                initializeHelper();

                sendCommand(GcmSyncRequest.UPLOAD_NON_LOCAL_MESSAGES, true);

                sendCrocoIdAndUsername(true);

                sendSyncPreference(true);

                sendBlockedCrocoIds();

                sendHeaders();

                receiveHeaders();

                sendMessages();    // with processSentMessages()

                shutdown();

                // now we're sure both parties have the same content

                cancelCommunicationErrorNotification(false);

                gcmSyncDone();

                markMessagesAsSentToAppServer();
            } catch (InterruptedException e) {
                Log.w(TAG, "connect() has been interrupted", e);
                // try again immediately, it's an user-initiated interrupt
                sync(context, true, fullSync);
            } catch (IOException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                if (e.getMessage() == null || !(e instanceof IOException)) {
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
                networkSyncServiceFinished();

                close();
            }
        }

        private void initializeHelper() {
            messagesHelper = new MessagesHelper(TAG, context, dis, dos);
        }

        private void sendHeaders() throws IOException {
            Log.v(TAG, getClass().getSimpleName() + ".sendHeaders");

            sentHeaders = messagesHelper.getNonLocalHeadersAndMyPersistentHeaders();
            Log.d(TAG, counter++ + ". we have " + sentHeaders.size() + " non-local headers (incl. broadcasts from us)");

            messagesHelper.sendHeaders(sentHeaders);
            Log.d(TAG, counter++ + ". sent " + sentHeaders.size() + " non-local headers");
        }

        private void receiveHeaders() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
            Log.v(TAG, getClass().getSimpleName() + ".receiveHeaders");

            receivedHeaders = messagesHelper.receiveOpaqueHeaders();
            Log.d(TAG, counter++ + ". received " + receivedHeaders.size() + " non-local header requests");
        }

        private void sendMessages() throws IOException {
            Log.v(TAG, getClass().getSimpleName() + ".sendMessages");

            List<NetworkMessage> sentMessages = messagesHelper.getMessages(receivedHeaders);

            messagesHelper.sendMessages(sentMessages);
            Log.d(TAG, counter++ + ". sent " + sentMessages.size() + " non-local messages");

            messagesHelper.processSentMessages(sentMessages);
        }

        private void markMessagesAsSentToAppServer() {
            Log.v(TAG, getClass().getSimpleName() + ".markMessagesAsSentToAppServer");

            messagesHelper.markHeadersAsSent(sentHeaders);
        }

        private void networkSyncServiceFinished() {
            new NetworkSyncServiceFinished().send(context, null, 0, -1);
        }
    }

    public AppServerSyncUploadNonLocalMessagesService() {
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
                sync(startId, crocoId, new AppServerSyncUploadNonLocalMessagesRunnable(TAG, this, crocoId, socketAddress, fullSync));
            } else {
                // schedule a task when internet connectivity is available
                scheduleOneOffTask(this, null);
            }
        } else if (ACTION_CANCEL.equals(intent.getAction())) {
            String crocoId = intent.getStringExtra(EXTRA_CROCO_ID);

            // cancel any pending sync request
            cancelOneOffTask(this);
            // cancel current sync operation, if present
            cancel(crocoId);
        }

        return START_REDELIVER_INTENT;
    }

    public static void sync(Context context, boolean isInternetConnectivityAvailable, boolean fullSync) {
        sync(context, AppServerSyncUploadNonLocalMessagesService.class, isInternetConnectivityAvailable, fullSync);
    }

    public static void cancelSync(Context context) {
        cancelSync(context, AppServerSyncUploadNonLocalMessagesService.class);
    }
}
