package com.croconaut.cpt.link.handler.main;

import android.app.Service;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import com.croconaut.cpt.common.State;
import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.data.CptClientCommunication;
import com.croconaut.cpt.data.DatabaseManager;
import com.croconaut.cpt.link.Settings;
import com.croconaut.cpt.link.handler.DropConnection;
import com.croconaut.cpt.link.handler.Handler;
import com.croconaut.cpt.link.handler.connection.ConnectionHandler;
import com.croconaut.cpt.link.handler.group.GroupHandler;
import com.croconaut.cpt.link.handler.notification.ConnectionProgress;
import com.croconaut.cpt.link.handler.notification.PublishProgress;
import com.croconaut.cpt.link.handler.p2p.P2pHandler;
import com.croconaut.cpt.link.handler.p2p.UpdatedNetworkState;
import com.croconaut.cpt.link.handler.p2p.UpdatedTargetAp;
import com.croconaut.cpt.network.ClientIntroductionRunnable;
import com.croconaut.cpt.network.ClientSyncAttachmentsService;
import com.croconaut.cpt.network.ClientSyncMessagesService;
import com.croconaut.cpt.network.ServerListeningThread;
import com.croconaut.cpt.ui.BootstrapReceiver;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;

public class MainHandler extends Handler {
    private static final String TAG = "link";

    private final Service mService;
    private final ConnectivityManager mConnectivityManager;
    private final MainHandlerReceiver mReceiver;
    private final ServerListeningThread mServerListeningThread;
    private Handler mCurrentHandler;

    private boolean mIsPendingStop;
    private boolean mIsHardStop;
    private int mWifiP2pState = -1;
    private int mStartId = -1;
    private NetworkInfo mPendingNetworkInfo;
    private boolean mIsPendingWifiRestart;
    private User mUserToConnectTo;
    private long mLatestNewMessageTimestamp;
    private IgnoredHashesManager mIgnoredHashesManager = new IgnoredHashesManager();
    private Timer mRestartWifiTimer = new Timer();
    private Set<String> mIgnoredDevices;
    private boolean mIsPendingLocalMessagesRecipientsReinitialize = true;
    private Set<String> mLocalMessagesRecipients;

    private static volatile Set<String> mLatestNearbyDevices = Collections.emptySet();

    /**
     * We can be connected to:
     * - 1 wifi ap
     * - K wifi clients (which are actually connected to us and sent us an introduction)
     * - L network servers (IPs/ports gathered from the discovery sweep) (possible simultaneously with p2p group)
     * - M network clients (which are actually connected to us and sent us an introduction) (possible simultaneously with p2p group)
     * - N instances of the app server (but these connections are not monitored in mConnectedClients) (possible simultaneously with p2p group)
     */
    private class ConnectedClient {
        InetSocketAddress socketAddress;
        String hash;
        int finishedServices;
        long timeStamp = -1;
    }
    Map<String, ConnectedClient> mConnectedClients = new HashMap<>();

    private final HandlerState FIRST_TIME = new HandlerState("FIRST_TIME") {
        private GroupHandler mGroupHandler;

        @Override
        HandlerState onP2pStateChanged(Context context, int wifiP2pState) {
            if (mWifiP2pState == -1) {
                mWifiP2pState = wifiP2pState;

                String lastUsedHandlerName = mPreferenceHelper.getWifiHandler();
                // there's only one handler to restore at time
                if (lastUsedHandlerName == null) {
                    Log.i(TAG, "Nothing to restore");
                    return onFinished(context);
                } else if (lastUsedHandlerName.equals(GroupHandler.class.getSimpleName())) {
                    mGroupHandler = new GroupHandler(context);
                    mGroupHandler.cleanup();
                } else {
                    Log.w(TAG, "Stopped while another handler was active: " + lastUsedHandlerName);
                    return onFinished(context);
                }
            } else {
                mWifiP2pState = wifiP2pState;
            }

            // don't handle wifi on/off yet (as we may require disabled wifi in a handler restore)
            return this;
        }

        @Override
        HandlerState onNetworkStateChanged(Context context, NetworkInfo networkInfo) {
            mPendingNetworkInfo = networkInfo;
            return this;
        }

        @Override
        HandlerState onStart(Context context, int startId) {
            mStartId = startId;
            mIsPendingStop = false;
            return this;
        }

        @Override
        HandlerState onStop(Context context, int startId, boolean isHardStop) {
            mStartId = startId;
            mIsPendingStop = true;
            mIsHardStop = isHardStop;
            return this;
        }

        @Override
        HandlerState onFailure(Context context) {
            // pretend like nothing has happened... the handler is supposed to clean everything up
            return onFinished(context);
        }

        @Override
        HandlerState onFinished(Context context) {
            Log.v(TAG, toString() + ".onFinished");

            mGroupHandler = null;

            if (mPreferenceHelper.getHash() == null) {
                // in the other case it's called in DatalayerIntentService's processClientMessage
                DatabaseManager.setHashDirty(context);
            }
            return RESTART.onFinished(context);
        }
    };

