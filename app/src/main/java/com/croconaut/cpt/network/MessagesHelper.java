package com.croconaut.cpt.network;

import android.app.NotificationManager;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.util.Log;

import com.croconaut.cpt.R;
import com.croconaut.cpt.common.NotificationId;
import com.croconaut.cpt.data.AttachmentIdentifier;
import com.croconaut.cpt.data.Communication;
import com.croconaut.cpt.data.CptClientCommunication;
import com.croconaut.cpt.data.DatabaseManager;
import com.croconaut.cpt.data.MessageAttachmentIdentifier;
import com.croconaut.cpt.data.SQLiteCptHelper;
import com.croconaut.cpt.data.StreamUtil;
import com.croconaut.cpt.link.handler.main.NewAttachment;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import static junit.framework.Assert.assertTrue;

class MessagesHelper extends AbstractHelper {
    private static final AtomicInteger receivingIdCounter = new AtomicInteger();
    private static final AtomicInteger   sendingIdCounter = new AtomicInteger();

    public MessagesHelper(String TAG, Context context, DataInputStream dis, DataOutputStream dos) {
        super(TAG, context, dis, dos);
    }

    TreeSet<NetworkHeader> getHeaders() {
        TreeSet<NetworkHeader> headers = getNonLocalHeaders();
        headers.addAll(getLocalHeaders());
        return headers;
    }

    TreeSet<NetworkHeader> getLocalHeaders() {
        return DatabaseManager.getLocalHeaders(context, crocoId);
    }

    TreeSet<NetworkHeader> getNonLocalHeaders() {
        return DatabaseManager.getHeaders(context, null, null);
    }

    TreeSet<NetworkHeader> getNonLocalHeadersAndMyPersistentHeaders() {
        return DatabaseManager.getHeaders(context, null, /*helper.getCrocoId()*/null);
    }

    List<NetworkMessage> getMessages(List<NetworkHeader> headers) {
        return DatabaseManager.getMessages(context, headers);
    }

    void sendHeaders(TreeSet<NetworkHeader> headers) throws IOException {
        StreamUtil.writeStreamablesTo(context, dos, headers);
        dos.flush();
    }

    void sendOpaqueHeaders(List<NetworkHeader> headers) throws IOException {
        StreamUtil.writeStreamablesTo(context, dos, headers);
        dos.flush();
    }

