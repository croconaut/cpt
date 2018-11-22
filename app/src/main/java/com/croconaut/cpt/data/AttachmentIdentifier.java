package com.croconaut.cpt.data;

import android.content.Context;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class AttachmentIdentifier implements Streamable {
    private /*final*/ String sourceUri;
    private /*final*/ String storageDirectory;

    public AttachmentIdentifier(String sourceUri, String storageDirectory) {
        this.sourceUri = sourceUri;
        this.storageDirectory = storageDirectory;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public String getStorageDirectory() {
        return storageDirectory;
    }

    @Override
    public String toString() {
        return "AttachmentIdentifier{" +
                "sourceUri='" + sourceUri + '\'' +
                ", storageDirectory='" + storageDirectory + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AttachmentIdentifier that = (AttachmentIdentifier) o;

        if (!sourceUri.equals(that.sourceUri)) return false;
        return storageDirectory != null ? storageDirectory.equals(that.storageDirectory) : that.storageDirectory == null;

    }

    @Override
    public int hashCode() {
        int result = sourceUri.hashCode();
        result = 31 * result + (storageDirectory != null ? storageDirectory.hashCode() : 0);
        return result;
    }

    // Streamable

    public AttachmentIdentifier() {
    }

    @Override
    public void readFrom(Context context, DataInputStream dis) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        sourceUri = dis.readUTF();
        if (dis.readBoolean()) {
            storageDirectory = dis.readUTF();
        }

    }

    @Override
    public void writeTo(Context context, DataOutputStream dos) throws IOException {
        dos.writeUTF(sourceUri);
        if (storageDirectory != null) {
            dos.writeBoolean(true);
            dos.writeUTF(storageDirectory);
        } else {
            dos.writeBoolean(false);
        }
    }
}
