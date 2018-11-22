package com.croconaut.cpt.common.intent;

import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;

public class PrettyState {
    public static String log(Intent intent) {
        switch(intent.getAction()) {
            case WifiManager.WIFI_STATE_CHANGED_ACTION:
                return logWifiState(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1));

            case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                return logNetworkState((NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO));

            case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
                return logWifiP2pState(intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1));

            case WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION:
                return logWifiP2pDiscoveryState(intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1));

            default:
                return intent.getAction();
        }
    }

    private static String logNetworkState(NetworkInfo ni) {
        NetworkInfo.State state = ni.getState();
        return "Network state: " + state.toString();
    }

    private static String logWifiState(int state) {
        String stateString = "Unknown";
        switch (state) {
            case WifiManager.WIFI_STATE_DISABLED:
                stateString = "WIFI_STATE_DISABLED";
                break;
            case WifiManager.WIFI_STATE_DISABLING:
                stateString = "WIFI_STATE_DISABLING";
                break;
            case WifiManager.WIFI_STATE_ENABLED:
                stateString = "WIFI_STATE_ENABLED";
                break;
            case WifiManager.WIFI_STATE_ENABLING:
                stateString = "WIFI_STATE_ENABLING";
                break;
            case WifiManager.WIFI_STATE_UNKNOWN:
                stateString = "WIFI_STATE_UNKNOWN";
                break;
        }

        return "Wi-Fi state: " + stateString;
    }

    private static String logWifiP2pState(int state) {
        String stateString = "Unknown";
        switch (state) {
            case WifiP2pManager.WIFI_P2P_STATE_DISABLED:
                stateString = "WIFI_P2P_STATE_DISABLED";
                break;
            case WifiP2pManager.WIFI_P2P_STATE_ENABLED:
                stateString = "WIFI_P2P_STATE_ENABLED";
                break;
        }

        return "Wi-Fi P2P state: " + stateString;
    }

    private static String logWifiP2pDiscoveryState(int state) {
        String stateString = "Unknown";
        switch (state) {
            case WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED:
                stateString = "WIFI_P2P_DISCOVERY_STARTED";
                break;
            case WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED:
                stateString = "WIFI_P2P_DISCOVERY_STOPPED";
                break;
        }

        return "Wi-Fi P2P discovery state: " + stateString;
    }
}
