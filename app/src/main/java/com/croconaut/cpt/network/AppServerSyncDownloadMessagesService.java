package com.croconaut.cpt.network;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.croconaut.cpt.data.Communication;
import com.croconaut.cpt.data.CptClientCommunication;
import com.croconaut.cpt.data.DatabaseManager;
import com.croconaut.cpt.data.MessageAttachmentIdentifier;
import com.croconaut.cpt.data.StreamUtil;
import com.croconaut.cpt.link.handler.main.GcmSyncRequest;
import com.croconaut.cpt.link.handler.main.NetworkSyncServiceFinished;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class AppServerSyncDownloadMessagesService extends AppServerSyncService {
    private static final String TAG = "network.gcm.down.msg";

    private class AppServerSyncDownloadMessagesRunnable extends AppServerSyncRunnable {
        private MessagesHelper messagesHelper;
        private AttachmentsHelper attachmentsHelper;
        private int counter;
        private boolean hasReceivedNonLocalMessages;
        private List<NetworkMessage> acks;

        public AppServerSyncDownloadMessagesRunnable(String TAG, Context context, String crocoId, InetSocketAddress socketAddress, boolean fullSync) {
            super(TAG, context, crocoId, socketAddress, fullSync);
        }

        @Override
        public void run() {
            Log.v(TAG, getClass().getSimpleName() + ".run");

            if (!isGcmSyncNeeded(GcmSyncRequest.DOWNLOAD_MESSAGES_AND_ATTACHMENTS_DELIVERY)) {
                Log.v(TAG, "GCM sync not needed, skipping");
                return;
            }

            try {
                connect();
                initializeDataStreams();
                initializeHelpers();

                sendCommand(GcmSyncRequest.DOWNLOAD_MESSAGES_AND_ATTACHMENTS_DELIVERY, true);

                sendCrocoIdAndUsername(true);

                sendSyncPreference(true);

                sendBlockedCrocoIds();

                sendHeaders();

                receiveMessages();   // with processReceivedMessages()

                sendAcks();

                receiveDeliveredIdentifiers();

                shutdown();

                // now we're sure both parties have the same content

                cancelCommunicationErrorNotification(false);

                gcmSyncDone();

                markAcksAsSentToAppServer();

                checkHash();
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

                if (messagesHelper != null) {
                    // if something went wrong, better update hash
                    messagesHelper.recalculateHash();
                }
                // if there was an communication error, don't try immediately again, use the built-in backoff algorithm
                scheduleOneOffTask(context, null);
            } finally {
                networkSyncServiceFinished();

                close();
            }
        }

        private void initializeHelpers() {
            messagesHelper = new MessagesHelper(TAG, context, dis, dos);
            attachmentsHelper = new AttachmentsHelper(TAG, context, dis, dos);
        }

        private void sendHeaders() throws IOException {
            Log.v(TAG, getClass().getSimpleName() + ".sendHeaders");

            TreeSet<NetworkHeader> headers = messagesHelper.getHeaders();
            Log.d(TAG, counter++ + ". we have " + headers.size() + " headers");

            messagesHelper.sendHeaders(headers);
            Log.d(TAG, counter++ + ". sent " + headers.size() + " headers");
        }

        private void receiveMessages() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
            Log.v(TAG, getClass().getSimpleName() + ".receiveMessages");

            List<NetworkMessage> receivedMessages = messagesHelper.receiveMessages();
            Log.d(TAG, counter++ + ". received " + receivedMessages.size() + " messages");

            for (NetworkMessage networkMessage : receivedMessages) {
                if (!networkMessage.isLocal()) {
                    hasReceivedNonLocalMessages = true;
                    break;
                }
            }

            acks = messagesHelper.processReceivedMessages(receivedMessages);
            Log.d(TAG, counter++ + ". turned into " + acks.size() + " ACK messages");
        }

        private void sendAcks() throws IOException {
            Log.v(TAG, getClass().getSimpleName() + ".sendAcks");

            messagesHelper.sendMessages(acks);
            Log.d(TAG, counter++ + ". sent " + acks.size() + " ACKs");
        }

        private void receiveDeliveredIdentifiers() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
            Log.v(TAG, getClass().getSimpleName() + ".receiveDeliveredIdentifiers");

            @SuppressWarnings("unchecked") List<MessageAttachmentIdentifier> deliveredAttachmentIdentifiers = (List<MessageAttachmentIdentifier>) StreamUtil.readStreamablesFrom(context, dis);
            Log.d(TAG, "Received " + deliveredAttachmentIdentifiers.size() + " delivered attachment identifiers");

            Set<MessageAttachmentIdentifier> sentButNotDeliveredIdentifiers = attachmentsHelper.getMessageAttachmentIdentifiers(
                    DatabaseManager.getUploadRequestsSentToAppServer(context)
            );
            for (MessageAttachmentIdentifier messageAttachmentIdentifier : sentButNotDeliveredIdentifiers) {
                // not very common scenario, it's ok to do it one by one
                if (deliveredAttachmentIdentifiers.contains(messageAttachmentIdentifier)) {
                    Log.i(TAG, "Marked as delivered: " + messageAttachmentIdentifier);
                    DatabaseManager.markUploadUriAsDelivered(context,
                            messageAttachmentIdentifier,
                            true
                    );
                    CptClientCommunication.messageAttachmentUploadAction(context, helper,
                            Communication.ACTION_MESSAGE_ATTACHMENT_DELIVERED,
                            messageAttachmentIdentifier,
                            new Date(), // TODO: we should receive this time from the other side but TABLE_NAME_CPT_ATTACHMENTS_TO_DOWNLOAD contains only COLUMN_NAME_DOWNLOAD_FLAG_RECEIVED
                            -1
                    );
                }
            }
        }

        private void markAcksAsSentToAppServer() {
            Log.v(TAG, getClass().getSimpleName() + ".markAcksAsSentToAppServer");

            messagesHelper.markMessagesAsSent(acks);
        }

        private void checkHash() {
            Log.v(TAG, getClass().getSimpleName() + ".checkHash");

            if (hasReceivedNonLocalMessages) {
                messagesHelper.recalculateHash();
            }
        }

        private void networkSyncServiceFinished() {
            new NetworkSyncServiceFinished().send(context, null, 0, -1);
        }
    }

    public AppServerSyncDownloadMessagesService() {
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
                sync(startId, crocoId, new AppServerSyncDownloadMessagesRunnable(TAG, this, crocoId, socketAddress, fullSync));
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
        sync(context, AppServerSyncDownloadMessagesService.class, isInternetConnectivityAvailable, fullSync);
    }

    public static void cancelSync(Context context) {
        cancelSync(context, AppServerSyncDownloadMessagesService.class);
    }
}
