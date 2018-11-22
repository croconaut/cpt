package com.croconaut.cpt.data;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.croconaut.cpt.link.PreferenceHelper;
import com.croconaut.cpt.link.handler.main.User;
import com.croconaut.cpt.network.NetworkAttachmentPreview;
import com.croconaut.cpt.network.NetworkHeader;
import com.croconaut.cpt.network.NetworkHop;
import com.croconaut.cpt.network.NetworkMessage;
import com.croconaut.cpt.network.NetworkPersistentAttachment;
import com.croconaut.cpt.ui.LinkLayerMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static junit.framework.Assert.assertTrue;

public class CptClientCommunication {
    private static final String TAG = "data";

    public static void messageSent(Context context, PreferenceHelper helper, NetworkHeader header, @Communication.SentTo int recipientClass, Date sentTime) {
        Log.v(TAG, CptClientCommunication.class.getSimpleName() + ".messageSent");

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        if (helper.getAppId() == null || helper.getClassId() == null) {
            Toast.makeText(context, "messageSent: No CPT client registered", Toast.LENGTH_SHORT).show();
            return;
        } else if (!helper.getAppId().equals(header.getAppId())) {
            Toast.makeText(context, "messageSent: message for: " + header.getAppId(), Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Communication.ACTION_MESSAGE_SENT);
        intent.setClassName(helper.getAppId(), helper.getClassId());
        intent.putExtra(Communication.EXTRA_MESSAGE_SENT, recipientClass);
        intent.putExtra(Communication.EXTRA_MESSAGE_ID, header.getCreationTime());
        intent.putExtra(Communication.EXTRA_MESSAGE_TIME, sentTime);
        context.sendBroadcast(intent);
    }

    public static void messageDeleted(Context context, PreferenceHelper helper, String appId, long messageId) {
        Log.v(TAG, CptClientCommunication.class.getSimpleName() + ".messageDeleted");

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        if (helper.getAppId() == null || helper.getClassId() == null) {
            Toast.makeText(context, "messageDeleted: No CPT client registered", Toast.LENGTH_SHORT).show();
            return;
        } else if (!helper.getAppId().equals(appId)) {
            Toast.makeText(context, "messageDeleted: message for: " + appId, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Communication.ACTION_MESSAGE_DELETED);
        intent.setClassName(helper.getAppId(), helper.getClassId());
        intent.putExtra(Communication.EXTRA_MESSAGE_ID, messageId);
        context.sendBroadcast(intent);
    }

    public static void messageAcked(Context context, PreferenceHelper helper, NetworkHeader header, List<NetworkHop> hops, Date deliveredTime) {
        Log.v(TAG, CptClientCommunication.class.getSimpleName() + ".messageAcked");

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        if (helper.getAppId() == null || helper.getClassId() == null) {
            Toast.makeText(context, "messageAcked: No CPT client registered", Toast.LENGTH_SHORT).show();
            return;
        } else if (!helper.getAppId().equals(header.getAppId())) {
            Toast.makeText(context, "messageAcked: message for: " + header.getAppId(), Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Communication.ACTION_MESSAGE_ACKED);
        intent.setClassName(helper.getAppId(), helper.getClassId());
        intent.putParcelableArrayListExtra(Communication.EXTRA_MESSAGE_ACKED, new ArrayList<>(hops));  // most likely this is an ArrayList -> ArrayList operation but let's be safe
        intent.putExtra(Communication.EXTRA_MESSAGE_ID, header.getCreationTime());
        intent.putExtra(Communication.EXTRA_MESSAGE_TIME, deliveredTime);
        context.sendBroadcast(intent);
    }

    public static void messageNew(Context context, PreferenceHelper helper, NetworkMessage networkMessage, Date receivedTime) {
        Log.v(TAG, CptClientCommunication.class.getSimpleName() + ".messageNew");

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        if (helper.getAppId() == null || helper.getClassId() == null) {
            Toast.makeText(context, "messageNew: No CPT client registered", Toast.LENGTH_SHORT).show();
            return;
        } else if (!helper.getAppId().equals(networkMessage.header.getAppId())) {
            Toast.makeText(context, "messageNew: message for: " + networkMessage.header.getAppId(), Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Communication.ACTION_MESSAGE_ARRIVED);
        intent.setClassName(helper.getAppId(), helper.getClassId());
        intent.putExtra(Communication.EXTRA_MESSAGE_ARRIVED, networkMessageToIncomingMessage(context, networkMessage, helper.getCrocoId()));
        intent.putExtra(Communication.EXTRA_MESSAGE_ID, networkMessage.header.getCreationTime());
        intent.putExtra(Communication.EXTRA_MESSAGE_TIME, receivedTime);
        context.sendBroadcast(intent);
    }

    public static void messageAttachmentUploadAction(Context context, PreferenceHelper helper, String action, MessageAttachmentIdentifier messageAttachmentIdentifier) {
        messageAttachmentUploadAction(context, helper, action,
                messageAttachmentIdentifier,
                null
        );
    }
    public static void messageAttachmentUploadAction(Context context, PreferenceHelper helper, String action, MessageAttachmentIdentifier messageAttachmentIdentifier, Date time) {
        messageAttachmentUploadAction(context, helper, action,
                messageAttachmentIdentifier,
                time,
                -1
        );
    }
    public static void messageAttachmentUploadAction(Context context, PreferenceHelper helper, @Communication.AttachmentUploadAction String action, MessageAttachmentIdentifier messageAttachmentIdentifier, Date time, int bytesPerSecond) {
        messageAttachmentAction(context, helper, action,
                new MessageAttachmentIdentifier(
                        messageAttachmentIdentifier.getAttachmentIdentifier(),
                        new MessageIdentifier(
                                messageAttachmentIdentifier.getAppId(),
                                null,   // from
                                messageAttachmentIdentifier.getTo(),
                                messageAttachmentIdentifier.getCreationTime()
                        )
                ),
                null,
                time,
                bytesPerSecond
        );
    }
    public static void messageAttachmentDownloadAction(Context context, PreferenceHelper helper, String action, MessageAttachmentIdentifier messageAttachmentIdentifier) {
        messageAttachmentDownloadAction(context, helper, action,
                messageAttachmentIdentifier,
                null,
                -1
        );
    }
    public static void messageAttachmentDownloadAction(Context context, PreferenceHelper helper, String action, MessageAttachmentIdentifier messageAttachmentIdentifier, ParcelableAttachment attachment, int bytesPerSecond) {
        messageAttachmentDownloadAction(context, helper, action,
                messageAttachmentIdentifier,
                attachment,
                null,
                bytesPerSecond
        );
    }
    public static void messageAttachmentDownloadAction(Context context, PreferenceHelper helper, @Communication.AttachmentDownloadAction String action, MessageAttachmentIdentifier messageAttachmentIdentifier, ParcelableAttachment attachment, Date time, int bytesPerSecond) {
        messageAttachmentAction(context, helper, action,
                new MessageAttachmentIdentifier(
                        messageAttachmentIdentifier.getAttachmentIdentifier(),
                        new MessageIdentifier(
                                messageAttachmentIdentifier.getAppId(),
                                messageAttachmentIdentifier.getFrom(),
                                null,   // to
                                messageAttachmentIdentifier.getCreationTime()
                        )
                ),
                attachment,
                time,
                bytesPerSecond
        );
    }

    private static void messageAttachmentAction(Context context, PreferenceHelper helper, String action, MessageAttachmentIdentifier messageAttachmentIdentifier, ParcelableAttachment attachment, Date time, int bytesPerSecond) {
        Log.v(TAG, CptClientCommunication.class.getSimpleName() + ".messageAttachmentAction");

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        if (helper.getAppId() == null || helper.getClassId() == null) {
            Toast.makeText(context, "messageAttachmentAction: No CPT client registered", Toast.LENGTH_SHORT).show();
            return;
        } else if (!helper.getAppId().equals(messageAttachmentIdentifier.getAppId())) {
            Toast.makeText(context, "messageAttachmentAction: attachment for: " + messageAttachmentIdentifier.getAppId(), Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(action);
        intent.setClassName(messageAttachmentIdentifier.getAppId(), helper.getClassId());
        intent.putExtra(Communication.EXTRA_MESSAGE_ID, messageAttachmentIdentifier.getCreationTime());
        intent.putExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_CROCO_ID, messageAttachmentIdentifier.getFrom());  // can be null
        intent.putExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_TARGET_CROCO_ID, messageAttachmentIdentifier.getTo());  // can be null
        intent.putExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI, messageAttachmentIdentifier.getSourceUri());
        intent.putExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR, messageAttachmentIdentifier.getStorageDirectory());
        intent.putExtra(Communication.EXTRA_MESSAGE_ATTACHMENT, attachment);    // can be null
        intent.putExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_TIME, time);    // can be null
        if (bytesPerSecond != -1) {
            intent.putExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SPEED, bytesPerSecond);
        }
        context.sendBroadcast(intent);
    }

