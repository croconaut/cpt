package com.croconaut.cpt.provider;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import com.commonsware.cwac.provider.StreamStrategy;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class StreamProvider extends com.commonsware.cwac.provider.StreamProvider {
    public static final String             LAST_MODIFIED = "_modified";

    // only local strategies are supported
    private static final String            TAG_ROOT_PATH = "root-path";
    private static final String           TAG_FILES_PATH = "files-path";
    private static final String           TAG_CACHE_PATH = "cache-path";
    private static final String             TAG_EXTERNAL = "external-path";
    private static final String       TAG_EXTERNAL_FILES = "external-files-path";
    private static final String TAG_EXTERNAL_CACHE_FILES = "external-cache-path";
    private static final File                DEVICE_ROOT = new File("/");

    @Override
    protected Object getValueForQueryColumn(Uri uri, String col) {
        Object result;

        if (LAST_MODIFIED.equals(col) && getRootStrategy().getStrategy(uri) instanceof LocalPathStrategy) {
            result = ((LocalPathStrategy) (getRootStrategy().getStrategy(uri))).getLastModified(uri);
        } else {
            result = super.getValueForQueryColumn(uri, col);
        }

        return result;
    }

    @Override
    protected StreamStrategy buildStrategy(Context context, String tag, String name, String path, boolean readOnly, HashMap<String, String> attrs) throws IOException {
        File target = null;

        // we want insert() and update() so we have to override handling for all the supported tags
        if (TAG_ROOT_PATH.equals(tag)) {
            target = buildPath(DEVICE_ROOT, path);
        } else if (TAG_FILES_PATH.equals(tag)) {
//            if (TextUtils.isEmpty(path)) {
//                throw new SecurityException("Cannot serve files from all of getFilesDir()");
//            }
            target = buildPath(context.getFilesDir(), path);
        } else if (TAG_CACHE_PATH.equals(tag)) {
            target = buildPath(context.getCacheDir(), path);
        } else if (TAG_EXTERNAL.equals(tag)) {
            target = buildPath(Environment.getExternalStorageDirectory(), path);
        } else if (TAG_EXTERNAL_FILES.equals(tag)) {
            target = buildPath(context.getExternalFilesDir(null), path);
        } else if (TAG_EXTERNAL_CACHE_FILES.equals(tag)) {
            target = buildPath(context.getExternalCacheDir(), path);
        }

        if (target != null) {
            return new LocalPathStrategy(context, name, target, readOnly);
        } else {
            return super.buildStrategy(context, tag, name, path, readOnly, attrs);
        }
    }

    @Override
    protected String getUriPrefix() {
        // don't use uri prefixes
        return null;
    }
}