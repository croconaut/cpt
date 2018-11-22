package com.croconaut.cpt.data;

import android.os.Parcel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Arrays;

public abstract class IncomingPayload implements MessagePayload {
    private final byte[] mAppData;

    protected IncomingPayload(byte[] rawAppData) {
        mAppData = rawAppData;
    }

    /**
     * Get app-specific data associated with this payload
     * @return                          app-specific deserialized instance
     * @throws IOException              if there's a deserialization error
     * @throws ClassNotFoundException   if there's a deserialization error
     */
    public Serializable getAppData() throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(mAppData);

        ObjectInputStream ois = new ObjectInputStream(bais);
        Serializable data = (Serializable) ois.readObject();
        ois.close();

        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IncomingPayload that = (IncomingPayload) o;

        return Arrays.equals(mAppData, that.mAppData);

    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mAppData);
    }

    // Parcelable

    protected IncomingPayload(Parcel in) {
        mAppData = in.createByteArray();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(mAppData);
    }
}
