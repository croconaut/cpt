package com.croconaut.cpt.data;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.croconaut.cpt.common.CptServiceStarter;
import com.croconaut.cpt.link.PreferenceHelper;
import com.croconaut.cpt.link.handler.main.CancelConnection;
import com.croconaut.cpt.link.handler.main.GcmSyncRequest;
import com.croconaut.cpt.link.handler.main.NewAttachment;
import com.croconaut.cpt.link.handler.main.NewMessage;
import com.croconaut.cpt.link.handler.main.UpdatedIgnoredDevices;
import com.croconaut.cpt.link.handler.p2p.AddClientP2pDnsSdRecord;
import com.croconaut.cpt.link.handler.p2p.RemoveClientP2pDnsSdRecord;
import com.croconaut.cpt.link.handler.p2p.UpdatedUsername;
import com.croconaut.cpt.network.NetworkHeader;

import java.util.Set;

public class DataLayerIntentService extends WakefulIntentService {
    private static final String TAG = "data";

    public DataLayerIntentService() {
        super("DataLayerIntentService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // this is still done in the UI thread -- we must do this since we require a hop in processClientMessage()
        DatabaseManager.obtainLocation(this);
    }

    @Override
    protected void doWakefulWork(Intent intent) {
        Log.v(TAG, getClass().getSimpleName() + ".doWakefulWork: " + intent);

        if (intent != null) {
            CptServiceStarter.finish(this, intent);

            final String action = intent.getAction();

            switch (action) {
                case Communication.ACTION_REGISTER: {
                    String username = intent.getStringExtra(Communication.EXTRA_REGISTER_USERNAME);
                    String className = intent.getStringExtra(Communication.EXTRA_REGISTER_APP_SERVICE);
                    messageRegister(className, username);
                    break;
                }

                case Communication.ACTION_INVITE_FRIEND: {
                    String title = intent.getStringExtra(Communication.EXTRA_PENDING_INTENT_TITLE);
                    String subject = intent.getStringExtra(Communication.EXTRA_PENDING_INTENT_SUBJECT);
                    String body = intent.getStringExtra(Communication.EXTRA_PENDING_INTENT_BODY);
                    String bodyHtml = intent.getStringExtra(Communication.EXTRA_PENDING_INTENT_BODY_HTML);
                    Uri baseUri = intent.getParcelableExtra(Communication.EXTRA_PENDING_INTENT_BASE_URI);
                    String uriParam = intent.getStringExtra(Communication.EXTRA_PENDING_INTENT_URI_PARAM);
                    inviteFriend(title, subject, body, bodyHtml, baseUri, uriParam);
                    break;
                }

                case Communication.ACTION_NEW_MESSAGE: {
                    OutgoingMessage message = intent.getParcelableExtra(Communication.EXTRA_NEW_MESSAGE);
                    messageNew(message);
                    break;
                }

                case Communication.ACTION_DELETE_MESSAGE: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_DELETE_MESSAGE, -1);
                    messageDelete(messageId);
                    break;
                }

                case Communication.ACTION_CHANGE_TRUST: {
                    String crocoId = intent.getStringExtra(Communication.EXTRA_CHANGE_TRUST_CROCO_ID);
                    int trustLevel = intent.getIntExtra(Communication.EXTRA_CHANGE_TRUST_LEVEL, Communication.USER_TRUST_LEVEL_NORMAL);
                    changeTrustLevel(crocoId, trustLevel);
                    break;
                }

                case Communication.ACTION_DOWNLOAD: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_CONNECTION_MESSAGE_ID, -1);
                    String from = intent.getStringExtra(Communication.EXTRA_DOWNLOAD_SOURCE_CROCO_ID);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_CONNECTION_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_CONNECTION_STORAGE_DIR);
                    requestAttachmentDownload(messageId, from, sourceUri, storageDirectory, false);
                    break;
                }
                case Communication.ACTION_DOWNLOAD_ON_WIFI: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_CONNECTION_MESSAGE_ID, -1);
                    String from = intent.getStringExtra(Communication.EXTRA_DOWNLOAD_SOURCE_CROCO_ID);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_CONNECTION_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_CONNECTION_STORAGE_DIR);
                    requestAttachmentDownload(messageId, from, sourceUri, storageDirectory, true);
                    break;
                }
                case Communication.ACTION_DOWNLOAD_CANCEL: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_CONNECTION_MESSAGE_ID, -1);
                    String from = intent.getStringExtra(Communication.EXTRA_DOWNLOAD_SOURCE_CROCO_ID);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_CONNECTION_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_CONNECTION_STORAGE_DIR);
                    cancelAttachmentDownload(messageId, from, sourceUri, storageDirectory);
                    break;
                }

                case Communication.ACTION_UPLOAD: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_CONNECTION_MESSAGE_ID, -1);
                    String to = intent.getStringExtra(Communication.EXTRA_UPLOAD_TARGET_CROCO_ID);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_CONNECTION_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_CONNECTION_STORAGE_DIR);
                    requestAttachmentUpload(messageId, to, sourceUri, storageDirectory, false);
                    break;
                }
                case Communication.ACTION_UPLOAD_ON_WIFI: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_CONNECTION_MESSAGE_ID, -1);
                    String to = intent.getStringExtra(Communication.EXTRA_UPLOAD_TARGET_CROCO_ID);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_CONNECTION_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_CONNECTION_STORAGE_DIR);
                    requestAttachmentUpload(messageId, to, sourceUri, storageDirectory, true);
                    break;
                }
                case Communication.ACTION_UPLOAD_CANCEL: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_CONNECTION_MESSAGE_ID, -1);
                    String to = intent.getStringExtra(Communication.EXTRA_UPLOAD_TARGET_CROCO_ID);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_CONNECTION_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_CONNECTION_STORAGE_DIR);
                    cancelAttachmentUpload(messageId, to, sourceUri, storageDirectory);
                    break;
                }

                case Communication.ACTION_SEND_CPT_LOGS: {
                    messageSendLogs();
                    break;
                }

                case Communication.ACTION_ADD_P2P_DNS_SD: {
                    String instanceName = intent.getStringExtra(Communication.EXTRA_P2P_DNS_SD_INSTANCE_NAME);
                    Bundle record = intent.getBundleExtra(Communication.EXTRA_P2P_DNS_SD_INSTANCE_RECORD);
                    addP2pDnsSd(instanceName, record);
                    break;
                }
                case Communication.ACTION_REMOVE_P2P_DNS_SD: {
                    String instanceName = intent.getStringExtra(Communication.EXTRA_P2P_DNS_SD_INSTANCE_NAME);
                    removeP2pDnsSd(instanceName);
                    break;
                }
            }
        }
    }

    private void inviteFriend(String title, String subject, String body, String bodyHtml, Uri baseUri, String uriParam) {
        Log.v(TAG, getClass().getSimpleName() + ".inviteFriend");

        PreferenceHelper helper = new PreferenceHelper(this);
        String text = String.format(
                body,
                baseUri.buildUpon()
                        .appendQueryParameter(uriParam, helper.getCrocoId())
                        .build()
        );
        String textHtml = String.format(
                bodyHtml,
                baseUri.buildUpon()
                        .appendQueryParameter(uriParam, helper.getCrocoId())
                        .build()
        );

        Intent txtIntent = new Intent(android.content.Intent.ACTION_SEND);
        txtIntent.setType("text/plain");
        txtIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
        txtIntent.putExtra(android.content.Intent.EXTRA_TEXT, text);
        txtIntent.putExtra(Intent.EXTRA_HTML_TEXT, Html.fromHtml(textHtml));
        startActivity(Intent.createChooser(txtIntent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void messageRegister(String className, String username) {
        Log.v(TAG, getClass().getSimpleName() + ".messageRegister");

        if (className != null) {
            PreferenceHelper helper = new PreferenceHelper(this);

            if (helper.getAppId() == null && helper.getClassId() == null) {
                // first time...
                new GcmSyncRequest().send(this, GcmSyncRequest.UPLOAD_TOKEN_AND_NAME | GcmSyncRequest.UPLOAD_FRIENDS);
            }

            helper.setAppId(getPackageName());  // the rest of CPT heavily depends on this so let it be for now...
            helper.setClassId(className);

            if (username != null) {
                if (!username.equals(helper.getUsername())) {
                    new UpdatedUsername().send(this, username);
                    new GcmSyncRequest().send(this, GcmSyncRequest.UPLOAD_TOKEN_AND_NAME);
                }
            }

            new GcmSyncRequest().send(this, GcmSyncRequest.UPLOAD_TOKEN_AND_NAME);
        }
    }

    private void messageNew(OutgoingMessage clientMessage) {
        Log.v(TAG, getClass().getSimpleName() + ".messageNew");

        PreferenceHelper helper = new PreferenceHelper(this);
        if (helper.getAppId() != null && clientMessage != null && clientMessage.payload != null) {
            DatabaseManager.processClientMessage(this, helper.getAppId(), helper.getCrocoId(), clientMessage.sanitize(this));
        }
    }

    private void messageDelete(long messageId) {
        Log.v(TAG, getClass().getSimpleName() + ".messageDelete");

        PreferenceHelper helper = new PreferenceHelper(this);
        if (helper.getAppId() != null && messageId != -1) {
            String crocoId = null;  // cancel of messages only by default

            Set<NetworkHeader> myLocalHeaders = DatabaseManager.getLocalHeaders(this, helper.getCrocoId());
            for (NetworkHeader header : myLocalHeaders) {
                if (header.getCreationTime() == messageId && header.getFrom().equals(helper.getCrocoId())) {
                    crocoId = header.getTo();
                    break;
                }
            }

            if (DatabaseManager.deleteMessage(this, helper.getAppId(), messageId, helper.getCrocoId()) == 1) {
                CptClientCommunication.messageDeleted(this, helper,
                        helper.getAppId(),
                        messageId
                );
                new CancelConnection().send(this, crocoId);
            }
        }
    }

    private void changeTrustLevel(String crocoId, int trustLevel) {
        Log.v(TAG, getClass().getSimpleName() + ".changeTrustLevel");

        PreferenceHelper helper = new PreferenceHelper(this);
        if (helper.getAppId() != null && crocoId != null) {
            DatabaseManager.changeDeviceTrustLevel(this, helper.getAppId(), crocoId, trustLevel);
            if (trustLevel == Communication.USER_TRUST_LEVEL_BLOCKED) {
                new UpdatedIgnoredDevices().send(this, crocoId);
                new GcmSyncRequest().send(this, GcmSyncRequest.UPLOAD_FRIENDS);
            } else {
                new UpdatedIgnoredDevices().send(this, null);
                // if we change someone's trust (blocked -> unblocked, normal -> trusted, ...) we want a sync
                // hopefully the client app wont abuse this call...
                new GcmSyncRequest().send(this, GcmSyncRequest.DOWNLOAD_AND_UPLOAD_EVERYTHING);
            }
        }
    }

    // TODO: right now if changed 'wifiOnly' we require user to cancel connection first and then ask for this, it would by nice to cancel it for him
    private void requestAttachmentDownload(long messageId, String from, String sourceUri, String storageDirectory, boolean wifiOnly) {
        Log.v(TAG, getClass().getSimpleName() + ".requestAttachmentDownload");

        PreferenceHelper helper = new PreferenceHelper(this);
        if (helper.getAppId() != null && helper.getCrocoId() != null && messageId != -1 && sourceUri != null) {
            MessageAttachmentIdentifier messageAttachmentIdentifier = new MessageAttachmentIdentifier(
                    new AttachmentIdentifier(
                            sourceUri,
                            storageDirectory
                    ),
                    new MessageIdentifier(
                            helper.getAppId(),
                            from,
                            helper.getCrocoId(),
                            messageId
                    )
            );

            if (DatabaseManager.addDownloadUri(this,
                    messageAttachmentIdentifier,
                    wifiOnly)) {
                new NewAttachment().send(this, from);
                CptClientCommunication.messageAttachmentDownloadAction(this, helper,
                        Communication.ACTION_MESSAGE_ATTACHMENT_DOWNLOAD_CONFIRMED,
                        messageAttachmentIdentifier
                );
            }
        }
    }

    private void cancelAttachmentDownload(long messageId, String from, String sourceUri, String storageDirectory) {
        Log.v(TAG, getClass().getSimpleName() + ".cancelAttachmentDownload");

        PreferenceHelper helper = new PreferenceHelper(this);
        if (helper.getAppId() != null && helper.getCrocoId() != null && messageId != -1 && sourceUri != null) {
            MessageAttachmentIdentifier messageAttachmentIdentifier = new MessageAttachmentIdentifier(
                    new AttachmentIdentifier(
                            sourceUri,
                            storageDirectory
                    ),
                    new MessageIdentifier(
                            helper.getAppId(),
                            from,
                            helper.getCrocoId(),
                            messageId
                    )
            );

            DatabaseManager.removeDownloadUri(this,
                    messageAttachmentIdentifier
            );
            new CancelConnection().send(this, from);
            // this is not entirely accurate but we can't send it from NetworkAttachment, we don't know
            // what app/message was cancelled, only that we must break the link to this croco id
            CptClientCommunication.messageAttachmentDownloadAction(this, helper,
                    Communication.ACTION_MESSAGE_ATTACHMENT_DOWNLOAD_CANCELLED,
                    messageAttachmentIdentifier
            );
        }
    }

    // TODO: right now if changed 'wifiOnly' we require user to cancel connection first and then ask for this, it would by nice to cancel it for him
    // TODO: 'upload' -> upload to server -> 'upload on wifi only' -> 'upload', the file is going to be send to the app server again (because sentToAppServer flag is cleared)
    private void requestAttachmentUpload(long messageId, String to, String sourceUri, String storageDirectory, boolean wifiOnly) {
        Log.v(TAG, getClass().getSimpleName() + ".requestAttachmentUpload");

        PreferenceHelper helper = new PreferenceHelper(this);
        if (helper.getAppId() != null && helper.getCrocoId() != null && messageId != -1 && sourceUri != null) {
            MessageAttachmentIdentifier messageAttachmentIdentifier = new MessageAttachmentIdentifier(
                    new AttachmentIdentifier(
                            sourceUri,
                            storageDirectory),
                    new MessageIdentifier(
                            helper.getAppId(),
                            helper.getCrocoId(),
                            to,
                            messageId
                    )
            );

            if ((DatabaseManager.addUploadUri(this,
                    messageAttachmentIdentifier,
                    wifiOnly))) {
                new NewMessage().send(this, to);    // this will try to re-upload a local message which has been refused due to zero attachments
                CptClientCommunication.messageAttachmentUploadAction(this, helper,
                        Communication.ACTION_MESSAGE_ATTACHMENT_UPLOAD_CONFIRMED,
                        messageAttachmentIdentifier
                );
            }
        }
    }

    private void cancelAttachmentUpload(long messageId, String to, String sourceUri, String storageDirectory) {
        Log.v(TAG, getClass().getSimpleName() + ".cancelAttachmentUpload");

        PreferenceHelper helper = new PreferenceHelper(this);
        if (helper.getAppId() != null && helper.getCrocoId() != null && messageId != -1 && sourceUri != null) {
            MessageAttachmentIdentifier messageAttachmentIdentifier = new MessageAttachmentIdentifier(
                    new AttachmentIdentifier(
                            sourceUri,
                            storageDirectory),
                    new MessageIdentifier(
                            helper.getAppId(),
                            helper.getCrocoId(),
                            to,
                            messageId
                    )
            );

            DatabaseManager.removeUploadUri(this,
                    messageAttachmentIdentifier
            );
            new CancelConnection().send(this, to);
            // this is not entirely accurate but we can't send it from NetworkAttachment, we don't know
            // what app/message was cancelled, only that we must break the link to this croco id
            CptClientCommunication.messageAttachmentUploadAction(this, helper,
                    Communication.ACTION_MESSAGE_ATTACHMENT_UPLOAD_CANCELLED,
                    messageAttachmentIdentifier
            );
        }
    }

    private void messageSendLogs() {
        Log.v(TAG, getClass().getSimpleName() + ".messageSendLogs");

        PreferenceHelper helper = new PreferenceHelper(this);
        if (helper.getAppId() != null && helper.getClassId() != null && helper.getCrocoId() != null) {
            // a bit awkward...
            Intent intent = new Intent(Communication.ACTION_SEND_CPT_LOGS);
            intent.setClassName(helper.getAppId(), helper.getClassId());
            sendBroadcast(intent);
        }
    }

    private void addP2pDnsSd(String instanceName, Bundle record) {
        new AddClientP2pDnsSdRecord().send(this, instanceName, record);
    }
    private void removeP2pDnsSd(String instanceName) {
        new RemoveClientP2pDnsSdRecord().send(this, instanceName);
    }
}