    TreeSet<NetworkHeader> receiveHeaders() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
        //noinspection unchecked
        return (TreeSet<NetworkHeader>) StreamUtil.readStreamablesFrom(context, dis);
    }

    List<NetworkHeader> receiveOpaqueHeaders() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
        //noinspection unchecked
        return (List<NetworkHeader>) StreamUtil.readStreamablesFrom(context, dis);
    }

    void sendMessages(List<NetworkMessage> messages) throws IOException {
        StreamUtil.writeStreamablesTo(context, dos, messages);
        dos.flush();
    }

    List<NetworkMessage> receiveMessages() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
        //noinspection unchecked
        return (List<NetworkMessage>) StreamUtil.readStreamablesFrom(context, dis);
    }

    void recalculateHash() {
        DatabaseManager.setHashDirty(context);
    }

    void processSentMessages(List<NetworkMessage> messages) {
        Date now = Calendar.getInstance().getTime();

        int notificationId = getAndIncrement(NotificationId.SENT_MSG_BASE, sendingIdCounter);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setContentTitle("processSentMessages()")
                .setSmallIcon(R.drawable.ic_wifon)
                .setWhen(now.getTime())
                .setShowWhen(true)
        ;

        int sentNormal = 0;
        int sentAck = 0;
        int toRecipient = 0;
        int toInternet = 0;
        int toOther = 0;

        for (NetworkMessage networkMessage : messages) {
            if (networkMessage.header.getType() == NetworkHeader.Type.NORMAL) {
                sentNormal++;
            } else {
                sentAck++;
            }
            int recipientClass = -1;
            // 'isExpectingSent' is set to false as soon as the message is stored so only the source device gets notified
            if (networkMessage.isExpectingSent()) {
                if (!networkMessage.isSentToRecipient() && networkMessage.header.getTo().equals(crocoId)) {
                    toRecipient++;
                    recipientClass = Communication.MESSAGE_SENT_TO_RECIPIENT;
                } else if (!networkMessage.isSentToAppServer() && crocoId == null) {
                    toInternet++;
                    recipientClass = Communication.MESSAGE_SENT_TO_INTERNET;
                } else if (!networkMessage.isSentToOtherDevice()) {
                    toOther++;
                    recipientClass = Communication.MESSAGE_SENT_TO_OTHER_DEVICE;
                }
                if (recipientClass != -1) {
                    CptClientCommunication.messageSent(context, helper,
                            networkMessage.header,
                            recipientClass,
                            now
                    );
                }
            }
            Log.v(TAG, "Sent & processed message " + networkMessage);
        }

        String mask = "";
        if (toRecipient > 0) {
            mask += "Recipient";
        }
        if (toInternet > 0) {
            mask += "Server";
        }
        if (toOther > 0) {
            mask += "Other";
        }

        builder.setContentText(
                Html.fromHtml(
                        "<b>All</b>:" + messages.size()
                                + ", <b>Nrm</b>:" + sentNormal
                                + ", <b>Ack</b>:" + sentAck
                                + ", <b>To</b>:" + mask
                )
        );
        builder.setNumber(messages.size());
        //nm.notify(notificationId, builder.build());
    }

    void markHeadersAsSent(TreeSet<NetworkHeader> headers) {
        DatabaseManager.markMessagesAsSent(context, crocoId, headers);
    }

    void markMessagesAsSent(List<NetworkMessage> messages) {
        TreeSet<NetworkHeader> headers = new TreeSet<>();

        for (NetworkMessage message : messages) {
            headers.add(message.header);
        }

        markHeadersAsSent(headers);
    }

    List<NetworkMessage> processReceivedMessages(List<NetworkMessage> messages) {
        Date now = Calendar.getInstance().getTime();
        List<NetworkMessage> turnedIntoAck = new ArrayList<>();

        int notificationId = getAndIncrement(NotificationId.RECEIVED_MSG_BASE, receivingIdCounter);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setContentTitle("processReceivedMessages()")
                .setSmallIcon(R.drawable.ic_wifon)
                .setWhen(now.getTime())
                .setShowWhen(true)
        ;

        int receivedNormal = 0;
        int receivedAck = 0;
        int receivedErr = 0;

        SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
        SQLiteDatabase database = dbHelper.getWritableDatabase();

        database.beginTransaction();
        try {
            for (NetworkMessage networkMessage : messages) {
                DatabaseManager.ProcessMessageResult result = DatabaseManager.processRemoteMessage(database, context, networkMessage, now, helper.getCrocoId(), crocoId == null);
                if (result == DatabaseManager.ProcessMessageResult.OK || result == DatabaseManager.ProcessMessageResult.OK_TURNED_INTO_ACK) {
                    if (result == DatabaseManager.ProcessMessageResult.OK_TURNED_INTO_ACK) {
                        turnedIntoAck.add(turnIntoAck(networkMessage, now));
                    }
                    if (networkMessage.header.getType() == NetworkHeader.Type.NORMAL) {
                        receivedNormal++;
                        CptClientCommunication.messageNew(context, helper, networkMessage, now);
                        Log.v(TAG, "Received & processed NEW remote message " + networkMessage);

                        // if we received a normal+local message, register all the uris which are available there
                        // so we can decide to accept them now or later (do it after the message has been stored into the db)
                        if (networkMessage.isLocal() && !networkMessage.header.isPersistent()) {
                            int trustLevel = DatabaseManager.getDeviceTrustLevel(context, networkMessage.header.getFrom());
                            if (trustLevel == Communication.USER_TRUST_LEVEL_TRUSTED || trustLevel == Communication.USER_TRUST_LEVEL_TRUSTED_ON_WIFI) {
                                for (StreamableAttachment attachment : networkMessage.getAttachments()) {
                                    assertTrue(attachment instanceof NetworkAttachmentPreview);
                                    // TODO: this is nearly a 1:1 copy of DataLayerIntentServer.requestAttachment()...
                                    DatabaseManager.addDownloadUri(context,
                                            new MessageAttachmentIdentifier(
                                                    new AttachmentIdentifier(
                                                            attachment.getSourceUri(),
                                                            attachment.getStorageDirectory()
                                                    ),
                                                    networkMessage.header.getIdentifier()    // foreign key
                                            ),
                                            trustLevel == Communication.USER_TRUST_LEVEL_TRUSTED_ON_WIFI
                                    );
                                    new NewAttachment().send(context, networkMessage.header.getFrom());
                                    CptClientCommunication.messageAttachmentDownloadAction(context, helper,
                                            Communication.ACTION_MESSAGE_ATTACHMENT_DOWNLOAD_CONFIRMED,
                                            new MessageAttachmentIdentifier(
                                                    new AttachmentIdentifier(
                                                            attachment.getSourceUri(),
                                                            attachment.getStorageDirectory()
                                                    ),
                                                    networkMessage.header.getIdentifier()
                                            )
                                    );
                                }
                            }
                        }
                    } else {
                        receivedAck++;
                        // we don't have to check isExpectingAck -- such messages shouldn't arrive at all!
                        CptClientCommunication.messageAcked(context, helper, networkMessage.header, networkMessage.getHops(), networkMessage.getDeliveredTime());
                        Log.v(TAG, "Received & processed ACK remote message " + networkMessage);
                    }
                } else if (result == DatabaseManager.ProcessMessageResult.ERROR) {
                    receivedErr++;
                    Log.e(TAG, "Received & processed ERR remote message " + networkMessage);
                }
            }

            database.setTransactionSuccessful();

            builder.setContentText(
                    Html.fromHtml(
                        "<b>All</b>:" + messages.size()
                        + ", <b>Nrm</b>:" + receivedNormal
                        + " (-><b>Ack</b>:" + turnedIntoAck.size() + ")"
                        + ", <b>Ack</b>:" + receivedAck
                        + ", <b>Err</b>:" + receivedErr
                    )
            );
            builder.setNumber(messages.size());
            //nm.notify(notificationId, builder.build());
        } finally {
            database.endTransaction();
        }

        return turnedIntoAck;
    }

    private NetworkMessage turnIntoAck(NetworkMessage networkMessage, Date deliveredTime) {
        NetworkHeader ackNetworkHeader = new NetworkHeader(
                networkMessage.header.getRowId(),
                networkMessage.header.getIdentifier(),
                NetworkHeader.Type.ACK,
                networkMessage.header.getPersistentId()
        );
        NetworkMessage ackNetworkMessage = new NetworkMessage(
                ackNetworkHeader,
                networkMessage.getExpirationTime(),
                null,
                networkMessage.getHops(),
                false,  // isSentToRecipient
                false,  // isSentToOtherDevice
                false,  // isSentToAppServer
                false,  // isExpectingSent
                networkMessage.isExpectingAck(),  // useless but still, sent over the network
                networkMessage.isLocal()
        );
        ackNetworkMessage.setDeliveredTime(deliveredTime);

        return ackNetworkMessage;
    }
}
