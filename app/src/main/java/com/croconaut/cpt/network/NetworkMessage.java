package com.croconaut.cpt.network;

import android.content.Context;

import com.croconaut.cpt.data.StreamUtil;
import com.croconaut.cpt.data.Streamable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;

public class NetworkMessage implements Streamable {
    public /*final*/ NetworkHeader header;

    private /*final*/ int expirationTime;   // -1 if persistent
    private /*final*/ byte[] appPayload; // null if ACK
    private List<? extends StreamableAttachment> attachments; // null if ACK
    private /*final*/ List<NetworkHop> hops;

    private /*final*/ boolean isExpectingAck;
    private /*final*/ boolean isLocal;

    private Date deliveredTime;  // null if NORMAL

    // not sent over the network
    private /*final transient*/ boolean isSentToRecipient;
    private /*final transient*/ boolean isSentToOtherDevice;
    private /*final transient*/ boolean isSentToAppServer;
    private /*final transient*/ boolean isExpectingSent;

    public NetworkMessage(NetworkHeader header, int expirationTime, byte[] appPayload, List<NetworkHop> hops,
                          boolean isSentToRecipient, boolean isSentToOtherDevice, boolean isSentToAppServer, boolean isExpectingSent, boolean isExpectingAck, boolean isLocal) {
        this.header = header;

        this.expirationTime = expirationTime;
        this.appPayload = appPayload;
        this.hops = hops;

        this.isExpectingAck = isExpectingAck;
        this.isLocal = isLocal;

        this.isSentToRecipient = isSentToRecipient;
        this.isSentToOtherDevice = isSentToOtherDevice;
        this.isSentToAppServer = isSentToAppServer;
        this.isExpectingSent = isExpectingSent;
    }

    // for persistent messages
    public NetworkMessage(NetworkHeader header, byte[] appPayload, List<NetworkHop> hops) {
        this.header = header;

        this.expirationTime = -1;
        this.appPayload = appPayload;
        this.hops = hops;
    }

    public void setAttachments(List<? extends StreamableAttachment> attachments) {
        this.attachments = attachments;
    }

    public void setDeliveredTime(Date deliveredTime) {
        this.deliveredTime = deliveredTime;
    }

    public int getExpirationTime() {
        return expirationTime;
    }

    public byte[] getAppPayload() {
        return appPayload;
    }

    public List<? extends StreamableAttachment> getAttachments() {
        return attachments;
    }

    public void addHop(NetworkHop hop) {
        hops.add(hop);
    }

    public List<NetworkHop> getHops() {
        return hops;
    }

    public boolean isExpectingAck() {
        return isExpectingAck;
    }

    public boolean isLocal() {
        return isLocal;
    }

    public Date getDeliveredTime() {
        return deliveredTime;
    }

    public boolean isSentToRecipient() {
        return isSentToRecipient;
    }

    public boolean isSentToOtherDevice() {
        return isSentToOtherDevice;
    }

    public boolean isSentToAppServer() {
        return isSentToAppServer;
    }

    public boolean isExpectingSent() {
        return isExpectingSent;
    }

    @Override
    public String toString() {
        return "NetworkMessage{" +
                "header=" + header +
                ", isLocal=" + isLocal +
                ", deliveredTime=" + deliveredTime +
                ", expirationTime=" + expirationTime +
                ", attachments=" + attachments +
                ", hops=" + hops +
                ", isExpectingAck=" + isExpectingAck +
                ", isSentToRecipient=" + isSentToRecipient +
                ", isSentToOtherDevice=" + isSentToOtherDevice +
                ", isSentToAppServer=" + isSentToAppServer +
                ", isExpectingSent=" + isExpectingSent +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NetworkMessage that = (NetworkMessage) o;

        return header.equals(that.header);

    }

    @Override
    public int hashCode() {
        return header.hashCode();
    }

    // Streamable

    public NetworkMessage() {
    }

    @Override
    public void readFrom(Context context, DataInputStream dis) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        header = (NetworkHeader) StreamUtil.readStreamableFrom(context, dis);

        expirationTime = dis.readInt();
        int appPayloadLength = dis.readInt();
        if (appPayloadLength != -1) {
            appPayload = StreamUtil.readByteArray(dis, appPayloadLength);
        }
        if (dis.readBoolean()) {
            //noinspection unchecked
            attachments = (List<StreamableAttachment>) StreamUtil.readStreamablesFrom(context, dis);
        }
        //noinspection unchecked
        hops = (List<NetworkHop>) StreamUtil.readStreamablesFrom(context, dis);

        isExpectingAck = dis.readBoolean();
        isLocal = dis.readBoolean();

        long deliveredTimeInMs = dis.readLong();
        if (deliveredTimeInMs != -1) {
            deliveredTime = new Date(deliveredTimeInMs);
        }
    }

    @Override
    public void writeTo(Context context, DataOutputStream dos) throws IOException {
        StreamUtil.writeStreamableTo(context, dos, header);

        dos.writeInt(expirationTime);
        dos.writeInt(appPayload != null ? appPayload.length : -1);
        if (appPayload != null) {
            StreamUtil.writeByteArray(dos, appPayload);
        }
        if (attachments == null) {
            dos.writeBoolean(false);
        } else {
            dos.writeBoolean(true);
            StreamUtil.writeStreamablesTo(context, dos, attachments);
        }
        StreamUtil.writeStreamablesTo(context, dos, hops);

        dos.writeBoolean(isExpectingAck);
        dos.writeBoolean(isLocal);

        dos.writeLong(deliveredTime != null ? deliveredTime.getTime() : -1);
    }
}
