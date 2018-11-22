package com.croconaut.cpt.network;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.croconaut.cpt.data.Communication;
import com.croconaut.cpt.data.CptClientCommunication;
import com.croconaut.cpt.data.DatabaseManager;
import com.croconaut.cpt.data.LocalAttachment;
import com.croconaut.cpt.data.MessageAttachmentIdentifier;
import com.croconaut.cpt.data.MessageIdentifier;
import com.croconaut.cpt.data.StreamUtil;
import com.croconaut.cpt.link.handler.main.GcmSyncRequest;
import com.croconaut.cpt.link.handler.main.NetworkSyncServiceFinished;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class AppServerSyncUploadLocalMessagesWithAttachmentsService extends AppServerSyncService {
    private static final String TAG = "network.gcm.up.att";

    private class AppServerSyncUploadLocalMessagesWithAttachmentsRunnable extends AppServerSyncRunnable {
        private MessagesHelper messagesHelper;
        private AttachmentsHelper attachmentsHelper;
        private int counter;
        private boolean isNewRunningConnectionAdded;
        private Map<String, List<MessageAttachmentIdentifier>> uploadRequests;
        private ArrayList<UriIdentifierResponse>  uriIdentifierResponses;
        private TreeSet<NetworkHeader> sentHeaders;
        private List<NetworkHeader> receivedHeaders;

        public AppServerSyncUploadLocalMessagesWithAttachmentsRunnable(String TAG, Context context, String crocoId, InetSocketAddress socketAddress, boolean fullSync) {
            super(TAG, context, crocoId, socketAddress, fullSync);
        }

        @Override
        public void run() {
            Log.v(TAG, getClass().getSimpleName() + ".run");

            if (!isGcmSyncNeeded(GcmSyncRequest.UPLOAD_LOCAL_MESSAGES_WITH_ATTACHMENTS)) {
                Log.v(TAG, "GCM sync not needed, skipping");
                return;
            }

            try {
                connect();
                initializeDataStreams();
                initializeHelpers();

                sendCommand(GcmSyncRequest.UPLOAD_LOCAL_MESSAGES_WITH_ATTACHMENTS, true);

                sendCrocoIdAndUsername(true);

                sendSyncPreference(true);

                sendBlockedCrocoIds();

                sendMessageAttachmentIdentifiers();

                receiveRequests();

                addNewRunningConnection();

                sendAttachments();

                sendHeaders();

                receiveHeaders();

                sendMessages(); // with processSentMessages()

                shutdown();

                // now we're sure both parties have the same content

                cancelCommunicationErrorNotification(false);

                gcmSyncDone();

                markAttachmentsAsSentToAppServer();

                markMessagesAsSentToAppServer();
            } catch (InterruptedException | InterruptedIOException e) {
                if (e instanceof InterruptedException) {
                    Log.w(TAG, "connect() has been interrupted", e);
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

        private void initializeHelpers() {
            messagesHelper = new MessagesHelper(TAG, context, dis, dos);
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

        private void sendMessageAttachmentIdentifiers() throws IOException {
            Log.v(TAG, getClass().getSimpleName() + ".sendMessageAttachmentIdentifiers");

            uploadRequests = DatabaseManager.getUploadRequestsNotSentToAppServer(context, fullSync);
            // pose as some kind of "attachment previews" to the app server
            Set<MessageAttachmentIdentifier> identifiers = attachmentsHelper.getMessageAttachmentIdentifiers(uploadRequests);
            StreamUtil.writeStreamablesTo(context, dos, identifiers);
            dos.flush();
            Log.d(TAG, counter++ + ". sent " + identifiers.size() + " message attachment identifiers");
        }

        private void receiveRequests() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
            Log.v(TAG, getClass().getSimpleName() + ".receiveRequests");

            // collection of uris the app server is interested in
            //noinspection unchecked
            Set<UriIdentifier> requestedUriIdentifiers = (Set<UriIdentifier>) StreamUtil.readStreamablesFrom(context, dis);
            Log.d(TAG, "Received request for " + requestedUriIdentifiers.size() + " uri identifiers");

            Map<String, UriIdentifier> uriIdentifiers = attachmentsHelper.getUriIdentifiers(uploadRequests);
            Set<MessageIdentifier> messagesToDelete = new HashSet<>();
            uriIdentifierResponses = new ArrayList<>();
            for (UriIdentifier requestedUriIdentifier : requestedUriIdentifiers) {
                if (uploadRequests.containsKey(requestedUriIdentifier.getSourceUri())) {
                    // help out with a temporary attachment instance...
                    LocalAttachment localAttachment = new LocalAttachment(context,
                            Uri.parse(requestedUriIdentifier.getSourceUri()),
                            "",   // unused (but must not be null)
                            false   // unused
                    );
                    if (localAttachment.getLength(context) > 0) {
                        // we don't need to differentiate storage directories, the app server stores only one copy...
                        uriIdentifierResponses.add(new UriIdentifierResponse(
                                        requestedUriIdentifier.getSourceUri(),
                                        uriIdentifiers.get(requestedUriIdentifier.getSourceUri()).getStorageDirectories(),
                                        localAttachment.getName(context),
                                        localAttachment.getLength(context),
                                        localAttachment.getLastModified(context).getTime(),
                                        localAttachment.getType(context)
                                )
                        );
                    } else {
                        Log.e(TAG, "Local attachment not found: " + localAttachment);
                        for (MessageAttachmentIdentifier messageAttachmentIdentifier : uploadRequests.get(requestedUriIdentifier.getSourceUri())) {
                            messagesToDelete.add(messageAttachmentIdentifier.getMessageIdentifier());
                        }
                    }
                } else {
                    Log.e(TAG, "App server asked for an illegal uri identifier: " + requestedUriIdentifier);
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
                    // TODO: if fullSync == false, messageAttachmentIdentifiers may lack some items with the same source uri + storage directory
                    attachmentsHelper.writeUri(uriIdentifierResponse, messageAttachmentIdentifiers, false);
                } else {
                    // shouldn't happen at all, ignore the consequences for now
                    Log.e(TAG, "Something has gone wrong with uriIdentifierResponse: " + uriIdentifierResponse);
                }
            }
            nonBufferedDos.flush();

            Log.d(TAG, "Sent " + uriIdentifierResponses.size() + " unique attachments");
        }

        private void sendHeaders() throws IOException {
            Log.v(TAG, getClass().getSimpleName() + ".sendHeaders");

            sentHeaders = messagesHelper.getLocalHeaders();
            Log.d(TAG, counter++ + ". we have " + sentHeaders.size() + " local headers");

            messagesHelper.sendHeaders(sentHeaders);
            Log.d(TAG, counter++ + ". sent " + sentHeaders.size() + " local headers");
        }

        private void receiveHeaders() throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
            Log.v(TAG, getClass().getSimpleName() + ".receiveHeaders");

            receivedHeaders = messagesHelper.receiveOpaqueHeaders();
            Log.d(TAG, counter++ + ". received " + receivedHeaders.size() + " local headers");
        }

        private void sendMessages() throws IOException {
            Log.v(TAG, getClass().getSimpleName() + ".sendMessages");

            List<NetworkMessage> sentMessages = messagesHelper.getMessages(receivedHeaders);

            messagesHelper.sendMessages(sentMessages);
            Log.d(TAG, counter++ + ". sent " + sentMessages.size() + " local messages");

            messagesHelper.processSentMessages(sentMessages);
        }

        private void markAttachmentsAsSentToAppServer() {
            Log.v(TAG, getClass().getSimpleName() + ".markAttachmentsAsSentToAppServer");

            for (UriIdentifierResponse uriIdentifierResponse : uriIdentifierResponses) {
                for (MessageAttachmentIdentifier messageAttachmentIdentifier : uploadRequests.get(uriIdentifierResponse.getSourceUri())) {
                    DatabaseManager.markUploadUriAsSentToAppServer(context,
                            messageAttachmentIdentifier,
                            true
                    );
                    CptClientCommunication.messageAttachmentUploadAction(context, helper,
                            Communication.ACTION_MESSAGE_ATTACHMENT_UPLOADED_TO_APP_SERVER,
                            messageAttachmentIdentifier,
                            uriIdentifierResponse.getTimeSent(),
                            uriIdentifierResponse.getBytesPerSecondSent()
                    );
                }
            }

            Log.d(TAG, "Marked " + uriIdentifierResponses.size() + " unique attachments as sent to the app server");
        }

        private void markMessagesAsSentToAppServer() {
            Log.v(TAG, getClass().getSimpleName() + ".markMessagesAsSentToAppServer");

            messagesHelper.markHeadersAsSent(sentHeaders);
        }

        private void networkSyncServiceFinished() {
            new NetworkSyncServiceFinished().send(context, null, 0, -1);
        }
    }

    public AppServerSyncUploadLocalMessagesWithAttachmentsService() {
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
                sync(startId, crocoId, new AppServerSyncUploadLocalMessagesWithAttachmentsRunnable(TAG, this, crocoId, socketAddress, fullSync));
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
        sync(context, AppServerSyncUploadLocalMessagesWithAttachmentsService.class, isInternetConnectivityAvailable, fullSync);
    }

    public static void cancelSync(Context context) {
        cancelSync(context, AppServerSyncUploadLocalMessagesWithAttachmentsService.class);
    }
}
