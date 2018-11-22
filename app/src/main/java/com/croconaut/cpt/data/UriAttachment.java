package com.croconaut.cpt.data;

import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import com.croconaut.cpt.provider.Contract;
import com.croconaut.cpt.provider.FileUtils;
import com.croconaut.cpt.provider.StreamProvider;

import java.io.File;
import java.util.Date;

abstract class UriAttachment implements MessageAttachment {
    private static final String TAG = "data";

    @Override
    public String getName(Context context) {
        String name = "";

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(getUri(), null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return name;
    }

    @Override
    public long getLength(Context context) {
        long length = 0;

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(getUri(), null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                length = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return length;
    }

    @Override
    public Date getLastModified(Context context) {
        long lastModified = 0;

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(getUri(), null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                if (Contract.getAuthority(context).equals(getUri().getAuthority())) {
                    // it's us, just ask
                    lastModified = cursor.getLong(cursor.getColumnIndex(StreamProvider.LAST_MODIFIED));
                }
                else if (FileUtils.isMediaUri(getUri())) {
                    // the MediaProvider table
                    lastModified = cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED));
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, getUri())) {
                    // a DocumentsProvider table
                    lastModified = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED));
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // last resort (unknown CP, most likely a private one)
        if (lastModified == 0) {
            Log.w(TAG, "Unable to resolve a CP for: " + getUri());
            String path = FileUtils.getPath(context, getUri());
            if (path != null) {
                lastModified = new File(path).lastModified();
            }
        }

        return new Date(lastModified);
    }

    @Override
    public String getType(Context context) {
        String mimeType = context.getContentResolver().getType(getUri());
        return mimeType == null ? "" : mimeType;
    }
}
