package com.croconaut.cpt.link.handler.connection;

import android.content.Context;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import com.croconaut.cpt.common.State;
import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.link.handler.DropConnection;
import com.croconaut.cpt.link.handler.NetworkStateChanged;
import com.croconaut.cpt.link.handler.WifiP2pDiscoveryChanged;
import com.croconaut.cpt.link.handler.WifiScanResultsAvailable;
import com.croconaut.cpt.link.handler.connection.ConnectionHandler.ConnectionState;

import java.util.List;

class ConnectionHandlerReceiver extends CptBroadcastReceiver implements
        Start.Receiver, Stop.Receiver, DropConnection.Receiver, TimerExpired.Receiver,
        NetworkStateChanged.Receiver, WifiScanResultsAvailable.Receiver, WifiP2pDiscoveryChanged.Receiver, WifiP2pManager.ActionListener {
    private ConnectionState state;

    public ConnectionHandlerReceiver(Context context, String tag, ConnectionState initialState) {
        super(context, tag);
        this.state = initialState;

        // local intents
        addIntent(new Start());
        addIntent(new Stop());
        addIntent(new DropConnection());
        addIntent(new TimerExpired());
        // global intents
        addIntent(new NetworkStateChanged());
        addIntent(new WifiScanResultsAvailable());
        addIntent(new WifiP2pDiscoveryChanged());
    }

    @Override
    protected State getState() {
        return state;
    }

    private void setState(ConnectionState newState) {
        if (state != newState) {
            Log.d(TAG, state + " -> " + newState);
            state = newState;
        }
    }

    @Override
    public void onStart(Context context) {
        setState(state.onStart(context));
    }

    @Override
    public void onStop(Context context) {
        setState(state.onStop(context));
    }

    @Override
    public void onDropConnection(Context context) {
        setState(state.onDropConnection(context));
    }

    @Override
    public void onTimerExpired(Context context) {
        setState(state.onWifiTimerExpired(context));
    }

    @Override
    public void onNetworkStateChanged(Context context, NetworkInfo networkInfo) {
        setState(state.onNetworkStateChanged(context, networkInfo));
    }

    @Override
    public void onScanResultsAvailable(Context context, List<ScanResult> scanResults) {
        setState(state.onScanResultsAvailable(context, scanResults));
    }

    @Override
    public void onWifiP2pDiscoveryChanged(Context context, int wifiP2pDiscoveryState) {
        setState(state.onWifiP2pDiscoveryChanged(context, wifiP2pDiscoveryState));
    }

    @Override
    public void onSuccess() {
        setState(state.onSuccess(context));
    }

    @Override
    public void onFailure(int reason) {
        setState(state.onFailure(context, reason));
    }
}