    private final HandlerState DISCOVERY = new HandlerState("DISCOVERY") {
        private HandlerState mNextState =  this;

        @Override
        HandlerState onDiscoveryResultsAvailable(Context context, int startId, Collection<User> users) {
            Log.v(TAG, toString() + ".onDiscoveryResultsAvailable");
            mStartId = startId;

            if (users == null) {
                // don't do anything (sync intent)
                return this;
            }

            final String hash = mPreferenceHelper.getHash();
            final User me = new User(mPreferenceHelper.getCrocoId());

            CptClientCommunication.nearbyChanged(context, mPreferenceHelper, users);

            P2pHandler p2pHandler = null;
            if (mCurrentHandler instanceof P2pHandler) {
                // all right, no other handler is assigned yet
                p2pHandler = (P2pHandler) mCurrentHandler;
            }
            mNextState = this;

            boolean isOnLocalNetwork = isOnLocalNetwork();

            ArrayList<User> availableServers = new ArrayList<>();
            ArrayList<User> usersRequestingGroup = new ArrayList<>();
            Set<String> saneNearbySet = new HashSet<>();
            int ignoredDevices = 0;
            int unknownDevices = 0;
            for (User user : users) {
                /**
                 * hash must be non-null all the time:
                 * if we're on local network, we don't need any data except an indication
                 * whether the other side is as well; and if it is not (L=null) and we have messages for it
                 * we create group instead of letting it go
                 */
                if (!mIgnoredDevices.contains(user.crocoId)
                        && user.hash != null
                        && ((!user.hash.equals(hash) && !mIgnoredHashesManager.isHashIgnored(user.crocoId, user.hash))
                        || me.crocoId.equals(user.targetAp)
                        || isPendingLocalRecipient(user.crocoId))) {
                    if (!Settings.getInstance().useLocalOnly
                            && ((!isOnLocalNetwork && user.networkAddress == null && (me.isClientOf(user) || user.isReady())) // we aren't on network, he isn't on network => compare MACs
                                || (!isOnLocalNetwork && user.networkAddress != null))) {  // we aren't on network, he is => we connect
                        Log.v(TAG, "Adding device to connect: " + user);
                        availableServers.add(user);
                    } else if (!Settings.getInstance().useLocalOnly
                            && ((!isOnLocalNetwork && user.networkAddress == null && user.isClientOf(me))    // we aren't on network, he isn't on network => compare MACs
                                || (isOnLocalNetwork && user.networkAddress == null))) {    // we're on network, he is not => create server
                        Log.v(TAG, "Adding device to create group for: " + user);
                        usersRequestingGroup.add(user);
                    } else if (isOnLocalNetwork && user.networkAddress != null  // we're on network, he's on network => exchange data
                            && ((!user.hash.equals(hash) && me.isClientOf(user)) || (user.hash.equals(hash) && isPendingLocalRecipient(user.crocoId)))) {
                        if (!mConnectedClients.containsKey(user.crocoId)) {
                            mConnectedClients.put(user.crocoId, new ConnectedClient());
                            Log.d(TAG, "Starting an introduction thread connected to " + user.networkAddress.getHostAddress());
                            new Thread(
                                    new ClientIntroductionRunnable(
                                            context,
                                            user.crocoId,
                                            P2pHandler.getLastAdvertisedHash(),
                                            user.hash,
                                            new InetSocketAddress(intToInetAddress(mWifiManager.getConnectionInfo().getIpAddress()), mServerListeningThread.getPort()),    // local socket address
                                            new InetSocketAddress(user.networkAddress, user.port), // remote socket address
                                            false
                                    )
                            ).start();
                        } else {
                            Log.d(TAG, "Not starting already started introduction thread to " + user.networkAddress.getHostAddress());
                        }
                    } else if (!Settings.getInstance().useLocalOnly) {
                        Log.d(TAG, "Expecting an introduction thread from " + user.networkAddress.getHostAddress());
                    } else {
                        Log.d(TAG, "Skipping device (local only): " + user);
                        ignoredDevices++;
                    }
                } else {
                    if (user.hash != null) {
                        Log.d(TAG, "Skipping device: " + user);
                        ignoredDevices++;
                    } else {
                        Log.v(TAG, "Waiting for hash for device " + user);
                        unknownDevices++;
                    }
                }

                if (!mIgnoredDevices.contains(user.crocoId)) {
                    // include devices with null hash into the sane nearby set -- we may be waiting for their
                    // service info and we definitely don't want to rush uploads of huge attachments here!
                    /*if (user.hash != null)*/ {
                        saneNearbySet.add(user.crocoId);
                    }
                }
            }

            mLatestNearbyDevices = saneNearbySet;

            /**
             * [x] # of unknown devices
             * [y] # of ignored devices
             * [z] # of available devices (which offers new data)
             * [u] # of devices to connect to / #devices to create group for (or the device, if only one)
             * [v] chosen device to connect to
             */
            String connectionInfoText = "";
            connectionInfoText += "[" + String.valueOf(unknownDevices) + "]";
            connectionInfoText += "[" + String.valueOf(ignoredDevices) + "]";
            connectionInfoText += "[" + String.valueOf(availableServers.size() + usersRequestingGroup.size()) + "]";

            // we set only discovery-related statuses here, the rest is up to the handlers
            if (availableServers.isEmpty() && usersRequestingGroup.isEmpty()) {
                new PublishProgress().send(context, ConnectionProgress.DISCOVERING, connectionInfoText);
            }

            if (!availableServers.isEmpty()) {
                connectionInfoText += "[" + String.valueOf(availableServers.size()) + "]";

                Collections.sort(availableServers, new Comparator<User>() {
                    @Override
                    public int compare(User lhs, User rhs) {
                        if (lhs.isReady() && !rhs.isReady()) {
                            return -1;
                        } else if (!lhs.isReady() && rhs.isReady()) {
                            return 1;
                        } else {    // both or none are ready
                            if (lhs.isAvailable() && !rhs.isAvailable()) {
                                return -1;
                            } else if (!lhs.isAvailable() && rhs.isAvailable()) {
                                return 1;
                            } else {    // both or none are available
                                if (me.crocoId.equals(lhs.targetAp) && !me.crocoId.equals(rhs.targetAp)) {
                                    return -1;
                                } else if (!me.crocoId.equals(lhs.targetAp) && me.crocoId.equals(rhs.targetAp)) {
                                    return 1;
                                } else {    // both or none have local messages for us
                                    if (isPendingLocalRecipient(lhs.crocoId) && !isPendingLocalRecipient(rhs.crocoId)) {
                                        return -1;
                                    } else if (!isPendingLocalRecipient(lhs.crocoId) && isPendingLocalRecipient(rhs.crocoId)) {
                                        return 1;
                                    } else {    // we have local messages for both or none of them
                                        if (!hash.equals(lhs.hash) && hash.equals(rhs.hash)) {
                                            return -1;
                                        } else if (hash.equals(lhs.hash) && !hash.equals(rhs.hash)) {
                                            return 1;
                                        } else {    // both or none have the same hash as we have
                                            if (lhs.networkAddress != null && rhs.networkAddress == null) {
                                                return -1;
                                            } else if (lhs.networkAddress == null && rhs.networkAddress != null) {
                                                return 1;
                                            } else {    // both or none are on network, choose higher mac address
                                                if (lhs.crocoId.compareTo(rhs.crocoId) > 0) {
                                                    return -1;
                                                } else if (lhs.crocoId.compareTo(rhs.crocoId) < 0) {
                                                    return 1;
                                                } else {
                                                    return 0;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                });

                // take the the best candidate
                mUserToConnectTo = availableServers.get(0);

                Log.d(TAG, "connectTo: " + mUserToConnectTo);
                if (mUserToConnectTo.username != null) {
                    connectionInfoText += "[" + mUserToConnectTo.username + "]";
                } else {
                    connectionInfoText += "[" + mUserToConnectTo.crocoId + "]";
                }

                if (mUserToConnectTo.isAvailable()) {
                    if (mPreferenceHelper.getWifiNeedsRestart()) {
                        Log.e(TAG, "Android 4.1/4.1.1 device has built a group before and now wants to connect => Wi-Fi reset!");
                        new RestartTimerExpired().send(context, false);
                        return this;
                    }

                    if (mUserToConnectTo.isGroupOwner) {
                        ((ConnectionHandler) setCurrentHandler(new ConnectionHandler(context)))
                                .setup(mUserToConnectTo.ssid, mUserToConnectTo.passphrase);
                        new PublishProgress().send(context, ConnectionProgress.SCANNING, connectionInfoText);

                        mNextState = CONNECTION;
                    } else {
                        new PublishProgress().send(context, ConnectionProgress.DISCOVERING_WAITING_FOR_GO, connectionInfoText);
                    }
                } else {
                    // setting the target ap makes sense only if we can't connect to the peer immediately
                    setTargetAp(context, hash, availableServers);  // availableServers is again sorted

                    if (mUserToConnectTo.isGroupOwner) {
                        new PublishProgress().send(context, ConnectionProgress.DISCOVERING_WAITING_FOR_CREDENTIALS, connectionInfoText);
                    } else {
                        new PublishProgress().send(context, ConnectionProgress.DISCOVERING_WAITING_FOR_GO_AND_CREDENTIALS, connectionInfoText);
                    }
                }
            } else if (!usersRequestingGroup.isEmpty()) {
                // there's absolutely nobody else to connect to

                // it's important to realize we'd be creating group also for clients which have
                // local messages for us -- we just don't set targetAp to reflect that because
                // its purpose is different (to provide the most stable/reasonable value to
                // motivate others to connect)
                setTargetAp(context, hash, usersRequestingGroup);

                Log.d(TAG, "We expect " + usersRequestingGroup.size() + " peer(s) to connect to our group");

                if (usersRequestingGroup.size() == 1) {
                    User groupFor = usersRequestingGroup.get(0);
                    Log.d(TAG, "groupFor: " + groupFor);
                    if (groupFor.username != null) {
                        connectionInfoText += "[" + groupFor.username + "]";
                    } else {
                        connectionInfoText += "[" + groupFor.crocoId + "]";
                    }
                } else {
                    Log.d(TAG, "groupFor: " + usersRequestingGroup.size());
                    connectionInfoText += "[" + usersRequestingGroup.size() + "]";
                }

                setCurrentHandler(new GroupHandler(context));
                new PublishProgress().send(context, ConnectionProgress.CREATING_GROUP, connectionInfoText);

                mNextState = GROUP;
            } else {
                // nobody nearby => why bother with announcing
                new UpdatedTargetAp().send(context, null);
            }

            if (p2pHandler != null) {
                // mCurrentHandler was P2pHandler
                if (mNextState != this) {
                    // ... and it's about to change
                    p2pHandler.stop(mNextState == GROUP);
                }
            } else {
                // mCurrentHandler was something else than P2pHandler (perhaps we're waiting for
                // P2pHandler to stop while another discovery results have arrived)
                if (mNextState != this) {
                    // all right, still something to look for
                    Log.d(TAG, "P2pHandler's stop is already pending");
                } else {
                    Log.w(TAG, "P2pHandler's stop is pending and there's no handler to switch to anymore");
                    setCurrentHandler(new P2pHandler(context, mServerListeningThread.getPort()));
                }
            }

            return this;    // wait for onFinished()
        }

        @Override
        HandlerState onFinished(Context context) {
            // discovery handler has stopped
            Log.v(TAG, toString() + ".onFinished");

            // whoever has won the war, start it
            mCurrentHandler.start();

            HandlerState nextState = mNextState;
            mNextState = this;  // reset so if/when this handler stops/fails after setting in RESTART, we'll land here again
            return nextState;
        }

        /**
         * Purpose of this method is to always get the most stable result (i.e. a target which
         * doesn't change very much unless it's disappeared from nearby or the data has been
         * transmitted). This is the reason why we don't compare whether an user has local
         * messages for us -- the order could change quite quickly as we'd have been picking up
         * different targetAp values.
         */
        private void setTargetAp(Context context, final String hash, List<User> users) {
            Collections.sort(users, new Comparator<User>() {
                @Override
                public int compare(User lhs, User rhs) {
                    if (isPendingLocalRecipient(lhs.crocoId) && !isPendingLocalRecipient(rhs.crocoId)) {
                        return -1;
                    } else if (!isPendingLocalRecipient(lhs.crocoId) && isPendingLocalRecipient(rhs.crocoId)) {
                        return 1;
                    } else {    // we have local messages for both or none of them
                        if (!hash.equals(lhs.hash) && hash.equals(rhs.hash)) {
                            return -1;
                        } else if (hash.equals(lhs.hash) && !hash.equals(rhs.hash)) {
                            return 1;
                        } else {    // both or none have the same hash as we have
                            if (lhs.crocoId.compareTo(rhs.crocoId) > 0) {
                                return -1;
                            } else if (lhs.crocoId.compareTo(rhs.crocoId) < 0) {
                                return 1;
                            } else {
                                return 0;
                            }
                        }
                    }
                }
            });

            User userForTargetAp = users.get(0);
            if (isPendingLocalRecipient(userForTargetAp.crocoId)) {
                new UpdatedTargetAp().send(context, userForTargetAp.crocoId);
                Log.i(TAG, "We announced local messages for " + userForTargetAp.crocoId);
            } else {
                new UpdatedTargetAp().send(context, null);
                Log.i(TAG, "Nobody (nor we) have local messages to exchange");
            }
        }
    };

    private final HandlerState GROUP = new HandlerState("GROUP") {
        private boolean hasGroupClientConnected;

        @Override
        HandlerState onFinished(Context context) {
            // we can't rely solely on onNetworkSyncServiceFinished() -- the group can be removed after a timeout
            hasGroupClientConnected = false;
            return super.onFinished(context);
        }

        @Override
        HandlerState onFailure(Context context) {
            hasGroupClientConnected = false;
            return super.onFailure(context);
        }

        @Override
        HandlerState onNewConnectableClient(Context context, String crocoId, InetSocketAddress socketAddress, String hash, boolean isP2pClient) {
            if (isP2pClient) {
                hasGroupClientConnected = true;
            }
            return super.onNewConnectableClient(context, crocoId, socketAddress, hash, isP2pClient);
        }

        @Override
        HandlerState onNetworkSyncServiceFinished(Context context, String crocoId, int what, long timeStampSuccess) {
            super.onNetworkSyncServiceFinished(context, crocoId, what, timeStampSuccess);

            if (hasGroupClientConnected && mConnectedClients.isEmpty()) {
                // avoid misleading the other side to create a group / make a connection attempt
                // TODO: not very dumb-proof, if there's a communication with another client in progress,
                // the finished client would try to make connection again and again until the group
                // is removed
                new UpdatedTargetAp().send(context, null);
                // yes, this is right, even if there are non-group connections, we wait; it makes perfect sense,
                // if there are lengthy uploads/downloads, we would shut the service down anyway and if it's
                // going to end shortly, why bother?
                new DropConnection().send(context);

                // wait for onFinished() but mark it as soon as possible
                hasGroupClientConnected = false;
            }

            return this;
        }
    };

    private final HandlerState CONNECTION = new HandlerState("CONNECTION") {
        private boolean hasDisconnected;
        private InetAddress ipAddress;
        private InetAddress serverAddress;

        @Override
        HandlerState onFinished(Context context) {
            hasDisconnected = false;
            return super.onFinished(context);
        }

        @Override
        HandlerState onFailure(Context context) {
            hasDisconnected = false;
            return super.onFailure(context);
        }

        // TODO: count onFailure for each croco id and merge with ignored peers (perhaps we need to save the target croco id after all...)
        @Override
        HandlerState onNetworkStateChanged(Context context, NetworkInfo networkInfo) {
            if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected()) {
                getDhcpInfo();

                // protection against multiple 'connected' intents
                if (!mConnectedClients.containsKey(mUserToConnectTo.crocoId) && !hasDisconnected) {
                    mConnectedClients.put(mUserToConnectTo.crocoId, new ConnectedClient());
                    new Thread(
                            new ClientIntroductionRunnable(
                                    context,
                                    mUserToConnectTo.crocoId,
                                    P2pHandler.getLastAdvertisedHash(),
                                    mUserToConnectTo.hash,
                                    new InetSocketAddress(ipAddress, mServerListeningThread.getPort()),    // local socket address
                                    new InetSocketAddress(mUserToConnectTo.networkAddress != null ? mUserToConnectTo.networkAddress : serverAddress, mUserToConnectTo.port), // remote socket address
                                    true
                            )
                    ).start();
                }
            }
            return this;
        }

        @Override
        HandlerState onNetworkSyncServiceFinished(Context context, String crocoId, int what, long timeStampSuccess) {
            super.onNetworkSyncServiceFinished(context, crocoId, what, timeStampSuccess);

            if (mConnectedClients.isEmpty()) {
                // avoid misleading the other side to create a group / make a connection attempt
                new UpdatedTargetAp().send(context, null);

                new DropConnection().send(context);

                hasDisconnected = true;
            }

            // wait for onFinished()
            return this;
        }

        private void getDhcpInfo() {
            DhcpInfo dhcpInfo = mWifiManager.getDhcpInfo();
            if (dhcpInfo != null) {
                ipAddress = intToInetAddress(dhcpInfo.ipAddress);
                serverAddress = intToInetAddress(dhcpInfo.serverAddress);
            }
        }
    };

    private final HandlerState FORCED_CONNECTION = new HandlerState("FORCED_CONNECTION") {
        @Override
        HandlerState onNetworkStateChanged(Context context, NetworkInfo networkInfo) {
            return this;
        }

        @Override
        HandlerState onFinished(Context context) {
            // previous handler has stopped
            Log.v(TAG, toString() + ".onFinished");

            mCurrentHandler.start();
            return CONNECTION.onNetworkStateChanged(context, mConnectivityManager.getActiveNetworkInfo());
        }
    };

    private final HandlerState RESTART = new HandlerState("RESTART") {
        // we can get here from onStart, onStop, onP2pStateChanged (we expect this one to fail) (via mCurrentHandler.stop())
        // or onFailure (mCurrentHandler's internal state) (via timerExpired())
        // in the meantime we can get the other events, too
        @Override
        HandlerState onP2pStateChanged(Context context, int wifiP2pState) {
            mWifiP2pState = wifiP2pState;
            return this;
        }

        @Override
        HandlerState onNetworkStateChanged(Context context, NetworkInfo networkInfo) {
            mPendingNetworkInfo = networkInfo;
            return this;
        }

        @Override
        HandlerState onStart(Context context, int startId) {
            mStartId = startId;
            mIsPendingStop = false;
            return this;
        }

        @Override
        HandlerState onStop(Context context, int startId, boolean isHardStop) {
            mStartId = startId;
            mIsPendingStop = true;
            mIsHardStop = isHardStop;
            return this;
        }

        @Override
        HandlerState onFailure(Context context) {
            Log.w(TAG, toString() + ".onFailure");

            // a handler has failed
                /*
                 we rely on the fact that mCurrentHandler's stop cleans everything up even in
                 a case of failure (except mServiceHandler which needs special treatment,
                 we can't erase the service info/request after each failure, that would open us
                 to the risk of too many add/remove events => halt)
                 NOTE: mConnectionHandler is an interesting case, it can have a network saved but
                 the connection algorithm deals with that nicely
                 */
            return onFinished(context);
        }

        @Override
        HandlerState onFinished(Context context) {
            // a handler has stopped
            Log.v(TAG, toString() + ".onFinished: "
                    + (mCurrentHandler != null ? mCurrentHandler.getClass().getSimpleName() : "(null handler)") + " (" + mWifiP2pState + " / " + mIsPendingStop + " / " + mIsHardStop + ")");

            boolean isWifiOff = false;
            if (mWifiP2pState == WifiP2pManager.WIFI_P2P_STATE_DISABLED || mWifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED) {
                isWifiOff = true;

                if (mWifiP2pState == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                    mWifiP2pState = -1;
                } else {
                    Log.e(TAG, "mWifiP2pState=" + mWifiP2pState + ", but wifi is disabled");
                }

                // wifi off is the same as starting the service
                P2pHandler.firstRunInitialization();

                handleWifiOff(context);

                boolean disableBootstrap = false;
                if (mIsPendingStop) {
                    // let mIsPendingStop set
                    disableBootstrap = mIsHardStop;
                }
                // we definitely want to restore the handler when wifi is on again, so no resetCurrentHandler() calling here
                return disable(context, disableBootstrap, true, isWifiOff);
            } else {
                if (Settings.getInstance().mode == -1) {
                    // we got here from a new message and/or a sync request while being OFF
                    Log.d(TAG, "Settings.mode = " + Settings.getInstance().mode);
                    return disable(context, false, true, isWifiOff);
                } else if (mIsPendingStop) {
                    // the stop command has higher priority, before handleWifiOn() (it could disable wifi and we could stop after that)

                    // let mIsPendingStop set

                    // no matter whether it's hard stop or soft stop, we have to disable everything
                    new P2pHandler(context, mServerListeningThread.getPort()).removeService();
                    new ConnectionHandler(context).disconnect();
                    resetCurrentHandler();
                    return disable(context, mIsHardStop, mIsHardStop, isWifiOff);
                } else {
                    if (mWifiP2pState == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        mWifiP2pState = -1;

                        handleWifiOn(context);
                    }

                    // wifi enabled, a stop not pending => (re)start
                    setCurrentHandler(new P2pHandler(context, mServerListeningThread.getPort())).start();
                    return prepareDiscovery(context);
                }
            }
        }

        @Override
        public HandlerState onTimerExpired(Context context) {
            Log.v(TAG, toString() + ".onTimerExpired");

            // onFailure's timeout has passed, pretend this is just a regular failed restart
            return onFailure(context);
        }

        private HandlerState prepareDiscovery(Context context) {
            HandlerState nextState = DISCOVERY;
            String connectionInfoText = "[0][0][0]";
            if (mPendingNetworkInfo != null) {
                nextState = super.onNetworkStateChanged(context, mPendingNetworkInfo);
                if (nextState == this) {
                    new PublishProgress().send(context, ConnectionProgress.DISCOVERING, connectionInfoText);
                    nextState = DISCOVERY;
                }
            } else {
                new PublishProgress().send(context, ConnectionProgress.DISCOVERING, connectionInfoText);
            }

            return nextState;
        }

        private HandlerState disable(Context context, boolean disableBootstrap, boolean disableService, boolean isWifiOff) {
            Log.v(TAG, toString() + ".disable");

            mRestartWifiTimer.cancel();

            if (disableBootstrap) {
                BootstrapReceiver.disable(context);
            }

            CptClientCommunication.nearbyChanged(context, mPreferenceHelper, Collections.<User>emptyList());
            mLatestNearbyDevices.clear();

            if (disableService) {
                if (!mService.stopSelfResult(mStartId)) {
                    Log.w(TAG, "stopSelfResult() has failed, there's either P2P ENABLED or a mode pending: doing nothing");
                }
            } else {
                // the only option
                new PublishProgress().send(context, ConnectionProgress.BACKGROUND_MODE);
            }

            return DISABLED;
        }
    };

    private final HandlerState DISABLED = new HandlerState("DISABLED") {
        // wifi is either disabled but on its way to enable
        // or is really disabled and waiting for service termination
        @Override
        HandlerState onP2pStateChanged(Context context, int wifiP2pState) {
            mWifiP2pState = wifiP2pState;
            // mIsPendingStop is still untouched so it will go back to DISABLED if necessary
            return RESTART.onFinished(context);
        }

        @Override
        HandlerState onNetworkStateChanged(Context context, NetworkInfo networkInfo) {
            mPendingNetworkInfo = networkInfo;
            return this;
        }

        @Override
        HandlerState onStart(Context context, int startId) {
            mStartId = startId;
            mIsPendingStop = false;
            return RESTART.onFinished(context);
        }

        @Override
        HandlerState onStop(Context context, int startId, boolean isHardStop) {
            mStartId = startId;
            mIsPendingStop = true;
            mIsHardStop = isHardStop;
            return RESTART.onFinished(context);
        }

        @Override
        HandlerState onFinished(Context context) {
            Log.w(TAG, "onFinished() in state " + this);
            return this;
        }

        @Override
        HandlerState onFailure(Context context) {
            Log.w(TAG, "onFailure() in state " + this);
            return this;
        }

        @Override
        HandlerState onRestartWifiTimerExpired(Context context, int startId, boolean force) {
            Log.w(TAG, "onRestartWifiTimerExpired() in state " + this);
            mStartId = startId;
            return this;
        }
    };

    abstract class HandlerState extends State {
        public HandlerState(String name) {
            super(name);
        }

        HandlerState onNetworkStateChanged(Context context, NetworkInfo networkInfo) {
            Log.v(TAG, toString() + ".onNetworkStateChanged");

            mPendingNetworkInfo = null;

            String connectionSsid = getConnectionSsid();
            if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                if (networkInfo.isConnected() && connectionSsid != null) {
                    Log.w(TAG, "Unexpected connection to: " + connectionSsid);
                    String lastUsedSsid = ConnectionHandler.getLastUsedSsid();
                    if (lastUsedSsid == null) {
                        // try this one, too
                        lastUsedSsid = mPreferenceHelper.getWifiPeerSsid();
                    }
                    if (connectionSsid.equals(lastUsedSsid)) {
                        mCurrentHandler.stop();
                        setCurrentHandler(new ConnectionHandler(context));
                        return FORCED_CONNECTION;
                    } else if (isWifiDirectSsid(connectionSsid)) {
                        Log.e(TAG, "Most likely a CPT bug, disconnecting!");
                        new ConnectionHandler(context).disconnect(connectionSsid);
                    } else {
                        // connected to a local network
                        InetAddress address = intToInetAddress(mWifiManager.getConnectionInfo().getIpAddress());
                        new UpdatedNetworkState().send(context, address);
                    }
                } else {
                    // not connected anymore
                    new UpdatedNetworkState().send(context, null);
                }
            }

            return this;
        }

        HandlerState onP2pStateChanged(Context context, int wifiP2pState) {
            mWifiP2pState = wifiP2pState;

            mCurrentHandler.stop();
            return RESTART;
        }

        HandlerState onStart(Context context, int startId) {
            mStartId = startId;
            mIsPendingStop = false;

            mCurrentHandler.stop();
            return RESTART;
        }

        HandlerState onStop(Context context, int startId, boolean isHardStop) {
            mStartId = startId;
            mIsPendingStop = true;
            mIsHardStop = isHardStop;

            mCurrentHandler.stop();
            return RESTART;
        }

        HandlerState onFinished(Context context) {
            // handler has been stopped, let's start again (we are sure there's nothing pending)
            return RESTART.onFinished(context);
        }

        HandlerState onFailure(final Context context) {
            Log.e(TAG, "onFailure() in state: " + this);

            mConnectedClients.clear();
            invalidateLocalMessagesRecipients();

            scheduleTask(new Runnable() {
                @Override
                public void run() {
                    new TimerExpired().send(context);
                }
            }, 1);

            return RESTART;
        }

        HandlerState onDiscoveryResultsAvailable(Context context, int startId, Collection<User> users) {
            mStartId = startId;
            return this;
        }

        HandlerState onNewMessage(Context context, int startId, String to) {
            Log.v(TAG, toString() + ".onNewMessage: " + to);
            mStartId = startId;

            if (to == null) {
                mLatestNewMessageTimestamp = System.currentTimeMillis();
                for (Map.Entry<String, ConnectedClient> entry : mConnectedClients.entrySet()) {
                    String crocoId = entry.getKey();
                    ConnectedClient connectedClient = entry.getValue();
                    ClientSyncMessagesService.sync(context, crocoId, connectedClient.socketAddress);
                    connectedClient.finishedServices &= ~NetworkSyncServiceFinished.CLIENT_MESSAGES;
                }
            } else {
                if (mConnectedClients.get(to) != null) {
                    ConnectedClient connectedClient = mConnectedClients.get(to);
                    ClientSyncMessagesService.sync(context, to, connectedClient.socketAddress);
                    connectedClient.finishedServices &= ~NetworkSyncServiceFinished.CLIENT_MESSAGES;
                } else {
                    Log.v(TAG, "Added new local message recipient: " + to);
                    invalidateLocalMessagesRecipients();    // the message has been stored in the db already
                }
            }

            return this;
        }

        HandlerState onNewAttachment(Context context, int startId, String from) {
            Log.v(TAG, toString() + ".onNewAttachment");
            mStartId = startId;

            if (mConnectedClients.get(from) != null) {
                ConnectedClient connectedClient = mConnectedClients.get(from);
                ClientSyncAttachmentsService.sync(context, from, connectedClient.socketAddress);
                connectedClient.finishedServices &= ~NetworkSyncServiceFinished.CLIENT_ATTACHMENTS;
            } else {
                Log.v(TAG, "Adding new attachment request recipient: " + from);
                invalidateLocalMessagesRecipients();    // the download uri has been stored in the db already
            }

            return this;
        }

        HandlerState onTimerExpired(Context context) {
            Log.w(TAG, "onTimerExpired() in state " + this);
            return this;
        }

        HandlerState onRestartWifiTimerExpired(Context context, int startId, boolean force) {
            Log.v(TAG, toString() + ".onRestartWifiTimerExpired");
            mStartId = startId;

            if (isOnLocalNetwork() && !force) {
                Log.w(TAG, "Regular Wi-Fi restart ignored, we're on a local network");
                return this;
            }

            // the timer may be forced
            mRestartWifiTimer.cancel();

            if (mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
                Log.w(TAG, "Regular Wi-Fi restart scheduled");

                // last checkpoint to make sure wifi is still on
                mWifiManager.setWifiEnabled(false);

                mIsPendingWifiRestart = true;
            }
            return this;
        }

        HandlerState onUpdatedIgnoredDevices(Context context, String crocoIdToCancel) {
            Log.v(TAG, toString() + ".onUpdatedIgnoredDevices");

            mIgnoredDevices = DatabaseManager.getBlockedDevices(context);

            return crocoIdToCancel != null ? onCancelConnection(context, -1, crocoIdToCancel) : this;
        }

        HandlerState onCancelConnection(Context context, int startId, String crocoId) {
            Log.v(TAG, toString() + ".onCancelConnection");
            if (startId != -1) {
                mStartId = startId;
            }

            // if the croco id is connected to us (local network, group)
            mServerListeningThread.cancel(crocoId);

            // if we are connected to the croco id (local network, wifi)
            ClientSyncMessagesService.cancelSync(context, crocoId);
            if (crocoId != null) {
                // null means 'messages only'
                ClientSyncAttachmentsService.cancelSync(context, crocoId);
            }

            invalidateLocalMessagesRecipients();

            return this;
        }

        HandlerState onServerSyncStarted(Context context) {
            Log.i(TAG, toString() + ".onServerSyncStarted");

            for (ConnectedClient client : mConnectedClients.values()) {
                client.finishedServices &= ~NetworkSyncServiceFinished.SERVER;
            }

            return this;
        }

        HandlerState onNewConnectableClient(Context context, String crocoId, InetSocketAddress socketAddress, String hash, boolean isP2pClient) {
            Log.i(TAG, toString() + ".onNewConnectableClient: " + crocoId + ", " + hash + ", " + isP2pClient);

            if (mIgnoredDevices.contains(crocoId)) {
                Log.w(TAG, "Croco ID " + crocoId + " is not trusted, ending connection");
                return onNetworkSyncServiceFinished(context, crocoId, NetworkSyncServiceFinished.EVERYTHING, 0);
            }

            DatabaseManager.obtainLocation(context);

            // automatically ask for new (all: local, non-local, persistent) messages, can't hurt :)
            ClientSyncMessagesService.sync(context, crocoId, socketAddress);
            // do the same for the attachment requests, it simplifies things a lot
            ClientSyncAttachmentsService.sync(context, crocoId, socketAddress);

            ConnectedClient connectedClient = mConnectedClients.get(crocoId);
            if (connectedClient == null) {
                connectedClient = new ConnectedClient();
                mConnectedClients.put(crocoId, connectedClient);
            }
            connectedClient.socketAddress = socketAddress;
            connectedClient.hash = hash;

            return this;
        }

        HandlerState onNetworkSyncServiceFinished(Context context, String crocoId, int what, long timeStampSuccess) {
            Log.v(TAG, toString() + ".onNetworkSyncServiceFinished: " + crocoId + ", " + what + ", " + (timeStampSuccess > 0 ? new Date(timeStampSuccess) : timeStampSuccess));

            if (crocoId != null) {
                if (mConnectedClients.containsKey(crocoId)) {
                    ConnectedClient connectedClient = mConnectedClients.get(crocoId);
                    if (handleFinishedConnectedClient(crocoId, connectedClient, what, timeStampSuccess)) {
                        mConnectedClients.remove(crocoId);
                    }
                } else {
                    Log.w(TAG, "This was a followup connection after we'd removed its croco id from the list of active clients");
                }
            } else if (what == NetworkSyncServiceFinished.SERVER) {
                for (Iterator<Map.Entry<String, ConnectedClient>> entryIt = mConnectedClients.entrySet().iterator(); entryIt.hasNext(); ) {
                    Map.Entry<String, ConnectedClient> entry = entryIt.next();
                    if (handleFinishedConnectedClient(entry.getKey(), entry.getValue(), what, timeStampSuccess)) {
                        entryIt.remove();
                    }
                }
            }   // else it's an app server service

            // we don't know whether all messages/attachments got through...
            invalidateLocalMessagesRecipients();

            return this;
        }

        // helper functions

        protected InetAddress intToInetAddress(int ip) {
            try {
                return InetAddress.getByAddress(new byte[] { (byte) ip, (byte) (ip >>> 8), (byte) (ip >>> 16), (byte) (ip >>> 24) });
            } catch (UnknownHostException e) {
                return null;
            }
        }

        protected boolean isOnLocalNetwork() {
            NetworkInfo activeNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI && activeNetworkInfo.isConnectedOrConnecting();
        }

        protected String getConnectionSsid() {
            String connectionSsid = null;

            WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
            if (wifiInfo != null && wifiInfo.getSSID() != null && !wifiInfo.getSSID().equals("<unknown ssid>")) {   // WifiSsid.NONE
                connectionSsid = wifiInfo.getSSID();
                if (connectionSsid.startsWith("\"") && connectionSsid.endsWith("\"")) {
                    connectionSsid = connectionSsid.substring(1, connectionSsid.length() - 1);
                } else {
                    // 4.1 and 4.1.1 only
                    //Log.w(TAG, "SSID quotation bug in Android " + Build.VERSION.RELEASE + " detected");
                }
            }

            return connectionSsid;
        }

        protected boolean isWifiDirectSsid(String connectionSsid) {
            return connectionSsid.matches("DIRECT-[a-zA-Z0-9]{2}-.+");
        }

        protected Handler setCurrentHandler(Handler handler) {
            if (mCurrentHandler == null || !mCurrentHandler.getClass().getSimpleName().equals(handler.getClass().getSimpleName())) {
                Log.i(TAG, "Current handler: " + handler.getClass().getSimpleName());
                mPreferenceHelper.setWifiHandler(handler.getClass().getSimpleName());
            }
            mCurrentHandler = handler;  // always store the instance, it's new every time
            return mCurrentHandler;
        }

        protected void resetCurrentHandler() {
            mCurrentHandler = null;
            mPreferenceHelper.resetWifiHandler();
        }

        protected void handleWifiOn(final Context context) {
            Log.v(TAG, toString() + ".handleWifiOn");

            /*
            // setup timer to restart wifi half an hour from the last restart
            long restartInterval = System.currentTimeMillis() - mPreferenceHelper.getWifiTimestamp();
            if (restartInterval >= AlarmManager.INTERVAL_HALF_HOUR) {
                restartWifiTimerExpired(context, false); // force
            } else {
                restartInterval = AlarmManager.INTERVAL_HALF_HOUR - restartInterval;
                Log.d(TAG, "Wi-Fi is going to be restarted in " + restartInterval / (60 * 1000) + " minutes");

                mRestartWifiTimer.cancel();
                mRestartWifiTimer = new Timer();
                mRestartWifiTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        restartWifiTimerExpired(context, false);
                    }
                }, restartInterval);
            }
            */
        }

        protected void handleWifiOff(Context context) {
            Log.v(TAG, toString() + ".handleWifiOff");

            // not anymore ...
            mPreferenceHelper.setWifiNeedsRestart(false);

            // update timestamp
            mPreferenceHelper.setWifiTimestamp(System.currentTimeMillis());

            if (mIsPendingWifiRestart) {
                mIsPendingWifiRestart = false;

                mWifiManager.setWifiEnabled(true);
            }
        }

        protected boolean isPendingLocalRecipient(String crocoId) {
            if (mIsPendingLocalMessagesRecipientsReinitialize) {
                mLocalMessagesRecipients = DatabaseManager.getLocalMessagesRecipients(context);
                mIsPendingLocalMessagesRecipientsReinitialize = false;
            }
            return mLocalMessagesRecipients.contains(crocoId);
        }

        protected void invalidateLocalMessagesRecipients() {
            mIsPendingLocalMessagesRecipientsReinitialize = true;
        }

        protected boolean handleFinishedConnectedClient(String crocoId, ConnectedClient connectedClient, int what, long timeStampSuccess) {
            boolean hasFinished = false;

            connectedClient.finishedServices |= what;
            if (timeStampSuccess != -1 && (connectedClient.timeStamp == -1 || timeStampSuccess > connectedClient.timeStamp)) {
                // timeStamp: 0 - failure (so the hash wont be ignored); >0 - a real time stamp; -1 - don't compare, just ignore
                connectedClient.timeStamp = timeStampSuccess;
            }

            if ((connectedClient.finishedServices ^ NetworkSyncServiceFinished.EVERYTHING) == 0) {
                Log.d(TAG, "Client " + crocoId + " has finished data all transfers");

                if (connectedClient.timeStamp == -1 || connectedClient.timeStamp > mLatestNewMessageTimestamp) {
                    Log.i(TAG, "Adding " + connectedClient.hash + " of " + crocoId + " to the list of ignored hashes");
                    mIgnoredHashesManager.addIgnoredHash(crocoId, connectedClient.hash);
                }

                hasFinished = true;
            }

            return hasFinished;
        }
    }

    public MainHandler(Service service) {
        super(service, TAG);
        mService = service;
        mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mReceiver = new MainHandlerReceiver(context, TAG, FIRST_TIME);

        mServerListeningThread = new ServerListeningThread(context);
        mServerListeningThread.start();

        // beware, this is a DB operation on the main thread
        mIgnoredDevices = DatabaseManager.getBlockedDevices(context);

        // PSP3404 doesn't deliver network info by default ...
        mPendingNetworkInfo = mConnectivityManager.getActiveNetworkInfo();

        mLatestNearbyDevices.clear();
    }

    @Override
    public void start() {
        mReceiver.register();
    }

    @Override
    public void stop() {
        // this is really the last breath -- called from service's onDestroy()
        mServerListeningThread.interrupt();

        mReceiver.unregister();
    }

    public CptBroadcastReceiver getReceiver() {
        return mReceiver;
    }

    public static Set<String> getLatestNearbyCrocoIds() {
        return mLatestNearbyDevices;
    }
}
