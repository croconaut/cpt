package com.croconaut.cpt.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.croconaut.cpt.link.PreferenceHelper;
import com.croconaut.cpt.link.Settings;
import com.croconaut.cpt.link.handler.main.NewMessage;
import com.croconaut.cpt.link.handler.p2p.UpdatedHash;
import com.croconaut.cpt.network.NetworkAttachmentPreview;
import com.croconaut.cpt.network.NetworkHeader;
import com.croconaut.cpt.network.NetworkHop;
import com.croconaut.cpt.network.NetworkMessage;
import com.croconaut.cpt.network.NetworkPersistentAttachment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.CRC32;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

@SuppressWarnings({"ConstantConditions", "StringBufferReplaceableByString"})
public class DatabaseManager {
    private static final String TAG = "data";
    
    private static double mLatitude;
    private static double mLongitude;
    private static Date mLocationTime;

    public enum ProcessMessageResult {
        OK,
        OK_TURNED_INTO_ACK,
        NOT_FOR_US,
        ERROR
    }

    public static ProcessMessageResult processRemoteMessage(SQLiteDatabase database, Context context, NetworkMessage message, Date deliveredTime, String myCrocoId, boolean isFromServer) {
        Log.v(TAG, TAG + ".processRemoteMessage");

        boolean isForUs = message.header.getTo().equals(myCrocoId) || message.header.getFrom().equals(myCrocoId) || message.header.getTo().equals(MessageIdentifier.BROADCAST_ID);
        if (isForUs) {
            isForUs = isPackageInstalled(context, message.header.getAppId());
        }
        boolean turnIntoAck = isForUs && message.header.getType() == NetworkHeader.Type.NORMAL && message.isExpectingAck();

        // it's imperative to understand that we may get *anything* as input, even though the queries are sane -- imagine a NORMAL which is sent to two
        // devices (the recipient and another one), then we delete all our messages and then we create a server for both devices in the same time:
        // the recipient would send us ACK while the other device would send us NORMAL so we always have to check before insert/replace!

        String   table;
        ContentValues values = new ContentValues();
        values.put(SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID, message.header.getFrom());
        values.put(SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID, message.header.getTo());
        values.put(SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID, message.header.getAppId());
        values.put(SQLiteCptHelper.COLUMN_NAME_TIME_CREATED, message.header.getCreationTime());

        if (isFromServer && !turnIntoAck) {
            // no matter what it is, we definitely want to mark it as 'sent to the app server'
            values.put(SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_APP_SERVER, 1);
        }

        if (message.header.isPersistent()) {
            table = SQLiteCptHelper.TABLE_NAME_CPT_PERSISTENT_MESSAGE;
            values.put(SQLiteCptHelper.COLUMN_NAME_CONTENT, message.getAppPayload());
            // we need them in the db, too
            List<LocalDbAttachment> localDbAttachments = new ArrayList<>();
            for (MessageAttachment attachment : message.getAttachments()) {
                NetworkPersistentAttachment persistentAttachment = (NetworkPersistentAttachment) attachment;
                localDbAttachments.add(
                        new LocalDbAttachment(
                                persistentAttachment.getUri(),
                                persistentAttachment.getStorageDirectory(), // null
                                persistentAttachment.getName(context),
                                persistentAttachment.getLength(context)
                        )
                );
            }
            values.put(SQLiteCptHelper.COLUMN_NAME_PERSISTENT_ATTACHMENTS, streamablesToByteArray(context, localDbAttachments));
            values.put(SQLiteCptHelper.COLUMN_NAME_PERSISTENT_ID, message.header.getPersistentId());
        } else {
            table = SQLiteCptHelper.TABLE_NAME_CPT_MESSAGE;
            values.put(SQLiteCptHelper.COLUMN_NAME_TIME_VALIDITY_TO, message.getExpirationTime());
            values.put(SQLiteCptHelper.COLUMN_NAME_FLAG_LOCAL_ONLY, message.isLocal() ? 1 : 0);

            if (message.header.getType() == NetworkHeader.Type.ACK) {
                values.put(SQLiteCptHelper.COLUMN_NAME_TYPE, message.header.getType().getValue());
                values.put(SQLiteCptHelper.COLUMN_NAME_TIME_DELIVERED, message.getDeliveredTime().getTime());

                if (message.header.getFrom().equals(myCrocoId)) {
                    // avoid of using it ever again for transmission
                    values.put(SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_RECIPIENT, 1);
                    values.put(SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_OTHER_DEVICE, 1);
                    values.put(SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_APP_SERVER, 1);
                }
            } else if (turnIntoAck) {
                values.put(SQLiteCptHelper.COLUMN_NAME_TYPE, NetworkHeader.Type.ACK.getValue());
                values.put(SQLiteCptHelper.COLUMN_NAME_TIME_DELIVERED, deliveredTime.getTime());
            } else {
                values.put(SQLiteCptHelper.COLUMN_NAME_TYPE, message.header.getType().getValue());
                values.put(SQLiteCptHelper.COLUMN_NAME_CONTENT, message.getAppPayload());
                values.put(SQLiteCptHelper.COLUMN_NAME_FLAG_EXPECTS_ACK, message.isExpectingAck() ? 1 : 0);

                if (message.header.getTo().equals(myCrocoId)) {
                    // avoid of using it ever again for transmission
                    values.put(SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_RECIPIENT, 1);
                    values.put(SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_OTHER_DEVICE, 1);
                    values.put(SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_APP_SERVER, 1);
                }
            }
            // 'expects sent' and the sent flags are set to false, in all cases
            // (the sent flags get updated in markMessagesAsSent(), even for foreign messages)
        }

        if (Settings.getInstance().allowTracking) {
            if (message.header.getType() == NetworkHeader.Type.NORMAL) {
                // only if the incoming message was NORMAL
                message.addHop(getNewNetworkHop(context));
            }
        }
        values.put(SQLiteCptHelper.COLUMN_NAME_HOPS, streamablesToByteArray(context, message.getHops()));  // ACK stores the original NORMAL hops


        long rowId = -1;
        try {
            rowId = database.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_ABORT);
        } catch (SQLiteConstraintException e) {
            Log.w(TAG, "Not inserting " + message.header);
            // if NORMAL or ACK violates UNIQUE (i.e. exactly this message is already in the DB)
            // if NORMAL violates PK (i.e. we have NORMAL and the message is ACK)
            // if PERSISTENT violates PK (i.e. we have either exactly this message or another with different time)
        } catch (SQLiteException e) {
            Log.e(TAG, "Error by inserting " + message.header, e);
        }

