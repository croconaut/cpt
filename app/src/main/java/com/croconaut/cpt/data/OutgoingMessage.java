package com.croconaut.cpt.data;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;

public class OutgoingMessage implements Parcelable {
    // one week, in ms
    private static final int DEFAULT_EXPIRATION_TIME = 7 * 24 * 60 * 60 * 1000;
    // maximum attachments size for persistent message, in bytes
    private static final int MAX_PERSISTENT_ATTACHMENTS_SIZE = 128 * 1024;

    boolean isLocal = false;
    boolean isExpectingSent = true;
    boolean isExpectingAck = true;
    final int persistentId;

    final long creationTime;
    final String to;
    int expirationTime;
    OutgoingPayload payload;

    public OutgoingMessage(String to) {
        synchronized (OutgoingMessage.class) {
            // we want to be sure that the date is unique (let's ignore the performance side for now)
            // within the given app (i.e. protection against creating messages in a multithreaded env.)
            this.creationTime = System.currentTimeMillis();
        }
        this.to = to;
        this.expirationTime = DEFAULT_EXPIRATION_TIME;
        this.persistentId = -1;
    }

    public OutgoingMessage(String to, OutgoingPayload payload) {
        this(to);
        this.payload = payload;
    }

    protected OutgoingMessage(String to, int persistentId) {
        synchronized (OutgoingMessage.class) {
            // we want to be sure that the date is unique (let's ignore the performance side for now)
            // within the given app (i.e. protection against creating messages in a multithreaded env.)
            this.creationTime = System.currentTimeMillis();
        }
        this.to = to;
        // expirationTime is not used in the persistent messages table
        this.persistentId = persistentId;
    }

    protected OutgoingMessage(String to, OutgoingPayload payload, int persistentId) {
        this(to, persistentId);
        this.payload = payload;
    }

    public long getId() {
        return creationTime;
    }

    public Date getCreationDate() {
        return new Date(creationTime);
    }

    /**
     * Set the expiration time for given message.
     * @param expirationTime expiration time in minutes
     */
    @SuppressWarnings("PointlessArithmeticExpression")
    public OutgoingMessage setExpirationTime(int expirationTime) {
        int adjusted = expirationTime * 60 * 1000;
        if (adjusted <= 0) {
            adjusted = 1 * 60 * 1000;   // one minute minimum
        } else if (adjusted > DEFAULT_EXPIRATION_TIME) {
            adjusted = DEFAULT_EXPIRATION_TIME;
        }
        this.expirationTime = adjusted;
        return this;
    }

    public OutgoingMessage setPayload(OutgoingPayload payload) {
        this.payload = payload;
        return this;
    }

    public OutgoingMessage setIsExpectingSent(boolean isExpectingSent) {
        this.isExpectingSent = isExpectingSent;
        return this;
    }

    public OutgoingMessage setIsExpectingAck(boolean isExpectingAck) {
        this.isExpectingAck = isExpectingAck;
        return this;
    }

    @SuppressWarnings("PointlessArithmeticExpression")
    OutgoingMessage sanitize(Context context) {
        if (!payload.getAttachments().isEmpty() && !to.equals(MessageIdentifier.BROADCAST_ID)) {
            // private messages with attachments must be local while broadcast messages are supposed
            // to be public, i.e. spreading immediately even with an attachment (TODO: some safety checks)
            isLocal = true;
        }

        if (!payload.getAttachments().isEmpty() && persistentId != -1) {
            // persistent messages are allowed to send only limited amount of bytes per message
            long totalBytes = 0;
            for (MessageAttachment attachment : payload.getAttachments()) {
                totalBytes += attachment.getLength(context);
            }
            if (totalBytes > MAX_PERSISTENT_ATTACHMENTS_SIZE) {
                // sorry...
                payload.removeAllAttachments();
            }
        }

        if (persistentId != -1) {
            // on the other hand, if it's a persistent message, it surely can't be local
            isLocal = false;
            isExpectingSent = false;
            isExpectingAck = false;
        }

        return this;
    }

    @Override
    public String toString() {
        return "OutgoingMessage{" +
                "isLocal=" + isLocal +
                ", isExpectingSent=" + isExpectingSent +
                ", isExpectingAck=" + isExpectingAck +
                ", persistentId=" + persistentId +
                ", creationTime=" + getCreationDate() +
                ", to='" + to + '\'' +
                ", expirationTime=" + expirationTime +
                ", payload=" + payload +
                '}';
    }

    // Parcelable

    protected OutgoingMessage(Parcel in) {
        isLocal = in.readByte() != 0;
        isExpectingSent = in.readByte() != 0;
        isExpectingAck = in.readByte() != 0;
        persistentId = in.readInt();
        creationTime = in.readLong();
        to = in.readString();
        expirationTime = in.readInt();
        payload = in.readParcelable(OutgoingPayload.class.getClassLoader());
    }

    public static final Creator<OutgoingMessage> CREATOR = new Creator<OutgoingMessage>() {
        @Override
        public OutgoingMessage createFromParcel(Parcel in) {
            return new OutgoingMessage(in);
        }

        @Override
        public OutgoingMessage[] newArray(int size) {
            return new OutgoingMessage[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (isLocal ? 1 : 0));
        dest.writeByte((byte) (isExpectingSent ? 1 : 0));
        dest.writeByte((byte) (isExpectingAck ? 1 : 0));
        dest.writeInt(persistentId);
        dest.writeLong(creationTime);
        dest.writeString(to);
        dest.writeInt(expirationTime);
        dest.writeParcelable(payload, flags);
    }
}
