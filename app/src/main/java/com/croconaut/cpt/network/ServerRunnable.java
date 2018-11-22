package com.croconaut.cpt.network;

import android.content.Context;

import java.io.IOException;
import java.net.Socket;
import java.util.Set;

abstract class ServerRunnable extends NetworkRunnable {
    protected static final String TAG = "network.server";

    protected volatile String crocoId;
    private final Set<String> pendingInterruptCrocoIds;
    private final Object lock = new Object();

    protected ServerRunnable(Context context, Socket socket, Set<String> pendingInterruptCrocoIds) {
        super(TAG, context, socket);

        this.pendingInterruptCrocoIds = pendingInterruptCrocoIds;
    }

    protected void receiveCrocoId() throws IOException {
        crocoId = dis.readUTF();
        synchronized (lock) {
            if (pendingInterruptCrocoIds.contains(crocoId)) {
                Thread.currentThread().interrupt();
            }
        }
    }

    protected void interruptIfEqualsTo(String crocoId) {
        synchronized (lock) {
            if (crocoId == null || crocoId.equals(this.crocoId)) {
                Thread.currentThread().interrupt();
            } else if (this.crocoId == null) {
                pendingInterruptCrocoIds.add(crocoId);
            }
        }
    }
}
