package com.croconaut.cpt.data;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class SQLiteCptHelper extends SQLiteOpenHelper {
    private static final String TAG = "data";

    private static final String DATABASE_NAME = "cpt.db";
    private static final int DATABASE_VERSION = 4;
    // Tables
    public static final String TABLE_NAME_CPT_MESSAGE = "cpt_message";
    public static final String TABLE_NAME_CPT_PERSISTENT_MESSAGE = "cpt_persistent_message";
    public static final String TABLE_NAME_CPT_DEVICES_TRUST = "cpt_devices_trust";
    public static final String TABLE_NAME_CPT_ATTACHMENTS_TO_DOWNLOAD = "cpt_attachments_download";
    public static final String TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD = "cpt_attachments_upload";
    public static final String TABLE_NAME_CPT_GCM_TRANSACTIONS = "cpt_gcm_transactions";
    // Hidden column rowid
    public static final String COLUMN_NAME_ROWID = "rowid";

    // both tables
    public static final String COLUMN_NAME_SOURCE_CROCO_ID = "source_croco_id";
    public static final String COLUMN_NAME_TARGET_CROCO_ID = "target_croco_id";
    public static final String COLUMN_NAME_APPLICATION_ID = "application_id";
    public static final String COLUMN_NAME_TIME_CREATED = "time_created";
    public static final String COLUMN_NAME_CONTENT = "msg_content";
    public static final String COLUMN_NAME_HOPS = "msg_hops";
    public static final String COLUMN_NAME_FLAG_SENT_TO_RECIPIENT = "msg_flag_sent_to_recipient";
    public static final String COLUMN_NAME_FLAG_SENT_TO_OTHER_DEVICE = "msg_flag_sent_to_other";
    public static final String COLUMN_NAME_FLAG_SENT_TO_APP_SERVER = "msg_flag_sent_to_server";
    // TABLE_NAME_CPT_MESSAGE only
    public static final String COLUMN_NAME_TYPE = "msg_type";
    public static final String COLUMN_NAME_TIME_VALIDITY_TO = "msg_time_validity_to";
    public static final String COLUMN_NAME_TIME_DELIVERED = "msg_time_delivered";
    public static final String COLUMN_NAME_FLAG_LOCAL_ONLY = "msg_flag_local_only";
    public static final String COLUMN_NAME_FLAG_EXPECTS_ACK = "msg_flag_expects_ack";
    public static final String COLUMN_NAME_FLAG_EXPECTS_SENT = "msg_flag_expects_sent";
    // TABLE_NAME_CPT_PERSISTENT_MESSAGE only
    public static final String COLUMN_NAME_PERSISTENT_ID = "msg_persistent_id";
    public static final String COLUMN_NAME_PERSISTENT_ATTACHMENTS = "msg_persistent_attachments";

    // TABLE_NAME_CPT_DEVICES_TRUST only
    public static final String COLUMN_NAME_CROCO_ID = "croco_id";
    public static final String COLUMN_NAME_TRUST_LEVEL = "trust_level"; // 0: blocked, 2: trusted on wifi, 3: trusted (1 is implied = record does not exist)

    // TABLE_NAME_CPT_ATTACHMENTS_TO_DOWNLOAD, TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD
    public static final String COLUMN_NAME_URI = "uri";
    public static final String COLUMN_NAME_CONNECTION_CONDITION = "connection_condition";   // 0: download 1: download on wifi only
    public static final String COLUMN_NAME_STORAGE_DIR = "storage_dir";   // null: use sourceUri else Environment.<value>

    // TABLE_NAME_CPT_ATTACHMENTS_TO_DOWNLOAD only
    public static final String COLUMN_NAME_DOWNLOAD_FLAG_RECEIVED = "flag_received";
    public static final String COLUMN_NAME_DOWNLOAD_FLAG_REQUEST_SENT_TO_APP_SERVER = "flag_request_sent_to_app_server";

    // TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD only
    public static final String COLUMN_NAME_UPLOAD_FLAG_SENT_TO_RECIPIENT = "flag_sent_to_recipient";
    public static final String COLUMN_NAME_UPLOAD_FLAG_SENT_TO_APP_SERVER = "flag_sent_to_app_server";
    public static final String COLUMN_NAME_UPLOAD_FLAG_DELIVERED = "flag_delivered";

    // TABLE_NAME_CPT_GCM_TRANSACTIONS
    public static final String COLUMN_NAME_GCM_TIME = "time";
    public static final String COLUMN_NAME_GCM_MASK = "gcm_mask";   // GcmSyncRequest constants

    // some convenient defines
    private static final String MSG_HEADER_SCHEMA
            = COLUMN_NAME_SOURCE_CROCO_ID + " text NOT NULL, "
            + COLUMN_NAME_TARGET_CROCO_ID + " text NOT NULL, "
            + COLUMN_NAME_APPLICATION_ID  + " text NOT NULL, "
            + COLUMN_NAME_TIME_CREATED    + " integer NOT NULL";
    public static final String COLUMNS_MSG_HEADER
            = COLUMN_NAME_SOURCE_CROCO_ID + ", "
            + COLUMN_NAME_TARGET_CROCO_ID + ", "
            + COLUMN_NAME_APPLICATION_ID  + ", "
            + COLUMN_NAME_TIME_CREATED;

    private volatile static SQLiteCptHelper mInstance;
    private SQLiteCptHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized SQLiteCptHelper getHelper(Context context) {
        if (mInstance == null ) {
            synchronized (SQLiteCptHelper.class) {
                if (mInstance == null) {
                    mInstance = new SQLiteCptHelper(context.getApplicationContext());
                }
            }
        }
        return mInstance;
    }

    @Override
    public void onConfigure (SQLiteDatabase db) {
        //db.enableWriteAheadLogging();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {   // create table
            // an index is not really needed here as we're querying all headers and then pick up
            // messages by their row ids = fast
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME_CPT_MESSAGE + " ("
                    + MSG_HEADER_SCHEMA + ", "
                    + COLUMN_NAME_TYPE + " integer not null, "
                    + COLUMN_NAME_TIME_VALIDITY_TO + " integer not null, "
                    + COLUMN_NAME_TIME_DELIVERED + " integer default null, "
                    + COLUMN_NAME_CONTENT + " blob default null, "
                    + COLUMN_NAME_HOPS + " blob not null, "
                    // these three are default == 1 for remote messages, easier to check the flags
                    // than to compare from fields with current croco id (they are purely local flags!)
                    + COLUMN_NAME_FLAG_SENT_TO_RECIPIENT + " integer not null default 0, "
                    + COLUMN_NAME_FLAG_SENT_TO_OTHER_DEVICE + " integer not null default 0, "
                    + COLUMN_NAME_FLAG_SENT_TO_APP_SERVER + " integer not null default 0, "
                    + COLUMN_NAME_FLAG_LOCAL_ONLY + " integer not null, "
                    + COLUMN_NAME_FLAG_EXPECTS_SENT + " integer not null default 0, "
                    + COLUMN_NAME_FLAG_EXPECTS_ACK + " integer not null default 0, "
                    + "PRIMARY KEY ("
                        + COLUMNS_MSG_HEADER
                    + "), CONSTRAINT unique_header_with_type UNIQUE ("
                        + COLUMNS_MSG_HEADER + ", "
                        + COLUMN_NAME_TYPE
                    + ")"
                + ");"
            );
        } catch (SQLException e) {
            Log.e(TAG, "Error on create table '" + TABLE_NAME_CPT_MESSAGE + "' in db '" + DATABASE_NAME + "'", e);
        }

        try {   // create table
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME_CPT_PERSISTENT_MESSAGE + " ("
                    + MSG_HEADER_SCHEMA + ", "
                    + COLUMN_NAME_CONTENT + " blob not null, "  // cannot be null if persistent message!
                    + COLUMN_NAME_PERSISTENT_ATTACHMENTS + " blob not null, "   // empty yes, null no
                    + COLUMN_NAME_HOPS + " blob not null, "
                    + COLUMN_NAME_FLAG_SENT_TO_RECIPIENT + " integer not null default 0, "
                    + COLUMN_NAME_FLAG_SENT_TO_OTHER_DEVICE + " integer not null default 0, "
                    + COLUMN_NAME_FLAG_SENT_TO_APP_SERVER + " integer not null default 0, "
                    + COLUMN_NAME_PERSISTENT_ID + " integer not null, "
                    + "PRIMARY KEY ("
                        + COLUMN_NAME_SOURCE_CROCO_ID + ", "
                        + COLUMN_NAME_APPLICATION_ID + ", "
                        + COLUMN_NAME_PERSISTENT_ID
                    + ")"
                + ");"
            );
        } catch (SQLException e) {
            Log.e(TAG, "Error on create table '" + TABLE_NAME_CPT_PERSISTENT_MESSAGE + "' in db '" + DATABASE_NAME + "'", e);
        }

        try {   // create table
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME_CPT_DEVICES_TRUST + " ("
                    + COLUMN_NAME_CROCO_ID + " text not null, "
                    + COLUMN_NAME_TRUST_LEVEL + " integer not null, "
                    + "PRIMARY KEY ("
                        + COLUMN_NAME_CROCO_ID
                    + ")"
                + ");"
            );
        } catch (SQLException e) {
            Log.e(TAG, "Error on create table '" + TABLE_NAME_CPT_DEVICES_TRUST + "' in db '" + DATABASE_NAME + "'", e);
        }

        try {   // create table
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME_CPT_ATTACHMENTS_TO_DOWNLOAD + " ("
                    + MSG_HEADER_SCHEMA + ", "
                    + COLUMN_NAME_CONNECTION_CONDITION + " integer not null, "
                    + COLUMN_NAME_STORAGE_DIR + " string, "
                    + COLUMN_NAME_URI + " text not null, "
                    + COLUMN_NAME_DOWNLOAD_FLAG_RECEIVED + " integer not null default 0, "
                    + COLUMN_NAME_DOWNLOAD_FLAG_REQUEST_SENT_TO_APP_SERVER + " integer not null default 0, "
                    + "PRIMARY KEY ("
                        + COLUMNS_MSG_HEADER + ", "
                        + COLUMN_NAME_STORAGE_DIR + ", "
                        + COLUMN_NAME_URI
                    + "), FOREIGN KEY ("
                        + COLUMNS_MSG_HEADER
                    + ") REFERENCES " + TABLE_NAME_CPT_MESSAGE + " ("
                        + COLUMNS_MSG_HEADER
                    + ") ON DELETE CASCADE"  // delete download requests when the local message is deleted (likely its ACK)
                + ");"
            );
            db.execSQL("CREATE INDEX IF NOT EXISTS cptDownloadsFkIndex ON " + TABLE_NAME_CPT_ATTACHMENTS_TO_DOWNLOAD + " ("
                    + COLUMNS_MSG_HEADER
                + ")"
            );
            db.execSQL("CREATE INDEX IF NOT EXISTS cptDownloadsIndex ON " + TABLE_NAME_CPT_ATTACHMENTS_TO_DOWNLOAD + " ("
                    + COLUMN_NAME_SOURCE_CROCO_ID + ", "
                    + COLUMN_NAME_CONNECTION_CONDITION
                + ")"
            );
        } catch (SQLException e) {
            Log.e(TAG, "Error on create table '" + TABLE_NAME_CPT_ATTACHMENTS_TO_DOWNLOAD + "' in db '" + DATABASE_NAME + "'", e);
        }

        try {   // create table
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD + " ("
                    + MSG_HEADER_SCHEMA + ", "
                    + COLUMN_NAME_CONNECTION_CONDITION + " integer not null, "
                    + COLUMN_NAME_STORAGE_DIR + " string, "
                    + COLUMN_NAME_URI + " text not null, "
                    + COLUMN_NAME_UPLOAD_FLAG_SENT_TO_RECIPIENT + " integer not null default 0, "
                    + COLUMN_NAME_UPLOAD_FLAG_SENT_TO_APP_SERVER + " integer not null default 0, "
                    + COLUMN_NAME_UPLOAD_FLAG_DELIVERED + " integer not null default 0, "
                    + "PRIMARY KEY ("
                        + COLUMNS_MSG_HEADER + ", "
                        + COLUMN_NAME_STORAGE_DIR + ", "
                        + COLUMN_NAME_URI
                    + "), FOREIGN KEY ("
                        + COLUMNS_MSG_HEADER
                    + ") REFERENCES " + TABLE_NAME_CPT_MESSAGE + " ("
                        + COLUMNS_MSG_HEADER
                    + ") ON DELETE CASCADE"  // delete upload requests when the local message is deleted (likely its ACK)
                + ");"
            );
            db.execSQL("CREATE INDEX IF NOT EXISTS cptUploadsFkIndex ON " + TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD + " ("
                    + COLUMNS_MSG_HEADER
                + ")"
            );
            db.execSQL("CREATE INDEX IF NOT EXISTS cptUploadsIndex ON " + TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD + " ("
                    + COLUMN_NAME_TARGET_CROCO_ID + ", "
                    + COLUMN_NAME_CONNECTION_CONDITION
                    + ")"
            );
        } catch (SQLException e) {
            Log.e(TAG, "Error on create table '" + TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD + "' in db '" + DATABASE_NAME + "'", e);
        }

        try {   // create table
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME_CPT_GCM_TRANSACTIONS + " ("
                    + COLUMN_NAME_GCM_TIME + " integer not null, "
                    + COLUMN_NAME_GCM_MASK + " integer not null"
                    + ");"
            );
        } catch (SQLException e) {
            Log.e(TAG, "Error on create table '" + TABLE_NAME_CPT_GCM_TRANSACTIONS + "' in db '" + DATABASE_NAME + "'", e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading from version " + oldVersion + " to version " + newVersion);
        try {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_CPT_MESSAGE);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_CPT_PERSISTENT_MESSAGE);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_CPT_DEVICES_TRUST);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_CPT_ATTACHMENTS_TO_DOWNLOAD);
            db.execSQL("DROP INDEX IF EXISTS cptDownloadsFkIndex");
            db.execSQL("DROP INDEX IF EXISTS cptDownloadsIndex");
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_CPT_ATTACHMENTS_TO_UPLOAD);
            db.execSQL("DROP INDEX IF EXISTS cptUploadsFkIndex");
            db.execSQL("DROP INDEX IF EXISTS cptUploadsIndex");
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_CPT_GCM_TRANSACTIONS);
            onCreate(db);
        } catch (SQLException e) {
            Log.e(TAG, "Error on update db '" + DATABASE_NAME + "'", e);
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);

        if (!db.isReadOnly()) {
            // Enable foreign key constraints
            db.execSQL("PRAGMA foreign_keys = ON;");
        }
    }
}
