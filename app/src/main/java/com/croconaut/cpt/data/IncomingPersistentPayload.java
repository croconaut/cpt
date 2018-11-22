package com.croconaut.cpt.data;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class IncomingPersistentPayload extends IncomingPayload {
    private final ArrayList<DownloadedAttachment> mAttachments;

    /* package */ IncomingPersistentPayload(byte[] rawAppData) {
        super(rawAppData);
        mAttachments = new ArrayList<>();
    }

    /* package */ IncomingPersistentPayload addAttachment(DownloadedAttachment attachment) {
        if (attachment == null) {
            throw new IllegalArgumentException("Attachment can't be null");
        }
        mAttachments.add(attachment);

        return this;
    }

    @Override
    public List<? extends MessageAttachment> getAttachments() {
        return mAttachments;
    }

    @Override
    public String toString() {
        return super.toString() + ", IncomingPersistentPayload{" +
                ", mAttachments=" + mAttachments +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IncomingPersistentPayload that = (IncomingPersistentPayload) o;

        return mAttachments.equals(that.mAttachments);

    }

    @Override
    public int hashCode() {
        return mAttachments.hashCode();
    }

    // Parcelable

    protected IncomingPersistentPayload(Parcel in) {
        super(in);
        mAttachments = in.createTypedArrayList(DownloadedAttachment.CREATOR);
    }

    public static final Parcelable.Creator<IncomingPersistentPayload> CREATOR = new Parcelable.Creator<IncomingPersistentPayload>() {
        @Override
        public IncomingPersistentPayload createFromParcel(Parcel in) {
            return new IncomingPersistentPayload(in);
        }

        @Override
        public IncomingPersistentPayload[] newArray(int size) {
            return new IncomingPersistentPayload[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeTypedList(mAttachments);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
