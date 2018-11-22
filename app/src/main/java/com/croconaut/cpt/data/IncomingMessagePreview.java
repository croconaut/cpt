package com.croconaut.cpt.data;

import android.os.Parcel;

import com.croconaut.cpt.network.NetworkHop;

import java.util.List;

public class IncomingMessagePreview extends IncomingMessage {
    public final IncomingPayloadPreview payload;

    /* package */ IncomingMessagePreview(long creationTime, String from, String to, List<NetworkHop> hops, IncomingPayloadPreview payload) {
        super(creationTime, from, to, hops);
        this.payload = payload;
    }

    @Override
    public IncomingPayload getPayload() {
        return payload;
    }

    // Parcelable

    protected IncomingMessagePreview(Parcel in) {
        super(in);
        payload = in.readParcelable(IncomingPayloadPreview.class.getClassLoader());
    }

    public static final Creator<IncomingMessagePreview> CREATOR = new Creator<IncomingMessagePreview>() {
        @Override
        public IncomingMessagePreview createFromParcel(Parcel in) {
            return new IncomingMessagePreview(in);
        }

        @Override
        public IncomingMessagePreview[] newArray(int size) {
            return new IncomingMessagePreview[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(payload, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
