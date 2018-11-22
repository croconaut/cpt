package com.croconaut.cpt.data;

import android.os.Parcel;

public class OutgoingPersistentBroadcastMessage extends OutgoingPersistentMessage {
    public OutgoingPersistentBroadcastMessage(int persistentId) {
        super(MessageIdentifier.BROADCAST_ID, persistentId);
    }

    public OutgoingPersistentBroadcastMessage(OutgoingPayload payload, int persistentId) {
        super(MessageIdentifier.BROADCAST_ID, payload, persistentId);
    }

    // Parcelable

    protected OutgoingPersistentBroadcastMessage(Parcel in) {
        super(in);
    }

    public static final Creator<OutgoingPersistentBroadcastMessage> CREATOR = new Creator<OutgoingPersistentBroadcastMessage>() {
        @Override
        public OutgoingPersistentBroadcastMessage createFromParcel(Parcel in) {
            return new OutgoingPersistentBroadcastMessage(in);
        }

        @Override
        public OutgoingPersistentBroadcastMessage[] newArray(int size) {
            return new OutgoingPersistentBroadcastMessage[size];
        }
    };
}
