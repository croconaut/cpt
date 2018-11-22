package com.croconaut.cpt.network;

import android.content.Context;
import android.util.Log;

import com.croconaut.cpt.data.DatabaseManager;
import com.croconaut.cpt.link.handler.main.NetworkSyncServiceFinished;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

class ServerSyncMessagesRunnable extends ServerRunnable {
    private MessagesHelper messagesHelper;

    private int counter;
    private boolean hasReceivedNonLocalMessages;
    private TreeSet<NetworkHeader> receivedHeaders;
    private List<NetworkMessage> acks;

    public ServerSyncMessagesRunnable(Context context, Socket socket, Set<String> pendingInterruptCrocoIds) {
        super(context, socket, pendingInterruptCrocoIds);
    }

    @Override
    public void run() {
        Log.v(TAG, getClass().getSimpleName() + ".run");

        long timeStamp = 0;
        try {
            initializeDataStreams();
            initializeHelper();

            receiveCrocoId();
            setCrocoId();

            receiveHeaders();

            sendHeaders();

            receiveMessages();   // with processReceivedMessages()

            sendAcks();

            shutdown();

            // now we're sure both parties have the same content

            cancelCommunicationErrorNotification(true);

            timeStamp = -1; // we don't care about timeStamp, actually; it's just a "success" flag here

            markAcksAsSent();

            checkHash();
        } catch (IOException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            Log.e(TAG, "exception", e);
            showCommunicationErrorNotification(true);

            if (messagesHelper != null) {
                // if something went wrong, better update hash
                messagesHelper.recalculateHash();
            }
        } finally {
            networkSyncServiceFinished(timeStamp);

            close();
        }
    }

    private void initializeHelper() {
        messagesHelper = new MessagesHelper(TAG, context, dis, dos);
    }

    private void setCrocoId() {
        messagesHelper.setCrocoId(crocoId);
    }

    private void receiveHeaders() throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        Log.v(TAG, getClass().getSimpleName() + ".receiveHeaders");

        receivedHeaders = messagesHelper.receiveHeaders();
        Log.d(TAG, counter++ + ". received " + receivedHeaders.size() + " headers");
    }

    private void sendHeaders() throws IOException {
        Log.v(TAG, getClass().getSimpleName() + ".sendHeaders");

        TreeSet<NetworkHeader> headers = messagesHelper.getHeaders();
        Log.d(TAG, counter++ + ". we have " + headers.size() + " headers");

        List<NetworkHeader> sentHeaders = DatabaseManager.getOpaqueHeaders(receivedHeaders, headers);
        messagesHelper.sendOpaqueHeaders(sentHeaders);
        Log.d(TAG, counter++ + ". sent " + sentHeaders.size() + " headers");
    }

    private void receiveMessages() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
        Log.v(TAG, getClass().getSimpleName() + ".receiveMessages");

        List<NetworkMessage> receivedMessages = messagesHelper.receiveMessages();
        Log.d(TAG, counter++ + ". received " + receivedMessages.size() + " messages");

        for (NetworkMessage networkMessage : receivedMessages) {
            if (!networkMessage.isLocal()) {
                hasReceivedNonLocalMessages = true;
                break;
            }
        }

        acks = messagesHelper.processReceivedMessages(receivedMessages);
        Log.d(TAG, counter++ + ". turned into " + acks.size() + " ACK messages");
    }

    private void sendAcks() throws IOException {
        Log.v(TAG, getClass().getSimpleName() + ".sendAcks");

        messagesHelper.sendMessages(acks);
        Log.d(TAG, counter++ + ". sent " + acks.size() + " ACKs");
    }

    private void markAcksAsSent() {
        Log.v(TAG, getClass().getSimpleName() + ".markAcksAsSent");

        messagesHelper.markMessagesAsSent(acks);
    }

    private void checkHash() {
        Log.v(TAG, getClass().getSimpleName() + ".checkHash");

        if (hasReceivedNonLocalMessages) {
            messagesHelper.recalculateHash();
        }
    }

    private void networkSyncServiceFinished(long timeStamp) {
        new NetworkSyncServiceFinished().send(context, crocoId, 0, timeStamp);
    }
}
