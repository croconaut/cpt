package com.croconaut.cpt.data;

import android.content.Context;
import android.net.Uri;

import java.util.Date;

public interface MessageAttachment {
    String getName(Context context);

    long getLength(Context context);

    Date getLastModified(Context context);

    String getType(Context context);

    String getStorageDirectory();

    Uri getUri();

    String getSourceUri();
}
