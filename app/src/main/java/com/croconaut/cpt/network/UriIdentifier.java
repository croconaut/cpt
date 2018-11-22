package com.croconaut.cpt.network;

import android.content.Context;

import com.croconaut.cpt.data.Streamable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class UriIdentifier implements Streamable {
    private /*final*/ String sourceUri;
    private final Set<String> storageDirectories = new HashSet<>();

    public UriIdentifier(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    protected UriIdentifier(String sourceUri, Set<String> storageDirectories) {
        this.sourceUri = sourceUri;
        this.storageDirectories.addAll(storageDirectories);
    }

    public void addPrivateStorage() {
        storageDirectories.add(null);
    }

    public void addPublicStorage(String storageDirectory) {
        storageDirectories.add(storageDirectory);
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public Set<String> getStorageDirectories() {
        return storageDirectories;
    }

    @Override
    public String toString() {
        return "UriIdentifier{" +
                "sourceUri='" + sourceUri + '\'' +
                ", storageDirectories=" + storageDirectories +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UriIdentifier that = (UriIdentifier) o;

        return sourceUri.equals(that.sourceUri);

    }

    @Override
    public int hashCode() {
        return sourceUri.hashCode();
    }

    // Streamable

    public UriIdentifier() {
    }

    @Override
    public void readFrom(Context context, DataInputStream dis) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        boolean containsNull = dis.readBoolean();
        int count = dis.readInt();

        if (containsNull) {
            storageDirectories.add(null);
        }
        for (int i = 0; i < count; ++i) {
            storageDirectories.add(dis.readUTF());
        }

        sourceUri = dis.readUTF();
    }

    @Override
    public void writeTo(Context context, DataOutputStream dos) throws IOException {
        boolean containsNull = storageDirectories.contains(null);

        dos.writeBoolean(containsNull);
        dos.writeInt(containsNull ? storageDirectories.size() - 1 : storageDirectories.size());
        for (String storageDirectory: storageDirectories) {
            if (storageDirectory != null) {
                dos.writeUTF(storageDirectory);
            }
        }

        dos.writeUTF(sourceUri);
    }
}
