package com.croconaut.cpt.data;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class IncomingPayloadPreview extends IncomingPayload {

    private final ArrayList<DownloadedAttachmentPreview> mAttachments;

    /* package */ IncomingPayloadPreview(byte[] rawAppData) {
        super(rawAppData);
        mAttachments = new ArrayList<>();
    }

    /* package */ IncomingPayloadPreview addAttachment(DownloadedAttachmentPreview attachment) {
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
        return super.toString() + ", IncomingPayloadPreview{" +
                ", mAttachments=" + mAttachments +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IncomingPayloadPreview that = (IncomingPayloadPreview) o;

        return mAttachments.equals(that.mAttachments);

    }

    @Override
    public int hashCode() {
        return mAttachments.hashCode();
    }

    // Parcelable

    protected IncomingPayloadPreview(Parcel in) {
        super(in);
        mAttachments = in.createTypedArrayList(DownloadedAttachmentPreview.CREATOR);
    }

    public static final Parcelable.Creator<IncomingPayloadPreview> CREATOR = new Parcelable.Creator<IncomingPayloadPreview>() {
        @Override
        public IncomingPayloadPreview createFromParcel(Parcel in) {
            return new IncomingPayloadPreview(in);
        }

        @Override
        public IncomingPayloadPreview[] newArray(int size) {
            return new IncomingPayloadPreview[size];
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
