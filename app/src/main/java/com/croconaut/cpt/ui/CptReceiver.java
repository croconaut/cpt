package com.croconaut.cpt.ui;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.croconaut.cpt.R;
import com.croconaut.cpt.data.Communication;
import com.croconaut.cpt.data.IncomingMessage;
import com.croconaut.cpt.data.MessageAttachment;
import com.croconaut.cpt.data.NearbyUser;
import com.croconaut.cpt.link.PreferenceHelper;
import com.croconaut.cpt.network.NetworkHop;
import com.croconaut.cpt.provider.Contract;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public abstract class CptReceiver extends BroadcastReceiver {
    private static final String TAG = CptReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case Communication.ACTION_OPEN_CPT_SETTINGS: {
                    onCptNotificationTapped(context);
                    break;
                }

                case Communication.ACTION_MESSAGE_SENT: {
                    int sentTo = intent.getIntExtra(Communication.EXTRA_MESSAGE_SENT, -1);
                    long messageId = intent.getLongExtra(Communication.EXTRA_MESSAGE_ID, -1);
                    Date sentTime = (Date) intent.getSerializableExtra(Communication.EXTRA_MESSAGE_TIME);
                    if (sentTo != -1 && messageId != -1 && sentTime != null) {
                        switch (sentTo) {
                            case Communication.MESSAGE_SENT_TO_RECIPIENT:
                                onMessageSentToRecipient(context, messageId, sentTime);
                                break;
                            case Communication.MESSAGE_SENT_TO_OTHER_DEVICE:
                                onMessageSentToOtherDevice(context, messageId, sentTime);
                                break;
                            case Communication.MESSAGE_SENT_TO_INTERNET:
                                onMessageSentToAppServer(context, messageId, sentTime);
                                break;
                        }
                    }
                    break;
                }

                case Communication.ACTION_MESSAGE_ACKED: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_MESSAGE_ID, -1);
                    Date deliveredTime = (Date) intent.getSerializableExtra(Communication.EXTRA_MESSAGE_TIME);
                    ArrayList<NetworkHop> hops = intent.getParcelableArrayListExtra(Communication.EXTRA_MESSAGE_ACKED);
                    if (messageId != -1 && deliveredTime != null) {
                        onMessageAcked(context, messageId, deliveredTime, hops);
                    }
                    break;
                }

                case Communication.ACTION_MESSAGE_DELETED: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_MESSAGE_ID, -1);
                    if (messageId != -1) {
                        onMessageDeleted(context, messageId);
                    }
                    break;
                }

                case Communication.ACTION_MESSAGE_ARRIVED: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_MESSAGE_ID, -1);
                    Date receivedTime = (Date) intent.getSerializableExtra(Communication.EXTRA_MESSAGE_TIME);
                    IncomingMessage incomingMessage = intent.getParcelableExtra(Communication.EXTRA_MESSAGE_ARRIVED);
                    if (messageId != -1 && receivedTime != null) {
                        onNewMessage(context, messageId, receivedTime, incomingMessage);
                    }
                    break;
                }

                case Communication.ACTION_NEARBY_ARRIVED: {
                    ArrayList<NearbyUser> nearbyUsers = intent.getParcelableArrayListExtra(Communication.EXTRA_NEARBY_ARRIVED);
                    onNearbyPeers(context, nearbyUsers);
                    break;
                }

                case Communication.ACTION_MESSAGE_ATTACHMENT_NOTIFICATION: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_MESSAGE_ID, -1);
                    String from = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_CROCO_ID);
                    String to = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_TARGET_CROCO_ID);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR);
                    if (messageId != -1 && sourceUri != null) {
                        if (from == null && to != null) {
                            // upload
                            onUploadNotificationTapped(context, messageId, sourceUri, storageDirectory, to);
                        } else if (from != null && to == null) {
                            // download
                            onDownloadNotificationTapped(context, messageId, sourceUri, storageDirectory, from);
                        }
                    }
                    break;
                }

                case Communication.ACTION_SEND_CPT_LOGS: {
                    onSendLogs(context);
                    break;
                }

                case Communication.ACTION_MESSAGE_ATTACHMENT_UPLOAD_CONFIRMED: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_MESSAGE_ID, -1);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR);
                    String to = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_TARGET_CROCO_ID);
                    if (messageId != -1 && sourceUri != null && to != null) {
                        onMessageAttachmentUploadConfirmed(context, messageId, sourceUri, storageDirectory, to);
                    }
                    break;
                }
                case Communication.ACTION_MESSAGE_ATTACHMENT_UPLOAD_CANCELLED: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_MESSAGE_ID, -1);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR);
                    String to = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_TARGET_CROCO_ID);
                    if (messageId != -1 && sourceUri != null && to != null) {
                        onMessageAttachmentUploadCancelled(context, messageId, sourceUri, storageDirectory, to);
                    }
                    break;
                }
                case Communication.ACTION_MESSAGE_ATTACHMENT_UPLOADING_TO_RECIPIENT: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_MESSAGE_ID, -1);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR);
                    String to = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_TARGET_CROCO_ID);
                    if (messageId != -1 && sourceUri != null && to != null) {
                        onMessageAttachmentUploadingToRecipient(context, messageId, sourceUri, storageDirectory, to);
                    }
                    break;
                }
                case Communication.ACTION_MESSAGE_ATTACHMENT_UPLOADING_TO_APP_SERVER: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_MESSAGE_ID, -1);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR);
                    String to = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_TARGET_CROCO_ID);
                    if (messageId != -1 && sourceUri != null && to != null) {
                        onMessageAttachmentUploadingToAppServer(context, messageId, sourceUri, storageDirectory, to);
                    }
                    break;
                }
                case Communication.ACTION_MESSAGE_ATTACHMENT_UPLOADED_TO_RECIPIENT: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_MESSAGE_ID, -1);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR);
                    String to = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_TARGET_CROCO_ID);
                    Date uploadedTime = (Date) intent.getSerializableExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_TIME);
                    int uploadedBytesPerSecond = intent.getIntExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SPEED, -1);
                    if (messageId != -1 && sourceUri != null && to != null && uploadedTime != null && uploadedBytesPerSecond != -1) {
                        onMessageAttachmentUploadedToRecipient(context, messageId, sourceUri, storageDirectory, to, uploadedTime, uploadedBytesPerSecond);
                    }
                    break;
                }
                case Communication.ACTION_MESSAGE_ATTACHMENT_UPLOADED_TO_APP_SERVER: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_MESSAGE_ID, -1);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR);
                    String to = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_TARGET_CROCO_ID);
                    Date uploadedTime = (Date) intent.getSerializableExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_TIME);
                    int uploadedBytesPerSecond = intent.getIntExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SPEED, -1);
                    if (messageId != -1 && sourceUri != null && to != null && uploadedTime != null && uploadedBytesPerSecond != -1) {
                        onMessageAttachmentUploadedToAppServer(context, messageId, sourceUri, storageDirectory, to, uploadedTime, uploadedBytesPerSecond);
                    }
                    break;
                }
                case Communication.ACTION_MESSAGE_ATTACHMENT_DELIVERED: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_MESSAGE_ID, -1);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR);
                    Date deliveredTime = (Date) intent.getSerializableExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_TIME);
                    String to = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_TARGET_CROCO_ID);
                    if (messageId != -1 && sourceUri != null && to != null && deliveredTime != null) {
                        onMessageAttachmentDelivered(context, messageId, sourceUri, to, storageDirectory, deliveredTime);
                    }
                    break;
                }

                case Communication.ACTION_MESSAGE_ATTACHMENT_DOWNLOAD_CONFIRMED: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_MESSAGE_ID, -1);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR);
                    String from = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_CROCO_ID);
                    if (messageId != -1 && sourceUri != null && from != null) {
                        onMessageAttachmentDownloadConfirmed(context, messageId, sourceUri, storageDirectory, from);
                    }
                    break;
                }
                case Communication.ACTION_MESSAGE_ATTACHMENT_DOWNLOAD_CANCELLED: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_MESSAGE_ID, -1);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR);
                    String from = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_CROCO_ID);
                    if (messageId != -1 && sourceUri != null && from != null) {
                        onMessageAttachmentDownloadCancelled(context, messageId, sourceUri, storageDirectory, from);
                    }
                    break;
                }
                case Communication.ACTION_MESSAGE_ATTACHMENT_REQUEST_EXPIRED: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_MESSAGE_ID, -1);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR);
                    String from = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_CROCO_ID);
                    if (messageId != -1 && sourceUri != null && from != null) {
                        onMessageAttachmentDownloadExpired(context, messageId, sourceUri, storageDirectory, from);
                    }
                    break;
                }
                case Communication.ACTION_MESSAGE_ATTACHMENT_DOWNLOADING: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_MESSAGE_ID, -1);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR);
                    String from = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_CROCO_ID);
                    MessageAttachment messageAttachment = intent.getParcelableExtra(Communication.EXTRA_MESSAGE_ATTACHMENT);
                    if (messageId != -1 && sourceUri != null && from != null) {
                        onMessageAttachmentDownloading(context, messageId, sourceUri, storageDirectory, from, messageAttachment);
                    }
                    break;
                }
                case Communication.ACTION_MESSAGE_ATTACHMENT_DOWNLOADED: {
                    long messageId = intent.getLongExtra(Communication.EXTRA_MESSAGE_ID, -1);
                    String sourceUri = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_URI);
                    String storageDirectory = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_STORAGE_DIR);
                    String from = intent.getStringExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SOURCE_CROCO_ID);
                    MessageAttachment messageAttachment = intent.getParcelableExtra(Communication.EXTRA_MESSAGE_ATTACHMENT);
                    Date downloadedTime = (Date) intent.getSerializableExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_TIME);
                    int downloadedBytesPerSecond = intent.getIntExtra(Communication.EXTRA_MESSAGE_ATTACHMENT_SPEED, -1);
                    if (messageId != -1 && sourceUri != null && from != null && downloadedTime != null && downloadedBytesPerSecond != -1) {
                        onMessageAttachmentDownloaded(context, messageId, sourceUri, storageDirectory, from, messageAttachment, downloadedTime, downloadedBytesPerSecond);
                    }
                    break;
                }

                case Communication.ACTION_SUPPLY_P2P_DNS_SD_RECORDS: {
                    onSupplyWifiP2pDnsSdTxtRecords(context);
                    break;
                }

                case Communication.ACTION_P2P_DNS_SD_RECORD_AVAILABLE: {
                    String instanceName = intent.getStringExtra(Communication.EXTRA_P2P_DNS_SD_INSTANCE_NAME);
                    Bundle record = intent.getBundleExtra(Communication.EXTRA_P2P_DNS_SD_INSTANCE_RECORD);
                    onWifiP2pDnsSdTxtRecordAvailable(context, instanceName, record);
                    break;
                }
            }
        }
    }

    // Download related listeners

    protected abstract void onMessageAttachmentDownloadConfirmed(Context context, long messageId, String sourceUri, String storageDirectory, String from);
    protected abstract void onMessageAttachmentDownloadCancelled(Context context, long messageId, String sourceUri, String storageDirectory, String from);
    protected abstract void onMessageAttachmentDownloading(Context context, long messageId, String sourceUri, String storageDirectory, String from, MessageAttachment messageAttachment);
    protected abstract void onMessageAttachmentDownloaded(Context context, long messageId, String sourceUri, String storageDirectory, String from, MessageAttachment messageAttachment, Date downloadedTime, int downloadedBytesPerSecond);
    protected abstract void onMessageAttachmentDownloadExpired(Context context, long messageId, String sourceUri, String storageDirectory, String from);

    // Upload related listeners

    protected abstract void onMessageAttachmentUploadConfirmed(Context context, long messageId, String sourceUri, String storageDirectory, String to);
    protected abstract void onMessageAttachmentUploadCancelled(Context context, long messageId, String sourceUri, String storageDirectory, String to);
    protected abstract void onMessageAttachmentUploadingToRecipient(Context context, long messageId, String sourceUri, String storageDirectory, String to);
    protected abstract void onMessageAttachmentUploadingToAppServer(Context context, long messageId, String sourceUri, String storageDirectory, String to);
    protected abstract void onMessageAttachmentUploadedToRecipient(Context context, long messageId, String sourceUri, String storageDirectory, String to, Date uploadedTime, int uploadedBytesPerSecond);
    protected abstract void onMessageAttachmentUploadedToAppServer(Context context, long messageId, String sourceUri, String storageDirectory, String to, Date uploadedTime, int uploadedBytesPerSecond);
    protected abstract void onMessageAttachmentDelivered(Context context, long messageId, String sourceUri, String storageDirectory, String to, Date deliveredTime);

    // Message related listeners

    protected abstract void onNewMessage(Context context, long messageId, Date receivedTime, IncomingMessage incomingMessage);
    protected abstract void onMessageSentToRecipient(Context context, long messageId, Date sentTime);
    protected abstract void onMessageSentToAppServer(Context context, long messageId, Date sentTime);
    protected abstract void onMessageSentToOtherDevice(Context context, long messageId, Date sentTime);
    protected abstract void onMessageAcked(Context context, long messageId, Date deliveredTime, ArrayList<NetworkHop> hops);
    protected abstract void onMessageDeleted(Context context, long messageId);

    // Other listeners

    protected abstract void onNearbyPeers(Context context, ArrayList<NearbyUser> nearbyUsers);
    protected abstract void onCptNotificationTapped(Context context);
    protected abstract void onDownloadNotificationTapped(Context context, long messageId, String sourceUri, String storageDirectory, String from);
    protected abstract void onUploadNotificationTapped(Context context, long messageId, String sourceUri, String storageDirectory, String to);
    protected void onSupplyWifiP2pDnsSdTxtRecords(Context context) {
    }
    protected void onWifiP2pDnsSdTxtRecordAvailable(Context context, String instanceName, Bundle record) {
    }

    protected void onSendLogs(Context context) {
        Process adbProcess = null;
        try {
            adbProcess = new ProcessBuilder()
                    .command("logcat", "-s", "-d", "-v", "time",
                            "gcm",
                            "data",
                            "link.bootstrap", "link", "link.notify", "link.connect", "link.group", "link.service",
                            "network", "network.gcm", "network.client", "network.server",
                            "network.gcm.down.att", "network.gcm.up.att", "network.gcm.up.token",
                            "network.gcm.up.msg", "network.gcm.down.msg", "network.gcm.up.friends", "network.gcm.up.nearby")  // TODO: add any new TAG here
                    .redirectErrorStream(true)
                    .start()
            ;

            PreferenceHelper helper = new PreferenceHelper(context);
            String username = helper.getUsername() != null ? helper.getUsername() : helper.getCrocoId();

            Uri logsUri = Uri.withAppendedPath(Contract.getCacheDirUri(context), "logs/" + (username != null ? username : "Mobil Unknown") + "_log.txt");
            logsUri = context.getContentResolver().insert(logsUri, new ContentValues());
            if (logsUri == null) {
                Log.e(TAG, "logsUri == null");
                return;
            }
            OutputStream logsOutputStream = context.getContentResolver().openOutputStream(logsUri);
            if (logsOutputStream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(adbProcess.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(logsOutputStream));
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    writer.write(line);
                    writer.newLine();
                }
                writer.close();
                reader.close();
                Log.v(TAG, "Logs written to " + logsUri.getPath());
            } else {
                Log.e(TAG, "logsOutputStream == null for " + logsUri.getPath());
                return;
            }

            Intent intent = new Intent(Intent.ACTION_SEND)
                    .setType("message/rfc822")
                    .putExtra(Intent.EXTRA_EMAIL, new String[]{context.getResources().getString(R.string.cpt_logs_email_address)})
                    .putExtra(Intent.EXTRA_SUBJECT, helper.getCrocoId() + " (" + helper.getUsername() + ")")
                    .putExtra(Intent.EXTRA_TEXT, context.getResources().getString(R.string.cpt_logs_email_subject, Calendar.getInstance().getTime()))
                    .putExtra(Intent.EXTRA_STREAM, logsUri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newUri(context.getContentResolver(), null, logsUri));   // done automatically by ACTION_SEND but wont hurt
            context.startActivity(Intent.createChooser(intent, context.getResources().getString(R.string.cpt_logs_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (IOException e) {
            Log.e(TAG, "exception", e);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, context.getResources().getString(R.string.cpt_logs_no_email_app), Toast.LENGTH_SHORT).show();
        } finally {
            if (adbProcess != null) {
                adbProcess.destroy();
            }
        }
    }
}
