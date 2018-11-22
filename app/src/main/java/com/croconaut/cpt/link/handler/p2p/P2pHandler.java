package com.croconaut.cpt.link.handler.p2p;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Bundle;
import android.util.Log;

import com.croconaut.cpt.common.State;
import com.croconaut.cpt.data.CptClientCommunication;
import com.croconaut.cpt.link.handler.Handler;
import com.croconaut.cpt.link.handler.group.GroupHandler;
import com.croconaut.cpt.link.handler.main.DiscoveryResults;
import com.croconaut.cpt.link.handler.main.HandlerFailed;
import com.croconaut.cpt.link.handler.main.HandlerFinished;
import com.croconaut.cpt.link.handler.main.User;
import com.croconaut.cpt.network.NetworkUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class P2pHandler extends Handler {
    private static final String TAG = "link.service";

    private static final String SERVICE_TYPE  = "_wifon._tcp";
    private static final String INSTANCE_NAME = "_device";

    public static final int DISCOVERY_PERIOD_IN_SECS = 15;

    private final P2pHandlerReceiver mReceiver;
    private final int mPort;
    private boolean mIsDiscovering;

    private static HashMap<String, HashMap<String, String>> mTxtRecods = new HashMap<>();
    private static HashSet<String> mIsDnsSdRecordChanged = new HashSet<>();

    private boolean mIsPendingStop;
    private static WifiP2pDnsSdServiceRequest mWifiP2pDnsSdServiceRequest;  // static because of https://code.google.com/p/android/issues/detail?id=178610
    private static HashMap<String, WifiP2pDnsSdServiceInfo> mWifiP2pDnsSdServiceInfo = new HashMap<>();    // ... but also because it less stresses out the wifi stack

    private HashMap<String, User> mUsers = new HashMap<>();
    private static HashMap<String, String> mNameCache = new HashMap<>();

    private final P2pState READY = new P2pState("READY") {
        @Override
        P2pState onStartDiscovery(Context context) {
            if (mWifiP2pDnsSdServiceRequest == null) {
                addServiceRequest();
                return REQUEST_ADD;
            } else {
                Log.d(TAG, "mWifiP2pDnsSdServiceRequest != null");
                return REQUEST_ADD.onSuccess(context);
            }
        }

        @Override
        P2pState onStopDiscovery(Context context, boolean waitForDnsTxtRecordChange) {
            // READY means no start discovery has happened, i.e. no receiver is registered
            new HandlerFinished().send(context);
            return this;
        }

        private void addServiceRequest() {
            Log.v(TAG, toString() + ".addServiceRequest");

            mWifiP2pDnsSdServiceRequest = WifiP2pDnsSdServiceRequest.newInstance(); // instance & service type parameters work for DnsSdServiceResponseListener only? TODO check
            mWifiP2pManager.addServiceRequest(mWifiP2pChannel, mWifiP2pDnsSdServiceRequest, mReceiver);
        }
    };

    private final P2pState REQUEST_ADD = new P2pState("REQUEST_ADD") {
        @Override
        public P2pState onFailure(Context context, int reason) {
            Log.e(TAG, "addServiceRequest() failed: " + reason);
            mWifiP2pDnsSdServiceRequest = null;

            return failure(context);
        }

        @Override
        public P2pState onSuccess(Context context) {
            if (!mIsDnsSdRecordChanged.isEmpty()) {
                // INSTANCE_NAME has always top priority
                String instanceName = mIsDnsSdRecordChanged.contains(INSTANCE_NAME)
                        ? INSTANCE_NAME : mIsDnsSdRecordChanged.iterator().next();
                SERVICE_REMOVE.instanceName = instanceName;
                if (mWifiP2pDnsSdServiceInfo.containsKey(instanceName)) {
                    removeLocalService(instanceName);
                    return SERVICE_REMOVE;
                } else {
                    return SERVICE_REMOVE.onSuccess(context);
                }
            } else if (mIsPendingStop) {
                // don't remove neither the request nor info
                return finish(context);
            } else {
                return SERVICE_ADD.onSuccess(context);
            }
        }

        private void removeLocalService(String instanceName) {
            Log.v(TAG, toString() + ".removeLocalService: " + instanceName);

            mWifiP2pManager.removeLocalService(mWifiP2pChannel,
                    mWifiP2pDnsSdServiceInfo.get(instanceName),
                    mReceiver);
        }
    };

    private final P2pState SERVICE_REMOVE = new P2pState("SERVICE_REMOVE") {
        @Override
        public P2pState onFailure(Context context, int reason) {
            Log.e(TAG, "removeLocalService() '" + instanceName + "' failed: " + reason);
            mWifiP2pDnsSdServiceInfo.remove(instanceName);

            return failure(context);
        }

        @Override
        public P2pState onSuccess(Context context) {
            mWifiP2pDnsSdServiceInfo.remove(instanceName);

            SERVICE_ADD.instanceName = instanceName;
            if (!mTxtRecods.get(instanceName).isEmpty()) {
                addLocalService(instanceName);
                return SERVICE_ADD;
            } else {
                Log.d(TAG, "Not adding empty instance: " + instanceName);
                return SERVICE_ADD.onSuccess(context);
            }
        }

        private void addLocalService(String instanceName) {
            Log.v(TAG, toString() + ".addLocalService: " + instanceName);

            HashMap<String, String> txtMap = mTxtRecods.get(instanceName);
            Log.i(TAG, "Registered map: " + txtMap);

            mWifiP2pDnsSdServiceInfo.put(instanceName,
                    WifiP2pDnsSdServiceInfo.newInstance(instanceName, SERVICE_TYPE, txtMap));
            mWifiP2pManager.addLocalService(mWifiP2pChannel,
                    mWifiP2pDnsSdServiceInfo.get(instanceName),
                    mReceiver);
        }
    };

    private final P2pState SERVICE_ADD = new P2pState("SERVICE_ADD") {
        private HashSet<String> mIsAnotherDnsSdRecordPending = new HashSet<>();

        @Override
        public P2pState onFailure(Context context, int reason) {
            Log.e(TAG, "addLocalService() '" + instanceName + "' failed: " + reason);
            mWifiP2pDnsSdServiceInfo.remove(instanceName);

            return failure(context);
        }

        @Override
        public P2pState onSuccess(Context context) {
            mIsDnsSdRecordChanged.remove(instanceName);

            if (!mIsAnotherDnsSdRecordPending.isEmpty()) {
                mIsDnsSdRecordChanged.addAll(mIsAnotherDnsSdRecordPending);
                Log.w(TAG, "Another DNS SD record change request pending: " + mIsAnotherDnsSdRecordPending);
                mIsAnotherDnsSdRecordPending.clear();
                // don't return to READY -- to avoid infinite loop on too many hash changes,
                // better wait for discoverServices() to finish
            }

            if (mIsPendingStop) {
                return finish(context);
            } else {
                if (NetworkUtil.hasRunningConnection()) {
                    Log.d(TAG, "Not starting peer discovery because there's a long running network connection");
                    stopPeerDiscovery(false);
                    return DISCOVERY_PEERS.onSuccess(context);
                } else {
                    discoverPeers();
                    return DISCOVERY_PEERS;
                }
            }
        }

        @Override
        protected P2pState onUpdateDnsSdTxtRecord(Context context, String instanceName) {
            mIsAnotherDnsSdRecordPending.add(instanceName);
            return this;
        }

        private void discoverPeers() {
            Log.v(TAG, toString() + ".discoverPeers");

            mWifiP2pManager.discoverPeers(mWifiP2pChannel, mReceiver);
        }

        private void stopPeerDiscovery(boolean isInterestedInResult) {
            Log.v(TAG, toString() + ".stopPeerDiscovery");

            mWifiP2pManager.stopPeerDiscovery(mWifiP2pChannel, isInterestedInResult ? mReceiver : null);
        }
    };

    private final P2pState DISCOVERY_PEERS = new P2pState("DISCOVERY_PEERS") {
        // don't check for changed dns sd records here -- in case of a steady stream of hash changes
        // we would never do a service discovery => never connect to anyone

        @Override
        public P2pState onFailure(final Context context, int reason) {
            Log.e(TAG, "discoverPeers() failed: " + reason);

            // TODO: stop peer discovery? (perhaps not, it seems the discovery stops itself)
            return failure(context);
        }

        @Override
        public P2pState onSuccess(Context context) {
            if (mIsPendingStop) {
                return DISCOVERY.onStopDiscovery(context, false);
            }
            startTimer(context);
            return DISCOVERY;
        }

        private void startTimer(final Context context) {
            scheduleTask(new Runnable() {
                @Override
                public void run() {
                    new TimerExpired().send(context);
                }
            }, DISCOVERY_PERIOD_IN_SECS);
        }
    };

    private final P2pState DISCOVERY = new P2pState("DISCOVERY") {
        private long mLastDiscoveryServicesTime;
        private boolean mIsServiceDiscoveryInitiated;

        @Override
        P2pState onDiscoveryChanged(Context context, int wifiP2pDiscoveryState) {
            super.onDiscoveryChanged(context, wifiP2pDiscoveryState);

            if (wifiP2pDiscoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                Log.w(TAG, "Discovery abruptly stopped, restarting");
                resetBeforeLeave();
                return READY.onStartDiscovery(context);
            }
            return this;
        }

        @Override
        public P2pState onFailure(final Context context, int reason) {
            Log.e(TAG, "discoverServices() failed: " + reason);
            resetBeforeLeave();
            // TODO: stop peer discovery?
            return failure(context);
        }

        @Override
        public P2pState onSuccess(Context context) {
            // discoverServices() has succeeded
            mIsServiceDiscoveryInitiated = true;

            if (!mIsDnsSdRecordChanged.isEmpty()) {
                stopPeerDiscovery(false);
            }
            return this;
        }

        @Override
        P2pState onInitiateServiceDiscovery(final Context context) {
            boolean hasSkippedDiscoverServices = false;

            long currentTime = System.currentTimeMillis();
            int timeDifferenceInSecs = (int) ((currentTime - mLastDiscoveryServicesTime) / 1000);
            if (timeDifferenceInSecs >= DISCOVERY_PERIOD_IN_SECS) {
                mLastDiscoveryServicesTime = currentTime;
                mIsServiceDiscoveryInitiated = false;
                timeDifferenceInSecs = (int) ((currentTime - mLastDiscoveryServicesTime) / 1000);
                if (NetworkUtil.hasRunningConnection()) {
                    Log.d(TAG, "Not starting service discovery because there's a long running network connection");
                    stopPeerDiscovery(false);
                    hasSkippedDiscoverServices = true;
                } else {
                    discoverServices();
                }
            }

            cancelCurrentlyScheduledTask();
            if (!mUsers.isEmpty()) {
                // if there's nobody nearby in the meantime, stop discovering services
                scheduleTask(new Runnable() {
                    @Override
                    public void run() {
                        new TimerExpired().send(context);
                    }
                }, DISCOVERY_PERIOD_IN_SECS - timeDifferenceInSecs);
                if (mLastDiscoveryServicesTime == currentTime) {
                    Log.i(TAG, "Discovery scheduled in " + (DISCOVERY_PERIOD_IN_SECS - timeDifferenceInSecs) + " seconds");
                } else {
                    Log.v(TAG, (DISCOVERY_PERIOD_IN_SECS - timeDifferenceInSecs) + " seconds left to next discovery");
                }
            }

            return hasSkippedDiscoverServices ? onSuccess(context) : this;
        }

        @Override
        protected P2pState onUpdateDnsSdTxtRecord(Context context, String instanceName) {
            mIsDnsSdRecordChanged.add(instanceName);

            if (mIsServiceDiscoveryInitiated || mUsers.isEmpty()) {
                // only if discoverServices() has really succeeded
                stopPeerDiscovery(false);
            }
            return this;
        }

        @Override
        P2pState onStopDiscovery(Context context, boolean waitForDnsTxtRecordChange) {
            if (!waitForDnsTxtRecordChange || mIsDnsSdRecordChanged.isEmpty()) {
                resetBeforeLeave();
                stopPeerDiscovery(true);
                startTimer(context);
                return DISCOVERY_STOPPING;
            } else {
                Log.w(TAG, "Not stopping as we want update DNS SD records first");
                mIsPendingStop = true;
                return this;
            }
        }

        @Override
        P2pState onDiscoveryTimerExpired(Context context) {
            Log.w(TAG, "Restarting discovery");
            resetBeforeLeave();
            return READY.onStartDiscovery(context);
        }

        private void stopPeerDiscovery(boolean isInterestedInResult) {
            Log.v(TAG, toString() + ".stopPeerDiscovery");

            mWifiP2pManager.stopPeerDiscovery(mWifiP2pChannel, isInterestedInResult ? mReceiver : null);
        }

        private void discoverServices() {
            Log.v(TAG, toString() + ".discoverServices");

            mWifiP2pManager.discoverServices(mWifiP2pChannel, mReceiver);
        }

        private void startTimer(final Context context) {
            scheduleTask(new Runnable() {
                @Override
                public void run() {
                    new TimerExpired().send(context);
                }
            }, 1/*5*/);
        }

        private void resetBeforeLeave() {
            mIsServiceDiscoveryInitiated = false;
            cancelCurrentlyScheduledTask();
            mLastDiscoveryServicesTime = 0;
        }
    };

    private final P2pState DISCOVERY_STOPPING = new P2pState("DISCOVERY_STOPPING") {
        @Override
        P2pState onDiscoveryChanged(final Context context, int wifiP2pDiscoveryState) {
            super.onDiscoveryChanged(context, wifiP2pDiscoveryState);

            if (wifiP2pDiscoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                cancelCurrentlyScheduledTask();

                return finish(context);
            }
            return this;
        }

        @Override
        P2pState onSuccess(Context context) {
            // stopPeerDiscovery() has succeeded, don't wait for actual stop
            return finish(context);
        }

        @Override
        public P2pState onFailure(Context context, int reason) {
            Log.e(TAG, "stopPeerDiscovery() failed: " + reason);
            cancelCurrentlyScheduledTask();

            return failure(context);
        }

        @Override
        P2pState onDiscoveryTimerExpired(Context context) {
            Log.w(TAG, "Forced discovery stop");
            return onDiscoveryChanged(context, WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);
        }
    };

    abstract class P2pState extends State {
        // bit of a hack...
        protected String instanceName;

        public P2pState(String name) {
            super(name);
        }

        void onPeersAvailable(Context context, WifiP2pDeviceList wifiP2pDeviceList) {
            Log.v(TAG, toString() + ".onPeersAvailable");

            if (!mIsDiscovering) {
                // avoid reporting zero nearby peers only because discovery is stopped
                Log.v(TAG, "Not reporting in onPeersAvailable() as discovery is stopped");
                return;
            }

            if (wifiP2pDeviceList != null) {
                Set<String> currentUsers = new HashSet<>();
                for (WifiP2pDevice wifiP2pDevice : wifiP2pDeviceList.getDeviceList()) {
                    String status = "???";
                    switch (wifiP2pDevice.status) {
                        case WifiP2pDevice.CONNECTED:
                            status = "CONNECTED";
                            break;
                        case WifiP2pDevice.INVITED:
                            status = "INVITED";
                            break;
                        case WifiP2pDevice.FAILED:
                            status = "FAILED";
                            break;
                        case WifiP2pDevice.AVAILABLE:
                            status = "AVAILABLE";
                            break;
                        case WifiP2pDevice.UNAVAILABLE:
                            status = "UNAVAILABLE";
                            break;
                    }
                    Log.d(TAG, "P2P Device: " + wifiP2pDevice.deviceAddress + " [" + status + "] (GO: " + wifiP2pDevice.isGroupOwner() + ", SDC: " + wifiP2pDevice.isServiceDiscoveryCapable() + ")");

                    currentUsers.add(wifiP2pDevice.deviceAddress);

                    User user = mUsers.get(wifiP2pDevice.deviceAddress);
                    if (user == null) {
                        user = new User(wifiP2pDevice.deviceAddress, wifiP2pDevice.isGroupOwner());
                        mUsers.put(wifiP2pDevice.deviceAddress, user);
                    } else {
                        user.isGroupOwner = wifiP2pDevice.isGroupOwner();
                    }
                    if (user.username == null) {
                        user.username = mNameCache.get(user.crocoId);
                    }
                }

                // remove disappeared peers
                for (Iterator<String> it = mUsers.keySet().iterator(); it.hasNext(); ) {
                    String crocoId = it.next();

                    if (!currentUsers.contains(crocoId)) {
                        it.remove();
                    }
                }

                if (!currentUsers.isEmpty()) {
                    new InitiateDiscovery().send(context);
                }
                new DiscoveryResults().send(context, new ArrayList<>(mUsers.values()));
            } else {
                requestPeers();
            }
        }

        void onDnsSdTxtRecordAvailable(Context context, String fullDomainName, Map<String, String> txtRecordMap, WifiP2pDevice srcDevice) {
            Log.v(TAG, toString() + ".onDnsSdTxtRecordAvailable");

            if (fullDomainName.startsWith(INSTANCE_NAME + "." + SERVICE_TYPE)) {
                String deviceAddress = srcDevice.deviceAddress;
                boolean isGroupOwner = srcDevice.isGroupOwner();
                String hash = txtRecordMap.get("H");
                String networkAddress = txtRecordMap.get("L");
                int port = Integer.valueOf(txtRecordMap.get("O"));
                String username = txtRecordMap.get("N");
                String targetAp = txtRecordMap.get("A");

                InetAddress networkInetAddress = null;
                try {
                    if (networkAddress != null) {
                        networkInetAddress = InetAddress.getByName(networkAddress);
                    }
                } catch (UnknownHostException e) {
                    Log.w(TAG, "Illegal IP address: " + networkAddress);
                }

                User user = mUsers.get(deviceAddress);
                if (user == null) {
                    user = new User(deviceAddress, hash, networkInetAddress, port, isGroupOwner, username, targetAp);
                    mUsers.put(user.crocoId, user);
                } else {
                    user.isGroupOwner = isGroupOwner;
                    user.hash = hash;
                    user.networkAddress = networkInetAddress;
                    user.port = port;
                    user.username = username;
                    user.targetAp = targetAp;
                }
                if (user.username == null) {
                    user.username = mNameCache.get(user.crocoId);
                } else {
                    mNameCache.put(user.crocoId, user.username);
                }
                Log.i(TAG, user.toString());
                new DiscoveryResults().send(context, new ArrayList<>(mUsers.values()));
            } else if (fullDomainName.startsWith(GroupHandler.INSTANCE_NAME + "." + GroupHandler.SERVICE_TYPE)) {
                String deviceAddress = srcDevice.deviceAddress;
                boolean isGroupOwner = srcDevice.isGroupOwner();
                String ssid = txtRecordMap.get("S");
                String passphrase = txtRecordMap.get("P");

                User user = mUsers.get(deviceAddress);
                if (user == null) {
                    user = new User(deviceAddress, ssid, passphrase, isGroupOwner);
                    mUsers.put(user.crocoId, user);
                } else {
                    user.isGroupOwner = isGroupOwner;
                    user.ssid = ssid;
                    user.passphrase = passphrase;
                }
                if (user.username == null) {
                    user.username = mNameCache.get(user.crocoId);
                }
                Log.i(TAG, user.toString());
                new DiscoveryResults().send(context, new ArrayList<>(mUsers.values()));
            } else {
                String instanceName = fullDomainName.substring(0, fullDomainName.indexOf('.'));
                String serviceType = fullDomainName.substring(fullDomainName.indexOf('.') + 1);
                if (serviceType.startsWith(SERVICE_TYPE)) {
                    CptClientCommunication.p2pDnsSdRecordAvailable(context, mPreferenceHelper, instanceName, txtRecordMap);
                }
            }
        }

        P2pState onDiscoveryChanged(Context context, int wifiP2pDiscoveryState) {
            mIsDiscovering = wifiP2pDiscoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED;
            return this;
        }

        P2pState onFailure(Context context, int reason) {
            Log.e(TAG, "onFailure() in state " + this + ": " + reason);
            return this;
        }

        P2pState onSuccess(Context context) {
            return this;
        }

        P2pState onStartDiscovery(Context context) {
            Log.e(TAG, "onStartDiscovery() in state " + this);
            return this;
        }

        P2pState onStopDiscovery(Context context, boolean waitForDnsTxtRecordChange) {
            mIsPendingStop = true;
            return this;
        }

        P2pState onInitiateServiceDiscovery(Context context) {
            Log.w(TAG, "onInitiateServiceDiscovery() in state " + this);
            return this;
        }

        P2pState onDiscoveryTimerExpired(Context context) {
            Log.w(TAG, "onDiscoveryTimerExpired() in state " + this);
            return this;
        }

        P2pState onUpdatedHash(Context context, String hash) {
            HashMap<String, String> txtMap = mTxtRecods.get(INSTANCE_NAME);

            if (hash != null && (!txtMap.containsKey("H") || !txtMap.get("H").equals(hash))) {
                txtMap.put("H", hash);
                return onUpdateDnsSdTxtRecord(context, INSTANCE_NAME);
            }
            return this;
        }

        P2pState onUpdatedNetworkState(Context context, InetAddress networkAddress) {
            HashMap<String, String> txtMap = mTxtRecods.get(INSTANCE_NAME);

            String networkAddressString = networkAddress != null ? networkAddress.getHostAddress() : null;
            if (networkAddressString == null && txtMap.containsKey("L")) {
                txtMap.remove("L");
                return onUpdateDnsSdTxtRecord(context, INSTANCE_NAME);
            } else if (networkAddressString != null && (!txtMap.containsKey("L") || !txtMap.get("L").equals(networkAddressString))) {
                txtMap.put("L", networkAddressString);
                return onUpdateDnsSdTxtRecord(context, INSTANCE_NAME);
            }
            return this;
        }

        P2pState onUpdatedTargetAp(Context context, String targetAp) {
            HashMap<String, String> txtMap = mTxtRecods.get(INSTANCE_NAME);

            if (targetAp == null && txtMap.containsKey("A")) {
                txtMap.remove("A");
                return onUpdateDnsSdTxtRecord(context, INSTANCE_NAME);
            } else if (targetAp != null && (!txtMap.containsKey("A") || !txtMap.get("A").equals(targetAp))) {
                txtMap.put("A", targetAp);
                return onUpdateDnsSdTxtRecord(context, INSTANCE_NAME);
            }
            return this;
        }

        P2pState onUpdatedUsername(Context context, String username) {
            HashMap<String, String> txtMap = mTxtRecods.get(INSTANCE_NAME);

            if (username != null && (!txtMap.containsKey("N") || !txtMap.get("N").equals(username))) {
                txtMap.put("N", username);
                return onUpdateDnsSdTxtRecord(context, INSTANCE_NAME);
            }
            return this;
        }

        protected P2pState onUpdateDnsSdTxtRecord(Context context, String instanceName) {
            mIsDnsSdRecordChanged.add(instanceName);
            return this;
        }

        protected P2pState failure(Context context) {
            mReceiver.unregister();
            mWifiP2pManager.setDnsSdResponseListeners(mWifiP2pChannel, null, null);
            new HandlerFailed().send(context);
            return READY;
        }

        protected P2pState finish(Context context) {
            mReceiver.unregister();
            mWifiP2pManager.setDnsSdResponseListeners(mWifiP2pChannel, null, null);
            new HandlerFinished().send(context);
            return READY;
        }

        private void requestPeers() {
            Log.v(TAG, toString() + ".requestPeers");
            mWifiP2pManager.requestPeers(mWifiP2pChannel, mReceiver);
        }

        protected P2pState onAddClientP2pDnsSdRecord(Context context, String instanceName, Bundle record) {
            HashMap<String, String> txtMap = new HashMap<>();
            for (String key : record.keySet()) {
                txtMap.put(key, record.getString(key));
            }

            if (!mTxtRecods.containsKey(instanceName) || !mTxtRecods.get(instanceName).equals(txtMap)) {
                mTxtRecods.put(instanceName, txtMap);
                return onUpdateDnsSdTxtRecord(context, instanceName);
            }
            return this;
        }

        protected P2pState onRemoveClientP2pDnsSdRecord(Context context, String instanceName) {
            if (mTxtRecods.containsKey(instanceName)) {
                mTxtRecods.put(instanceName, new HashMap<String, String>());
                return onUpdateDnsSdTxtRecord(context, instanceName);
            }
            return this;
        }
    }

    public P2pHandler(Context context, int port) {
        super(context, TAG);
        mReceiver = new P2pHandlerReceiver(context, TAG, READY);
        // as the port can't change at all, do not complicate things more than needed
        mPort = port;
    }

    @Override
    public void start() {
        Log.v(TAG, getClass().getSimpleName() + ".start");

        mReceiver.register();
        mWifiP2pManager.setDnsSdResponseListeners(mWifiP2pChannel, null, mReceiver);

        // it's super important to read these values *after* the register() call because if one of the values
        // is changed just before the call and we didn't catch it, this makes sure we will. opposite order
        // (read -> write -> register) would lead to an unnoticed change.
        if (!mTxtRecods.containsKey(INSTANCE_NAME)) {
            mTxtRecods.put(INSTANCE_NAME, new HashMap<String, String>());
        }
        HashMap<String, String> txtMap = mTxtRecods.get(INSTANCE_NAME);
        txtMap.put("O", Integer.toString(mPort));

        mReceiver.onUpdatedHash(context, UpdatedHash.getLastSetValue(context));
        mReceiver.onUpdatedNetworkState(context, UpdatedNetworkState.getLastSetValue());
        mReceiver.onUpdatedUsername(context, UpdatedUsername.getLastSetValue(context));
        mReceiver.onUpdatedTargetAp(context, UpdatedTargetAp.getLastSetValue());

        CptClientCommunication.supplyP2pDnsSdRecords(context, mPreferenceHelper);

        new Start().send(context);
        mIsDiscovering = true;
    }

    @Override
    public void stop() {
        stop(false);
    }

    public void stop(boolean waitForDnsTxtRecordChange) {
        Log.v(TAG, getClass().getSimpleName() + ".stop");

        new Stop().send(context, waitForDnsTxtRecordChange);
    }

    public void removeService() {
        Log.v(TAG, getClass().getSimpleName() + ".removeService");

        // don't make this more complicated than needed...
        if (mWifiP2pDnsSdServiceRequest != null) {
            mWifiP2pManager.removeServiceRequest(mWifiP2pChannel, mWifiP2pDnsSdServiceRequest, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    //new HandlerFinished().send(mContext);
                }
                @Override
                public void onFailure(int reason) {
                    Log.w(TAG, "removeServiceRequest() failed: " + reason);
                    //new HandlerFailed().send(mContext);
                }
            });
            mWifiP2pDnsSdServiceRequest = null;
        }
        for (WifiP2pDnsSdServiceInfo wifiP2pDnsSdServiceInfo : mWifiP2pDnsSdServiceInfo.values()) {
            mWifiP2pManager.removeLocalService(mWifiP2pChannel, wifiP2pDnsSdServiceInfo, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    //new HandlerFinished().send(mContext);
                }
                @Override
                public void onFailure(int reason) {
                    Log.w(TAG, "removeLocalService() failed: " + reason);
                    //new HandlerFailed().send(mContext);
                }
            });
        }
        mWifiP2pDnsSdServiceInfo.clear();
        mTxtRecods.clear();
        mIsDnsSdRecordChanged.clear();
    }

    public static void firstRunInitialization() {
        if (mWifiP2pDnsSdServiceRequest != null) {
            Log.e(TAG, "mWifiP2pDnsSdServiceRequest != null");
        }
        if (!mWifiP2pDnsSdServiceInfo.isEmpty()) {
            Log.e(TAG, "mWifiP2pDnsSdServiceInfo not empty");
        }
        // for the cases when the service (and this handler instance) is destroyed but the new service
        // (and this class's instance) is created in the same process.. shouldn't happen but who knows,
        // all it takes is one over-looked exit scenario
        mWifiP2pDnsSdServiceRequest = null;
        mWifiP2pDnsSdServiceInfo.clear();
        // this must be reinitialized as well else we might not set service info at all
        mTxtRecods.clear();
        mIsDnsSdRecordChanged.clear();
        UpdatedTargetAp.resetLastSetValue();
        UpdatedNetworkState.resetLastSetValue();
    }

    public static String getLastAdvertisedHash() {
        if (mTxtRecods.containsKey(INSTANCE_NAME) && mTxtRecods.get(INSTANCE_NAME).containsKey("H")) {
            return mTxtRecods.get(INSTANCE_NAME).get("H");
        }
        return null;
    }
}
