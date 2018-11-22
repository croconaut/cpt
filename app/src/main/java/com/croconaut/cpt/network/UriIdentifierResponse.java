package com.croconaut.cpt.network;

import android.content.Context;

import com.croconaut.cpt.common.util.FileUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Set;

public class UriIdentifierResponse extends UriIdentifier {
    private /*final*/ String name;  // file name
    private /*final*/ long length;  // file length
    private /*final*/ long lastModified;    // last modification time
    private /*final*/ String type;  // mime type
    private /*transient*/ Date timeSent;
    private /*transient*/ int bytesPerSecondSent;

    public UriIdentifierResponse(String sourceUri, Set<String> storageDirectories, String name, long length, long lastModified, String type) {
        super(sourceUri, storageDirectories);

        this.name = name;
        this.length = length;
        this.lastModified = lastModified;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public long getLength() {
        return length;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getType() {
        return type;
    }


    public Date getTimeSent() {
        return timeSent;
    }

    public void setTimeSent(Date timeSent) {
        this.timeSent = timeSent;
    }

    public int getBytesPerSecondSent() {
        return bytesPerSecondSent;
    }

    public void setBytesPerSecondSent(int bytesPerSecondSent) {
        this.bytesPerSecondSent = bytesPerSecondSent;
    }

    @Override
    public String toString() {
        return super.toString() + ", UriIdentifierResponse{" +
                "name='" + name + '\'' +
                ", length=" + length +
                ", lastModified=" + new Date(lastModified) +
                ", type='" + type + '\'' +
                '}';
    }

    // don't overload equals() and hashCode() -- we want them to behave the same as the base class

    // Streamable

    public UriIdentifierResponse() {
    }

    @Override
    public void readFrom(Context context, DataInputStream dis) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        super.readFrom(context, dis);

        name = dis.readUTF();
        name = FileUtil.getBaseName(name).concat(FileUtil.getExtension(name).toLowerCase());
        length = dis.readLong();
        lastModified = dis.readLong();
        type = dis.readUTF();
    }

    @Override
    public void writeTo(Context context, DataOutputStream dos) throws IOException {
        super.writeTo(context, dos);

        dos.writeUTF(name);
        dos.writeLong(length);
        dos.writeLong(lastModified);
        dos.writeUTF(type);
    }
}