    public static void nearbyChanged(Context context, PreferenceHelper helper, Collection<User> users) {
        Log.v(TAG, CptClientCommunication.class.getSimpleName() + ".nearbyChanged");

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        if (helper.getAppId() == null || helper.getClassId() == null) {
            if (Looper.myLooper() == null) {
                Looper.prepare();
            }
            Toast.makeText(context, "nearbyChanged: No CPT client registered", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Communication.ACTION_NEARBY_ARRIVED);
        intent.setClassName(helper.getAppId(), helper.getClassId());
        ArrayList<NearbyUser> nearbyUsers = new ArrayList<>();
        for (User user : users) {
            /*if (user.username != null)*/ {
                nearbyUsers.add(new NearbyUser(user.crocoId, user.username));
            }
        }
        /*if (users.isEmpty() || !nearbyUsers.isEmpty())*/ {
            intent.putParcelableArrayListExtra(Communication.EXTRA_NEARBY_ARRIVED, nearbyUsers);
            context.sendBroadcast(intent);
        }
    }

    public static void supplyP2pDnsSdRecords(Context context, PreferenceHelper helper) {
        Log.v(TAG, CptClientCommunication.class.getSimpleName() + ".supplyP2pDnsSdRecords");

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        if (helper.getAppId() == null || helper.getClassId() == null) {
            if (Looper.myLooper() == null) {
                Looper.prepare();
            }
            Toast.makeText(context, "supplyP2pDnsSdRecords: No CPT client registered", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Communication.ACTION_SUPPLY_P2P_DNS_SD_RECORDS);
        intent.setClassName(helper.getAppId(), helper.getClassId());
        context.sendBroadcast(intent);
    }

    public static void p2pDnsSdRecordAvailable(Context context, PreferenceHelper helper, String instanceName, Map<String, String> txtMap) {
        Log.v(TAG, CptClientCommunication.class.getSimpleName() + ".p2pDnsSdRecordAvailable");

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        if (helper.getAppId() == null || helper.getClassId() == null) {
            if (Looper.myLooper() == null) {
                Looper.prepare();
            }
            Toast.makeText(context, "p2pDnsSdRecordAvailable: No CPT client registered", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Communication.ACTION_P2P_DNS_SD_RECORD_AVAILABLE);
        intent.setClassName(helper.getAppId(), helper.getClassId());
        intent.putExtra(Communication.EXTRA_P2P_DNS_SD_INSTANCE_NAME, instanceName);
        Bundle record = new Bundle();
        for (String key : txtMap.keySet()) {
            record.putString(key, txtMap.get(key));
        }
        intent.putExtra(Communication.EXTRA_P2P_DNS_SD_INSTANCE_RECORD, record);
        context.sendBroadcast(intent);
    }

    public static PendingIntent getCptModeRequestPendingIntent(Context context, PreferenceHelper helper, @LinkLayerMode.CptMode int mode) {
        Intent intent = new Intent(Communication.ACTION_REQUEST_CPT_MODE);
        intent.putExtra(Communication.EXTRA_REQUEST_CPT_MODE, mode);
        intent.setClassName(helper.getAppId(), helper.getClassId());

        return PendingIntent.getBroadcast(context, mode, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    public static PendingIntent getCptNotificationPendingIntent(Context context, PreferenceHelper helper) {
        Intent intent = new Intent(Communication.ACTION_OPEN_CPT_SETTINGS);
        intent.setClassName(helper.getAppId(), helper.getClassId());

        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    public static PendingIntent getCommunicationErrorPendingIntent(Context context, PreferenceHelper helper) {
        Intent intent = new Intent(Communication.ACTION_SEND_CPT_LOGS);
        intent.setClassName(helper.getAppId(), helper.getClassId());

        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static PendingIntent getMessageAttachmentDownloadNotificationPendingIntent(Context context, PreferenceHelper helper, int requestCode, MessageAttachmentIdentifier messageAttachmentIdentifier) {
        return getMessageAttachmentNotificationPendingIntent(context, helper, requestCode,
                new MessageAttachmentIdentifier(
                        messageAttachmentIdentifier.getAttachmentIdentifier(),
                        new MessageIdentifier(
                                messageAttachmentIdentifier.getAppId(),
                                messageAttachmentIdentifier.getFrom(),
                                null,   // to
                                messageAttachmentIdentifier.getCreationTime()
                        )
                )
        );
    }

    public static PendingIntent getMessageAttachmentUploadNotificationPendingIntent(Context context, PreferenceHelper helper, int requestCode, MessageAttachmentIdentifier messageAttachmentIdentifier) {
        return getMessageAttachmentNotificationPendingIntent(context, helper, requestCode,
                new MessageAttachmentIdentifier(
                        messageAttachmentIdentifier.getAttachmentIdentifier(),
                        new MessageIdentifier(
                                messageAttachmentIdentifier.getAppId(),
                                null,   // from
                                messageAttachmentIdentifier.getTo(),
                                messageAttachmentIdentifier.getCreationTime()
                        )
                )
        );
    }

    private static PendingIntent getMessageAttachmentNotificationPendingIntent(Context context, PreferenceHelper helper, int requestCode, MessageAttachmentIdentifier messageAttachmentIdentifier) {
        Intent intent = new Intent(Communication.ACTION_MESSAGE_ATTACHMENT_NOTIFICATION);
        intent.setClassName(messageAttachmentIdentifier.getAppId(), helper.getClassId());
        intent.putExtra(Communication.EXTRA_MESSAGE_ID, messageAttachmentIdentifier.getCreationTime());
        intent.putExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_CROCO_ID, messageAttachmentIdentifier.getFrom());  // can be null
        intent.putExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_TARGET_CROCO_ID, messageAttachmentIdentifier.getTo());  // can be null
        intent.putExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI, messageAttachmentIdentifier.getSourceUri());
        intent.putExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR, messageAttachmentIdentifier.getStorageDirectory()); // can be null

        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    private static IncomingMessage networkMessageToIncomingMessage(Context context, NetworkMessage networkMessage, String myCrocoId) {
        // this is all because Parcelable can't hold an array of abstract classes (DownloadedAttachmentPreview & DownloadedAttachment)
        if (!networkMessage.header.isPersistent()) {
            IncomingMessagePreview incomingMessagePreview = new IncomingMessagePreview(
                    networkMessage.header.getCreationTime(),
                    networkMessage.header.getFrom().equals(myCrocoId) ? null : networkMessage.header.getFrom(),
                    networkMessage.header.getTo().equals(myCrocoId) || networkMessage.header.getTo().equals(MessageIdentifier.BROADCAST_ID) ? null : networkMessage.header.getTo(),
                    networkMessage.getHops(),
                    new IncomingPayloadPreview(networkMessage.getAppPayload())
            );
            for (MessageAttachment networkAttachment : networkMessage.getAttachments()) {
                assertTrue(networkAttachment instanceof NetworkAttachmentPreview);
                incomingMessagePreview.payload.addAttachment(new DownloadedAttachmentPreview(
                                networkAttachment.getSourceUri(),
                                networkAttachment.getName(context),
                                networkAttachment.getLength(context),
                                networkAttachment.getLastModified(context).getTime(),
                                networkAttachment.getType(context),
                                networkAttachment.getStorageDirectory()
                        )
                );
            }
            return incomingMessagePreview;
        } else {
            IncomingPersistentMessage incomingPersistentMessage = new IncomingPersistentMessage(
                    networkMessage.header.getCreationTime(),
                    networkMessage.header.getFrom().equals(myCrocoId) ? null : networkMessage.header.getFrom(),
                    networkMessage.header.getTo().equals(myCrocoId) || networkMessage.header.getTo().equals(MessageIdentifier.BROADCAST_ID) ? null : networkMessage.header.getTo(),
                    networkMessage.getHops(),
                    new IncomingPersistentPayload(networkMessage.getAppPayload())
            );
            for (MessageAttachment networkAttachment : networkMessage.getAttachments()) {
                assertTrue(networkAttachment instanceof NetworkPersistentAttachment);
                incomingPersistentMessage.payload.addAttachment(new DownloadedAttachment(
                        networkAttachment.getUri(),
                        networkAttachment.getStorageDirectory(),
                        networkAttachment.getName(context), // not really needed, equals to null anyway
                        networkAttachment.getSourceUri()    // not really needed, equals to Uri anyway
                ));
            }
            return incomingPersistentMessage;
        }
    }
}
