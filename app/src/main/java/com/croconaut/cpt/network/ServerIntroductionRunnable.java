package com.croconaut.cpt.network;

import android.content.Context;
import android.util.Log;

import com.croconaut.cpt.data.DatabaseManager;
import com.croconaut.cpt.link.handler.notification.ConnectionProgress;

import java.io.IOException;
import java.net.Socket;
import java.util.Set;

public class ServerIntroductionRunnable extends ServerRunnable {
    private IntroductionHelper miscHelper;

    public ServerIntroductionRunnable(Context context, Socket socket, Set<String> pendingInterruptCrocoIds) {
        super(context, socket, pendingInterruptCrocoIds);
    }

    @Override
    public void run() {
        Log.v(TAG, getClass().getSimpleName() + ".run");

        // cleanup before making any connection
        cleanupDatabase();

        try {
            initializeDataStreams();
            initializeHelper();

            receiveCrocoId();
            setCrocoId();

            receiveHash();

            receiveIsP2pClient();

            receiveSocketAddress();

            shutdown();

            // now we're sure both parties have the same content

            cancelCommunicationErrorNotification(true);

            // whoever comes first
            publishProgress();
            newConnectableClient();
        } catch (IOException | ClassNotFoundException e) {
            Log.e(TAG, "exception", e);
            showCommunicationErrorNotification(true);
        } finally {
            close();
        }
    }

    private void initializeHelper() {
        miscHelper = new IntroductionHelper(TAG, context, dis, dos);
    }

    private void setCrocoId() {
        miscHelper.setCrocoId(crocoId);
    }

    private void receiveSocketAddress() throws IOException, ClassNotFoundException {
        miscHelper.receiveSocketAddress();
    }

    private void receiveHash() throws IOException, ClassNotFoundException {
        miscHelper.receiveHash();
    }

    private void receiveIsP2pClient() throws IOException {
        miscHelper.receiveIsP2pClient();
    }

    private void publishProgress() {
        if (miscHelper.isP2pClient()) {
            miscHelper.publishProgress(ConnectionProgress.CREATED_GROUP_AS_SERVER);
        }
    }

    private void newConnectableClient() {
        miscHelper.newConnectableClient();
    }

    private void cleanupDatabase() {
        if (DatabaseManager.cleanMessages(context, helper.getCrocoId()) > 0) {
            DatabaseManager.setHashDirty(context);
        }
    }
}
