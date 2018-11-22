package com.croconaut.cpt.data;

import android.os.Parcel;

public class DownloadedAttachmentPreview extends AbstractPreviewAttachment implements ParcelableAttachment {
    public DownloadedAttachmentPreview(String sourceUri, String name, long length, long lastModified, String type, String storageDirectory) {
        super(sourceUri, name, length, lastModified, type, storageDirectory);
    }

    // Parcelable

    protected DownloadedAttachmentPreview(Parcel in) {
        // String sourceUri, String name, long length, long lastModified, String type, StorageType storageDirectory
        super(in.readString(), in.readString(), in.readLong(), in.readLong(), in.readString(), in.readString());
    }

    public static final Creator<DownloadedAttachmentPreview> CREATOR = new Creator<DownloadedAttachmentPreview>() {
        @Override
        public DownloadedAttachmentPreview createFromParcel(Parcel in) {
            return new DownloadedAttachmentPreview(in);
        }

        @Override
        public DownloadedAttachmentPreview[] newArray(int size) {
            return new DownloadedAttachmentPreview[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(sourceUri);
        dest.writeString(name);
        dest.writeLong(length);
        dest.writeLong(lastModified);
        dest.writeString(type);
        dest.writeString(storageDirectory);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
