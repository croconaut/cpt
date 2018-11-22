package com.croconaut.cpt.network;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.croconaut.cpt.R;
import com.croconaut.cpt.common.NotificationId;
import com.croconaut.cpt.data.CptClientCommunication;
import com.croconaut.cpt.link.PreferenceHelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.net.Socket;

abstract class NetworkRunnable implements Runnable {
    protected final String TAG;
    protected final Context context;
    protected /*final*/ Socket socket;

    protected final PreferenceHelper helper;
    protected final NotificationManager nm;

    protected DataOutputStream dos;
    protected DataInputStream  dis;
    protected DataOutputStream nonBufferedDos;
    protected DataInputStream  nonBufferedDis;

    protected NetworkRunnable(String TAG, Context context, Socket socket) {
        this.TAG = TAG;
        this.context = context;
        this.socket = socket;

        helper = new PreferenceHelper(context);
        nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    protected void initializeDataStreams() throws IOException {
        // NOTE: use data streams where possible (instead of object streams which write/read a few bytes by their creation!)
        dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), socket.getSendBufferSize()));
        dis = new DataInputStream( new BufferedInputStream( socket.getInputStream(),  socket.getReceiveBufferSize()));

        nonBufferedDos = new DataOutputStream(socket.getOutputStream());
        nonBufferedDis = new DataInputStream( socket.getInputStream());
    }

    protected void shutdown() throws IOException {
        // semi-synchronization
        socket.shutdownOutput();

        int read = dis.read();
        if (read != -1) {
            throw new StreamCorruptedException("Expected to read -1");
        }
    }

    protected void close() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "exception", e);
        }
    }

    protected void showCommunicationErrorNotification(boolean isP2pConnection) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setContentTitle(context.getString(R.string.cpt_notif_communication_p2p))
                .setContentText(context.getString(R.string.cpt_notif_connection_error2))
                .setDefaults(Notification.DEFAULT_ALL)
                .setSmallIcon(R.drawable.ic_wifon)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setContentIntent(CptClientCommunication.getCommunicationErrorPendingIntent(context, helper))
                ;
        //nm.notify(isP2pConnection ? NotificationId.COMMUNICATION_P2P_ERROR : NotificationId.COMMUNICATION_APP_SERVER_ERROR, builder.build());
    }

    protected void cancelCommunicationErrorNotification(boolean isP2pConnection) {
        nm.cancel(isP2pConnection ? NotificationId.COMMUNICATION_P2P_ERROR : NotificationId.COMMUNICATION_APP_SERVER_ERROR);
    }
}
