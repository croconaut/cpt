package com.croconaut.cpt.data;

import android.content.Context;
import android.net.Uri;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

class LocalDbAttachment extends UriAttachment implements Streamable {
    private /*transient*/ Uri uri;
    private /*final*/ String storageDirectory;
    private /*final*/ String sourceUri;
    private /*final*/ String name;
    private /*final*/ long length;

    LocalDbAttachment(Uri uri, String storageDirectory) {
        this.uri = uri;
        this.storageDirectory = storageDirectory;
        this.sourceUri = uri.toString();    // only for serialization
        this.name = null;
        this.length = -1;
    }

    LocalDbAttachment(Uri uri, String storageDirectory, String name, long length) {
        this.uri = uri;
        this.storageDirectory = storageDirectory;
        this.sourceUri = uri.toString();    // only for serialization
        this.name = name;
        this.length = length;
    }

    @Override
    public String getName(Context context) {
        return name == null ? super.getName(context) : name;
    }

    @Override
    public long getLength(Context context) {
        return length == -1 ? super.getLength(context) : length;
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
        return sourceUri;
    }

    @Override
    public String toString() {
        return "LocalDbAttachment{" +
                "uri=" + uri +
                ", storageDirectory='" + storageDirectory + '\'' +
                ", name='" + name + '\'' +
                ", length=" + length +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocalDbAttachment that = (LocalDbAttachment) o;

        if (!uri.equals(that.uri)) return false;
        return storageDirectory != null ? storageDirectory.equals(that.storageDirectory) : that.storageDirectory == null;

    }

    @Override
    public int hashCode() {
        int result = uri.hashCode();
        result = 31 * result + (storageDirectory != null ? storageDirectory.hashCode() : 0);
        return result;
    }

    // Streamable

    public LocalDbAttachment() {
        length = -1;
    }

    @Override
    public void readFrom(Context context, DataInputStream dis) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        if (dis.readBoolean()) {
            storageDirectory = dis.readUTF();
        }
        sourceUri = dis.readUTF();
        if (dis.readBoolean()) {
            name = dis.readUTF();
            length = dis.readLong();
        }

        uri = Uri.parse(getSourceUri());
    }

    @Override
    public void writeTo(Context context, DataOutputStream dos) throws IOException {
        if (storageDirectory == null) {
            dos.writeBoolean(false);
        } else {
            dos.writeBoolean(true);
            dos.writeUTF(storageDirectory);
        }
        dos.writeUTF(sourceUri);
        if (name == null) {
            dos.writeBoolean(false);
        } else {
            dos.writeBoolean(true);
            dos.writeUTF(name);
            dos.writeLong(length);
        }
    }
}
