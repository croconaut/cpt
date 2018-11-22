package com.croconaut.cpt.network;

import android.content.Context;
import android.util.Log;

import com.croconaut.cpt.data.DatabaseManager;
import com.croconaut.cpt.link.handler.main.NetworkSyncServiceFinished;
import com.croconaut.cpt.link.handler.notification.ConnectionProgress;

import java.io.IOException;
import java.net.InetSocketAddress;

public class ClientIntroductionRunnable extends ClientRunnable {
    private static final String TAG = "network.client";

    private final String localAdvertisedHash;
    private final String hash;
    private final InetSocketAddress localSocketAddress;
    private final boolean isP2pClient;

    private IntroductionHelper miscHelper;

    public ClientIntroductionRunnable(Context context, String crocoId, String localHash, String remoteHash, InetSocketAddress localSocketAddress, InetSocketAddress remoteSocketAddress, boolean isP2pClient) {
        super(TAG, context, crocoId, remoteSocketAddress);

        this.localAdvertisedHash = localHash;
        this.hash = remoteHash;
        this.localSocketAddress = localSocketAddress;
        this.isP2pClient = isP2pClient;
    }

    @Override
    public void run() {
        Log.v(TAG, getClass().getSimpleName() + ".run");

        // cleanup before making any connection
        cleanupDatabase();

        try {
            connect();
            initializeDataStreams();
            initializeHelper();

            sendCommand(NetworkUtil.CMD_INTRODUCTION, true);

            sendCrocoId(true);

            sendHash(true);

            sendIsP2pClient(true);

            sendSocketAddress();

            shutdown();

            // now we're sure both parties have the same content

            cancelCommunicationErrorNotification(true);

            publishProgress();
            newConnectableClient();
        } catch (InterruptedException e) {
            // impossible
            Log.w(TAG, "connect() has been interrupted", e);
        } catch (IOException e) {
            Log.e(TAG, "exception", e);
            if (!isConnectionErrorNotificationShown) {
                showCommunicationErrorNotification(true);
            }

            // nothing else to do here...
            dropConnection();
        } finally {
            close();
        }
    }

    private void initializeHelper() {
        miscHelper = new IntroductionHelper(TAG, context, dis, dos);
        miscHelper.setCrocoId(crocoId);
        miscHelper.setSocketAddress(socketAddress);
        miscHelper.setHash(hash);
        miscHelper.setIsP2pClient(false);   // remote server most certainly isn't a client
    }

    private void sendSocketAddress() throws IOException {
        miscHelper.sendSocketAddress(localSocketAddress);
    }

    private void sendHash(boolean buffered) throws IOException {
        miscHelper.sendHash(localAdvertisedHash, buffered);
    }

    private void sendIsP2pClient(boolean buffered) throws IOException {
        miscHelper.sendIsP2pClient(isP2pClient, buffered);
    }

    private void publishProgress() {
        if (isP2pClient) {
            miscHelper.publishProgress(ConnectionProgress.CONNECTED_AS_CLIENT);
        }
    }

    private void newConnectableClient() {
        miscHelper.newConnectableClient();
    }

    private void dropConnection() {
        // can't use a helper
        new NetworkSyncServiceFinished().send(context, crocoId, NetworkSyncServiceFinished.EVERYTHING, 0);
    }

    private void cleanupDatabase() {
        if (DatabaseManager.cleanMessages(context, helper.getCrocoId()) > 0) {
            DatabaseManager.setHashDirty(context);
        }
    }
}
