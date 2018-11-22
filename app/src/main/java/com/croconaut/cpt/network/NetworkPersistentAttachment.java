package com.croconaut.cpt.network;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

// this attachment will be stored straight away. if there are two identical uris in both PersistentNetworkAttachment
// and some other attachment, we'll send them separately, it shouldn't do much harm as persistent
// attachments are supposed to be small (and perhaps not very suited for sharing with others -- they reside in a private storage directory)
public class NetworkPersistentAttachment implements StreamableAttachment {
    private static final String TAG = "network";

    private /*final*/ String sourceUri;
    private /*final*/ String name;  // file name to send over network
    private /*final*/ long length;
    private /*final*/ String appId; // used only on the app server but why not

    private /*transient*/ Uri uri;  // read in writeObject(), written in readObject()

    public NetworkPersistentAttachment(Uri srcUri, String name, long length, String appId) {
        this.sourceUri = srcUri.toString();
        this.name = name;
        this.length = length;
        this.appId = appId;
        this.uri = srcUri;
    }

    // these five methods are the only ones allowed. time & type is irrelevant for persistent messages,
    // they can be requested by the app in local attachment when delivered
    @Override
    public String getName(Context context) {
        return name;
    }

    @Override
    public long getLength(Context context) {
        return length;
    }

    @Override
    public String getStorageDirectory() {
        // private only
        return null;
    }

    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public String getSourceUri() {
        // not really useful
        return sourceUri;
    }

    @Override
    public Date getLastModified(Context context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Context context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "NetworkPersistentAttachment{" +
                "sourceUri='" + sourceUri + '\'' +
                ", name='" + name + '\'' +
                ", length=" + length +
                ", uri=" + uri +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NetworkPersistentAttachment that = (NetworkPersistentAttachment) o;

        if (!sourceUri.equals(that.sourceUri)) return false;
        if (!name.equals(that.name)) return false;
        return uri.equals(that.uri);

    }

    @Override
    public int hashCode() {
        int result = sourceUri.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + uri.hashCode();
        return result;
    }

    // Streamable

    public NetworkPersistentAttachment() {
    }

    @Override
    public void readFrom(Context context, DataInputStream dis) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        sourceUri = dis.readUTF();
        name = dis.readUTF();
        for (int i = name.lastIndexOf('.'); i > 0; i = -1) {
            name = name.substring(0, i+1).concat(name.substring(i+1).toLowerCase());
        }
        length = dis.readLong();
        appId = dis.readUTF();

        boolean isValid = dis.readBoolean();
        if (!isValid) {
            throw new FileNotFoundException("Unable to download this attachment: " + this);
        }

        // uri is private to the client app and the app is installed for sure so only app backward compatibility is the concern here
        Uri uri = Uri.parse(sourceUri);
        uri = context.getContentResolver().insert(uri, new ContentValues());

        ParcelFileDescriptor parcelFileDescriptor = null;
        FileOutputStream fos = null;
        try {
            parcelFileDescriptor = context.getContentResolver().openFileDescriptor(uri, "w");
            if (parcelFileDescriptor != null) {
                fos = new FileOutputStream(parcelFileDescriptor.getFileDescriptor());

                try {
                    byte[] buffer = new byte[NetworkUtil.ATTACHMENT_BUFFER_SIZE];
                    int len;
                    long total = 0;
                    while (total != length && (len = dis.read(buffer, 0, Math.min(buffer.length, (int) (length - total)))) != -1) {
                        fos.write(buffer, 0, len);
                        total += len;
                    }

                    if (total !=length) {
                        throw new IOException("read() ended prematurely");
                    }

                    this.uri = uri;
                } catch (IOException e) {
                    context.getContentResolver().delete(uri, null, null);
                    throw e;
                }
            } else {
                throw new UnknownError("Unable to get parcelFileDescriptor");
            }
        } finally {
            if (fos != null) {
                fos.close();
            }
            if (parcelFileDescriptor != null) {
                parcelFileDescriptor.close();
            }
        }
    }

    @Override
    public void writeTo(Context context, DataOutputStream dos) throws IOException {
        dos.writeUTF(sourceUri);
        dos.writeUTF(name);
        dos.writeLong(length);
        dos.writeUTF(appId);

        ParcelFileDescriptor parcelFileDescriptor = null;
        FileInputStream fis = null;
        try {
            parcelFileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");
            if (parcelFileDescriptor != null) {
                dos.writeBoolean(true); // stream valid

                fis = new FileInputStream(parcelFileDescriptor.getFileDescriptor());

                byte[] buffer = new byte[NetworkUtil.ATTACHMENT_BUFFER_SIZE];
                int len;
                long total = 0;
                while (total != length && (len = fis.read(buffer, 0, Math.min(buffer.length, (int) (length - total)))) != -1) {
                    dos.write(buffer, 0, len);
                    total += len;
                }

                if (total != length) {
                    throw new IOException("read() ended prematurely");
                }
            } else {
                throw new FileNotFoundException("Uri not found: " + uri);
            }
        } catch (FileNotFoundException e){
            dos.writeBoolean(false);    // stream invalid
            throw e;
        } finally {
            if (fis != null) {
                fis.close();
            }
            if (parcelFileDescriptor != null) {
                parcelFileDescriptor.close();
            }
        }
    }
}
