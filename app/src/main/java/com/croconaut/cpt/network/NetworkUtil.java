package com.croconaut.cpt.network;

import android.content.Context;

import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("PointlessArithmeticExpression")
public class NetworkUtil {
    static final int ATTACHMENT_BUFFER_SIZE = 1024 * 1024;    // 1 MB
    static final int  SERVER_SOCKET_TIMEOUT = 1 * 1000;
    static final int         SOCKET_TIMEOUT = 30 * 1000;
    static final int     CONNECTION_TIMEOUT = 3 * 1000;

    static final String     APP_SERVER_HOST = "wifon.sk";
    static final int        APP_SERVER_PORT = 50001;

    static final int    CMD_INTRODUCTION  = 0;
    static final int    CMD_SYNC_MESSAGES = 1;
    static final int CMD_SYNC_ATTACHMENTS = 2;

    private static AtomicInteger runningConnections = new AtomicInteger();

    static void addNewRunningConnection(Context context) {
        runningConnections.incrementAndGet();
        // make the renew time as little as possible
        new com.croconaut.cpt.link.handler.p2p.TimerExpired().send(context);
    }

    static void removeRunningConnection(Context context) {
        runningConnections.decrementAndGet();
        // make the renew time as little as possible
        new com.croconaut.cpt.link.handler.p2p.TimerExpired().send(context);
    }

    public static boolean hasRunningConnection() {
        return runningConnections.get() > 0;
    }
}
