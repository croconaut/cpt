package com.croconaut.cpt.network;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.croconaut.cpt.data.Communication;
import com.croconaut.cpt.data.CptClientCommunication;
import com.croconaut.cpt.data.DatabaseManager;
import com.croconaut.cpt.data.MessageAttachmentIdentifier;
import com.croconaut.cpt.data.StreamUtil;
import com.croconaut.cpt.link.handler.main.NetworkSyncServiceFinished;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClientSyncAttachmentsService extends ClientSyncService {
    private static final String TAG = "network.client";

    private class ClientSyncAttachmentsRunnable extends ClientRunnable {
        private AttachmentsHelper attachmentsHelper;
        private boolean isNewRunningConnectionAdded;
        private Map<String, List<MessageAttachmentIdentifier>> downloadRequests;

        public ClientSyncAttachmentsRunnable(String TAG, Context context, String crocoId, InetSocketAddress socketAddress) {
            super(TAG, context, crocoId, socketAddress);
        }

        @Override
        public void run() {
            Log.v(TAG, getClass().getSimpleName() + ".run");

            try {
                connect();
                initializeDataStreams();
                initializeHelper();

                sendCommand(NetworkUtil.CMD_SYNC_ATTACHMENTS, true);

                sendCrocoId(false);

                receiveDeliveredIdentifiers();

                sendRequests();

                addNewRunningConnection();

                receiveAttachments();

                shutdown();

                // now we're sure both parties have the same content

                cancelCommunicationErrorNotification(true);
            } catch (InterruptedException e) {
                Log.w(TAG, "connect() / MediaScannerConnection has been interrupted", e);
            } catch (IOException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                Log.e(TAG, "exception", e);
                if (!isConnectionErrorNotificationShown) {
                    showCommunicationErrorNotification(true);
                }
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

        private void receiveDeliveredIdentifiers() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
            Log.v(TAG, getClass().getSimpleName() + ".receiveDeliveredIdentifiers");

            @SuppressWarnings("unchecked") Set<MessageAttachmentIdentifier> deliveredAttachmentIdentifiers = (Set<MessageAttachmentIdentifier>) StreamUtil.readStreamablesFrom(context, dis);
            Log.d(TAG, "Received " + deliveredAttachmentIdentifiers.size() + " delivered attachment identifiers");

            Set<MessageAttachmentIdentifier> sentButNotDeliveredIdentifiers = attachmentsHelper.getMessageAttachmentIdentifiers(
                    DatabaseManager.getUploadRequestsSentToRecipient(context, crocoId)
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
                } else {
                    // not received => not sent, we'll let the other side to ask for it
                    // TODO: there's no good solution to this right now:
                    //       - if we set it to 'false', we lose the ability to detect it's actually a resend
                    //       - if we let it at 'true', we can possibly set target ap to uri's recipient
                    //         on some other croco id's expense even if it's not needed
                    // Right now we set it to false and notify the client app again.
                    DatabaseManager.markUploadUriAsSentToRecipient(context, messageAttachmentIdentifier, false);
                    Log.w(TAG, "Marked as not sent: " + messageAttachmentIdentifier);
                }
            }
        }

        private void sendRequests() throws IOException {
            Log.v(TAG, getClass().getSimpleName() + ".sendRequests");

            // Single message can contain multiple:
            // - private attachments (identified by their source uri [storage directory == null])
            // - public attachments (identified by their source uri *and* storage directory)
            // Basically, a message has a set of attachments indexed by (sourceUri, directory).
            // Naturally, the client app can contain multiple messages with the same uri.
            //
            // Each attachment can be chosen to download/upload separately, however for multiple references
            // following rules apply:
            // - if a private source uri is transmitted, all requests will update (as only one file is required)
            // - if a public source uri is transmitted, all requests for given storage directory only will update
            //
            // There can be one or more public uris with private *source* uri but not a private uri with a
            // public source uri.
            //
            // TODO: if multiple apps wish to send the same public (!) uri, we transmit&store it separately :-(
            //       (this is only hypothetical as we don't offer support for multiple apps yet but it will
            //       require some handling on a common network layer, perhaps public attachments (uris) can be
            //       offered by each app with granted permission for the time of transmission?)
            downloadRequests = DatabaseManager.getDownloadRequests(context, crocoId, true);
            Log.v(TAG, "We have " + downloadRequests.keySet().size() + " uris to request");

            Map<String, UriIdentifier> uriIdentifiers = attachmentsHelper.getUriIdentifiers(downloadRequests);
            StreamUtil.writeStreamablesTo(context, dos, new HashSet<>(uriIdentifiers.values()));
            dos.flush();
            Log.d(TAG, "Sent request for " + uriIdentifiers.size() + " uri identifiers");
        }

        private void receiveAttachments() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException, InterruptedException {
            Log.v(TAG, getClass().getSimpleName() + ".receiveAttachments");

            int uriResponsesCount = dis.readInt();
            Set<String> receivedSourceUris = new HashSet<>(uriResponsesCount);
            for (int i = 0; i < uriResponsesCount; ++i) {
                UriIdentifierResponse uriIdentifierResponse = (UriIdentifierResponse) StreamUtil.readStreamableFrom(context, dis);

                List<MessageAttachmentIdentifier> messageAttachmentIdentifiers = downloadRequests.get(uriIdentifierResponse.getSourceUri());
                if (messageAttachmentIdentifiers != null) {
                    receivedSourceUris.add(uriIdentifierResponse.getSourceUri());
                    attachmentsHelper.readUri(uriIdentifierResponse, messageAttachmentIdentifiers, true);
                } else {
                    Log.e(TAG, "Server returned an illegal uri response: " + uriIdentifierResponse);
                }
            }

            Log.d(TAG, "Received " + receivedSourceUris.size() + " unique attachments");

            // if the server refused to send an uri, cancel all associated download requests
            for (String requestedSourceUri : downloadRequests.keySet()) {
                if (!receivedSourceUris.contains(requestedSourceUri)) {
                    for (MessageAttachmentIdentifier messageAttachmentIdentifier : downloadRequests.get(requestedSourceUri)) {
                        Log.w(TAG, "Attachment " + messageAttachmentIdentifier + " has been deleted/rejected on the other side");

                        DatabaseManager.removeDownloadUri(context, messageAttachmentIdentifier);
                        CptClientCommunication.messageAttachmentDownloadAction(context, helper,
                                Communication.ACTION_MESSAGE_ATTACHMENT_DOWNLOAD_CANCELLED,
                                messageAttachmentIdentifier
                        );
                    }
                }
            }
        }

        private void networkSyncServiceFinished() {
            new NetworkSyncServiceFinished().send(context, crocoId, NetworkSyncServiceFinished.CLIENT_ATTACHMENTS, -1);
        }
    }

    public ClientSyncAttachmentsService() {
        super(TAG);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, getClass().getSimpleName() + ".onStartCommand: " + startId + " (" + intent.getAction() + ")");

        if (ACTION_SYNC.equals(intent.getAction())) {
            String crocoId = intent.getStringExtra(EXTRA_CROCO_ID);
            InetSocketAddress socketAddress = (InetSocketAddress) intent.getSerializableExtra(EXTRA_SOCKET_ADDRESS);

            sync(startId, crocoId, new ClientSyncAttachmentsRunnable(TAG, this, crocoId, socketAddress));
        } else if (ACTION_CANCEL.equals(intent.getAction())) {
            String crocoId = intent.getStringExtra(EXTRA_CROCO_ID);

            cancel(crocoId);
        }

        return START_REDELIVER_INTENT;
    }

    public static void sync(Context context, String crocoId, InetSocketAddress socketAddress) {
        sync(context, ClientSyncAttachmentsService.class, crocoId, socketAddress);
    }

    public static void cancelSync(Context context, String crocoId) {
        cancelSync(context, ClientSyncAttachmentsService.class, crocoId);
    }
}
