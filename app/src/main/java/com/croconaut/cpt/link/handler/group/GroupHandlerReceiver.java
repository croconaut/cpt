package com.croconaut.cpt.link.handler.group;

import android.content.Context;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import com.croconaut.cpt.common.State;
import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.link.handler.DropConnection;
import com.croconaut.cpt.link.handler.WifiP2pConnectionChanged;
import com.croconaut.cpt.link.handler.WifiP2pPeersChanged;
import com.croconaut.cpt.link.handler.group.GroupHandler.GroupState;

public class GroupHandlerReceiver extends CptBroadcastReceiver implements
        Stop.Receiver, DropConnection.Receiver, TimerExpired.Receiver,
        WifiP2pConnectionChanged.Receiver, WifiP2pPeersChanged.Receiver,
        WifiP2pManager.ActionListener, WifiP2pManager.GroupInfoListener, WifiP2pManager.ConnectionInfoListener {
    private GroupState state;

    public GroupHandlerReceiver(Context context, String tag, GroupState initialState) {
        super(context, tag);
        this.state = initialState;

        // local intents
        addIntent(new Stop());
        addIntent(new DropConnection());
        addIntent(new TimerExpired());
        // global intents
        addIntent(new WifiP2pConnectionChanged());
        addIntent(new WifiP2pPeersChanged());
    }

    @Override
    protected State getState() {
        return state;
    }

    private void setState(GroupState newState) {
        if (state != newState) {
            Log.d(TAG, state + " -> " + newState);
            state = newState;
        }
    }

    @Override
    public void onStop(Context context) {
        setState(state.onRemoveGroup(context));
    }

    @Override
    public void onDropConnection(Context context) {
        setState(state.onDropConnection(context));
    }

    @Override
    public void onTimerExpired(Context context) {
        setState(state.onGroupTimerExpired(context));
    }

    @Override
    public void onWifiP2pConnectionChanged(Context context, WifiP2pInfo wifiP2pInfo, NetworkInfo networkInfo, WifiP2pGroup wifiP2pGroup) {
        // TODO: check whether there isn't any useful information in networkInfo
        setState(state.onConnectionInfoAvailable(context, wifiP2pInfo));
        setState(state.onGroupInfoAvailable(context, wifiP2pGroup));
    }

    @Override
    public void onWifiP2pPeersChanged(Context context, WifiP2pDeviceList wifiP2pDeviceList) {
        // peers themselves are not required
        setState(state.onPeersChanged(context));
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
    public void onGroupInfoAvailable(WifiP2pGroup group) {
        Log.v(TAG, getClass().getSimpleName() + ".onGroupInfoAvailable" + " in state: " + state);
        setState(state.onGroupInfoAvailable(context, group));
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        Log.v(TAG, getClass().getSimpleName() + ".onConnectionInfoAvailable" + " in state: " + state);
        setState(state.onConnectionInfoAvailable(context, info));
    }
}
