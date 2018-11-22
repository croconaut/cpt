package com.croconaut.cpt.data;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OutgoingPayload implements MessagePayload {
    private final byte[] mAppData;
    private final ArrayList<LocalAttachment> mAttachments;

    /**
     * Initialize the payload with an app-specific data small enough to be sent in an Intent
     * @param appData       app-specific serializable instance
     * @throws IOException  if there's a serialization error
     */
    public OutgoingPayload(Serializable appData) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(appData);
        oos.close();

        // we convert it into a byte array because we don't want cpt to know about app-specific classes
        mAppData = baos.toByteArray();
        mAttachments = new ArrayList<>();
    }

    public OutgoingPayload(byte[] appData) {
        mAppData = appData;
        mAttachments = new ArrayList<>();
    }

    public OutgoingPayload addAttachment(LocalAttachment attachment) {
        if (attachment == null) {
            throw new IllegalArgumentException("Attachment can't be null");
        }
        mAttachments.add(attachment);

        return this;
    }

    void removeAllAttachments() {
        mAttachments.clear();
    }

    @Override
    public List<? extends MessageAttachment> getAttachments() {
        return mAttachments;
    }

    /* package */ byte[] getRawAppData() {
        return mAppData;
    }

    @Override
    public String toString() {
        return "OutgoingPayload{" +
                "mAppData.length=" + mAppData.length +
                ", mAttachments=" + mAttachments +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OutgoingPayload that = (OutgoingPayload) o;

        if (!Arrays.equals(mAppData, that.mAppData)) return false;
        return mAttachments.equals(that.mAttachments);

    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(mAppData);
        result = 31 * result + mAttachments.hashCode();
        return result;
    }

    // Parcelable

    protected OutgoingPayload(Parcel in) {
        mAppData = in.createByteArray();
        mAttachments = in.createTypedArrayList(LocalAttachment.CREATOR);
    }

    public static final Parcelable.Creator<OutgoingPayload> CREATOR = new Parcelable.Creator<OutgoingPayload>() {
        @Override
        public OutgoingPayload createFromParcel(Parcel in) {
            return new OutgoingPayload(in);
        }

        @Override
        public OutgoingPayload[] newArray(int size) {
            return new OutgoingPayload[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(mAppData);
        dest.writeTypedList(mAttachments);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
