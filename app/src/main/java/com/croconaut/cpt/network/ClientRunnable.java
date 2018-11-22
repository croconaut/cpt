package com.croconaut.cpt.network;

import android.app.Notification;
import android.content.Context;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.croconaut.cpt.R;
import com.croconaut.cpt.common.NotificationId;
import com.croconaut.cpt.data.CptClientCommunication;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

abstract class ClientRunnable extends NetworkRunnable {
    protected final String crocoId;
    protected final InetSocketAddress socketAddress;

    protected boolean isConnectionErrorNotificationShown;

    protected ClientRunnable(String TAG, Context context, String crocoId, InetSocketAddress socketAddress) {
        super(TAG, context, null);

        this.crocoId = crocoId;
        this.socketAddress = socketAddress;
    }

    protected void connect() throws IOException, InterruptedException {
        for (int attempts = socketAddress != null ? 10 : 0; ; attempts--) {
            try {
                // unfortunately, there's no timeout to apply to ENETUNREACH & friends (see http://stackoverflow.com/a/16273341/21009)
                socket = new Socket();
                socket.connect(socketAddress != null ? socketAddress : new InetSocketAddress(NetworkUtil.APP_SERVER_HOST, NetworkUtil.APP_SERVER_PORT), NetworkUtil.CONNECTION_TIMEOUT);
                break;
            } catch (ConnectException | SocketTimeoutException e) {
                Log.w(TAG, "Failed to connect, attempts left: " + attempts + " [" + e.getMessage() + "]");
                if (attempts == 0) {
                    showNotification(socketAddress != null ? socketAddress.getHostName() : NetworkUtil.APP_SERVER_HOST);
                    throw e;
                } else {
                    Thread.sleep(500);
                }
            }
        }

        socket.setSoTimeout(NetworkUtil.SOCKET_TIMEOUT);
        socket.setTcpNoDelay(true);

        cancelNotification();
    }

    private void showNotification(String hostAddress) {
        if (socketAddress != null) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                    .setContentTitle(context.getString(R.string.cpt_notif_connection_error1, hostAddress))
                    .setContentText(context.getString(R.string.cpt_notif_connection_error2))
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setSmallIcon(R.drawable.ic_wifon)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true)
                    .setContentIntent(CptClientCommunication.getCommunicationErrorPendingIntent(context, helper));
            //nm.notify(NotificationId.CONNECTION_P2P_ERROR, builder.build());
            isConnectionErrorNotificationShown = true;
        }
    }

    private void cancelNotification() {
        if (socketAddress != null) {
            nm.cancel(NotificationId.CONNECTION_P2P_ERROR);
            isConnectionErrorNotificationShown = false;
        }
    }

    protected void sendCommand(int command, boolean buffered) throws IOException {
        dos.write(command);
        if (!buffered) {
            dos.flush();
        }
    }

    protected void sendCommand(int command) throws IOException {
        sendCommand(command, false);
    }

    protected void sendCrocoId(boolean buffered) throws IOException {
        dos.writeUTF(helper.getCrocoId());
        if (!buffered) {
            dos.flush();
        }
    }

    protected void sendCrocoId() throws IOException {
        sendCrocoId(false);
    }
}
