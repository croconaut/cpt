package com.croconaut.cpt.data;

import android.content.Context;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class MessageAttachmentIdentifier implements Streamable {
    private /*final*/ AttachmentIdentifier attachmentIdentifier;
    private /*final*/ MessageIdentifier messageIdentifier;

    public MessageAttachmentIdentifier(AttachmentIdentifier attachmentIdentifier, MessageIdentifier messageIdentifier) {
        this.attachmentIdentifier = attachmentIdentifier;
        this.messageIdentifier = messageIdentifier;
    }

    public String getSourceUri() {
        return attachmentIdentifier.getSourceUri();
    }

    public String getStorageDirectory() {
        return attachmentIdentifier.getStorageDirectory();
    }

    public String getAppId() {
        return messageIdentifier.appId;
    }

    public String getFrom() {
        return messageIdentifier.from;
    }

    public String getTo() {
        return messageIdentifier.to;
    }

    public long getCreationTime() {
        return messageIdentifier.creationTime;
    }

    public AttachmentIdentifier getAttachmentIdentifier() {
        return attachmentIdentifier;
    }

    public MessageIdentifier getMessageIdentifier() {
        return messageIdentifier;
    }

    @Override
    public String toString() {
        return "MessageAttachmentIdentifier{" +
                "attachmentIdentifier=" + attachmentIdentifier +
                ", messageIdentifier=" + messageIdentifier +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MessageAttachmentIdentifier that = (MessageAttachmentIdentifier) o;

        if (!attachmentIdentifier.equals(that.attachmentIdentifier)) return false;
        return messageIdentifier.equals(that.messageIdentifier);

    }

    @Override
    public int hashCode() {
        int result = attachmentIdentifier.hashCode();
        result = 31 * result + messageIdentifier.hashCode();
        return result;
    }

    // Streamable

    public MessageAttachmentIdentifier() {
    }

    @Override
    public void readFrom(Context context, DataInputStream dis) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        attachmentIdentifier = (AttachmentIdentifier) StreamUtil.readStreamableFrom(context, dis);
        messageIdentifier = (MessageIdentifier) StreamUtil.readStreamableFrom(context, dis);
    }

    @Override
    public void writeTo(Context context, DataOutputStream dos) throws IOException {
        StreamUtil.writeStreamableTo(context, dos, attachmentIdentifier);
        StreamUtil.writeStreamableTo(context, dos, messageIdentifier);
    }
}
