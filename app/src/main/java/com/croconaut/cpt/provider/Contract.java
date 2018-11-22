package com.croconaut.cpt.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

@SuppressWarnings("FinalStaticMethod")
public class Contract {
    public static final String getAuthority(Context context) {
        return context.getPackageName() + ".cpt.fileprovider";
    }

    public static final Uri getContentUri(Context context) {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(getAuthority(context))
                .build();
    }

    public static final Uri getRootUri(Context context) {
        return Uri.withAppendedPath(getContentUri(context), "Root");
    }

    public static final Uri getFilesDirUri(Context context) {
        return Uri.withAppendedPath(getContentUri(context), "FilesDir");
    }

    public static final Uri getCacheDirUri(Context context) {
        return Uri.withAppendedPath(getContentUri(context), "CacheDir");
    }

    public static final Uri getExternalFilesDirUri(Context context) {
        return Uri.withAppendedPath(getContentUri(context), "ExternalFilesDir");
    }

    public static final Uri getExternalCacheDirUri(Context context) {
        return Uri.withAppendedPath(getContentUri(context), "ExternalCacheDir");
    }

    public static final Uri getExternalStoragePublicDirectoryUri(Context context) {
        return Uri.withAppendedPath(getContentUri(context), "ExternalStoragePublicDirectory");
    }
}