        if (rowId == -1) {
            // update doesn't set this automatically
            if (!values.containsKey(SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_RECIPIENT)) {
                values.put(SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_RECIPIENT, 0);
            }
            if (!values.containsKey(SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_OTHER_DEVICE)) {
                values.put(SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_OTHER_DEVICE, 0);
            }
            if (!values.containsKey(SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_APP_SERVER)) {
                values.put(SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_APP_SERVER, 0);
            }
            String whereClause = null;
            String[] whereArgs = null;

            if (message.header.isPersistent()) {
                whereClause = new StringBuilder()
                        .append(SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID).append(" = ?")
                        .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID).append(" = ?")
                        .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_PERSISTENT_ID).append(" = ?")
                        .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_TIME_CREATED).append(" < ?")
                        .toString();
                whereArgs = new String[] {
                        message.header.getFrom(),
                        message.header.getAppId(),
                        String.valueOf(message.header.getPersistentId()),
                        String.valueOf(message.header.getCreationTime())
                };
            } else if (turnIntoAck || message.header.getType() == NetworkHeader.Type.ACK) {
                // TODO: Context.getContentResolver().releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                // if the message had attachments
                values.putNull(SQLiteCptHelper.COLUMN_NAME_CONTENT);
                values.put(SQLiteCptHelper.COLUMN_NAME_FLAG_EXPECTS_SENT, 0);
                values.put(SQLiteCptHelper.COLUMN_NAME_FLAG_EXPECTS_ACK, 0);
                whereClause = new StringBuilder(SQLiteCptHelper.COLUMN_NAME_TYPE).append(" = ?")
                        .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID).append(" = ?")
                        .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID).append(" = ?")
                        .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID).append(" = ?")
                        .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_TIME_CREATED).append(" = ?")
                        .toString();
                whereArgs = new String[] {
                        String.valueOf(NetworkHeader.Type.NORMAL.getValue()),
                        message.header.getFrom(),
                        message.header.getTo(),
                        message.header.getAppId(),
                        String.valueOf(message.header.getCreationTime())
                };
            }

            if (whereClause != null && whereArgs != null) {
                Log.d(TAG, "Updating " + message.header);
                try {
                    rowId = database.update(table, values, whereClause, whereArgs) == 1 ? 1 : -1;   // we can't use getRowId() because the app server returns -1
                } catch (SQLiteConstraintException e) {
                    // the message has been received in another thread in the meantime
                    Log.w(TAG, "Not updating " + message.header);
                } catch (SQLiteException e) {
                    Log.e(TAG, "Error by updating " + message.header, e);
                }
            }
        }

        ProcessMessageResult result = ProcessMessageResult.ERROR;
        if (rowId != -1) {
            if (turnIntoAck) {
                result = ProcessMessageResult.OK_TURNED_INTO_ACK;
            } else if (isForUs) {
                result = ProcessMessageResult.OK;
            } else {
                result = ProcessMessageResult.NOT_FOR_US;
            }
        }
        return result;
    }

    public static void processClientMessage(Context context, String app, String from, OutgoingMessage clientMessage) {
        Log.v(TAG, TAG + ".processClientMessage: " + clientMessage.toString());

        SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
        SQLiteDatabase database = dbHelper.getWritableDatabase();

        String table;
        ContentValues values = new ContentValues();
        values.put(SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID, from);
        values.put(SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID, clientMessage.to);
        values.put(SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID, app);
        values.put(SQLiteCptHelper.COLUMN_NAME_TIME_CREATED, clientMessage.creationTime);
        values.put(SQLiteCptHelper.COLUMN_NAME_CONTENT, clientMessage.payload.getRawAppData());
        if (Settings.getInstance().allowTracking) {
            values.put(SQLiteCptHelper.COLUMN_NAME_HOPS, streamablesToByteArray(context, new ArrayList<>(Collections.singletonList(getNewNetworkHop(context)))));
        } else {
            values.put(SQLiteCptHelper.COLUMN_NAME_HOPS, streamablesToByteArray(context, new ArrayList<Streamable>()));
        }

        database.beginTransaction();    // one operation but provides a lock, too
        try {
            if (clientMessage.persistentId != -1) {
                table = SQLiteCptHelper.TABLE_NAME_CPT_PERSISTENT_MESSAGE;
                values.put(SQLiteCptHelper.COLUMN_NAME_PERSISTENT_ID, clientMessage.persistentId);
                // store attachments directly
                List<LocalDbAttachment> localDbAttachments = new ArrayList<>();
                for (MessageAttachment attachment : clientMessage.payload.getAttachments()) {
                    localDbAttachments.add(
                            new LocalDbAttachment(
                                    attachment.getUri(),
                                    attachment.getStorageDirectory()    // null
                            )
                    );
                }
                values.put(SQLiteCptHelper.COLUMN_NAME_PERSISTENT_ATTACHMENTS, streamablesToByteArray(context, localDbAttachments));
            } else {
                table = SQLiteCptHelper.TABLE_NAME_CPT_MESSAGE;
                values.put(SQLiteCptHelper.COLUMN_NAME_TYPE, NetworkHeader.Type.NORMAL.getValue());
                values.put(SQLiteCptHelper.COLUMN_NAME_TIME_VALIDITY_TO, clientMessage.expirationTime);
                values.put(SQLiteCptHelper.COLUMN_NAME_FLAG_LOCAL_ONLY, clientMessage.isLocal ? 1 : 0);
                values.put(SQLiteCptHelper.COLUMN_NAME_FLAG_EXPECTS_SENT, clientMessage.isExpectingSent ? 1 : 0);
                values.put(SQLiteCptHelper.COLUMN_NAME_FLAG_EXPECTS_ACK, clientMessage.isExpectingAck ? 1 : 0);
            }

            // replace because of persistent messages but makes no harm for normal ones
            long localId = database.replace(table, null, values);
            assertTrue(localId != -1);
            Log.d(TAG, "Storing new local message with id: " + localId);

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }

        if (clientMessage.persistentId == -1) {
            // "publish" the attachments after successful store
            for (MessageAttachment attachment : clientMessage.payload.getAttachments()) {
                LocalAttachment localAttachment = (LocalAttachment) attachment;
                addUploadUri(context,
                        new MessageAttachmentIdentifier(
                                new AttachmentIdentifier(
                                        localAttachment.getUri().toString(),
                                        attachment.getStorageDirectory()
                                ),
                                new MessageIdentifier(
                                        app,
                                        from,
                                        clientMessage.to,
                                        clientMessage.getId()
                                )
                        ),
                        localAttachment.isWifiOnly()
                );
            }
        }

        if (!clientMessage.isLocal) {
            setHashDirty(context);
        }
        new NewMessage().send(context, clientMessage.isLocal ? clientMessage.to : null);
    }

    private static void computeHash(CRC32 crc, SQLiteDatabase database, String table, String[] columns, String orderBy, boolean persistent) {
        String selection = persistent ? null : SQLiteCptHelper.COLUMN_NAME_FLAG_LOCAL_ONLY + " = 0";
        String[] selectionArgs = null;
        String groupBy = null;
        String having = null;

        Cursor cursor = null;
        try {
            cursor = database.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
            int rowsCount = cursor.getCount();
            if (rowsCount > 0 && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    NetworkHeader header = cursorToNetworkHeader(cursor, persistent);
                    cursor.moveToNext();

                    crc.update(header.getType().getValue()); // we count this also for persistent, no harm done
                    crc.update(header.getFrom().getBytes());
                    crc.update(header.getTo().getBytes());
                    crc.update(header.getAppId().getBytes());
                    crc.update(String.valueOf(header.getCreationTime()).getBytes());    // use string else we can't include a 64-bit value
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    // right now, this is ok to leave in the caller's thread: Network layer's threads are OK,
    // processClientMessage is also a separate thread, once-in-a-lifetime call is made from
    // LinkLayerService's main thread
    public static synchronized void setHashDirty(Context context) {
        Log.v(TAG, TAG + ".setHashDirty");

        SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        String hash;
        String orderBy;
        CRC32 crc = new CRC32();
        crc.update(0);  // for first time run

        orderBy = SQLiteCptHelper.COLUMN_NAME_TYPE + ", "
                + SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID + ", "
                + SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID + ", "
                + SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID + ", "
                + SQLiteCptHelper.COLUMN_NAME_TIME_CREATED + " ASC";    // critical for correct crc working
        computeHash(crc, database, SQLiteCptHelper.TABLE_NAME_CPT_MESSAGE, ALL_COLUMNS_OF_MSG_HEADER, orderBy, false);

        orderBy = SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID + ", "
                + SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID + ", "
                + SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID + ", "
                + SQLiteCptHelper.COLUMN_NAME_TIME_CREATED + " ASC";    // critical for correct crc working
        computeHash(crc, database, SQLiteCptHelper.TABLE_NAME_CPT_PERSISTENT_MESSAGE, ALL_COLUMNS_OF_PERSISTENT_MSG_HEADER, orderBy, true);

        hash = longToBase62String(crc.getValue());
        Log.i(TAG, "New hash: " + hash);

        new UpdatedHash().send(context, hash);
    }

    public static TreeSet<NetworkHeader> getLocalHeaders(Context context, String crocoId) {
        Log.v(TAG, TAG + ".getLocalHeaders");

        return getHeaders(context, crocoId, null, true);
    }

    public static TreeSet<NetworkHeader> getHeaders(Context context, String crocoId, String sourcePersistentCrocoId) {
        Log.v(TAG, TAG + ".getHeaders");

        return getHeaders(context, crocoId, sourcePersistentCrocoId, false);
    }

    private static TreeSet<NetworkHeader> getHeaders(Context context, String crocoId, String sourcePersistentCrocoId, boolean localMessages) {
        TreeSet<NetworkHeader> list = new TreeSet<>();

        SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        String table = SQLiteCptHelper.TABLE_NAME_CPT_MESSAGE;
        StringBuilder selection = new StringBuilder(SQLiteCptHelper.COLUMN_NAME_FLAG_LOCAL_ONLY).append(localMessages ? " = 1" : " = 0");
        String[] selectionArgs = null;
        if (crocoId != null) {
            // these headers only (use wisely, only for server communication; we have to include messages even from/to blocked croco ids)
            selection
                    .append(" AND (")
                        .append(SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID).append(" = ?")
                        .append(" OR ").append(SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID).append(" = ?")
                    .append(")");
            selectionArgs = new String[] { crocoId, crocoId };
        }
        String groupBy = null;
        String having = null;
        String orderBy = null;

        Cursor cursor = null;
        try {
            cursor = database.query(table, ALL_COLUMNS_OF_MSG_HEADER, selection.toString(), selectionArgs, groupBy, having, orderBy);
            int rowsCount = cursor.getCount();
            if (rowsCount > 0 && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    list.add(cursorToNetworkHeader(cursor, false));
                    cursor.moveToNext();
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }

        if (!localMessages) {
            selection = new StringBuilder();
            selectionArgs = null;
            if (sourcePersistentCrocoId != null) {
                selection
                        .append(SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID).append(" = ?");
                selectionArgs = new String[] { sourcePersistentCrocoId };
            }
            try {
                cursor = database.query(SQLiteCptHelper.TABLE_NAME_CPT_PERSISTENT_MESSAGE, ALL_COLUMNS_OF_PERSISTENT_MSG_HEADER, selection.toString(), selectionArgs, null, null, null);
                int rowsCount = cursor.getCount();
                if (rowsCount > 0 && cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        list.add(cursorToNetworkHeader(cursor, true));
                        cursor.moveToNext();
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        return list;
    }

    /**
     * Get all the headers the target side doesn't have but the source side does.
     * @param sourceHeaders source side headers.
     * @param targetHeaders target side headers.
     * @return headers the target side doesn't have but the source side does
     */
    public static List<NetworkHeader> getOpaqueHeaders(Collection<NetworkHeader> sourceHeaders, TreeSet<NetworkHeader> targetHeaders) {
        List<NetworkHeader> headersMissingOnTarget = new ArrayList<>();

        for (NetworkHeader sourceHeader : sourceHeaders) {
            if (!targetHeaders.contains(sourceHeader)) {
                if (sourceHeader.isPersistent()) {
                    NetworkHeader higher = targetHeaders.higher(sourceHeader);
                    // don't care about creationTime (it must be lower than sourceHeader's if the other fields are equal) and type/to (these can be overwritten if needed)
                    if (higher == null || !sourceHeader.getAppId().equals(higher.getAppId()) || !sourceHeader.getFrom().equals(higher.getFrom()) || sourceHeader.getPersistentId() != higher.getPersistentId()) {
                        // source header is definitely the latest/only possible candidate, add it
                        headersMissingOnTarget.add(sourceHeader);
                    }
                } else if (!targetHeaders.contains(sourceHeader.flipped()) || sourceHeader.getType() == NetworkHeader.Type.ACK) {
                    // target side either really doesn't have it or it has NORMAL and source side has ACK
                    headersMissingOnTarget.add(sourceHeader);
                }
            }
        }

        return headersMissingOnTarget;
    }

    /**
     * Get all messages specified by the headers argument.
     */
    // NOTE: in theory, we could send only headers, get back rowIds and send message bodies without header but
    //       1) we need to know whether it's a persistent rowId (so we'd need to send a structure anyway)
    //       2) we need the whole message because of the signature processing
    // TODO: send only headers which haven't been sent to the recipient/app server (saves bandwith; possible for local messages in p2p and all messages in app server connection)
    public static List<NetworkMessage> getMessages(Context context, List<NetworkHeader> headers) {
        Log.v(TAG, TAG + ".getMessages");

        List<NetworkMessage> messageList = new ArrayList<>();

        /*
         * Limits In SQLite:
         * Maximum length of a string or BLOB: 1,000,000,000
         * Maximum Length Of An SQL Statement: 1,000,000
         * Maximum Depth Of An Expression Tree: 1,000
         * Maximum Number Of Host Parameters In A Single SQL Statement: 999
         */
        if (!headers.isEmpty()) {
            SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
            SQLiteDatabase database = dbHelper.getReadableDatabase();

            StringBuilder selection = new StringBuilder(SQLiteCptHelper.COLUMN_NAME_ROWID + " IN (");
            StringBuilder selectionPersistent = new StringBuilder(selection);   // immutable
            for (NetworkHeader header : headers) {
                if (!header.isPersistent()) {
                    selection.append(header.getRowId()).append(",");
                } else {
                    selectionPersistent.append(header.getRowId()).append(",");
                }
            }
            if (selection.charAt(selection.length() - 1) == ',') {
                selection.deleteCharAt(selection.length() - 1);
            }
            selection.append(") AND (")
                    .append(SQLiteCptHelper.COLUMN_NAME_FLAG_LOCAL_ONLY).append(" = 0")
                    // select only local messages with at least one attachment
                    .append(" OR ").append("EXISTS (")
                        .append("SELECT 1 FROM ").append(SQLiteCptHelper.TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD)
                            .append(" WHERE ").append(MESSAGE_ATTACHMENTS_TO_UPLOAD_WHERE_CLAUSE)
                    .append(")")
            .append(")");
            if (selectionPersistent.charAt(selectionPersistent.length() - 1) == ',') {
                selectionPersistent.deleteCharAt(selectionPersistent.length() - 1);
            }
            selectionPersistent.append(')');
            String[] selectionArgs = null;
            String groupBy = null;
            String having = null;
            String orderBy = null;

            Cursor cursor = null;
            List<MessageIdentifier> messagesToDelete = new ArrayList<>();
            try {
                cursor = database.query(SQLiteCptHelper.TABLE_NAME_CPT_MESSAGE, ALL_COLUMNS_FOR_SELECT, selection.toString(), selectionArgs, groupBy, having, orderBy);
                int rowsCount = cursor.getCount();
                if (rowsCount > 0 && cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        NetworkMessage networkMessage = cursorToNetworkMessage(context, cursor);
                        List<NetworkAttachmentPreview> attachmentPreviews = getNetworkAttachmentPreviews(context, networkMessage.header.getIdentifier(), networkMessage.isLocal());
                        if (attachmentPreviews != null) {
                            networkMessage.setAttachments(attachmentPreviews);
                            messageList.add(networkMessage);
                        } else {
                            Log.e(TAG, "Some attachments not found: " + networkMessage.header);
                            messagesToDelete.add(networkMessage.header.getIdentifier());
                        }
                        cursor.moveToNext();
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }

            // super-slow but not often used code
            deleteMessages(context, messagesToDelete);

            messagesToDelete.clear();
            try {
                cursor = database.query(SQLiteCptHelper.TABLE_NAME_CPT_PERSISTENT_MESSAGE, ALL_COLUMNS_FOR_PERSISTENT_SELECT, selectionPersistent.toString(), selectionArgs, groupBy, having, orderBy);
                int rowsCount = cursor.getCount();
                if (rowsCount > 0 && cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        NetworkMessage networkMessage = cursorToPersistentNetworkMessage(context, cursor);
                        List<NetworkPersistentAttachment> attachments = getNetworkPersistentAttachments(context, cursor, networkMessage.header.getIdentifier());
                        if (attachments != null) {
                            networkMessage.setAttachments(attachments);
                            messageList.add(networkMessage);
                        } else {
                            Log.e(TAG, "Some attachments not found: " + networkMessage.header);
                            messagesToDelete.add(networkMessage.header.getIdentifier());
                        }
                        cursor.moveToNext();
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            // super-slow but not often used code
            deletePersistentMessages(context, messagesToDelete);
        }

        return messageList;
    }

    public static Set<String> getLocalMessagesRecipients(Context context) {
        Log.v(TAG, TAG + ".getLocalMessagesRecipients");

        Set<String> localRecipients = new HashSet<>();

        SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        String table;
        String[] columns;
        StringBuilder selection;
        String[] selectionArgs;
        String groupBy;
        String having = null;
        String orderBy = null;
        Cursor cursor = null;

        table = SQLiteCptHelper.TABLE_NAME_CPT_MESSAGE;
        columns = new String[] { SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID, SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID,
                SQLiteCptHelper.COLUMN_NAME_TYPE

        };
        selection = new StringBuilder(SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_RECIPIENT).append(" = 0")
                .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_FLAG_LOCAL_ONLY).append(" = 1");
        selectionArgs = null;
        groupBy = null;

        try {
            cursor = database.query(table, columns, selection.toString(), selectionArgs, groupBy, having, orderBy);
            int rowsCount = cursor.getCount();
            if (rowsCount > 0 && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    String from = cursor.getString(0);
                    String to = cursor.getString(1);
                    NetworkHeader.Type type = NetworkHeader.Type.fromValue(cursor.getInt(2));

                    if (type == NetworkHeader.Type.NORMAL) {
                        // target recipient
                        localRecipients.add(to);
                        Log.d(TAG, "Message recipient for NORMAL: " + to);
                    } else {
                        // it's a pending ack message for the sender
                        localRecipients.add(from);
                        Log.d(TAG, "Message recipient for ACK: " + from);
                    }
                    cursor.moveToNext();
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }

        // get list of all requested attachments
        table = SQLiteCptHelper.TABLE_NAME_CPT_ATTACHMENTS_TO_DOWNLOAD;
        columns = new String[] { SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID };
        selection = new StringBuilder(SQLiteCptHelper.COLUMN_NAME_DOWNLOAD_FLAG_RECEIVED).append(" = 0");
        selectionArgs = null;
        groupBy = SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID;

        try {
            cursor = database.query(table, columns, selection.toString(), selectionArgs, groupBy, having, orderBy);
            int rowsCount = cursor.getCount();
            if (rowsCount > 0 && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    String from = cursor.getString(0);

                    localRecipients.add(from);  // under all circumstances
                    Log.d(TAG, "Attachment recipient to download from: " + from);

                    cursor.moveToNext();
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }

        // get list of sent attachments which haven't been delivered due to a connection failure
        table = SQLiteCptHelper.TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD;
        columns = new String[] { SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID };
        selection = new StringBuilder(SQLiteCptHelper.COLUMN_NAME_UPLOAD_FLAG_DELIVERED).append(" = 0")
                .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_UPLOAD_FLAG_SENT_TO_RECIPIENT).append(" = 1");
        selectionArgs = null;
        groupBy = SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID;

        try {
            cursor = database.query(table, columns, selection.toString(), selectionArgs, groupBy, having, orderBy);
            int rowsCount = cursor.getCount();
            if (rowsCount > 0 && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    String to = cursor.getString(0);

                    // download actually, because this is where we receive the list of received attachments on the remote side
                    localRecipients.add(to);
                    Log.d(TAG, "Attachment recipient to upload: " + to);

                    cursor.moveToNext();
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }

        return localRecipients;
    }

    private static void markColumnAsSent(Context context, String table, String columnNameToUpdate, String rowIds) {
        ContentValues values = new ContentValues();
        values.put(columnNameToUpdate, 1);
        StringBuilder whereClause = new StringBuilder(columnNameToUpdate).append(" = 0")
                .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_ROWID).append(" IN (")
                .append(rowIds);
        whereClause.setCharAt(whereClause.length() - 1, ')');
        String[] whereArgs = null;

        int rows = updateTable(context, table, values, whereClause.toString(), whereArgs);
        Log.v(TAG, "Marked " + rows + " rows as sent where: " + whereClause);
    }

    public static void markMessagesAsSent(Context context, String sentTo, TreeSet<NetworkHeader> headers) {
        Log.v(TAG, TAG + ".markMessagesAsSent");

        // TODO: nice optimization would be to feed / check these headers for isSentXXX
        //       but that requires to have those flags in the header part and I'M NOT DOING IT AGAIN! :)

        StringBuilder   rowIdsToRecipient = new StringBuilder();
        StringBuilder rowIdsToOtherDevice = new StringBuilder();
        StringBuilder   rowIdsToAppServer = new StringBuilder();

        StringBuilder   persistentRowIdsToRecipient = new StringBuilder();
        StringBuilder persistentRowIdsToOtherDevice = new StringBuilder();
        StringBuilder   persistentRowIdsToAppServer = new StringBuilder();

        for (NetworkHeader header : headers) {
            String to = (header.getType() == NetworkHeader.Type.NORMAL) ? header.getTo() : header.getFrom();
            //Log.v(TAG, "sentTo=" + sentTo + ", from=" + header.getFrom() + ", " + "to=" + header.getTo() + ", type=" + header.getType() + ", pers=" + header.persistentId);

            if (!header.isPersistent()) {
                if (to.equals(MessageIdentifier.BROADCAST_ID)) {
                    if (sentTo == null) {
                        rowIdsToAppServer.append(header.getRowId()).append(",");
                    } // else ignore
                } else if (to.equals(sentTo)) {
                    rowIdsToRecipient.append(header.getRowId()).append(",");
                } else if (sentTo == null) {
                    rowIdsToAppServer.append(header.getRowId()).append(",");
                } else {
                    rowIdsToOtherDevice.append(header.getRowId()).append(",");
                }
            } else {
                if (to.equals(MessageIdentifier.BROADCAST_ID)) {
                    if (sentTo == null) {
                        persistentRowIdsToAppServer.append(header.getRowId()).append(",");
                    } // else ignore
                } else if (to.equals(sentTo)) {
                    persistentRowIdsToRecipient.append(header.getRowId()).append(",");
                } else if (sentTo == null) {
                    persistentRowIdsToAppServer.append(header.getRowId()).append(",");
                } else {
                    persistentRowIdsToOtherDevice.append(header.getRowId()).append(",");
                }
            }
        }

        if (rowIdsToRecipient.length() > 0) {
            markColumnAsSent(context, SQLiteCptHelper.TABLE_NAME_CPT_MESSAGE, SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_RECIPIENT, rowIdsToRecipient.toString());
        }
        if (rowIdsToOtherDevice.length() > 0) {
            markColumnAsSent(context, SQLiteCptHelper.TABLE_NAME_CPT_MESSAGE, SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_OTHER_DEVICE, rowIdsToOtherDevice.toString());
        }
        if (rowIdsToAppServer.length() > 0) {
            markColumnAsSent(context, SQLiteCptHelper.TABLE_NAME_CPT_MESSAGE, SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_APP_SERVER, rowIdsToAppServer.toString());
        }

        if (persistentRowIdsToRecipient.length() > 0) {
            markColumnAsSent(context, SQLiteCptHelper.TABLE_NAME_CPT_PERSISTENT_MESSAGE, SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_RECIPIENT, persistentRowIdsToRecipient.toString());
        }
        if (persistentRowIdsToOtherDevice.length() > 0) {
            markColumnAsSent(context, SQLiteCptHelper.TABLE_NAME_CPT_PERSISTENT_MESSAGE, SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_OTHER_DEVICE, persistentRowIdsToOtherDevice.toString());
        }
        if (persistentRowIdsToAppServer.length() > 0) {
            markColumnAsSent(context, SQLiteCptHelper.TABLE_NAME_CPT_PERSISTENT_MESSAGE, SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_APP_SERVER, persistentRowIdsToAppServer.toString());
        }
    }

    static int deleteMessage(Context context, String appId, long messageId, String crocoId) {
        Log.v(TAG, TAG + ".deleteMessage");

        SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
        SQLiteDatabase database = dbHelper.getWritableDatabase();

        database.beginTransaction();    // one operation but provides a lock, too
        int rows = 0;
        try {
            String table = SQLiteCptHelper.TABLE_NAME_CPT_MESSAGE;
            String whereClause = new StringBuilder(SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID).append(" = ?")
                    .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_TIME_CREATED).append(" = ?")
                    .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID).append(" = ?")
                    .toString();
            String[] whereArgs = { appId, String.valueOf(messageId), crocoId };
            // this should delete also all associated attachments
            rows = database.delete(table, whereClause, whereArgs);
            Log.i(TAG, "Deleted " + rows + " messages");

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }

        return rows;
    }

    public static void deleteMessages(Context context, Collection<MessageIdentifier> messagesToDelete) {
        PreferenceHelper helper = new PreferenceHelper(context);
        for (MessageIdentifier identifier : messagesToDelete) {
            // this should delete all associated attachments to upload
            deleteMessage(context,
                    identifier.appId,
                    identifier.creationTime,
                    identifier.from
            );
            CptClientCommunication.messageDeleted(context, helper,
                    identifier.appId,
                    identifier.creationTime
            );
        }
    }

    private static void deletePersistentMessages(Context context, Collection<MessageIdentifier> messagesToDelete) {
        Log.v(TAG, TAG + ".deletePersistentMessages");

        if (!messagesToDelete.isEmpty()) {
            SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
            SQLiteDatabase database = dbHelper.getWritableDatabase();

            database.beginTransaction();
            try {
                String table = SQLiteCptHelper.TABLE_NAME_CPT_PERSISTENT_MESSAGE;

                for (MessageIdentifier identifier : messagesToDelete) {
                    // we must use full identifier because also foreign (not ours) messages could be specified!
                    String whereClause = new StringBuilder(SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID).append(" = ?")
                            .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_TIME_CREATED).append(" = ?")
                            .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID).append(" = ?")
                            .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID).append(" = ?")
                            .toString();
                    String[] whereArgs = { identifier.appId, String.valueOf(identifier.creationTime), identifier.from, identifier.to };
                    int rows = database.delete(table, whereClause, whereArgs);
                    Log.i(TAG, "Deleted " + rows + " persistent messages");
                }

                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }
    }
    
    synchronized public static int cleanMessages(Context context, String myCrocoId) {
        Log.v(TAG, TAG + ".cleanMessages");
        
        SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
        SQLiteDatabase database = dbHelper.getWritableDatabase();

        Date now = new Date();
        PreferenceHelper helper = new PreferenceHelper(context);
        Set<MessageIdentifier> messageIdentifiers = new HashSet<>();

        String table;
        String[] columns;
        StringBuilder selection;
        String[] selectionArgs;
        String groupBy = null;
        String having = null;
        String orderBy = null;
        Cursor cursor = null;

        table = SQLiteCptHelper.TABLE_NAME_CPT_MESSAGE;
        columns = ALL_COLUMNS_OF_MSG_HEADER;
        selection = new StringBuilder("(")
                    .append(SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID).append(" = ?")
                    .append(" OR ").append(SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID).append(" = ?")
                .append(") AND ABS(")
                    .append("(? - ").append(SQLiteCptHelper.COLUMN_NAME_TIME_CREATED).append(") / (1000 * 60) >= ").append(SQLiteCptHelper.COLUMN_NAME_TIME_VALIDITY_TO).append(" / (1000 * 60)")
                .append(")");
        selectionArgs = new String[] { myCrocoId, myCrocoId, String.valueOf(now.getTime()) };
        try {
            cursor = database.query(table, columns, selection.toString(), selectionArgs, groupBy, having, orderBy);
            int rowsCount = cursor.getCount();
            if (rowsCount > 0 && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    NetworkHeader networkHeader = cursorToNetworkHeader(cursor, false);
                    if (networkHeader.getType() == NetworkHeader.Type.NORMAL && networkHeader.getFrom().equals(myCrocoId)) {
                        CptClientCommunication.messageDeleted(context, helper, networkHeader.getAppId(), networkHeader.getCreationTime());
                    } else if (networkHeader.getType() == NetworkHeader.Type.ACK && networkHeader.getTo().equals(myCrocoId)) {
                        // not handled in the UI yet (it means "unable to spread this ACK any longer")
                        //CptClientCommunication.messageDeleted(context, helper, networkHeader.getProfileId(), networkHeader.getCreationTime());

                        // but we can use it for local messages (received attachment previews are turned into ACK by now)
                        messageIdentifiers.add(networkHeader.getIdentifier());
                    }

                    cursor.moveToNext();
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }

        table = SQLiteCptHelper.TABLE_NAME_CPT_ATTACHMENTS_TO_DOWNLOAD;
        columns = ALL_COLUMNS_OF_MSG_ATTACHMENT_IDENTIFIER;
        selection = new StringBuilder(SQLiteCptHelper.COLUMN_NAME_DOWNLOAD_FLAG_RECEIVED).append(" = 0");
        selectionArgs = null;
        try {
            cursor = database.query(table, columns, selection.toString(), selectionArgs, groupBy, having, orderBy);
            int rowsCount = cursor.getCount();
            if (rowsCount > 0 && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    MessageAttachmentIdentifier attachmentIdentifier = cursorToMessageAttachmentIdentifier(cursor);
                    if (messageIdentifiers.contains(attachmentIdentifier.getMessageIdentifier())) {
                        Log.w(TAG, "Attachment " + attachmentIdentifier + " will never be received");
                        CptClientCommunication.messageAttachmentDownloadAction(context, helper,
                                Communication.ACTION_MESSAGE_ATTACHMENT_REQUEST_EXPIRED,
                                attachmentIdentifier
                        );
                    }
                    cursor.moveToNext();
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }

        database.beginTransaction();    // one operation but provides a lock, too
        int rows = 0;
        try {
            table = SQLiteCptHelper.TABLE_NAME_CPT_MESSAGE;
            String whereClause = "ABS((? - " + SQLiteCptHelper.COLUMN_NAME_TIME_CREATED + ") / (1000 * 60) >= " + SQLiteCptHelper.COLUMN_NAME_TIME_VALIDITY_TO + " / (1000 * 60))";
            String[] whereArgs = { String.valueOf(now.getTime()) };
            // for sent messages with an attachment this "revokes" the uris which others can download from us
            rows = database.delete(table, whereClause, whereArgs);
            Log.i(TAG, "Cleaned " + rows + " messages");

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return rows;
    }

    public static boolean addDownloadUri(Context context, MessageAttachmentIdentifier messageAttachmentIdentifier, boolean wifiOnly) {
        Log.v(TAG, TAG + ".addDownloadUri");

        SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
        SQLiteDatabase database = dbHelper.getWritableDatabase();

        String table = SQLiteCptHelper.TABLE_NAME_CPT_ATTACHMENTS_TO_DOWNLOAD;
        ContentValues values = new ContentValues();
        values.put(SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID, messageAttachmentIdentifier.getFrom());
        values.put(SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID, messageAttachmentIdentifier.getTo());
        values.put(SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID, messageAttachmentIdentifier.getAppId());
        values.put(SQLiteCptHelper.COLUMN_NAME_TIME_CREATED, messageAttachmentIdentifier.getCreationTime());
        values.put(SQLiteCptHelper.COLUMN_NAME_URI, messageAttachmentIdentifier.getSourceUri());
        values.put(SQLiteCptHelper.COLUMN_NAME_STORAGE_DIR, messageAttachmentIdentifier.getStorageDirectory());
        values.put(SQLiteCptHelper.COLUMN_NAME_CONNECTION_CONDITION, wifiOnly ? 1 : 0);

        database.beginTransaction();
        try {
            // replace because one could change 'wifiOnly'
            long rowId = database.replace(table, null, values);
            if (rowId == -1) {
                Log.e(TAG, "Inserting/replacing of " + messageAttachmentIdentifier + " has failed");
            } else {
                database.setTransactionSuccessful();
                return true;
            }
        } finally {
            database.endTransaction();
        }

        return false;
    }

    public static Map<String, List<MessageAttachmentIdentifier>> getDownloadRequests(Context context, String from, boolean allRequests) {
        Log.v(TAG, TAG + ".getDownloadRequests");
        return getDownloadRequests(context, from, allRequests, null, 0);
    }
    public static Map<String, List<MessageAttachmentIdentifier>> getDownloadRequests(Context context, String from, boolean allRequests, Set<String> sendersToIgnore) {
        Log.v(TAG, TAG + ".getDownloadRequests");
        return getDownloadRequests(context, from, allRequests, sendersToIgnore, 0);
    }
    public static Map<String, List<MessageAttachmentIdentifier>> getReceivedDownloadRequests(Context context, String from) {
        Log.v(TAG, TAG + ".getReceivedDownloadRequests");
        return getDownloadRequests(context, from, true, null, 1);
    }
    private static Map<String, List<MessageAttachmentIdentifier>> getDownloadRequests(Context context, String from, boolean allRequests, Set<String> sendersToIgnore, int received) {
        Map<String, List<MessageAttachmentIdentifier>> map = new HashMap<>();

        SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        String table = SQLiteCptHelper.TABLE_NAME_CPT_ATTACHMENTS_TO_DOWNLOAD;
        String[] columns = ALL_COLUMNS_OF_MSG_ATTACHMENT_IDENTIFIER;
        String selection = null;
        ArrayList<String> selectionArgs = new ArrayList<>();
        String groupBy = null;
        String having = null;
        String orderBy = null;

        if (from != null) {
            selection = DatabaseUtils.concatenateWhere(selection,
                    SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID + " = ?"
            );
            selectionArgs.add(from);
        }
        if (!allRequests) {
            selection = DatabaseUtils.concatenateWhere(selection,
                    SQLiteCptHelper.COLUMN_NAME_CONNECTION_CONDITION + " = ?"
            );
            selectionArgs.add(String.valueOf(0));   // there's no constant for this, wow
        }
        if (sendersToIgnore != null && !sendersToIgnore.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String sourceCrocoId : sendersToIgnore) {
                DatabaseUtils.appendValueToSql(sb, sourceCrocoId);
                sb.append(",");
            }
            sb.deleteCharAt(sb.length() - 1);

            // obviously, 'from' and 'sendersToIgnore' shouldn't be non-null at the same time
            selection = DatabaseUtils.concatenateWhere(selection,
                    SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID + " NOT IN (" + sb.toString() + ")");
        }
        if (received != -1) {
            selection = DatabaseUtils.concatenateWhere(selection,
                    SQLiteCptHelper.COLUMN_NAME_DOWNLOAD_FLAG_RECEIVED + " = ?"
            );
            selectionArgs.add(String.valueOf(received));
        }

        Cursor cursor = null;
        try {
            cursor = database.query(table, columns, selection, selectionArgs.toArray(new String[selectionArgs.size()]), groupBy, having, orderBy);
            int rowsCount = cursor.getCount();
            if (rowsCount > 0 && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    MessageAttachmentIdentifier messageAttachmentIdentifier = cursorToMessageAttachmentIdentifier(cursor);

                    List<MessageAttachmentIdentifier> messageAttachmentIdentifiers = map.get(messageAttachmentIdentifier.getSourceUri());
                    if (messageAttachmentIdentifiers == null) {
                        messageAttachmentIdentifiers = new ArrayList<>();
                        map.put(messageAttachmentIdentifier.getSourceUri(), messageAttachmentIdentifiers);
                    }
                    messageAttachmentIdentifiers.add(messageAttachmentIdentifier);

                    cursor.moveToNext();
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return map;
    }

    public static void markDownloadUriAsReceived(Context context, MessageAttachmentIdentifier messageAttachmentIdentifier) {
        Log.v(TAG, TAG + ".markDownloadUriAsReceived");
        updateDownloadUri(context, messageAttachmentIdentifier, -1, 1);
    }
    public static void markDownloadUriAsRequestedFromAppServer(Context context, MessageAttachmentIdentifier messageAttachmentIdentifier) {
        Log.v(TAG, TAG + ".markDownloadUriAsRequestedFromAppServer");
        updateDownloadUri(context, messageAttachmentIdentifier, 1, -1);
    }
    private static void updateDownloadUri(Context context, MessageAttachmentIdentifier messageAttachmentIdentifier, int requestSentToAppServer, int received) {
        String table = SQLiteCptHelper.TABLE_NAME_CPT_ATTACHMENTS_TO_DOWNLOAD;
        ContentValues values = new ContentValues();
        if (requestSentToAppServer != -1) {
            values.put(SQLiteCptHelper.COLUMN_NAME_DOWNLOAD_FLAG_REQUEST_SENT_TO_APP_SERVER, requestSentToAppServer);
        }
        if (received != -1) {
            values.put(SQLiteCptHelper.COLUMN_NAME_DOWNLOAD_FLAG_RECEIVED, received);
        }

        String whereClause = getWhereClauseForMessageAttachmentIdentifier(messageAttachmentIdentifier);
        String[] whereArgs = getWhereArgsForMessageAttachmentIdentifier(messageAttachmentIdentifier);

        int rows = updateTable(context, table, values, whereClause, whereArgs);
        if (rows == 0) {
            Log.e(TAG, "Updating of " + messageAttachmentIdentifier + " has failed");
        }
    }

    public static void removeDownloadUri(Context context, MessageAttachmentIdentifier messageAttachmentIdentifier) {
        Log.v(TAG, TAG + ".removeDownloadUri");

        SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
        SQLiteDatabase database = dbHelper.getWritableDatabase();

        String table = SQLiteCptHelper.TABLE_NAME_CPT_ATTACHMENTS_TO_DOWNLOAD;
        String whereClause = getWhereClauseForMessageAttachmentIdentifier(messageAttachmentIdentifier);
        String[] whereArgs = getWhereArgsForMessageAttachmentIdentifier(messageAttachmentIdentifier);

        database.beginTransaction();
        try {
            int rows = database.delete(table, whereClause, whereArgs);
            if (rows == 1) {
                Log.i(TAG, "Cancelled/Removed: " + messageAttachmentIdentifier);
            } else {
                Log.w(TAG, "Uri is already deleted: " + messageAttachmentIdentifier);
            }

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public static boolean addUploadUri(Context context, MessageAttachmentIdentifier messageAttachmentIdentifier, boolean wifiOnly) {
        Log.v(TAG, TAG + ".addUploadUri");

        SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
        SQLiteDatabase database = dbHelper.getWritableDatabase();

        String table = SQLiteCptHelper.TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD;
        ContentValues values = new ContentValues();
        values.put(SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID, messageAttachmentIdentifier.getFrom());
        values.put(SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID, messageAttachmentIdentifier.getTo());
        values.put(SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID, messageAttachmentIdentifier.getAppId());
        values.put(SQLiteCptHelper.COLUMN_NAME_TIME_CREATED, messageAttachmentIdentifier.getCreationTime());
        values.put(SQLiteCptHelper.COLUMN_NAME_URI, messageAttachmentIdentifier.getSourceUri());
        values.put(SQLiteCptHelper.COLUMN_NAME_STORAGE_DIR, messageAttachmentIdentifier.getStorageDirectory());
        values.put(SQLiteCptHelper.COLUMN_NAME_CONNECTION_CONDITION, wifiOnly ? 1 : 0);

        database.beginTransaction();
        try {
            // replace if the client is stupid enough to supply us with the same uri (or because one could change 'wifiOnly')
            long rowId = database.replace(table, null, values);
            if (rowId == -1) {
                Log.e(TAG, "Inserting of " + messageAttachmentIdentifier + " has failed");
            } else {
                database.setTransactionSuccessful();
                return true;
            }
        } finally {
            database.endTransaction();
        }

        return false;
    }

    public static void markUploadUriAsSentToRecipient(Context context, MessageAttachmentIdentifier messageAttachmentIdentifier, boolean sent) {
        Log.v(TAG, TAG + ".markUploadUriAsSentToRecipient");
        updateUploadUri(context, messageAttachmentIdentifier, sent ? 1 : 0, -1, -1);
    }
    public static void markUploadUriAsSentToAppServer(Context context, MessageAttachmentIdentifier messageAttachmentIdentifier, boolean sent) {
        Log.v(TAG, TAG + ".markUploadUriAsSentToAppServer");
        updateUploadUri(context, messageAttachmentIdentifier, -1, sent ? 1 : 0, -1);
    }
    public static void markUploadUriAsDelivered(Context context, MessageAttachmentIdentifier messageAttachmentIdentifier, boolean delivered) {
        Log.v(TAG, TAG + ".markUploadUriAsDelivered");
        updateUploadUri(context, messageAttachmentIdentifier, -1, -1, delivered ? 1 : 0);
    }
    private static void updateUploadUri(Context context, MessageAttachmentIdentifier messageAttachmentIdentifier, int sentToRecipient, int sentToAppServer, int delivered) {
        // TODO: we should update only not set fields!

        String table = SQLiteCptHelper.TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD;
        ContentValues values = new ContentValues();
        if (sentToRecipient != -1) {
            values.put(SQLiteCptHelper.COLUMN_NAME_UPLOAD_FLAG_SENT_TO_RECIPIENT, sentToRecipient);
        }
        if (sentToAppServer != -1) {
            values.put(SQLiteCptHelper.COLUMN_NAME_UPLOAD_FLAG_SENT_TO_APP_SERVER, sentToAppServer);
        }
        if (delivered != -1) {
            values.put(SQLiteCptHelper.COLUMN_NAME_UPLOAD_FLAG_DELIVERED, delivered);
        }
        String whereClause = getWhereClauseForMessageAttachmentIdentifier(messageAttachmentIdentifier);
        String[] whereArgs = getWhereArgsForMessageAttachmentIdentifier(messageAttachmentIdentifier);

        int rows = updateTable(context, table, values, whereClause, whereArgs);
        if (rows == 0) {
            Log.e(TAG, "Updating of " + messageAttachmentIdentifier + " has failed");
        }
    }

    public static Map<String, List<MessageAttachmentIdentifier>> getUploadRequests(Context context, String to, boolean allRequests) {
        Log.v(TAG, TAG + ".getUploadRequests");
        return getUploadRequests(context, to, allRequests, -1, -1);
    }
    public static Map<String, List<MessageAttachmentIdentifier>> getUploadRequestsSentToRecipient(Context context, String to) {
        Log.v(TAG, TAG + ".getUploadRequestsSentToRecipient");
        return getUploadRequests(context, to, true, 1, -1);
    }
    public static Map<String, List<MessageAttachmentIdentifier>> getUploadRequestsSentToAppServer(Context context) {
        Log.v(TAG, TAG + ".getUploadRequestsSentToRecipient");
        return getUploadRequests(context, null, true, -1, 1);
    }
    public static Map<String, List<MessageAttachmentIdentifier>> getUploadRequestsNotSentToAppServer(Context context, boolean allRequests) {
        Log.v(TAG, TAG + ".getUploadRequestsNotSentToAppServer");
        return getUploadRequests(context, null, allRequests, -1, 0);
    }
    private static Map<String, List<MessageAttachmentIdentifier>> getUploadRequests(Context context, String to, boolean allRequests, int sentToRecipient, int sentToAppServer) {
        Map<String, List<MessageAttachmentIdentifier>> map = new HashMap<>();

        SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        String table = SQLiteCptHelper.TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD;
        String[] columns = ALL_COLUMNS_OF_MSG_ATTACHMENT_IDENTIFIER;
        String selection = SQLiteCptHelper.COLUMN_NAME_UPLOAD_FLAG_DELIVERED + " = 0";
        ArrayList<String> selectionArgs = new ArrayList<>();
        String groupBy = null;
        String having = null;
        String orderBy = null;

        if (to != null) {
            selection = DatabaseUtils.concatenateWhere(selection,
                    SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID + " = ?"
            );
            selectionArgs.add(to);
        }
        if (!allRequests) {
            selection = DatabaseUtils.concatenateWhere(selection,
                    SQLiteCptHelper.COLUMN_NAME_CONNECTION_CONDITION + " = ?"
            );
            selectionArgs.add(String.valueOf(0));   // there's no constant for this, wow
        }
        if (sentToRecipient != -1) {
            selection = DatabaseUtils.concatenateWhere(selection,
                    SQLiteCptHelper.COLUMN_NAME_UPLOAD_FLAG_SENT_TO_RECIPIENT + " = ?"
            );
            selectionArgs.add(String.valueOf(sentToRecipient));
        }
        if (sentToAppServer != -1) {
            selection = DatabaseUtils.concatenateWhere(selection,
                    SQLiteCptHelper.COLUMN_NAME_UPLOAD_FLAG_SENT_TO_APP_SERVER + " = ?"
            );
            selectionArgs.add(String.valueOf(sentToAppServer));
        }

        Cursor cursor = null;
        try {
            cursor = database.query(table, columns, selection, selectionArgs.toArray(new String[selectionArgs.size()]), groupBy, having, orderBy);
            int rowsCount = cursor.getCount();
            if (rowsCount > 0 && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    MessageAttachmentIdentifier messageAttachmentIdentifier = cursorToMessageAttachmentIdentifier(cursor);

                    List<MessageAttachmentIdentifier> messageAttachmentIdentifiers = map.get(messageAttachmentIdentifier.getSourceUri());
                    if (messageAttachmentIdentifiers == null) {
                        messageAttachmentIdentifiers = new ArrayList<>();
                        map.put(messageAttachmentIdentifier.getSourceUri(), messageAttachmentIdentifiers);
                    }
                    messageAttachmentIdentifiers.add(messageAttachmentIdentifier);

                    cursor.moveToNext();
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return map;
    }

    private static List<NetworkAttachmentPreview> getNetworkAttachmentPreviews(Context context, MessageIdentifier messageIdentifier, boolean isLocal) {
        Log.v(TAG, TAG + ".getNetworkAttachmentPreviews");

        List<NetworkAttachmentPreview> list = new ArrayList<>();

        if (isLocal) {
            SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
            SQLiteDatabase database = dbHelper.getReadableDatabase();

            String table = SQLiteCptHelper.TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD;
            String[] columns = {
                    SQLiteCptHelper.COLUMN_NAME_URI,
                    SQLiteCptHelper.COLUMN_NAME_STORAGE_DIR
            };
            String selection = new StringBuilder(SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID).append(" = ?")
                    .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID).append(" = ?")
                    .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_TIME_CREATED).append(" = ?")
                    .toString();
            String[] selectionArgs = {messageIdentifier.to, messageIdentifier.appId, String.valueOf(messageIdentifier.creationTime)};
            String groupBy = null;
            String having = null;
            String orderBy = null;

            Cursor cursor = null;
            try {
                cursor = database.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
                int rowsCount = cursor.getCount();
                if (rowsCount > 0 && cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        LocalAttachment localAttachment = new LocalAttachment(
                                Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_URI))),
                                cursor.getString(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_STORAGE_DIR))
                        );
                        if (localAttachment.getLength(context) > 0) {
                            list.add(
                                    new NetworkAttachmentPreview(
                                            localAttachment.getSourceUri(),
                                            localAttachment.getName(context),
                                            localAttachment.getLength(context),
                                            localAttachment.getLastModified(context).getTime(),
                                            localAttachment.getType(context),
                                            localAttachment.getStorageDirectory()
                                    )
                            );
                        } else {
                            Log.e(TAG, "Local attachment not found: " + localAttachment);
                            list = null;
                            break;
                        }
                        cursor.moveToNext();
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        return list;
    }

    public static void removeUploadUri(Context context, MessageAttachmentIdentifier messageAttachmentIdentifier) {
        Log.v(TAG, TAG + ".removeUploadUri");

        SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
        SQLiteDatabase database = dbHelper.getWritableDatabase();

        String table = SQLiteCptHelper.TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD;
        String whereClause = getWhereClauseForMessageAttachmentIdentifier(messageAttachmentIdentifier);
        String[] whereArgs = getWhereArgsForMessageAttachmentIdentifier(messageAttachmentIdentifier);

        database.beginTransaction();
        try {
            int rows = database.delete(table, whereClause, whereArgs);
            if (rows == 1) {
                Log.i(TAG, "Cancelled/Removed: " + messageAttachmentIdentifier);
            } else {
                Log.w(TAG, "Uri is already deleted: " + messageAttachmentIdentifier);
            }

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    private static List<NetworkPersistentAttachment> getNetworkPersistentAttachments(Context context, Cursor cursor, MessageIdentifier messageIdentifier) {
        Log.v(TAG, TAG + ".getNetworkPersistentAttachments");

        List<NetworkPersistentAttachment> list = new ArrayList<>();
        List<LocalDbAttachment> localDbAttachments = (List<LocalDbAttachment>) byteArrayToStreamables(context, cursor.getBlob(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_PERSISTENT_ATTACHMENTS)));
        for (LocalDbAttachment attachment : localDbAttachments) {
            if (attachment.getLength(context) > 0) {
                list.add(
                        new NetworkPersistentAttachment(
                                attachment.getUri(),
                                attachment.getName(context), // name taken either dynamically or statically
                                attachment.getLength(context),  // ditto
                                messageIdentifier.appId
                        )
                );
            } else {
                Log.e(TAG, "Local attachment not found: " + localDbAttachments);
                list = null;
                break;
            }
        }

        return list;
    }

    static void changeDeviceTrustLevel(Context context, String appId, String crocoId, int trustLevel) {
        Log.v(TAG, TAG + ".changeDeviceTrustLevel");

        SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
        SQLiteDatabase database = dbHelper.getWritableDatabase();

        String table = SQLiteCptHelper.TABLE_NAME_CPT_DEVICES_TRUST;

        database.beginTransaction();
        try {
            if (trustLevel != Communication.USER_TRUST_LEVEL_NORMAL) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(SQLiteCptHelper.COLUMN_NAME_CROCO_ID, crocoId);
                contentValues.put(SQLiteCptHelper.COLUMN_NAME_TRUST_LEVEL, trustLevel);

                if (database.replace(table, null, contentValues) != -1) {
                    Log.i(TAG, "Changed trust level of " + crocoId);
                } else {
                    Log.w(TAG, "Changing trust level of " + crocoId + " has failed");
                }
            } else {
                String whereClause = new StringBuilder(SQLiteCptHelper.COLUMN_NAME_CROCO_ID).append(" = ?")
                        .toString();
                String[] whereArgs = { crocoId };

                int rows = database.delete(table, whereClause, whereArgs);
                if (rows == 1) {
                    Log.i(TAG, "Changed trust level of " + crocoId + " to NORMAL");
                } else {
                    Log.w(TAG, "Changing trust level of " + crocoId + " has failed (it's NORMAL already)");
                }
            }

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public static int getDeviceTrustLevel(Context context, String crocoId) {
        Log.v(TAG, TAG + ".getDeviceTrustLevel");

        int trustLevel = Communication.USER_TRUST_LEVEL_NORMAL;

        SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        String table = SQLiteCptHelper.TABLE_NAME_CPT_DEVICES_TRUST;
        String[] columns = { SQLiteCptHelper.COLUMN_NAME_TRUST_LEVEL };
        String selection = new StringBuilder(SQLiteCptHelper.COLUMN_NAME_CROCO_ID).append(" = ?")
                .toString();
        String[] selectionArgs = { crocoId };
        String groupBy = null;
        String having = null;
        String orderBy = null;

        Cursor cursor = null;
        try {
            cursor = database.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
            int rowsCount = cursor.getCount();
            if (rowsCount > 0 && cursor.moveToFirst()) {
                assertEquals(1, cursor.getCount());
                trustLevel = cursor.getInt(0);  // 0th index from above
            }
            // not found means USER_TRUST_LEVEL_NORMAL
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return trustLevel;
    }

    public static Set<String> getBlockedDevices(Context context) {
        return getDevicesForGivenTrustLevel(context, Communication.USER_TRUST_LEVEL_BLOCKED);
    }

    public static Set<String> getTrustedDevices(Context context) {
        return getDevicesForGivenTrustLevel(context, Communication.USER_TRUST_LEVEL_TRUSTED);
    }

    private static Set<String> getDevicesForGivenTrustLevel(Context context, int trustLevel) {
        Log.v(TAG, TAG + ".getDevicesForGivenTrustLevel: " + trustLevel);   // NORMAL isn't supported

        HashSet<String> set = new HashSet<>();

        SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        String table = SQLiteCptHelper.TABLE_NAME_CPT_DEVICES_TRUST;
        String[] columns = { SQLiteCptHelper.COLUMN_NAME_CROCO_ID };
        String selection = new StringBuilder(SQLiteCptHelper.COLUMN_NAME_TRUST_LEVEL).append(" = ?")
                .toString();
        String[] selectionArgs = { String.valueOf(trustLevel) };
        String groupBy = null;
        String having = null;
        String orderBy = null;

        Cursor cursor = null;
        try {
            cursor = database.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
            int rowsCount = cursor.getCount();
            if (rowsCount > 0 && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    set.add(cursor.getString(0));  // 0th index from above
                    cursor.moveToNext();
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return set;
    }

    private static int updateTable(Context context, String table, ContentValues values, String whereClause, String[] whereArgs) {
        int rows = 0;

        SQLiteCptHelper dbHelper = SQLiteCptHelper.getHelper(context);
        SQLiteDatabase database = dbHelper.getWritableDatabase();

        database.beginTransaction();
        try {
            rows = database.update(table, values, whereClause, whereArgs);
            if (rows > 0) {
                database.setTransactionSuccessful();
            }
        } finally {
            database.endTransaction();
        }

        return rows;
    }

    private static NetworkHop getNewNetworkHop(Context context) {
        PreferenceHelper preferenceHelper = new PreferenceHelper(context);
        return new NetworkHop(preferenceHelper.getCrocoId(),
                mLatitude, mLongitude, mLocationTime, android.os.Build.VERSION.RELEASE, new Date(),
                preferenceHelper.getUsername()
        );
    }

    private static String getWhereClauseForMessageAttachmentIdentifier(MessageAttachmentIdentifier messageAttachmentIdentifier) {
        StringBuilder whereClause = new StringBuilder(SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID).append(" = ?")
                .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID).append(" = ?")
                .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID).append(" = ?")
                .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_TIME_CREATED).append(" = ?")
                .append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_URI).append(" = ?");
        if (messageAttachmentIdentifier.getStorageDirectory() != null) {
            whereClause.append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_STORAGE_DIR).append(" = ?");
        } else {
            whereClause.append(" AND ").append(SQLiteCptHelper.COLUMN_NAME_STORAGE_DIR).append(" IS NULL");
        }
        return whereClause.toString();
    }

    private static String[] getWhereArgsForMessageAttachmentIdentifier(MessageAttachmentIdentifier messageAttachmentIdentifier) {
        if (messageAttachmentIdentifier.getStorageDirectory() != null) {
            return new String[]{
                    messageAttachmentIdentifier.getFrom(),
                    messageAttachmentIdentifier.getTo(),
                    messageAttachmentIdentifier.getAppId(),
                    String.valueOf(messageAttachmentIdentifier.getCreationTime()),
                    messageAttachmentIdentifier.getSourceUri(),
                    messageAttachmentIdentifier.getStorageDirectory()
            };
        } else {
            return new String[]{
                    messageAttachmentIdentifier.getFrom(),
                    messageAttachmentIdentifier.getTo(),
                    messageAttachmentIdentifier.getAppId(),
                    String.valueOf(messageAttachmentIdentifier.getCreationTime()),
                    messageAttachmentIdentifier.getSourceUri()
            };
        }
    }

    private static LocationManager mLocationManager;
    private static final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
        @Override
        public void onProviderEnabled(String provider) {
        }
        @Override
        public void onProviderDisabled(String provider) {
        }
        @Override
        public void onLocationChanged(Location location) {
            // Called when a new location is found by the network location provider.
            mLatitude = location.getLatitude();
            mLongitude = location.getLongitude();
            mLocationTime = new Date();
            // get location only once => remove listener
            mLocationManager.removeUpdates(this);
        }
    };

    // must run in UI thread
    public static void obtainLocation(Context context) {
        if (!Settings.getInstance().allowTracking) {
            Log.i(TAG, "Tracking is disabled, not obtaining location");
            return;
        }

        // Get the location manager
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        }

        // Define the criteria how to select the location provider
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        String provider = mLocationManager.getBestProvider(criteria, false);
        if (provider != null) {
            // Initialize the location fields
            Location location = mLocationManager.getLastKnownLocation(provider);
            if (location != null) {
                mLatitude = location.getLatitude();
                mLongitude = location.getLongitude();
            }

            // Register the listener with the Location Manager to receive location updates
            long minTime = 10 * 1000; // 10 sec
            float minDistance = 10; // 10 m
            mLocationManager.requestLocationUpdates(provider, minTime, minDistance, mLocationListener);
        } else {
            Toast toast = Toast.makeText(context, "No location provider available", Toast.LENGTH_SHORT);
            toast.show();
        }
        // regardless of GPS state, we must mark at least time here
        mLocationTime = new Date();
    }

    private static byte[] streamablesToByteArray(Context context, Collection<? extends Streamable> streamables) {
        byte[] byteArray = null;

        if (streamables != null) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                StreamUtil.writeStreamablesTo(context, dos, streamables);
                byteArray = baos.toByteArray();
                dos.close();
            } catch (IOException e) {
                Log.e(TAG, "exception", e);
            }
        }

        return byteArray;
    }

    private static Collection<? extends Streamable> byteArrayToStreamables(Context context, byte[] bytes) {
        Collection<? extends Streamable> streamables = null;

        if (bytes != null) {
            try {
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
                streamables = StreamUtil.readStreamablesFrom(context, dis);
                dis.close();
            } catch (IOException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                Log.e(TAG, "exception", e);
            }
        }

        return streamables;
    }

    private static NetworkHeader cursorToNetworkHeader(Cursor cursor, boolean isPersistent) {
        return new NetworkHeader(
                cursor.getLong(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_ROWID)),
                new MessageIdentifier(
                        cursor.getString(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_TIME_CREATED))
                ),
                isPersistent ? NetworkHeader.Type.NORMAL : NetworkHeader.Type.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_TYPE))),
                isPersistent ? cursor.getInt(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_PERSISTENT_ID)) : -1
        );
    }

    private static NetworkMessage cursorToPersistentNetworkMessage(Context context, Cursor cursor) {
        NetworkMessage message = new NetworkMessage(
                cursorToNetworkHeader(cursor, true),
                cursor.getBlob(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_CONTENT)),
                (List<NetworkHop>) byteArrayToStreamables(context, cursor.getBlob(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_HOPS)))
        );

        return message;
    }

    private static NetworkMessage cursorToNetworkMessage(Context context, Cursor cursor) {
        NetworkMessage message = new NetworkMessage(
                cursorToNetworkHeader(cursor, false),
                cursor.getInt(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_TIME_VALIDITY_TO)),
                cursor.getBlob(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_CONTENT)),
                (List<NetworkHop>) byteArrayToStreamables(context, cursor.getBlob(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_HOPS))),
                cursor.getInt(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_RECIPIENT)) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_OTHER_DEVICE)) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_APP_SERVER)) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_FLAG_EXPECTS_SENT)) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_FLAG_EXPECTS_ACK)) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_FLAG_LOCAL_ONLY)) == 1
        );

        int indexDeliveredTime = cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_TIME_DELIVERED);
        if (!cursor.isNull(indexDeliveredTime)) {
            message.setDeliveredTime(new Date(cursor.getLong(indexDeliveredTime)));
        }

        return message;
    }

    private static AttachmentIdentifier cursorToAttachmentIdentifier(Cursor cursor) {
        return new AttachmentIdentifier(
                cursor.getString(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_URI)),
                cursor.getString(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_STORAGE_DIR))
        );
    }

    private static MessageIdentifier cursorToMessageIdentifier(Cursor cursor) {
        return new MessageIdentifier(
                cursor.getString(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID)),
                cursor.getLong(cursor.getColumnIndexOrThrow(SQLiteCptHelper.COLUMN_NAME_TIME_CREATED))
        );
    }

    private static MessageAttachmentIdentifier cursorToMessageAttachmentIdentifier(Cursor cursor) {
        return new MessageAttachmentIdentifier(
                cursorToAttachmentIdentifier(cursor),
                cursorToMessageIdentifier(cursor)
        );
    }

    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static String longToBase62String(long val) {
        String ret = "";
        int dstBase = base62Alphabet.length();
        while (val > 0) {
            int digitVal = (int) (val % dstBase);
            char digit = base62Alphabet.charAt(digitVal);
            ret = (digit + ret);
            val /= dstBase;
        }
        return ret;
    }
    @SuppressWarnings("SpellCheckingInspection")
    private static final String base62Alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final String MESSAGE_ATTACHMENTS_TO_UPLOAD_WHERE_CLAUSE =
                        SQLiteCptHelper.TABLE_NAME_CPT_MESSAGE + "." + SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID + " = " + SQLiteCptHelper.TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD + "." + SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID
            + " AND " + SQLiteCptHelper.TABLE_NAME_CPT_MESSAGE + "." + SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID + " = " + SQLiteCptHelper.TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD + "." + SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID
            + " AND " + SQLiteCptHelper.TABLE_NAME_CPT_MESSAGE + "." + SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID  + " = " + SQLiteCptHelper.TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD + "." + SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID
            + " AND " + SQLiteCptHelper.TABLE_NAME_CPT_MESSAGE + "." + SQLiteCptHelper.COLUMN_NAME_TIME_CREATED    + " = " + SQLiteCptHelper.TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD + "." + SQLiteCptHelper.COLUMN_NAME_TIME_CREATED
    ;

    private static final String[] ALL_COLUMNS_FOR_SELECT = {
            SQLiteCptHelper.COLUMN_NAME_ROWID,
            SQLiteCptHelper.COLUMN_NAME_TYPE,
            SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID,
            SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID,
            SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID,
            SQLiteCptHelper.COLUMN_NAME_TIME_CREATED,
            SQLiteCptHelper.COLUMN_NAME_TIME_VALIDITY_TO,
            SQLiteCptHelper.COLUMN_NAME_TIME_DELIVERED,
            SQLiteCptHelper.COLUMN_NAME_CONTENT,
            SQLiteCptHelper.COLUMN_NAME_HOPS,
            SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_RECIPIENT,
            SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_OTHER_DEVICE,
            SQLiteCptHelper.COLUMN_NAME_FLAG_SENT_TO_APP_SERVER,
            SQLiteCptHelper.COLUMN_NAME_FLAG_LOCAL_ONLY,
            SQLiteCptHelper.COLUMN_NAME_FLAG_EXPECTS_SENT,
            SQLiteCptHelper.COLUMN_NAME_FLAG_EXPECTS_ACK
    };

    private static final String[] ALL_COLUMNS_FOR_PERSISTENT_SELECT = {
            SQLiteCptHelper.COLUMN_NAME_ROWID,
            SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID,
            SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID,
            SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID,
            SQLiteCptHelper.COLUMN_NAME_TIME_CREATED,
            SQLiteCptHelper.COLUMN_NAME_CONTENT,
            SQLiteCptHelper.COLUMN_NAME_HOPS,
            SQLiteCptHelper.COLUMN_NAME_PERSISTENT_ID,
            SQLiteCptHelper.COLUMN_NAME_PERSISTENT_ATTACHMENTS
    };
    
    private static final String[] ALL_COLUMNS_OF_MSG_HEADER = {
            SQLiteCptHelper.COLUMN_NAME_ROWID,
            SQLiteCptHelper.COLUMN_NAME_TYPE,
            SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID,
            SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID,
            SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID,
            SQLiteCptHelper.COLUMN_NAME_TIME_CREATED
    };

    private static final String[] ALL_COLUMNS_OF_PERSISTENT_MSG_HEADER = {
            SQLiteCptHelper.COLUMN_NAME_ROWID,
            SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID,
            SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID,
            SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID,
            SQLiteCptHelper.COLUMN_NAME_TIME_CREATED,
            SQLiteCptHelper.COLUMN_NAME_PERSISTENT_ID
    };

    private static final String[] ALL_COLUMNS_OF_MSG_ATTACHMENT_IDENTIFIER = {
            SQLiteCptHelper.COLUMN_NAME_URI,
            SQLiteCptHelper.COLUMN_NAME_STORAGE_DIR,
            SQLiteCptHelper.COLUMN_NAME_SOURCE_CROCO_ID,
            SQLiteCptHelper.COLUMN_NAME_TARGET_CROCO_ID,
            SQLiteCptHelper.COLUMN_NAME_APPLICATION_ID,
            SQLiteCptHelper.COLUMN_NAME_TIME_CREATED
    };
}
