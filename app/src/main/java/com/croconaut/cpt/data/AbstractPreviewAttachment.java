package com.croconaut.cpt.data;

import android.content.Context;
import android.net.Uri;

import java.util.Date;

public abstract class AbstractPreviewAttachment implements MessageAttachment {
    protected String name;  // file name
    protected long length;  // file length
    protected long lastModified;    // last modification time
    protected String type;  // mime type
    protected String sourceUri;
    protected String storageDirectory;

    protected AbstractPreviewAttachment() {
        // used for deserialization
    }

    protected AbstractPreviewAttachment(String sourceUri, String name, long length, long lastModified, String type, String storageDirectory) {
        this.sourceUri = sourceUri;
        this.name = name;
        this.length = length;
        this.lastModified = lastModified;
        this.type = type;
        this.storageDirectory = storageDirectory;
    }

    @Override
    public String getName(Context context) {
        return name;
    }

    @Override
    public long getLength(Context context) {
        return length;
    }

    @Override
    public Date getLastModified(Context context) {
        return new Date(lastModified);
    }

    @Override
    public String getType(Context context) {
        return type;
    }

    @Override
    public String getSourceUri() {
        return sourceUri;
    }

    @Override
    public String getStorageDirectory() {
        return storageDirectory;
    }

    @Override
    public Uri getUri() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "AbstractPreviewAttachment{" +
                "name='" + name + '\'' +
                ", length=" + length +
                ", lastModified=" + new Date(lastModified) +
                ", type='" + type + '\'' +
                ", sourceUri='" + sourceUri + '\'' +
                ", storageDirectory='" + storageDirectory + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractPreviewAttachment that = (AbstractPreviewAttachment) o;

        if (length != that.length) return false;
        if (lastModified != that.lastModified) return false;
        if (!name.equals(that.name)) return false;
        if (!type.equals(that.type)) return false;
        if (!sourceUri.equals(that.sourceUri)) return false;
        return storageDirectory != null ? storageDirectory.equals(that.storageDirectory) : that.storageDirectory == null;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (int) (length ^ (length >>> 32));
        result = 31 * result + (int) (lastModified ^ (lastModified >>> 32));
        result = 31 * result + type.hashCode();
        result = 31 * result + sourceUri.hashCode();
        result = 31 * result + (storageDirectory != null ? storageDirectory.hashCode() : 0);
        return result;
    }
}
