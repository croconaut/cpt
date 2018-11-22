package com.croconaut.cpt.data;

import android.os.Parcel;

import com.croconaut.cpt.network.NetworkHop;

import java.util.List;

public class IncomingPersistentMessage extends IncomingMessage {
    public final IncomingPersistentPayload payload;

    IncomingPersistentMessage(long creationTime, String from, String to, List<NetworkHop> hops, IncomingPersistentPayload payload) {
        super(creationTime, from, to, hops);
        this.payload = payload;
    }

    @Override
    public IncomingPayload getPayload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        IncomingPersistentMessage that = (IncomingPersistentMessage) o;

        return payload.equals(that.payload);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + payload.hashCode();
        return result;
    }

    // Parcelable

    protected IncomingPersistentMessage(Parcel in) {
        super(in);
        payload = in.readParcelable(IncomingPersistentPayload.class.getClassLoader());
    }

    public static final Creator<IncomingPersistentMessage> CREATOR = new Creator<IncomingPersistentMessage>() {
        @Override
        public IncomingPersistentMessage createFromParcel(Parcel in) {
            return new IncomingPersistentMessage(in);
        }

        @Override
        public IncomingPersistentMessage[] newArray(int size) {
            return new IncomingPersistentMessage[size];
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
