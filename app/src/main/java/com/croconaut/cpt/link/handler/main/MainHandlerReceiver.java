package com.croconaut.cpt.link.handler.main;

import android.content.Context;
import android.net.NetworkInfo;
import android.util.Log;

import com.croconaut.cpt.common.State;
import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.link.handler.NetworkStateChanged;
import com.croconaut.cpt.link.handler.WifiP2pStateChanged;
import com.croconaut.cpt.link.handler.main.MainHandler.HandlerState;

import java.net.InetSocketAddress;
import java.util.ArrayList;

class MainHandlerReceiver extends CptBroadcastReceiver implements
        // service
        GcmSyncRequest.Receiver, NewMessage.Receiver, RestartTimerExpired.Receiver,
        // local
        Start.Receiver, Stop.Receiver, TimerExpired.Receiver, HandlerFinished.Receiver, HandlerFailed.Receiver, DiscoveryResults.Receiver,
        NetworkSyncServiceFinished.Receiver, UpdatedIgnoredDevices.Receiver, CancelConnection.Receiver, NewConnectableClient.Receiver,
        NewAttachment.Receiver, ServerSyncStarted.Receiver,
        // global
        NetworkStateChanged.Receiver, WifiP2pStateChanged.Receiver {
    private HandlerState state;

    public MainHandlerReceiver(Context context, String tag, HandlerState initialState) {
        super(context, tag);
        this.state = initialState;

        // local intents
        addIntent(new Start());
        addIntent(new Stop());
        addIntent(new TimerExpired());
        addIntent(new HandlerFinished());
        addIntent(new HandlerFailed());
        addIntent(new NetworkSyncServiceFinished());
        addIntent(new UpdatedIgnoredDevices());
        addIntent(new NewConnectableClient());
        addIntent(new ServerSyncStarted());
        // global intents
        addIntent(new NetworkStateChanged());
        addIntent(new WifiP2pStateChanged());
        // global service intents
        addIntent(new GcmSyncRequest());
        addIntent(new NewMessage());
        addIntent(new NewAttachment());
        addIntent(new RestartTimerExpired());
        addIntent(new CancelConnection());
        addIntent(new DiscoveryResults());
    }

    @Override
    public void onWifiP2pStateChanged(Context context, int wifiP2pState) {
        setState(state.onP2pStateChanged(context, wifiP2pState));
    }

    @Override
    public void onNetworkStateChanged(Context context, NetworkInfo networkInfo) {
        setState(state.onNetworkStateChanged(context, networkInfo));
    }

    @Override
    public void onStart(Context context, int startId) {
        setState(state.onStart(context, startId));
    }

    @Override
    public void onStop(Context context, int startId, boolean disable) {
        setState(state.onStop(context, startId, disable));
    }

    @Override
    public void onTimerExpired(Context context) {
        setState(state.onTimerExpired(context));
    }

    @Override
    public void onRestartTimerExpired(Context context, int startId, boolean force) {
        setState(state.onRestartWifiTimerExpired(context, startId, force));
    }

    @Override
    public void onHandlerFinished(Context context) {
        setState(state.onFinished(context));
    }

    @Override
    public void onHandlerFailed(Context context) {
        setState(state.onFailure(context));
    }

    @Override
    public void onDiscoveryResults(Context context, int startId, ArrayList<User> users) {
        setState(state.onDiscoveryResultsAvailable(context, startId, users));
    }

    @Override
    public void onUpdatedIgnoredDevices(Context context, String crocoIdToCancel) {
        setState(state.onUpdatedIgnoredDevices(context, crocoIdToCancel));
    }

    @Override
    public void onCancelConnection(Context context, int startId, String cancelledCrocoId) {
        setState(state.onCancelConnection(context, startId, cancelledCrocoId));
    }

    @Override
    public void onNewMessage(Context context, int startId, String to) {
        setState(state.onNewMessage(context, startId, to));
    }

    @Override
    public void onGcmSyncRequest(Context context, int startId, int what) {
        setState(state.onGcmSyncRequest(context, startId, what));
    }

    @Override
    public void onNewConnectableClient(Context context, String crocoId, InetSocketAddress socketAddress, String hash, boolean isP2pClient) {
        setState(state.onNewConnectableClient(context, crocoId, socketAddress, hash, isP2pClient));
    }

    @Override
    public void onNetworkSyncServiceFinished(Context context, String crocoId, int what, long timeStampSuccess) {
        setState(state.onNetworkSyncServiceFinished(context, crocoId, what, timeStampSuccess));
    }

    @Override
    public void onNewAttachment(Context context, int startId, String from) {
        setState(state.onNewAttachment(context, startId, from));
    }

    @Override
    public void onServerSyncStarted(Context context) {
        setState(state.onServerSyncStarted(context));
    }

    @Override
    protected State getState() {
        return state;
    }

    private void setState(HandlerState newState) {
        if (state != newState) {
            Log.d(TAG, state + " -> " + newState);
            state = newState;
        }
    }
}
