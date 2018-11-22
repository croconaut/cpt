package com.croconaut.cpt.link.handler.p2p;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.util.Log;

import com.croconaut.cpt.common.State;
import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.link.handler.WifiP2pDiscoveryChanged;
import com.croconaut.cpt.link.handler.WifiP2pPeersChanged;
import com.croconaut.cpt.link.handler.p2p.P2pHandler.P2pState;

import java.net.InetAddress;
import java.util.Map;

public class P2pHandlerReceiver extends CptBroadcastReceiver implements
        Start.Receiver, Stop.Receiver, TimerExpired.Receiver, InitiateDiscovery.Receiver, UpdatedHash.Receiver, UpdatedNetworkState.Receiver, UpdatedTargetAp.Receiver, UpdatedUsername.Receiver,
        AddClientP2pDnsSdRecord.Receiver, RemoveClientP2pDnsSdRecord.Receiver,
        WifiP2pPeersChanged.Receiver, WifiP2pDiscoveryChanged.Receiver,
        WifiP2pManager.ActionListener, WifiP2pManager.DnsSdTxtRecordListener, WifiP2pManager.PeerListListener {
    private P2pState state;

    public P2pHandlerReceiver(Context context, String tag, P2pState initialState) {
        super(context, tag);
        this.state = initialState;

        // local intents
        addIntent(new Start());
        addIntent(new Stop());
        addIntent(new TimerExpired());
        addIntent(new InitiateDiscovery());
        addIntent(new UpdatedHash());
        addIntent(new UpdatedNetworkState());
        addIntent(new UpdatedTargetAp());
        addIntent(new UpdatedUsername());
        addIntent(new AddClientP2pDnsSdRecord());
        addIntent(new RemoveClientP2pDnsSdRecord());
        // global intents
        addIntent(new WifiP2pPeersChanged());
        addIntent(new WifiP2pDiscoveryChanged());
    }

    @Override
    protected State getState() {
        return state;
    }

    private void setState(P2pState newState) {
        if (state != newState) {
            Log.d(TAG, state + " -> " + newState);
            state = newState;
        }
    }

    @Override
    public void onStart(Context context) {
        setState(state.onStartDiscovery(context));
    }

    @Override
    public void onStop(Context context, boolean waitForDnsTxtRecordChange) {
        setState(state.onStopDiscovery(context, waitForDnsTxtRecordChange));
    }

    @Override
    public void onTimerExpired(Context context) {
        setState(state.onDiscoveryTimerExpired(context));
    }

    @Override
    public void onInitiateDiscovery(Context context) {
        setState(state.onInitiateServiceDiscovery(context));
    }

    @Override
    public void onUpdatedHash(Context context, String hash) {
        setState(state.onUpdatedHash(context, hash));
    }

    @Override
    public void onUpdatedNetworkState(Context context, InetAddress networkAddress) {
        setState(state.onUpdatedNetworkState(context, networkAddress));
    }

    @Override
    public void onUpdatedTargetAp(Context context, String targetAp) {
        setState(state.onUpdatedTargetAp(context, targetAp));
    }

    @Override
    public void onUpdatedUsername(Context context, String username) {
        setState(state.onUpdatedUsername(context, username));
    }

    @Override
    public void onAddClientP2pDnsSdRecord(Context context, String instanceName, Bundle record) {
        setState(state.onAddClientP2pDnsSdRecord(context, instanceName, record));
    }

    @Override
    public void onRemoveClientP2pDnsSdRecord(Context context, String instanceName) {
        setState(state.onRemoveClientP2pDnsSdRecord(context, instanceName));
    }

    @Override
    public void onWifiP2pPeersChanged(Context context, WifiP2pDeviceList wifiP2pDeviceList) {
        state.onPeersAvailable(context, wifiP2pDeviceList);
    }

    @Override
    public void onWifiP2pDiscoveryChanged(Context context, int wifiP2pDiscoveryState) {
        setState(state.onDiscoveryChanged(context, wifiP2pDiscoveryState));
    }

    @Override
    public void onFailure(int reason) {
        setState(state.onFailure(context, reason));
    }
    @Override
    public void onSuccess() {
        setState(state.onSuccess(context));
    }

    @Override
    public void onDnsSdTxtRecordAvailable(String fullDomainName, Map<String, String> txtRecordMap, WifiP2pDevice srcDevice) {
        // not changing state
        state.onDnsSdTxtRecordAvailable(context, fullDomainName, txtRecordMap, srcDevice);
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peers) {
        // not changing state
        state.onPeersAvailable(context, peers);
    }
}
