package com.croconaut.cpt.network;

import android.content.Context;

import com.croconaut.cpt.link.handler.main.NewConnectableClient;
import com.croconaut.cpt.link.handler.notification.ConnectionProgress;
import com.croconaut.cpt.link.handler.notification.PublishProgress;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;

class IntroductionHelper extends AbstractHelper {
    private InetSocketAddress socketAddress;
    private String hash;
    private boolean isP2pClient;

    public IntroductionHelper(String TAG, Context context, DataInputStream dis, DataOutputStream dos) {
        super(TAG, context, dis, dos);
    }

    void setSocketAddress(InetSocketAddress socketAddress) {
        this.socketAddress = socketAddress;
    }

    void receiveSocketAddress() throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(dis);
        socketAddress = (InetSocketAddress) ois.readObject();
    }

    void sendSocketAddress(InetSocketAddress socketAddress) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(dos);
        oos.writeObject(socketAddress);
        // always flush object output stream, primitive types are internally cached,
        // not sure about objects but it's worth a try
        oos.flush();
    }

    void setHash(String hash) {
        this.hash = hash;
    }

    void receiveHash() throws IOException {
        hash = dis.readUTF();
    }

    void sendHash(String hash, boolean buffered) throws IOException {
        dos.writeUTF(hash);
        if (!buffered) {
            dos.flush();
        }
    }

    void sendHash(String hash) throws IOException {
        sendHash(hash, false);
    }

    public boolean isP2pClient() {
        return isP2pClient;
    }

    void setIsP2pClient(boolean isP2pClient) {
        this.isP2pClient = isP2pClient;
    }

    void receiveIsP2pClient() throws IOException {
        isP2pClient = dis.readBoolean();
    }

    void sendIsP2pClient(boolean isP2pClient, boolean buffered) throws IOException {
        dos.writeBoolean(isP2pClient);
        if (!buffered) {
            dos.flush();
        }
    }

    void sendIsP2pClient(boolean isP2pClient) throws IOException {
        sendIsP2pClient(isP2pClient, false);
    }

    void publishProgress(ConnectionProgress connectionProgress) {
        new PublishProgress().send(context, connectionProgress);
    }

    void newConnectableClient() {
        new NewConnectableClient().send(context, crocoId, socketAddress, hash, isP2pClient);
    }
}
