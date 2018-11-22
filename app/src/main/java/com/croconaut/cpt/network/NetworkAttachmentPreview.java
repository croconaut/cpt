package com.croconaut.cpt.network;

import android.content.Context;

import com.croconaut.cpt.data.AbstractPreviewAttachment;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class NetworkAttachmentPreview extends AbstractPreviewAttachment implements StreamableAttachment {
    public NetworkAttachmentPreview(String sourceUri, String name, long length, long lastModified, String type, String storageDirectory) {
        super(sourceUri, name, length, lastModified, type, storageDirectory);
    }

    // Streamable

    public NetworkAttachmentPreview() {
    }

    @Override
    public void readFrom(Context context, DataInputStream dis) throws IOException, ClassNotFoundException {
        name = dis.readUTF();
        length = dis.readLong();
        lastModified = dis.readLong();
        type = dis.readUTF();
        sourceUri = dis.readUTF();
        if (dis.readBoolean()) {
            storageDirectory = dis.readUTF();
        }
    }

    @Override
    public void writeTo(Context context, DataOutputStream dos) throws IOException {
        dos.writeUTF(name);
        dos.writeLong(length);
        dos.writeLong(lastModified);
        dos.writeUTF(type);
        dos.writeUTF(sourceUri);
        if (storageDirectory == null) {
            dos.writeBoolean(false);
        } else {
            dos.writeBoolean(true);
            dos.writeUTF(storageDirectory);
        }
    }
}
