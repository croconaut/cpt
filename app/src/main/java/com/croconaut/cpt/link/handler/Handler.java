package com.croconaut.cpt.link.handler;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Looper;
import android.util.Log;

import com.croconaut.cpt.link.PreferenceHelper;
import com.croconaut.cpt.link.handler.main.HandlerFailed;

public abstract class Handler {
    private final String TAG;

    protected final Context context;
    protected static WifiP2pManager mWifiP2pManager;
    protected static WifiP2pManager.Channel mWifiP2pChannel;
    protected static WifiManager mWifiManager;
    protected static PreferenceHelper mPreferenceHelper;
    private final android.os.Handler mHandler;

    public Handler(Context context, String tag) {
        this.context = context;
        TAG = tag;

        Log.v(TAG, getClass().getSimpleName() + "." + getClass().getSimpleName());

        mHandler = new android.os.Handler();
    }

    public abstract void start();
    public abstract void stop();

    protected void cancelCurrentlyScheduledTask() {
        mHandler.removeCallbacksAndMessages(null);
    }

    protected void scheduleTask(Runnable runnable, int delayInSeconds) {
        cancelCurrentlyScheduledTask();
        mHandler.postDelayed(runnable, delayInSeconds * 1000);
    }

    public static final void firstRunInitialization(final Context context) {
        mWifiP2pManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        mWifiP2pChannel = mWifiP2pManager.initialize(context, Looper.myLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                Log.e("link", "Wi-Fi P2P channel disconnected");
                new HandlerFailed().send(context);
            }
        });
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mPreferenceHelper = new PreferenceHelper(context);
    }
}
