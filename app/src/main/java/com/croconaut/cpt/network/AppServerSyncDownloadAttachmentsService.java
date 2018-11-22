package com.croconaut.cpt.network;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.croconaut.cpt.data.DatabaseManager;
import com.croconaut.cpt.data.MessageAttachmentIdentifier;
import com.croconaut.cpt.data.StreamUtil;
import com.croconaut.cpt.link.handler.main.GcmSyncRequest;
import com.croconaut.cpt.link.handler.main.MainHandler;
import com.croconaut.cpt.link.handler.main.NetworkSyncServiceFinished;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppServerSyncDownloadAttachmentsService extends AppServerSyncService {
    private static final String TAG = "network.gcm.down.att";

    private class AppServerSyncDownloadAttachmentsRunnable extends AppServerSyncRunnable {
        private AttachmentsHelper attachmentsHelper;
        private boolean isNewRunningConnectionAdded;
        private Map<String, List<MessageAttachmentIdentifier>> downloadRequests;

        public AppServerSyncDownloadAttachmentsRunnable(String TAG, Context context, String crocoId, InetSocketAddress socketAddress, boolean fullSync) {
            super(TAG, context, crocoId, socketAddress, fullSync);
        }

        @Override
        public void run() {
            Log.v(TAG, getClass().getSimpleName() + ".run");

            if (!isGcmSyncNeeded(GcmSyncRequest.DOWNLOAD_ATTACHMENTS)) {
                Log.v(TAG, "GCM sync not needed, skipping");
                return;
            }

            try {
                connect();
                initializeDataStreams();
                initializeHelper();

                sendCommand(GcmSyncRequest.DOWNLOAD_ATTACHMENTS, true);

                sendCrocoIdAndUsername(true);

                sendSyncPreference(true);

                sendBlockedCrocoIds();

                sendRequests();

                addNewRunningConnection();

                receiveAttachments();

                shutdown();

                // now we're sure both parties have the same content

                cancelCommunicationErrorNotification(false);

                gcmSyncDone();

                markAttachmentsAsRequestedFromAppServer();
            } catch (InterruptedException | InterruptedIOException e) {
                if (e instanceof InterruptedException) {
                    Log.w(TAG, "connect() / MediaScannerConnection has been interrupted", e);
                    // try again immediately, it's an user-initiated interrupt
                } else {
                    Log.w(TAG, "Thread interrupted");
                    // if the thread was interrupted, try again immediately
                }
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
                removeRunningConnection();

                networkSyncServiceFinished();

                close();
            }
        }

        private void initializeHelper() {
            attachmentsHelper = new AttachmentsHelper(TAG, context, dis, dos);
        }

        private void addNewRunningConnection() {
            NetworkUtil.addNewRunningConnection(context);
            isNewRunningConnectionAdded = true;
        }

        private void removeRunningConnection() {
            if (isNewRunningConnectionAdded) {
                NetworkUtil.removeRunningConnection(context);
            }
        }

        private void sendRequests() throws IOException {
            Log.v(TAG, getClass().getSimpleName() + ".sendRequests");

            Set<String> crocoIdsToIgnore = new HashSet<>(MainHandler.getLatestNearbyCrocoIds());
            crocoIdsToIgnore.addAll(blockedCrocoIds);

            downloadRequests = DatabaseManager.getDownloadRequests(context, crocoId, fullSync, crocoIdsToIgnore);
            Log.v(TAG, "We have " + downloadRequests.keySet().size() + " uris to request");

            // we must ask for MessageAttachmentIdentifiers because this is the only way how to get the correct sourceUri for given attachment/client/app
            Set<MessageAttachmentIdentifier> identifiers = attachmentsHelper.getMessageAttachmentIdentifiers(downloadRequests);
            StreamUtil.writeStreamablesTo(context, dos, identifiers);
            dos.flush();
            Log.d(TAG, "Sent request for " + identifiers.size() + " message attachment identifiers");
        }

        private void receiveAttachments() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException, InterruptedException {
            Log.v(TAG, getClass().getSimpleName() + ".receiveAttachments");

            int uriResponsesCount = dis.readInt();
            Set<String> receivedSourceUris = new HashSet<>(uriResponsesCount);
            for (int i = 0; i < uriResponsesCount; ++i) {
                UriIdentifierResponse uriIdentifierResponse = (UriIdentifierResponse) StreamUtil.readStreamableFrom(context, dis);

                List<MessageAttachmentIdentifier> messageAttachmentIdentifiers = downloadRequests.get(uriIdentifierResponse.getSourceUri());
                if (messageAttachmentIdentifiers != null) {
                    // TODO: if fullSync == false, messageAttachmentIdentifiers may lack some items with the same source uri + storage directory
                    receivedSourceUris.add(uriIdentifierResponse.getSourceUri());
                    attachmentsHelper.readUri(uriIdentifierResponse, messageAttachmentIdentifiers, false);
                } else {
                    Log.e(TAG, "Server returned an illegal uri response: " + uriIdentifierResponse);
                }
            }

            Log.d(TAG, "Received " + receivedSourceUris.size() + " unique attachments");
        }

        private void markAttachmentsAsRequestedFromAppServer() {
            Log.v(TAG, getClass().getSimpleName() + ".markAttachmentsAsRequestedFromAppServer");

            for (List<MessageAttachmentIdentifier> messageAttachmentIdentifiers : downloadRequests.values()) {
                for (MessageAttachmentIdentifier messageAttachmentIdentifier : messageAttachmentIdentifiers) {
                    DatabaseManager.markDownloadUriAsRequestedFromAppServer(context, messageAttachmentIdentifier);
                }
            }

            Log.d(TAG, "Marked " + downloadRequests.keySet().size() + " source uris as requested from the app server");
        }

        private void networkSyncServiceFinished() {
            new NetworkSyncServiceFinished().send(context, null, 0, -1);
        }
    }

    public AppServerSyncDownloadAttachmentsService() {
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
                sync(startId, crocoId, new AppServerSyncDownloadAttachmentsRunnable(TAG, this, crocoId, socketAddress, fullSync));
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
        sync(context, AppServerSyncDownloadAttachmentsService.class, isInternetConnectivityAvailable, fullSync);
    }

    public static void cancelSync(Context context) {
        cancelSync(context, AppServerSyncDownloadAttachmentsService.class);
    }
}
