package com.croconaut.cpt.network;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

abstract class NetworkSyncService extends Service {
    protected final String TAG;

    private final List<CrocoIdThread> mSyncThreads = new ArrayList<>();
    private final ConcurrentHashMap<String, AtomicInteger> mCounter = new ConcurrentHashMap<>();

    protected NetworkSyncService(String TAG) {
        this.TAG = TAG;
    }

    protected void sync(int startId, String crocoId, Runnable runnable) {
        SyncThread syncThread = new SyncThread(startId, crocoId, runnable);
        syncThread.start();
        mSyncThreads.add(syncThread);
    }

    protected void cancel(String crocoId) {
        for (CrocoIdThread thread : mSyncThreads) {
            thread.interruptIfEqualsTo(crocoId);
        }
    }

    private class SyncThread extends CrocoIdThread {
        private final int startId;
        private final String crocoId;
        private final Runnable runnable;

        public SyncThread(int startId, String crocoId, Runnable runnable) {
            this.startId = startId;
            this.crocoId = crocoId != null ? crocoId : "00:00:00:00:00:00"; // use a fake croco id for server connections
            this.runnable = runnable;
        }

        public void interruptIfEqualsTo(String crocoId) {
            if (crocoId == null || this.crocoId.equals(crocoId)) {
                interrupt();
            }
        }

        @Override
        public void run() {
            Log.v(TAG, getClass().getSimpleName() + ".run: " + Thread.currentThread().getId());

            AtomicInteger counter = mCounter.putIfAbsent(crocoId, new AtomicInteger(0));
            if (counter == null) {
                // first time
                counter = mCounter.get(crocoId);
            }
            counter.incrementAndGet();

            synchronized (NetworkSyncService.this) {  // lock = this service
                if (!isInterrupted() && counter.getAndSet(0) > 0) {
                    runnable.run();
                }
            }

            // we're not really interested whether this has stopped or not... one thread surely will :)
            stopSelfResult(startId);
        }
    };

    @Override
    public void onCreate() {
        Log.v(TAG, getClass().getSimpleName() + ".onCreate");
    }

    @Override
    abstract public int onStartCommand(Intent intent, int flags, int startId);

    @Override
    public void onDestroy() {
        Log.v(TAG, getClass().getSimpleName() + ".onDestroy");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
