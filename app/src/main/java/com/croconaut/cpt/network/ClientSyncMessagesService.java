package com.croconaut.cpt.network;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.croconaut.cpt.link.handler.main.NetworkSyncServiceFinished;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.TreeSet;

public class ClientSyncMessagesService extends ClientSyncService {
    private static final String TAG = "network.client";

    private class ClientSyncMessagesRunnable extends ClientRunnable {
        private MessagesHelper messagesHelper;

        private int counter;
        private long connectionTimestamp;
        private boolean hasReceivedNonLocalAcks;
        private TreeSet<NetworkHeader> sentHeaders;
        private List<NetworkHeader> receivedHeaders;

        public ClientSyncMessagesRunnable(String TAG, Context context, String crocoId, InetSocketAddress socketAddress) {
            super(TAG, context, crocoId, socketAddress);
        }

        @Override
        public void run() {
            Log.v(TAG, getClass().getSimpleName() + ".run");

            long timeStamp = 0;
            try {
                connect();
                initializeDataStreams();
                initializeHelper();

                sendCommand(NetworkUtil.CMD_SYNC_MESSAGES, true);

                sendCrocoId(true);

                connectionTimestamp = System.currentTimeMillis();
                sendHeaders();

                receiveHeaders();

                sendMessages(); // with processSentMessages()

                receiveAcks();  // with processReceivedMessages()

                shutdown();

                // now we're sure both parties have the same content

                cancelCommunicationErrorNotification(true);

                timeStamp = connectionTimestamp;

                markMessagesAsSent();

                checkHash();
            } catch (InterruptedException e) {
                // actually this is the only place when Thread.interrupt() matter in this class
                Log.w(TAG, "connect() has been interrupted", e);
            } catch (IOException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                Log.e(TAG, "exception", e);
                if (!isConnectionErrorNotificationShown) {
                    showCommunicationErrorNotification(true);
                }

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
            messagesHelper.setCrocoId(crocoId);
        }

        private void sendHeaders() throws IOException {
            Log.v(TAG, getClass().getSimpleName() + ".sendHeaders");

            sentHeaders = messagesHelper.getHeaders();
            Log.d(TAG, counter++ + ". we have " + sentHeaders.size() + " headers");

            messagesHelper.sendHeaders(sentHeaders);
            Log.d(TAG, counter++ + ". sent " + sentHeaders.size() + " headers");
        }

        private void receiveHeaders() throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
            Log.v(TAG, getClass().getSimpleName() + ".receiveHeaders");

            receivedHeaders = messagesHelper.receiveOpaqueHeaders();
            Log.d(TAG, counter++ + ". received " + receivedHeaders.size() + " headers");
        }

        private void sendMessages() throws IOException {
            Log.v(TAG, getClass().getSimpleName() + ".sendMessages");

            List<NetworkMessage> sentMessages = messagesHelper.getMessages(receivedHeaders);

            messagesHelper.sendMessages(sentMessages);
            Log.d(TAG, counter++ + ". sent " + sentMessages.size() + " messages");

            messagesHelper.processSentMessages(sentMessages);
        }

        private void receiveAcks() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
            Log.v(TAG, getClass().getSimpleName() + ".receiveAcks");

            List<NetworkMessage> receivedAcks = messagesHelper.receiveMessages();
            Log.d(TAG, counter++ + ". received " + receivedAcks.size() + " ACKs");

            for (NetworkMessage networkMessage : receivedAcks) {
                if (!networkMessage.isLocal()) {
                    hasReceivedNonLocalAcks = true;
                    break;
                }
            }

            messagesHelper.processReceivedMessages(receivedAcks);
        }

        private void markMessagesAsSent() {
            Log.v(TAG, getClass().getSimpleName() + ".markMessagesAsSent");

            messagesHelper.markHeadersAsSent(sentHeaders);
        }

        private void checkHash() {
            Log.v(TAG, getClass().getSimpleName() + ".checkHash");

            if (hasReceivedNonLocalAcks) {
                messagesHelper.recalculateHash();
            }
        }

        private void networkSyncServiceFinished(long timeStamp) {
            new NetworkSyncServiceFinished().send(context, crocoId, NetworkSyncServiceFinished.CLIENT_MESSAGES, timeStamp);
        }
    }

    public ClientSyncMessagesService() {
        super(TAG);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, getClass().getSimpleName() + ".onStartCommand: " + startId + " (" + intent.getAction() + ")");

        if (ACTION_SYNC.equals(intent.getAction())) {
            String crocoId = intent.getStringExtra(EXTRA_CROCO_ID);
            InetSocketAddress socketAddress = (InetSocketAddress) intent.getSerializableExtra(EXTRA_SOCKET_ADDRESS);

            sync(startId, crocoId, new ClientSyncMessagesRunnable(TAG, this, crocoId, socketAddress));
        } else if (ACTION_CANCEL.equals(intent.getAction())) {
            String crocoId = intent.getStringExtra(EXTRA_CROCO_ID);

            cancel(crocoId);
        }

        return START_REDELIVER_INTENT;
    }

    public static void sync(Context context, String crocoId, InetSocketAddress socketAddress) {
        sync(context, ClientSyncMessagesService.class, crocoId, socketAddress);
    }

    public static void cancelSync(Context context, String crocoId) {
        cancelSync(context, ClientSyncMessagesService.class, crocoId);
    }
}
