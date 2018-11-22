package com.croconaut.cpt.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringDef;

import com.croconaut.cpt.common.CptServiceStarter;
import com.croconaut.cpt.provider.Contract;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class Communication {
    /*
     * Client -> CPT
     */

    static final String ACTION_REGISTER = "com.croconaut.cpt.data.action.REGISTER";
    static final String EXTRA_REGISTER_USERNAME = "username";
    static final String EXTRA_REGISTER_APP_SERVICE = "app_service";

    static final String ACTION_INVITE_FRIEND = "com.croconaut.cpt.data.action.INVITE_FRIEND";
    static final String EXTRA_PENDING_INTENT_TITLE = "title";
    static final String EXTRA_PENDING_INTENT_SUBJECT = "subject";
    static final String EXTRA_PENDING_INTENT_BODY = "body";
    static final String EXTRA_PENDING_INTENT_BODY_HTML = "body_html";
    static final String EXTRA_PENDING_INTENT_BASE_URI = "base_uri";
    static final String EXTRA_PENDING_INTENT_URI_PARAM = "uri_param";

    static final String ACTION_NEW_MESSAGE = "com.croconaut.cpt.data.action.NEW_MESSAGE";
    static final String EXTRA_NEW_MESSAGE = "message";

    static final String ACTION_DELETE_MESSAGE = "com.croconaut.cpt.data.action.DELETE_MESSAGE";
    static final String EXTRA_DELETE_MESSAGE = "message_id";

    static final String ACTION_CHANGE_TRUST = "com.croconaut.cpt.data.action.CHANGE_TRUST";
    static final String EXTRA_CHANGE_TRUST_CROCO_ID = "croco_id";
    static final String EXTRA_CHANGE_TRUST_LEVEL = "level";

    @IntDef({USER_TRUST_LEVEL_BLOCKED, USER_TRUST_LEVEL_NORMAL, USER_TRUST_LEVEL_TRUSTED_ON_WIFI, USER_TRUST_LEVEL_TRUSTED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface UserTrustLevel {}
    public static final int USER_TRUST_LEVEL_BLOCKED            = 0;
    public static final int USER_TRUST_LEVEL_NORMAL             = 1;
    public static final int USER_TRUST_LEVEL_TRUSTED_ON_WIFI    = 2;
    public static final int USER_TRUST_LEVEL_TRUSTED            = 3;

    @StringDef({ACTION_DOWNLOAD, ACTION_DOWNLOAD_ON_WIFI, ACTION_DOWNLOAD_CANCEL})
    @Retention(RetentionPolicy.SOURCE)
    @interface DownloadAction {}
    static final String ACTION_DOWNLOAD         = "com.croconaut.cpt.data.action.DOWNLOAD";
    static final String ACTION_DOWNLOAD_ON_WIFI = "com.croconaut.cpt.data.action.DOWNLOAD_ON_WIFI";
    static final String ACTION_DOWNLOAD_CANCEL  = "com.croconaut.cpt.data.action.DOWNLOAD_CANCEL";
    static final String EXTRA_DOWNLOAD_SOURCE_CROCO_ID = "croco_id";

    @StringDef({ACTION_UPLOAD, ACTION_UPLOAD_ON_WIFI, ACTION_UPLOAD_CANCEL})
    @Retention(RetentionPolicy.SOURCE)
    @interface UploadAction {}
    static final String ACTION_UPLOAD           = "com.croconaut.cpt.data.action.UPLOAD";
    static final String ACTION_UPLOAD_ON_WIFI   = "com.croconaut.cpt.data.action.UPLOAD_ON_WIFI";
    static final String ACTION_UPLOAD_CANCEL    = "com.croconaut.cpt.data.action.UPLOAD_CANCEL";
    static final String EXTRA_UPLOAD_TARGET_CROCO_ID = "croco_id";

    static final String EXTRA_CONNECTION_MESSAGE_ID = "message_id";
    static final String EXTRA_CONNECTION_SOURCE_URI = "source_uri";
    static final String EXTRA_CONNECTION_STORAGE_DIR = "storage_dir";

    static final String ACTION_ADD_P2P_DNS_SD    = "com.croconaut.cpt.data.action.ADD_P2P_DNS_SD";
    static final String ACTION_REMOVE_P2P_DNS_SD = "com.croconaut.cpt.data.action.REMOVE_P2P_DNS_SD";
    public static final String EXTRA_P2P_DNS_SD_INSTANCE_NAME = "instance_name";
    public static final String EXTRA_P2P_DNS_SD_INSTANCE_RECORD = "record";

    public static void register(@NonNull Context context, @NonNull Class<? extends BroadcastReceiver> cls) {
        register(context, null, cls);
    }

    public static void register(@NonNull Context context, @Nullable String username, @NonNull Class<? extends BroadcastReceiver> cls) {
        CptServiceStarter.startIntentService(context,
                getCptIntent(context)
                        .setAction(ACTION_REGISTER)
                        .putExtra(EXTRA_REGISTER_APP_SERVICE, cls.getName())
                        .putExtra(EXTRA_REGISTER_USERNAME, username),
                false
        );
    }

    public static void inviteFriend(@NonNull Context context, @NonNull String title, @NonNull String subject, @NonNull String textTemplate, @NonNull String textHtmlTemplate, @NonNull Uri baseUri, @NonNull String crocoIdUriParameter) {
        CptServiceStarter.startIntentService(context,
                new Intent()
                        .setClass(context, DataLayerIntentService.class)
                        .setAction(ACTION_INVITE_FRIEND)
                        .putExtra(EXTRA_PENDING_INTENT_TITLE, title)
                        .putExtra(EXTRA_PENDING_INTENT_SUBJECT, subject)
                        .putExtra(EXTRA_PENDING_INTENT_BODY, textTemplate)
                        .putExtra(EXTRA_PENDING_INTENT_BODY_HTML, textHtmlTemplate)
                        .putExtra(EXTRA_PENDING_INTENT_BASE_URI, baseUri)
                        .putExtra(EXTRA_PENDING_INTENT_URI_PARAM, crocoIdUriParameter),
                false
        );
    }

    public static void newMessage(@NonNull Context context, @NonNull OutgoingMessage message) {
        for (MessageAttachment attachment : message.payload.getAttachments()) {
            // ensure we have a read permission
            if (!Contract.getAuthority(context).equals(attachment.getUri().getAuthority())) {
                // TODO: API <= 18: The permission remains in effect until you revoke it by calling revokeUriPermission() or until the device reboots.

// I don't get it how it works but at least on a 4.1 device it throws an exception when reading an uri from Gallery
//                context.enforceUriPermission(
//                        attachment.getUri(),
//                        android.os.Process.myPid(),
//                        android.os.Process.myUid(),
//                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
//                        null
//                );
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, attachment.getUri())) {
                    // for api >= 19 we need to make sure to take the persistent permission, too
                    // (the uri can come from a gallery for instance; side effect is that this enforce
                    // presence of FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    context.getContentResolver().takePersistableUriPermission(attachment.getUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            }
        }

        Intent cptIntent = getCptIntent(context)
                .setAction(ACTION_NEW_MESSAGE)
                .putExtra(EXTRA_NEW_MESSAGE, message)
                ;

        CptServiceStarter.startIntentService(context, cptIntent, false);
    }

    public static void deleteMessage(@NonNull Context context, long messageId) {
        // TODO: Context.getContentResolver().releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // for every uri in the original message...
        CptServiceStarter.startIntentService(context,
                getCptIntent(context)
                        .setAction(ACTION_DELETE_MESSAGE)
                        .putExtra(EXTRA_DELETE_MESSAGE, messageId),
                false
        );
    }

    public static void changeUserTrustLevel(@NonNull Context context, @NonNull String crocoId, @UserTrustLevel int trustLevel) {
        CptServiceStarter.startIntentService(context,
                getCptIntent(context)
                        .setAction(ACTION_CHANGE_TRUST)
                        .putExtra(EXTRA_CHANGE_TRUST_CROCO_ID, crocoId)
                        .putExtra(EXTRA_CHANGE_TRUST_LEVEL, trustLevel),
                false
        );
    }

    private static void requestDownloadAction(@NonNull Context context, @DownloadAction String action, long messageId, @NonNull String from, @NonNull String sourceUri, @Nullable String storageDirectory) {
        CptServiceStarter.startIntentService(context,
                getCptIntent(context)
                        .setAction(action)
                        .putExtra(EXTRA_CONNECTION_MESSAGE_ID, messageId)
                        .putExtra(EXTRA_DOWNLOAD_SOURCE_CROCO_ID, from)
                        .putExtra(EXTRA_CONNECTION_SOURCE_URI, sourceUri)
                        .putExtra(EXTRA_CONNECTION_STORAGE_DIR, storageDirectory),
                false
        );
    }

    public static void requestPrivateDownload(@NonNull Context context, long messageId, @NonNull String from, @NonNull String sourceUri) {
        requestDownloadAction(context, ACTION_DOWNLOAD, messageId, from, sourceUri, null);
    }

    public static void requestPrivateDownloadOnWifiOnly(@NonNull Context context, long messageId, @NonNull String from, @NonNull String sourceUri) {
        requestDownloadAction(context, ACTION_DOWNLOAD_ON_WIFI, messageId, from, sourceUri, null);
    }

    public static void requestPublicDownload(@NonNull Context context, long messageId, @NonNull String from, @NonNull String sourceUri, @NonNull String storageDirectory) {
        requestDownloadAction(context, ACTION_DOWNLOAD, messageId, from, sourceUri, storageDirectory);
    }

    public static void requestPublicDownloadOnWifiOnly(@NonNull Context context, long messageId, @NonNull String from, @NonNull String sourceUri, @NonNull String storageDirectory) {
        requestDownloadAction(context, ACTION_DOWNLOAD_ON_WIFI, messageId, from, sourceUri, storageDirectory);
    }

    public static void cancelPrivateDownload(@NonNull Context context, long messageId, @NonNull String from, @NonNull String sourceUri) {
        requestDownloadAction(context, ACTION_DOWNLOAD_CANCEL, messageId, from, sourceUri, null);
    }

    public static void cancelPublicDownload(@NonNull Context context, long messageId, @NonNull String from, @NonNull String sourceUri, @NonNull String storageDirectory) {
        requestDownloadAction(context, ACTION_DOWNLOAD_CANCEL, messageId, from, sourceUri, storageDirectory);
    }

    private static void requestUploadAction(@NonNull Context context, @UploadAction String action, long messageId, @NonNull String to, @NonNull String sourceUri, @Nullable String storageDirectory) {
        CptServiceStarter.startIntentService(context,
                getCptIntent(context)
                        .setAction(action)
                        .putExtra(EXTRA_CONNECTION_MESSAGE_ID, messageId)
                        .putExtra(EXTRA_UPLOAD_TARGET_CROCO_ID, to)
                        .putExtra(EXTRA_CONNECTION_SOURCE_URI, sourceUri)
                        .putExtra(EXTRA_CONNECTION_STORAGE_DIR, storageDirectory),
                false
        );
    }

    public static void requestPrivateUpload(@NonNull Context context, long messageId, @NonNull String to, @NonNull String sourceUri) {
        requestUploadAction(context, ACTION_UPLOAD, messageId, to, sourceUri, null);
    }

    public static void requestPrivateUploadOnWifiOnly(@NonNull Context context, long messageId, @NonNull String to, @NonNull String sourceUri) {
        requestUploadAction(context, ACTION_UPLOAD_ON_WIFI, messageId, to, sourceUri, null);
    }

    public static void requestPublicUpload(@NonNull Context context, long messageId, @NonNull String to, @NonNull String sourceUri, @NonNull String storageDirectory) {
        requestUploadAction(context, ACTION_UPLOAD, messageId, to, sourceUri, storageDirectory);
    }

    public static void requestPublicUploadOnWifiOnly(@NonNull Context context, long messageId, String to, @NonNull String sourceUri, @NonNull String storageDirectory) {
        requestUploadAction(context, ACTION_UPLOAD_ON_WIFI, messageId, to, sourceUri, storageDirectory);
    }

    public static void cancelPrivateUpload(@NonNull Context context, long messageId, @NonNull String to, @NonNull String sourceUri) {
        requestUploadAction(context, ACTION_UPLOAD_CANCEL, messageId, to, sourceUri, null);
    }

    public static void cancelPublicUpload(@NonNull Context context, long messageId, @NonNull String to, @NonNull String sourceUri, @NonNull String storageDirectory) {
        requestUploadAction(context, ACTION_UPLOAD_CANCEL, messageId, to, sourceUri, storageDirectory);
    }

    public static void sendCptLogs(@NonNull Context context) {
        CptServiceStarter.startIntentService(context,
                getCptIntent(context).setAction(ACTION_SEND_CPT_LOGS),
                false
        );
    }

    public static void addP2pDnsSdRecord(@NonNull Context context, String instanceName, Bundle txtMap) {
        CptServiceStarter.startIntentService(context,
                getCptIntent(context)
                        .setAction(ACTION_ADD_P2P_DNS_SD)
                        .putExtra(EXTRA_P2P_DNS_SD_INSTANCE_NAME, instanceName)
                        .putExtra(EXTRA_P2P_DNS_SD_INSTANCE_RECORD, txtMap),
                false
        );
    }
    public static void removeP2pDnsSdRecord(@NonNull Context context, String instanceName) {
        CptServiceStarter.startIntentService(context,
                getCptIntent(context)
                        .setAction(ACTION_REMOVE_P2P_DNS_SD)
                        .putExtra(EXTRA_P2P_DNS_SD_INSTANCE_NAME, instanceName),
                false
        );
    }

    private static Intent getCptIntent(Context context) {
        return new Intent(context, DataLayerIntentService.class);
    }

    /*
     * CPT -> client
     */
    public static final String ACTION_OPEN_CPT_SETTINGS = "com.croconaut.cpt.link.action.settings";
    public static final String ACTION_REQUEST_CPT_MODE  = "com.croconaut.cpt.link.action.request_mode";
    public static final String EXTRA_REQUEST_CPT_MODE  = "mode";

    public static final String ACTION_MESSAGE_SENT = "com.croconaut.cpt.data.action.MESSAGE_SENT";
    public static final String EXTRA_MESSAGE_SENT = "to";   // MESSAGE_SENT_TO_* constant
    @IntDef({MESSAGE_SENT_TO_RECIPIENT, MESSAGE_SENT_TO_OTHER_DEVICE, MESSAGE_SENT_TO_INTERNET})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SentTo {}
    public static final int MESSAGE_SENT_TO_RECIPIENT    = 0;
    public static final int MESSAGE_SENT_TO_OTHER_DEVICE = 1;
    public static final int MESSAGE_SENT_TO_INTERNET     = 2;

    public static final String ACTION_MESSAGE_DELETED = "com.croconaut.cpt.data.action.MESSAGE_DELETED";

    public static final String ACTION_MESSAGE_ACKED = "com.croconaut.cpt.data.action.MESSAGE_ACKED";
    public static final String EXTRA_MESSAGE_ACKED = "hops";   // hops of the route to the recipient

    public static final String ACTION_MESSAGE_ARRIVED = "com.croconaut.cpt.data.action.MESSAGE_ARRIVED";
    public static final String EXTRA_MESSAGE_ARRIVED = "message_incoming";

    public static final String ACTION_MESSAGE_ATTACHMENT_NOTIFICATION       = "com.croconaut.cpt.data.action.MESSAGE_ATTACHMENT_NOTIFICATION";
    // EXTRA_MESSAGE_ID, EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI, EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR
    // EXTRA_MESSAGE_ATTACHMENT_SOURCE_CROCO_ID (null if attachment to upload)
    // EXTRA_MESSAGE_ATTACHMENT_TARGET_CROCO_ID (null if attachment to download)

    @StringDef({ACTION_MESSAGE_ATTACHMENT_UPLOAD_CONFIRMED, ACTION_MESSAGE_ATTACHMENT_UPLOAD_CANCELLED, ACTION_MESSAGE_ATTACHMENT_UPLOADING_TO_RECIPIENT, ACTION_MESSAGE_ATTACHMENT_UPLOADING_TO_APP_SERVER, ACTION_MESSAGE_ATTACHMENT_UPLOADED_TO_RECIPIENT, ACTION_MESSAGE_ATTACHMENT_UPLOADED_TO_APP_SERVER, ACTION_MESSAGE_ATTACHMENT_DELIVERED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface AttachmentUploadAction {}
    public static final String ACTION_MESSAGE_ATTACHMENT_UPLOAD_CONFIRMED        = "com.croconaut.cpt.data.action.MESSAGE_ATTACHMENT_UPLOAD_CONFIRMED";
    public static final String ACTION_MESSAGE_ATTACHMENT_UPLOAD_CANCELLED        = "com.croconaut.cpt.data.action.MESSAGE_ATTACHMENT_UPLOAD_CANCELLED";
    public static final String ACTION_MESSAGE_ATTACHMENT_UPLOADING_TO_RECIPIENT  = "com.croconaut.cpt.data.action.MESSAGE_ATTACHMENT_UPLOADING_TO_RECIPIENT";
    public static final String ACTION_MESSAGE_ATTACHMENT_UPLOADING_TO_APP_SERVER = "com.croconaut.cpt.data.action.MESSAGE_ATTACHMENT_UPLOADING_TO_APP_SERVER";
    public static final String ACTION_MESSAGE_ATTACHMENT_UPLOADED_TO_RECIPIENT   = "com.croconaut.cpt.data.action.MESSAGE_ATTACHMENT_UPLOADED_TO_RECIPIENT";
    public static final String ACTION_MESSAGE_ATTACHMENT_UPLOADED_TO_APP_SERVER  = "com.croconaut.cpt.data.action.MESSAGE_ATTACHMENT_UPLOADED_TO_APP_SERVER";
    public static final String ACTION_MESSAGE_ATTACHMENT_DELIVERED               = "com.croconaut.cpt.data.action.MESSAGE_ATTACHMENT_DELIVERED";
    // EXTRA_MESSAGE_ID, EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI, EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR
    // EXTRA_MESSAGE_ATTACHMENT_TARGET_CROCO_ID
    // EXTRA_MESSAGE_ATTACHMENT_TIME (for ACTION_MESSAGE_ATTACHMENT_UPLOADED_TO_*, ACTION_MESSAGE_ATTACHMENT_DELIVERED)
    // EXTRA_MESSAGE_ATTACHMENT_SPEED (for ACTION_MESSAGE_ATTACHMENT_UPLOADED_TO_*)

    @StringDef({ACTION_MESSAGE_ATTACHMENT_DOWNLOAD_CONFIRMED, ACTION_MESSAGE_ATTACHMENT_DOWNLOAD_CANCELLED, ACTION_MESSAGE_ATTACHMENT_DOWNLOADING, ACTION_MESSAGE_ATTACHMENT_DOWNLOADED, ACTION_MESSAGE_ATTACHMENT_REQUEST_EXPIRED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface AttachmentDownloadAction {}
    public static final String ACTION_MESSAGE_ATTACHMENT_DOWNLOAD_CONFIRMED = "com.croconaut.cpt.data.action.MESSAGE_ATTACHMENT_DOWNLOAD_CONFIRMED";
    public static final String ACTION_MESSAGE_ATTACHMENT_DOWNLOAD_CANCELLED = "com.croconaut.cpt.data.action.MESSAGE_ATTACHMENT_DOWNLOAD_CANCELLED";
    public static final String ACTION_MESSAGE_ATTACHMENT_DOWNLOADING        = "com.croconaut.cpt.data.action.MESSAGE_ATTACHMENT_DOWNLOADING";
    public static final String ACTION_MESSAGE_ATTACHMENT_DOWNLOADED         = "com.croconaut.cpt.data.action.MESSAGE_ATTACHMENT_DOWNLOADED";
    public static final String ACTION_MESSAGE_ATTACHMENT_REQUEST_EXPIRED    = "com.croconaut.cpt.data.action.MESSAGE_ATTACHMENT_REQUEST_EXPIRED";
    // EXTRA_MESSAGE_ID, EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI, EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR
    // EXTRA_MESSAGE_ATTACHMENT_SOURCE_CROCO_ID
    // EXTRA_MESSAGE_ATTACHMENT_TIME (for ACTION_MESSAGE_ATTACHMENT_DOWNLOADED)
    // EXTRA_MESSAGE_ATTACHMENT (for ACTION_MESSAGE_ATTACHMENT_{DOWNLOADING,DOWNLOADED})
    // EXTRA_MESSAGE_ATTACHMENT_SPEED (for ACTION_MESSAGE_ATTACHMENT_DOWNLOADED)

    public static final String ACTION_SEND_CPT_LOGS = "com.croconaut.cpt.data.action.SEND_CPT_LOGS";

    public static final String ACTION_SUPPLY_P2P_DNS_SD_RECORDS = "com.croconaut.cpt.data.action.SUPPLY_P2PDNSSD_RECORDS";
    public static final String ACTION_P2P_DNS_SD_RECORD_AVAILABLE = "com.croconaut.cpt.data.action.P2P_DNS_SD_RECORD_AVAILABLE";

    public static final String EXTRA_MESSAGE_ID = "id";
    public static final String EXTRA_MESSAGE_TIME = "time";

    public static final String EXTRA_MESSAGE_ATTACHMENT_SOURCE_CROCO_ID = "from";
    public static final String EXTRA_MESSAGE_ATTACHMENT_TARGET_CROCO_ID = "to";
    public static final String EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI = "source_uri";
    public static final String EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR = "storage_dir";
    public static final String EXTRA_MESSAGE_ATTACHMENT = "attachment";
    public static final String EXTRA_MESSAGE_ATTACHMENT_TIME = "time";
    public static final String EXTRA_MESSAGE_ATTACHMENT_SPEED = "speed";

    public static final String ACTION_NEARBY_ARRIVED = "com.croconaut.cpt.data.action.NEARBY_ARRIVED";
    public static final String EXTRA_NEARBY_ARRIVED = "users";
}
