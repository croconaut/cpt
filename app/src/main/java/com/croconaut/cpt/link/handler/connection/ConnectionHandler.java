package com.croconaut.cpt.link.handler.connection;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import com.croconaut.cpt.common.State;
import com.croconaut.cpt.link.handler.Handler;
import com.croconaut.cpt.link.handler.main.HandlerFailed;
import com.croconaut.cpt.link.handler.main.HandlerFinished;
import com.croconaut.cpt.link.handler.notification.ConnectionProgress;
import com.croconaut.cpt.link.handler.notification.PublishProgress;

import java.util.List;

import static junit.framework.Assert.assertNull;

public class ConnectionHandler extends Handler {
    private static final String TAG = "link.connect";

    private final ConnectionHandlerReceiver mReceiver;

    private static String mSsid;
    private String mPassphrase;

    private final ConnectionState READY = new ConnectionState("READY") {
        @Override
        ConnectionState onStart(Context context) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnectedOrConnecting()) {
                new PublishProgress().send(context, ConnectionProgress.CONNECTING2);
                // delayed connection
                return CONNECTING_TO_PEER.onNetworkStateChanged(context, networkInfo);
            } else {
                // connection request
                return connectionRestart(context, false);
            }
        }
    };

    private final ConnectionState WAITING_FOR_SCAN_RESULTS = new ConnectionState("WAITING_FOR_SCAN_RESULTS") {
        @Override
        ConnectionState onScanResultsAvailable(final Context context, List<ScanResult> scanResults) {
            cancelCurrentlyScheduledTask();

            if (isSsidInScanResults(mSsid, scanResults)) {
                Log.i(TAG, "Connecting to " + mSsid);
                if (connectToPeer()) {
                    new PublishProgress().send(context, ConnectionProgress.CONNECTING1);
                    //mWifiManager.startScan();   // refresh the list
                    scheduleTask(new Runnable() {
                        @Override
                        public void run() {
                            new TimerExpired().send(context);
                        }
                    }, 10);
                    return WAITING_FOR_CONNECTING;
                } else {
                    return connectionEnd(context, false);
                }
            } else {
                Log.w(TAG, "Chosen peer not found, abort");
                return connectionEnd(context, false);
            }
        }

        @Override
        ConnectionState onWifiP2pDiscoveryChanged(Context context, int wifiP2pDiscoveryState) {
            if (wifiP2pDiscoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                Log.w(TAG, "P2P discovery has stopped just now, reinitiated the scan to be sure");
            } else {
                Log.w(TAG, "P2P discovery has started again; stopped; reinitiated the scan");
                mWifiP2pManager.stopPeerDiscovery(mWifiP2pChannel, mReceiver);
            }
            return connectionRestart(context, false);
        }

        @Override
        ConnectionState onSuccess(Context context) {
            // TODO: maybe this is too much -- we listen to "discovery stopped" which should be enough
            Log.v(TAG, "stopPeerDiscovery.onSuccess");
            return connectionRestart(context, false);
        }

        @Override
        ConnectionState onFailure(Context context, int reason) {
            Log.e(TAG, "stopPeerDiscovery.onFailure: " + reason);
            return connectionRestart(context, false);
        }

        @Override
        ConnectionState onWifiTimerExpired(Context context) {
            Log.w(TAG, "Wifi scan results not delivered, aborting");
            return connectionEnd(context, false);
        }

        private boolean connectToPeer() {
            Log.v(TAG, toString() + ".connectToPeer");

            String peerSsid = mPreferenceHelper.getWifiPeerSsid();
            if (peerSsid != null) {
                Log.w(TAG, "This peer is still connected/connecting: " + peerSsid);
                int peerNetworkId = getNetworkIdForSsid(peerSsid);
                if (peerNetworkId >= 0) {
                    if (!peerSsid.equals(mSsid)) {
                        Log.w(TAG, "But it's different from the one: " + mSsid);
                        mWifiManager.removeNetwork(peerNetworkId);
                        mWifiManager.saveConfiguration();
                        mPreferenceHelper.resetWifiPeerSsid();
                    } else {
                        Log.w(TAG, "It's the same one, reconnecting");
                        // needed for Samsung S3 mini after not delivered CONNECTING for 2nd time
                        mWifiManager.enableNetwork(peerNetworkId, true);
                        return mWifiManager.reconnect();
                    }
                } else if (peerNetworkId == -1) {
                    Log.e(TAG, "Network ID for peer " + peerSsid + " not found (shouldn't have happened!), resetting SSID anyway");
                    mPreferenceHelper.resetWifiPeerSsid();
                } else {
                    Log.e(TAG, "Network ID for peer " + peerSsid + " not available, not connecting");
                    return false;
                }
            }

            WifiConfiguration config = new WifiConfiguration();
            config.SSID = "\"" + mSsid + "\"";
            config.preSharedKey = "\"" + mPassphrase + "\"";
            int peerNetworkId = mWifiManager.addNetwork(config);
            if (peerNetworkId != -1) {
                assertNull(mPreferenceHelper.getWifiPeerSsid());
                mPreferenceHelper.setWifiPeerSsid(mSsid);
                return mWifiManager.enableNetwork(peerNetworkId, true);
            } else {
                Log.e(TAG, "Peer network id is -1");
                return false;
            }
        }
    };

    private final ConnectionState WAITING_FOR_CONNECTING = new ConnectionState("WAITING_FOR_CONNECTING") {
        @Override
        ConnectionState onNetworkStateChanged(final Context context, NetworkInfo networkInfo) {
            if (networkInfo.getState() == NetworkInfo.State.CONNECTING && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                // now we're really connecting
                new PublishProgress().send(context, ConnectionProgress.CONNECTING2);

                //mWifiManager.startScan();   // refresh the list
                scheduleTask(new Runnable() {
                    @Override
                    public void run() {
                        new TimerExpired().send(context);
                    }
                }, 10);
                return CONNECTING_TO_PEER;
            }
            return super.onNetworkStateChanged(context, networkInfo);   // for possible CONNECTED
        }

        @Override
        ConnectionState onScanResultsAvailable(Context context, List<ScanResult> scanResults) {
            // scan results come randomly, it's not crucial to check them immediately, just do it at some point in time
            if (!isSsidInScanResults(mSsid, scanResults)) {
                Log.w(TAG, "The peer is gone, stopped connecting");
                return connectionEnd(context, false);
            } else {
                //mWifiManager.startScan();   // refresh the list
            }
            return this;
        }

        @Override
        ConnectionState onWifiTimerExpired(Context context) {
            Log.w(TAG, "CONNECTING not delivered, restarting wifi scan");
            return connectionRestart(context, true);
        }
    };

    private final ConnectionState CONNECTING_TO_PEER = new ConnectionState("CONNECTING_TO_PEER") {
        @Override
        ConnectionState onNetworkStateChanged(final Context context, NetworkInfo networkInfo) {
            if (networkInfo.getState() == NetworkInfo.State.CONNECTED && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                cancelCurrentlyScheduledTask();
                new PublishProgress().send(context, ConnectionProgress.CONNECTED);
                return CONNECTED_TO_PEER;
            } else if (networkInfo.getState() == NetworkInfo.State.CONNECTING && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                scheduleTask(new Runnable() {
                    @Override
                    public void run() {
                        new TimerExpired().send(context);
                    }
                }, 15);
            }
            return this;
        }

        @Override
        ConnectionState onScanResultsAvailable(Context context, List<ScanResult> scanResults) {
            // scan results come randomly, it's not crucial to check them immediately, just do it at some point in time
            if (!isSsidInScanResults(mSsid, scanResults)) {
                Log.w(TAG, "The peer is gone, stopped connecting");
                return connectionEnd(context, false);
            } else {
                //mWifiManager.startScan();   // refresh the list
            }
            return this;
        }

        @Override
        ConnectionState onWifiTimerExpired(Context context) {
            Log.w(TAG, "CONNECTED not delivered, restarting wifi scan");
            return connectionRestart(context, false);
        }
    };

    private final ConnectionState CONNECTED_TO_PEER = new ConnectionState("CONNECTED_TO_PEER") {
        @Override
        ConnectionState onNetworkStateChanged(Context context, NetworkInfo networkInfo) {
            if (networkInfo.getState() == NetworkInfo.State.DISCONNECTED && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                // no need to interrupt; there's been an IOException thrown for sure
                Log.w(TAG, "No longer connected to the peer");
                // there's no point in maintaining the connection, group is gone
                forgetPeer();
                return connectionEnd(context, false);
            }
            return this;
        }

        @Override
        ConnectionState onDropConnection(Context context) {
            new PublishProgress().send(context, ConnectionProgress.DISCONNECTING);

            if (!forgetPeer()) {
                return connectionEnd(context, true);
            } else {
                return DISCONNECTING_FROM_PEER;
            }
        }

        private boolean forgetPeer() {
            Log.v(TAG, toString() + ".forgetPeer");

            String peerSsid = mPreferenceHelper.getWifiPeerSsid();
            if (peerSsid != null) {
                if (!mWifiManager.disconnect()) {
                    Log.w(TAG, "disconnect() failed");
                } else {
                    int peerNetworkId = getNetworkIdForSsid(peerSsid);
                    if (peerNetworkId >= 0) {
                        mWifiManager.removeNetwork(peerNetworkId);
                        mWifiManager.saveConfiguration();
                        mPreferenceHelper.resetWifiPeerSsid();
                        return true;
                    } else if (peerNetworkId == -1) {
                        Log.e(TAG, "Network ID for peer " + peerSsid + " not found (shouldn't have happened!), resetting SSID anyway");
                        mPreferenceHelper.resetWifiPeerSsid();
                        return true;
                    } else {
                        Log.e(TAG, "Network ID for peer " + peerSsid + " not available, not resetting SSID");
                    }
                }
            }
            return false;
        }
    };

    private final ConnectionState DISCONNECTING_FROM_PEER = new ConnectionState("DISCONNECTING_FROM_PEER") {
        @Override
        ConnectionState onNetworkStateChanged(Context context, NetworkInfo networkInfo) {
            if (networkInfo.getState() == NetworkInfo.State.DISCONNECTED && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                new PublishProgress().send(context, ConnectionProgress.DISCONNECTED);
                return connectionEnd(context, true);
            }
            return this;
        }
    };

    abstract class ConnectionState extends State {
        public ConnectionState(String name) {
            super(name);
        }

        ConnectionState onNetworkStateChanged(Context context, NetworkInfo networkInfo) {
            if (networkInfo.getState() == NetworkInfo.State.CONNECTED && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                return CONNECTING_TO_PEER.onNetworkStateChanged(context, networkInfo);
            }
            return this;
        }

        ConnectionState onScanResultsAvailable(Context context, List<ScanResult> scanResults) {
            return this;
        }

        ConnectionState onWifiP2pDiscoveryChanged(Context context, int wifiP2pDiscoveryState) {
            return this;
        }

        ConnectionState onSuccess(Context context) {
            Log.w(TAG, "onSuccess() in state " + this);
            return this;
        }

        ConnectionState onFailure(Context context, int reason) {
            Log.w(TAG, "onFailure() in state " + this);
            return this;
        }

        ConnectionState onStart(Context context) {
            Log.e(TAG, "onStart() in state " + this);
            return this;
        }

        ConnectionState onStop(Context context) {
            // let the connection active -- if it's a real unregister, we'll call disconnect() later on
            return connectionEnd(context, true);
        }

        ConnectionState onWifiTimerExpired(Context context) {
            return this;
        }

        ConnectionState onDropConnection(Context context) {
            return this;
        }

        // helper functions

        protected ConnectionState connectionEnd(Context context, boolean success) {
            cancelCurrentlyScheduledTask();
            mReceiver.unregister();
            if (success) {
                new HandlerFinished().send(context);
            } else {
                new HandlerFailed().send(context);
            }
            return READY;
        }

        protected ConnectionState connectionRestart(final Context context, boolean disconnect) {
            new PublishProgress().send(context, ConnectionProgress.SCANNING);

            if (disconnect && !mWifiManager.disconnect()) {
                Log.w(TAG, "disconnect() failed");
                return connectionEnd(context, false);
            }
            if (!mWifiManager.startScan()) {
                Log.w(TAG, "startScan() failed");
                return connectionEnd(context, false);
            }

            scheduleTask(new Runnable() {
                @Override
                public void run() {
                    new TimerExpired().send(context);
                }
            }, 20);
            return WAITING_FOR_SCAN_RESULTS;
        }

        protected boolean isSsidInScanResults(String ssid, List<ScanResult> scanResults) {
            boolean found = false;
            if (scanResults != null) {
                for (ScanResult scanResult : scanResults) {
                    if (scanResult.SSID.equals(ssid)) {
                        found = true;
                        break;
                    }
                }
            }
            return found;
        }
    }

    public ConnectionHandler(Context context) {
        super(context, TAG);
        mReceiver = new ConnectionHandlerReceiver(context, TAG, READY);
    }

    @Override
    public void start() {
        Log.v(TAG, getClass().getSimpleName() + ".start");

        mReceiver.register();

        new Start().send(context);
    }

    @Override
    public void stop() {
        Log.v(TAG, getClass().getSimpleName() + ".stop");

        new Stop().send(context);
    }

    public void setup(String ssid, String passphrase) {
        mSsid = ssid;
        mPassphrase = passphrase;
    }

    public static String getLastUsedSsid() {
        return mSsid;
    }

    public void disconnect() {
        Log.v(TAG, getClass().getSimpleName() + ".disconnect");

        if (mSsid != null) {
            if (!mWifiManager.disconnect()) {
                Log.e(TAG, "disconnect() failed");
            }
            int networkId = getNetworkIdForSsid(mSsid);
            if (networkId >= 0) {
                if (!mWifiManager.removeNetwork(networkId)) {
                    Log.e(TAG, "removeNetwork() failed");
                }
                if (!mWifiManager.saveConfiguration()) {
                    Log.e(TAG, "saveConfiguration() failed");
                }
            } else {
                Log.w(TAG, "No network id for such ssid: " + mSsid);
            }
            mSsid = null;
        } else {
            Log.d(TAG, "No SSID registered");
        }
    }

    public void disconnect(String ssid) {
        // this should be called only with unknown SSIDs, so yeah, assign it here
        mSsid = ssid;
        disconnect();
    }

    private int getNetworkIdForSsid(String ssid) {
        Log.v(TAG, getClass().getSimpleName() + ".getNetworkIdForSsid");
        int networkId = -1;

        List<WifiConfiguration> wifiNetworks = mWifiManager.getConfiguredNetworks();
        if (wifiNetworks != null) {
            for (WifiConfiguration wifiNetwork : wifiNetworks) {
                if (wifiNetwork != null && wifiNetwork.SSID != null) {
                    String networkSSid = wifiNetwork.SSID;
                    if (networkSSid.startsWith("\"") && networkSSid.endsWith("\"")) {
                        networkSSid = networkSSid.substring(1, networkSSid.length() - 1);
                    } else {
                        // 4.1 and 4.1.1 only
                        //Log.w(TAG, "SSID quotation bug in Android " + Build.VERSION.RELEASE + " detected");
                    }
                    if (networkSSid.equals(ssid)) {
                        networkId = wifiNetwork.networkId;
                        break;
                    }
                }
            }
        } else {
            Log.w(TAG, "The list of configured networks is null");
            networkId = -2;
        }

        return networkId;
    }
}
