package com.croconaut.cpt.network;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

class ServerCommandThread extends CrocoIdThread {
    private static final String TAG = "network.server";

    private final Context context;
    private final Socket socket;

    private final Object lock = new Object();
    private volatile ServerRunnable serverRunnable;
    private final Set<String> pendingInterruptCrocoIds = new HashSet<>();

    public ServerCommandThread(Context context, Socket socket) {
        this.context = context;
        this.socket = socket;
    }

    @Override
    void interruptIfEqualsTo(String crocoId) {
        synchronized (lock) {
            if (serverRunnable != null) {
                serverRunnable.interruptIfEqualsTo(crocoId);
            } else {
                pendingInterruptCrocoIds.add(crocoId);
            }
        }
    }

    @Override
    public void run() {
        try {
            socket.setSoTimeout(NetworkUtil.SOCKET_TIMEOUT);
            socket.setTcpNoDelay(true);

            InputStream is = socket.getInputStream();
            int cmd = is.read();

            synchronized (lock) {
                switch (cmd) {
                    case NetworkUtil.CMD_INTRODUCTION:
                        serverRunnable = new ServerIntroductionRunnable(context, socket, pendingInterruptCrocoIds);
                        break;
                    case NetworkUtil.CMD_SYNC_MESSAGES:
                        serverRunnable = new ServerSyncMessagesRunnable(context, socket, pendingInterruptCrocoIds);
                        break;
                    case NetworkUtil.CMD_SYNC_ATTACHMENTS:
                        serverRunnable = new ServerSyncAttachmentsRunnable(context, socket, pendingInterruptCrocoIds);
                        break;
                    default:
                        Log.e(TAG, "Unknown command: " + cmd);
                }
            }
            if (serverRunnable != null) {
                serverRunnable.run();
            }
        } catch (IOException e) {
            Log.e(TAG, "exception", e);
        }
    }
}
