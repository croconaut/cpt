package com.croconaut.cpt.data;

import android.os.Parcel;
import android.os.Parcelable;

import com.croconaut.cpt.network.NetworkHop;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static junit.framework.Assert.assertNull;

public abstract class IncomingMessage implements Parcelable {
    // no other information is needed for client app
    private final long creationTime;
    private final String from;
    // except the special case where it gets its 'sent' message
    private final String to;

    private final ArrayList<NetworkHop> hops;

    protected IncomingMessage(long creationTime, String from, String to, List<NetworkHop> hops) {
        this.creationTime = creationTime;
        this.from = from;
        this.to = to;
        this.hops = new ArrayList<>(hops);  // most like this is an ArrayList -> ArrayList operation but let's be safe

        if (this.from != null) {
            assertNull(this.to);
        }
        if (this.to != null) {
            assertNull(this.from);
        }
    }

    public long getId() {
        return creationTime;
    }

    public Date getCreationTime() {
        return new Date(creationTime);
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public ArrayList<NetworkHop> getHops() {
        return hops;
    }

    public abstract IncomingPayload getPayload();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IncomingMessage that = (IncomingMessage) o;

        if (creationTime != that.creationTime) return false;
        if (from != null ? !from.equals(that.from) : that.from != null) return false;
        return to != null ? to.equals(that.to) : that.to == null;
    }

    @Override
    public int hashCode() {
        int result = (int) (creationTime ^ (creationTime >>> 32));
        result = 31 * result + (from != null ? from.hashCode() : 0);
        result = 31 * result + (to != null ? to.hashCode() : 0);
        return result;
    }

    // Parcelable

    protected IncomingMessage(Parcel in) {
        creationTime = in.readLong();
        from = in.readString();
        to = in.readString();
        hops = in.createTypedArrayList(NetworkHop.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(creationTime);
        dest.writeString(from);
        dest.writeString(to);
        dest.writeTypedList(hops);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
