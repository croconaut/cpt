package com.croconaut.cpt.network;

import android.content.Context;
import android.util.Log;

import com.croconaut.cpt.link.handler.main.NetworkSyncServiceFinished;
import com.croconaut.cpt.link.handler.main.ServerSyncStarted;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

public class ServerListeningThread extends Thread {
    private static final String TAG = "network.server";

    private final Context context;
    private ServerSocket serverSocket;
    private final List<CrocoIdThread> startedThreads = new ArrayList<>();

    public ServerListeningThread(Context context) {
        Log.v(TAG, getClass().getSimpleName() + ".ServerListeningThread");

        this.context = context;

        try {
            // unbound server socket
            serverSocket = new ServerSocket();
            // read (accept) timeout
            serverSocket.setSoTimeout(NetworkUtil.SERVER_SOCKET_TIMEOUT);
            serverSocket.setReuseAddress(true);
            // bind it any free port
            serverSocket.bind(new InetSocketAddress(0));
        } catch (IOException e) {
            Log.e(TAG, "exception", e);
        }
    }

    public int getPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : -1;
    }

    public void cancel(String crocoId) {
        synchronized (startedThreads) {
            for (CrocoIdThread thread : startedThreads) {
                thread.interruptIfEqualsTo(crocoId);
            }
        }
    }

    @Override
    public void run() {
        Log.v(TAG, getClass().getSimpleName() + ".run: " + Thread.currentThread().getId());

        // there are never two instances of ServerTask active
        synchronized (ServerListeningThread.class) {
            Log.i(TAG, "[" + Thread.currentThread().getId() + "] ServerListeningThread: Listening on " + serverSocket.getLocalSocketAddress());

            while (!isInterrupted()) {
                try {
                    Socket socket = serverSocket.accept();
                    Log.d(TAG, "ServerListeningThread: Received a TCP connection from: " + socket.getInetAddress().getHostAddress());
                    new ServerSyncStarted().send(context);

                    synchronized (startedThreads) {
                        CrocoIdThread serverThread = new ServerCommandThread(context, socket);
                        serverThread.start();
                        startedThreads.add(serverThread);
                    }
                } catch (SocketTimeoutException e) {
                    synchronized (startedThreads) {
                        boolean hasActiveTransfers = startedThreads.isEmpty();

                        for (Thread thread : startedThreads) {
                            if (thread.isAlive()) {
                                hasActiveTransfers = true;
                                break;
                            }
                        }

                        if (!hasActiveTransfers) {
                            Log.i(TAG, "No active clients => sync done for the server");
                            new NetworkSyncServiceFinished().send(context, null, NetworkSyncServiceFinished.SERVER, -1);
                            startedThreads.clear();
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "exception", e);
                    break;
                }
            }

            Log.w(TAG, "Listening thread interrupted: " + Thread.currentThread().getId());

            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "exception", e);
            }
        }
    }
}
