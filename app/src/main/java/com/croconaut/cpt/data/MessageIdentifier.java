package com.croconaut.cpt.data;

import android.content.Context;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

public class MessageIdentifier implements Streamable {
    public static final String BROADCAST_ID = "ff:ff:ff:ff:ff:ff";

    public /*final*/ String appId;
    public /*final*/ String from;   // occasionally it may be null (for attachments)
    public /*final*/ String to; // occasionally it may be null (for attachments)
    public /*final*/ long creationTime;

    public MessageIdentifier(String appId, String from, String to, long creationTime) {
        this.appId = appId;
        this.from = from;
        this.to = to;
        this.creationTime = creationTime;
    }

    @Override
    public String toString() {
        return "MessageIdentifier{" +
                "appId='" + appId + '\'' +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", creationTime='" + new Date(creationTime) + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MessageIdentifier that = (MessageIdentifier) o;

        if (creationTime != that.creationTime) return false;
        if (!appId.equals(that.appId)) return false;
        if (from != null ? !from.equals(that.from) : that.from != null) return false;
        return !(to != null ? !to.equals(that.to) : that.to != null);

    }

    @Override
    public int hashCode() {
        int result = appId.hashCode();
        result = 31 * result + (from != null ? from.hashCode() : 0);
        result = 31 * result + (to != null ? to.hashCode() : 0);
        result = 31 * result + (int) (creationTime ^ (creationTime >>> 32));
        return result;
    }

    // Streamable

    public MessageIdentifier() {
    }

    @Override
    public void readFrom(Context context, DataInputStream dis) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        appId = dis.readUTF();
        from = dis.readUTF();
        to = dis.readUTF();
        creationTime = dis.readLong();
    }

    @Override
    public void writeTo(Context context, DataOutputStream dos) throws IOException {
        dos.writeUTF(appId);
        dos.writeUTF(from);
        dos.writeUTF(to);
        dos.writeLong(creationTime);
    }
}
