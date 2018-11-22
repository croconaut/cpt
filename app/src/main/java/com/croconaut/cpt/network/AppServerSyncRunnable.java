package com.croconaut.cpt.network;

import android.content.Context;
import android.os.Build;

import com.croconaut.cpt.data.DatabaseManager;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.Set;

abstract class AppServerSyncRunnable extends ClientRunnable {
    protected final boolean fullSync;
    protected Set<String> blockedCrocoIds;

    private int gcmSyncCommand = -1;
    private Date gcmSyncTime;

    public AppServerSyncRunnable(String TAG, Context context, String crocoId, InetSocketAddress socketAddress, boolean fullSync) {
        super(TAG, context, crocoId, socketAddress);

        // 'crocoId' is set to null in all subclasses (same goes for their helpers)
        this.fullSync = fullSync;
    }

    protected void sendBlockedCrocoIds() throws IOException {
        // constructor is executed in the main thread...
        blockedCrocoIds = DatabaseManager.getBlockedDevices(context);

        ObjectOutputStream oos = new ObjectOutputStream(dos);
        oos.writeObject(blockedCrocoIds);
        // always flush object output stream, primitive types are internally cached,
        // not sure about objects but it's worth a try
        oos.flush();
    }

    protected void sendSyncPreference(boolean buffered) throws IOException {
        dos.writeBoolean(fullSync);
        if (!buffered) {
            dos.flush();
        }
    }

    protected void sendSyncPreference() throws IOException {
        sendSyncPreference(false);
    }

    protected void sendCrocoIdAndUsername(boolean buffered) throws IOException {
        sendCrocoId(true);  // always buffered
        dos.writeBoolean(helper.getUsername() != null);
        if (helper.getUsername() != null) {
            dos.writeUTF(helper.getUsername());
        }
        if (!buffered) {
            dos.flush();
        }
    }

    protected void sendDeviceInfo(boolean buffered) throws IOException {
        dos.writeUTF("MODEL: " + Build.MODEL
                + ", BRAND: " + Build.BRAND
                + ", DEVICE: " + Build.DEVICE
                + ", HARDWARE: " + Build.HARDWARE
                + ", MANUFACTURER: " + Build.MANUFACTURER
                + ", PRODUCT: " + Build.PRODUCT);

        if (!buffered) {
            dos.flush();
        }
    }

    protected void sendCrocoIdAndUsername() throws IOException {
        sendCrocoIdAndUsername(false);
    }

    protected boolean isGcmSyncNeeded(int command) {
        gcmSyncCommand = command;
        gcmSyncTime = new Date();
        return DatabaseManager.getGcmTransactionRequestsCount(context, gcmSyncCommand) > 0;
    }

    protected void gcmSyncDone() {
        DatabaseManager.removeGcmTransactionRequests(context, gcmSyncTime, gcmSyncCommand);
    }
}
