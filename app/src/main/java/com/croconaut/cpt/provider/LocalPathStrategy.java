package com.croconaut.cpt.provider;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import com.croconaut.cpt.common.util.FileUtil;

import java.io.File;
import java.io.IOException;

public class LocalPathStrategy extends com.commonsware.cwac.provider.LocalPathStrategy {
    private final Context context;

    public LocalPathStrategy(Context context, String name, File root, boolean readOnly) throws IOException {
        super(name, root, readOnly);

        this.context = context;
    }

    @Override
    public boolean canInsert(Uri uri) {
        return true;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        File file = getFileForUri(uri);
        final File parent = file.getParentFile();

        parent.mkdirs();
        try {
            if (!file.createNewFile()) {
                // file not created because it exists
                file = File.createTempFile(FileUtil.getBaseName(file.getName()).concat("_"), FileUtil.getExtension(file.getName()), parent);
            }
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Failed to create file: " + file);
        }

        return new Uri.Builder()
                .scheme(uri.getScheme())
                .authority(uri.getAuthority())
                .path(uri.getPath().substring(0, uri.getPath().lastIndexOf('/')))
                .appendPath(file.getName())
                .build();
    }

    public long getLastModified(Uri uri) {
        File file = getFileForUri(uri);

        return file.lastModified();
    }
}
