package com.croconaut.cpt.data;

import android.os.Parcel;

public class OutgoingPersistentMessage extends OutgoingMessage {
    public OutgoingPersistentMessage(String to, int persistentId) {
        super(to, persistentId);

        if (persistentId < 0) {
            throw new IllegalArgumentException("persistentId can't be negative");
        }
    }

    public OutgoingPersistentMessage(String to, OutgoingPayload payload, int persistentId) {
        super(to, payload, persistentId);

        if (persistentId < 0) {
            throw new IllegalArgumentException("persistentId can't be negative");
        }
    }
    
    // Parcelable

    protected OutgoingPersistentMessage(Parcel in) {
        super(in);
    }

    public static final Creator<OutgoingPersistentMessage> CREATOR = new Creator<OutgoingPersistentMessage>() {
        @Override
        public OutgoingPersistentMessage createFromParcel(Parcel in) {
            return new OutgoingPersistentMessage(in);
        }

        @Override
        public OutgoingPersistentMessage[] newArray(int size) {
            return new OutgoingPersistentMessage[size];
        }
    };
}
