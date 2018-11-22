package com.croconaut.cpt.data;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;

public class DownloadedAttachment extends LocalAttachment {
    private final String name;  // file name
    private final String sourceUri;

    public DownloadedAttachment(Uri uri, String storageDirectory, String name, String srcUri) {
        super(uri, storageDirectory);
        this.name = name;
        this.sourceUri = srcUri;
    }

    @Override
    public String getName(Context context) {
        // return the original name, not the one from our new uri
        return name;
    }

    @Override
    public String getSourceUri() {
        // not equal to the uri anymore
        return sourceUri;
    }

    @Override
    public String toString() {
        return super.toString() + ", DownloadedAttachment{" +
                "name='" + name + '\'' +
                "sourceUri='" + sourceUri + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        DownloadedAttachment that = (DownloadedAttachment) o;

        if (!name.equals(that.name)) return false;
        return sourceUri.equals(that.sourceUri);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + sourceUri.hashCode();
        return result;
    }

    // Parcelable

    protected DownloadedAttachment(Parcel in) {
        super(in);

        name = in.readString();
        sourceUri = in.readString();
    }

    public static final Creator<DownloadedAttachment> CREATOR = new Creator<DownloadedAttachment>() {
        @Override
        public DownloadedAttachment createFromParcel(Parcel in) {
            return new DownloadedAttachment(in);
        }

        @Override
        public DownloadedAttachment[] newArray(int size) {
            return new DownloadedAttachment[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);

        dest.writeString(name);
        dest.writeString(sourceUri);
    }
}
