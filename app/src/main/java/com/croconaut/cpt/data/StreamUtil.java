package com.croconaut.cpt.data;

import android.content.Context;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

public class StreamUtil {
    public static Collection<? extends Streamable> readStreamablesFrom(Context context, DataInputStream dis) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        final String collectionClassName = dis.readUTF();
        final int streamablesSize = dis.readInt();

        @SuppressWarnings("unchecked") Collection<Streamable> streamables = (Collection<Streamable>) Class.forName(collectionClassName).newInstance();
        for (int i = 0; i < streamablesSize; ++i) {
            streamables.add(readStreamableFrom(context, dis));
        }

        return streamables;
    }

    public static Streamable readStreamableFrom(Context context, DataInputStream dis) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        Streamable streamable = (Streamable) Class.forName(dis.readUTF()).newInstance();
        streamable.readFrom(context, dis);
        return streamable;
    }

    public static void writeStreamablesTo(Context context, DataOutputStream dos, Collection<? extends Streamable> streamables) throws IOException {
        dos.writeUTF(streamables.getClass().getName());
        dos.writeInt(streamables.size());

        for (Streamable streamable : streamables) {
            writeStreamableTo(context, dos, streamable);
        }
    }

    public static void writeStreamableTo(Context context, DataOutputStream dos, Streamable streamable) throws IOException {
        dos.writeUTF(streamable.getClass().getName());
        streamable.writeTo(context, dos);
    }

    public static byte[] readByteArray(InputStream is, int length) throws IOException {
        byte[] bytes = new byte[length];

        int byteOffset = 0;
        int byteCount = length;
        for (int readBytes = is.read(bytes, byteOffset, byteCount); readBytes >= 0 && byteCount > 0; readBytes = is.read(bytes, byteOffset, byteCount)) {
            byteOffset += readBytes;
            byteCount  -= readBytes;
        }

        if (byteCount > 0) {
            throw new IOException("read() ended prematurely");
        }

        return bytes;
    }

    public static void writeByteArray(OutputStream os, byte[] bytes) throws IOException {
        // when out.write returns, all bytes have been written to the local sending buffer
        os.write(bytes);
    }
}
