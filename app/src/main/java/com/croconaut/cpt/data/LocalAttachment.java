package com.croconaut.cpt.data;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;

import com.croconaut.cpt.provider.Contract;

public class LocalAttachment extends UriAttachment implements ParcelableAttachment {
    private final Uri uri;
    private final String storageDirectory;
    private final boolean wifiOnly;

    private LocalAttachment(Uri srcUri, String dstStorageDirectory, boolean wifiOnly) {
        this.uri = srcUri;
        this.storageDirectory = dstStorageDirectory;
        this.wifiOnly = wifiOnly;
    }

    protected LocalAttachment(Uri srcUri, String dstStorageDirectory) {
        this(srcUri, dstStorageDirectory, false);
    }

    public LocalAttachment(Context context, Uri srcUri, boolean wifiOnly) {
        this(srcUri, null, wifiOnly);

        if (!Contract.getAuthority(context).equals(srcUri.getAuthority())) {
            throw new IllegalStateException("You can't set private storage directory for public uris");
        }
    }

    public LocalAttachment(Context context, Uri srcUri) {
        this(context, srcUri, false);
    }

    public LocalAttachment(Context context, Uri srcUri, String dstStorageDirectory, boolean wifiOnly) {
        this(srcUri, dstStorageDirectory, wifiOnly);

        if (!Contract.getAuthority(context).equals(srcUri.getAuthority()) && dstStorageDirectory == null) {
            throw new IllegalStateException("You can't set private storage directory for public uris");
        }
    }

    public LocalAttachment(Context context, Uri srcUri, String dstStorageDirectory) {
        this(context, srcUri, dstStorageDirectory, false);
    }

    public boolean isWifiOnly() {
        return wifiOnly;
    }

    @Override
    public String getStorageDirectory() {
        return storageDirectory;
    }

    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public String getSourceUri() {
        return uri.toString();
    }

    @Override
    public String toString() {
        return "LocalAttachment{" +
                "uri=" + uri +
                ", storageDirectory='" + storageDirectory + '\'' +
                ", wifiOnly=" + wifiOnly +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocalAttachment that = (LocalAttachment) o;

        if (!uri.equals(that.uri)) return false;
        return storageDirectory != null ? storageDirectory.equals(that.storageDirectory) : that.storageDirectory == null;

    }

    @Override
    public int hashCode() {
        int result = uri.hashCode();
        result = 31 * result + (storageDirectory != null ? storageDirectory.hashCode() : 0);
        return result;
    }

    // Parcelable

    protected LocalAttachment(Parcel in) {
        uri = in.readParcelable(Uri.class.getClassLoader());
        storageDirectory = (String) in.readValue(String.class.getClassLoader());
        wifiOnly = in.readInt() == 1;
    }

    public static final Creator<LocalAttachment> CREATOR = new Creator<LocalAttachment>() {
        @Override
        public LocalAttachment createFromParcel(Parcel in) {
            return new LocalAttachment(in);
        }

        @Override
        public LocalAttachment[] newArray(int size) {
            return new LocalAttachment[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(uri, flags);
        dest.writeValue(storageDirectory);
        dest.writeInt(wifiOnly ? 1 : 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
