package com.croconaut.cpt.network;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.croconaut.cpt.data.Communication;
import com.croconaut.cpt.data.CptClientCommunication;
import com.croconaut.cpt.data.DatabaseManager;
import com.croconaut.cpt.data.LocalAttachment;
import com.croconaut.cpt.data.MessageAttachmentIdentifier;
import com.croconaut.cpt.data.MessageIdentifier;
import com.croconaut.cpt.data.StreamUtil;
import com.croconaut.cpt.link.handler.main.NetworkSyncServiceFinished;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ServerSyncAttachmentsRunnable extends ServerRunnable {
    private AttachmentsHelper attachmentsHelper;
    private boolean isNewRunningConnectionAdded;
    private Map<String, List<MessageAttachmentIdentifier>> uploadRequests;
    private ArrayList<UriIdentifierResponse> uriIdentifierResponses;

    public ServerSyncAttachmentsRunnable(Context context, Socket socket, Set<String> pendingInterruptCrocoIds) {
        super(context, socket, pendingInterruptCrocoIds);
    }

    @Override
    public void run() {
        Log.v(TAG, getClass().getSimpleName() + ".run");

        try {
            initializeDataStreams();
            initializeHelper();

            receiveCrocoId();

            sendDeliveredIdentifiers();

            receiveRequests();

            addNewRunningConnection();

            sendAttachments();

            shutdown();

            // now we're sure both parties have the same content

            cancelCommunicationErrorNotification(true);

            markAttachmentsAsDelivered();
        } catch (IOException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            Log.e(TAG, "exception", e);
            showCommunicationErrorNotification(true);
        } finally {
            removeRunningConnection();

            networkSyncServiceFinished();

            close();
        }
    }

    @Override
    protected void interruptIfEqualsTo(String crocoId) {
        if (crocoId != null) {
            // interrupt only non-message cancel requests
            super.interruptIfEqualsTo(crocoId);
        }
    }

    private void initializeHelper() {
        attachmentsHelper = new AttachmentsHelper(TAG, context, dis, nonBufferedDos);
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

    private void sendDeliveredIdentifiers() throws IOException {
        Log.v(TAG, getClass().getSimpleName() + ".sendDeliveredIdentifiers");

        /*
         * We do this charade because of the UI -- if the attachments are not sent, fine, we end up in an exception.
         * However, if they did get through and the failure occurred during shutdown(), we wouldn't mark them as delivered
         * at all -- because another request would never come. The fact we're setting 'sent' in AttachmentsHelper is purely
         * cosmetic, there's no real gain from that.
         */
        Set<MessageAttachmentIdentifier> identifiers = attachmentsHelper.getMessageAttachmentIdentifiers(
                DatabaseManager.getReceivedDownloadRequests(context, crocoId)
        );
        StreamUtil.writeStreamablesTo(context, dos, identifiers);
        dos.flush();
        Log.d(TAG, "Sent " + identifiers.size() + " message attachment identifiers");
    }

    private void receiveRequests() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        Log.v(TAG, getClass().getSimpleName() + ".receiveRequests");

        // collection of uri+storage dirs the client is interested in
        //noinspection unchecked
        Set<UriIdentifier> uriIdentifiers = (Set<UriIdentifier>) StreamUtil.readStreamablesFrom(context, dis);
        Log.d(TAG, "Received request for " + uriIdentifiers.size() + " uri identifiers");

        // all available uris for given client
        Map<String, List<MessageAttachmentIdentifier>> allUploadRequests = DatabaseManager.getUploadRequests(context, crocoId, true);
        Log.v(TAG, "We have " + allUploadRequests.keySet().size() + " uris to offer");
        uploadRequests = new HashMap<>();

        Set<MessageIdentifier> messagesToDelete = new HashSet<>();
        uriIdentifierResponses = new ArrayList<>();
        for (UriIdentifier uriIdentifier : uriIdentifiers) {
            if (allUploadRequests.containsKey(uriIdentifier.getSourceUri())) {
                // ok, now we know the uri can be sent over to the client, let's check whether the uri is accessible

                // help out with a temporary attachment instance...
                LocalAttachment localAttachment = new LocalAttachment(context,
                        Uri.parse(uriIdentifier.getSourceUri()),
                        "",   // unused (but must not be null)
                        false   // unused
                );
                if (localAttachment.getLength(context) > 0) {
                    uriIdentifierResponses.add(new UriIdentifierResponse(
                                    uriIdentifier.getSourceUri(),
                                    uriIdentifier.getStorageDirectories(),
                                    localAttachment.getName(context),
                                    localAttachment.getLength(context),
                                    localAttachment.getLastModified(context).getTime(),
                                    localAttachment.getType(context)
                            )
                    );

                    // let's check which target storage dirs can be flagged as 'sent' because the client
                    // could ask only for a subset of all possible upload requests for given source uri
                    for (MessageAttachmentIdentifier messageAttachmentIdentifier : allUploadRequests.get(uriIdentifier.getSourceUri())) {
                        if (uriIdentifier.getStorageDirectories().contains(messageAttachmentIdentifier.getStorageDirectory())) {
                            List<MessageAttachmentIdentifier> messageAttachmentIdentifiers = uploadRequests.get(uriIdentifier.getSourceUri());
                            if (messageAttachmentIdentifiers == null) {
                                messageAttachmentIdentifiers = new ArrayList<>();
                                uploadRequests.put(uriIdentifier.getSourceUri(), messageAttachmentIdentifiers);
                            }
                            messageAttachmentIdentifiers.add(messageAttachmentIdentifier);
                        }
                    }
                } else {
                    Log.e(TAG, "Local attachment not found: " + localAttachment);
                    for (MessageAttachmentIdentifier messageAttachmentIdentifier : allUploadRequests.get(uriIdentifier.getSourceUri())) {
                        // search in *all* upload requests
                        messagesToDelete.add(messageAttachmentIdentifier.getMessageIdentifier());
                    }
                }
            } else {
                Log.e(TAG, "Client asked for an illegal uri identifier: " + uriIdentifier);
            }
        }

        // super-slow but not often used code
        DatabaseManager.deleteMessages(context, messagesToDelete);
    }

    private void sendAttachments() throws IOException {
        Log.v(TAG, getClass().getSimpleName() + ".sendAttachments");

        nonBufferedDos.writeInt(uriIdentifierResponses.size());
        for (UriIdentifierResponse uriIdentifierResponse : uriIdentifierResponses) {
            StreamUtil.writeStreamableTo(context, nonBufferedDos, uriIdentifierResponse);

            List<MessageAttachmentIdentifier> messageAttachmentIdentifiers = uploadRequests.get(uriIdentifierResponse.getSourceUri());
            if (messageAttachmentIdentifiers != null) {
                attachmentsHelper.writeUri(uriIdentifierResponse, messageAttachmentIdentifiers, true);
            } else {
                // shouldn't happen at all, ignore the consequences for now
                Log.e(TAG, "Something has gone wrong with uriIdentifierResponse: " + uriIdentifierResponse);
            }
        }
        nonBufferedDos.flush();

        Log.d(TAG, "Sent " + uriIdentifierResponses.size() + " unique attachments");
    }

    private void markAttachmentsAsDelivered() {
        Log.v(TAG, getClass().getSimpleName() + ".markAttachmentsAsDelivered");

        for (UriIdentifierResponse uriIdentifierResponse : uriIdentifierResponses) {
            if (!uploadRequests.containsKey(uriIdentifierResponse.getSourceUri())) {
                continue;
            }
            for (MessageAttachmentIdentifier messageAttachmentIdentifier : uploadRequests.get(uriIdentifierResponse.getSourceUri())) {
                DatabaseManager.markUploadUriAsDelivered(context,
                        messageAttachmentIdentifier,
                        true
                );
                CptClientCommunication.messageAttachmentUploadAction(context, helper,
                        Communication.ACTION_MESSAGE_ATTACHMENT_DELIVERED,
                        messageAttachmentIdentifier,
                        uriIdentifierResponse.getTimeSent(),
                        -1
                );
            }
        }

        Log.d(TAG, "Marked " + uriIdentifierResponses.size() + " unique attachments as delivered");
    }

    private void networkSyncServiceFinished() {
        new NetworkSyncServiceFinished().send(context, null, 0, -1);
    }
}
