package com.croconaut.cpt.link.handler.group;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import com.croconaut.cpt.common.State;
import com.croconaut.cpt.link.handler.Handler;
import com.croconaut.cpt.link.handler.main.HandlerFailed;
import com.croconaut.cpt.link.handler.main.HandlerFinished;
import com.croconaut.cpt.link.handler.notification.ConnectionProgress;
import com.croconaut.cpt.link.handler.notification.PublishProgress;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class GroupHandler extends Handler {
    private static final String TAG = "link.group";

    public static final String SERVICE_TYPE  = "_wifon._tcp";
    public static final String INSTANCE_NAME = "_credentials";

    private final PowerManager.WakeLock mWakeLock;
    private final GroupHandlerReceiver mReceiver;
    private boolean mIsStarted;

    private boolean mIsPendingRemoveGroup;
    private WifiP2pDnsSdServiceInfo mWifiP2pDnsSdServiceInfo;
    private boolean mHasPreviousError;

    private final GroupState READY = new GroupState("READY") {
        @Override
        GroupState onConnectionInfoAvailable(Context context, WifiP2pInfo info) {
            // it's possible to receive an intent even after unregisterReceiver()
            if (mIsStarted) {
                if (!info.groupFormed) {
                    if (mIsPendingRemoveGroup) {
                        return finish(context);
                    } else {
                        scheduleTimer(context);
                        createGroup(context);
                        return CREATING;
                    }
                } else {
                    Log.w(TAG, "Group is already formed");
                    scheduleTimer(context);
                    return CREATING.onConnectionInfoAvailable(context, info);
                }
            }
            return this;
        }

        private void scheduleTimer(final Context context) {
            scheduleTask(new Runnable() {
                @Override
                public void run() {
                    new TimerExpired().send(context);
                }
            }, 60);
        }

        private void createGroup(Context context) {
            Log.v(TAG, toString() + ".createGroup");

            mWifiP2pManager.createGroup(mWifiP2pChannel, mReceiver);
        }
    };
    
    private final GroupState CREATING = new GroupState("CREATING") {
        @Override
        GroupState onConnectionInfoAvailable(Context context, WifiP2pInfo info) {
            if (info.groupFormed) {
                Log.i(TAG, "Group formed");
                assertTrue(info.isGroupOwner);

                new PublishProgress().send(context, ConnectionProgress.CREATED_GROUP);

                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN  // 4.1 (API 16)
                        && (Build.VERSION.RELEASE.equals("4.1") || Build.VERSION.RELEASE.equals("4.1.1"))) {
                    mPreferenceHelper.setWifiNeedsRestart(true);
                }

                if (mIsPendingRemoveGroup) {
                    return CREATED.onRemoveGroup(context);
                } else {
                    // there's going to be a consecutive call to obtain group info
                    return CREATED;
                }
            }
            return this;
        }

        @Override
        GroupState onFailure(Context context, int reason) {
            Log.e(TAG, "createGroup() failed: " + reason);
            return failure(context);
        }

        @Override
        GroupState onGroupTimerExpired(Context context) {
            Log.w(TAG, "Group timer has expired in CREATING => finish");
            return REMOVE_SERVICE.onSuccess(context);
        }
    };
    
    private final GroupState CREATED = new GroupState("CREATED") {
        @Override
        GroupState onGroupInfoAvailable(final Context context, WifiP2pGroup group) {
            if (group != null) {
                String networkName = utf8ToString(group.getNetworkName());
                String passphrase = group.getPassphrase();
                addLocalService(networkName, passphrase);
                return PUBLISHING;
            } else {
                requestGroupInfo(context);
            }
            return this;
        }

        @Override
        GroupState onGroupTimerExpired(Context context) {
            Log.w(TAG, "Group timer has expired in CREATED => removing group");
            return onRemoveGroup(context);
        }

        @Override
        GroupState onRemoveGroup(Context context) {
            return REMOVE_SERVICE.onSuccess(context);
        }

        private void requestGroupInfo(Context context) {
            Log.v(TAG, toString() + ".requestGroupInfo");

            mWifiP2pManager.requestGroupInfo(mWifiP2pChannel, mReceiver);
        }

        private void addLocalService(String ssid, String passphrase) {
            Log.v(TAG, toString() + ".addLocalService");

            HashMap<String, String> txtMap = new HashMap<>();
            txtMap.put("S", ssid);
            txtMap.put("P", passphrase);

            Log.i(TAG, "Registered map: " + txtMap);

            mWifiP2pDnsSdServiceInfo = WifiP2pDnsSdServiceInfo.newInstance(INSTANCE_NAME, SERVICE_TYPE, txtMap);
            mWifiP2pManager.addLocalService(mWifiP2pChannel, mWifiP2pDnsSdServiceInfo, mReceiver);
        }

        private String utf8ToString(final String utf8String) {
            String output = new String();
            for (int i = 0; i < utf8String.length(); ) {
                if (utf8String.charAt(i) == '\\' && utf8String.charAt(i + 1) == 'x') {
                    final byte[] bytes = new byte[2];

                    i += 2;
                    // next two chars are a hex code
                    String chars = new String();
                    chars += utf8String.charAt(i++);
                    chars += utf8String.charAt(i++);
                    bytes[0] = (byte) ((Integer.parseInt(chars, 16) << 24) >> 24);

                    assertEquals('\\', utf8String.charAt(i));
                    assertEquals('x', utf8String.charAt(i + 1));

                    i += 2;
                    // another couple
                    chars = new String();
                    chars += utf8String.charAt(i++);
                    chars += utf8String.charAt(i++);
                    bytes[1] = (byte) ((Integer.parseInt(chars, 16) << 24) >> 24);

                    try {
                        output += new String(bytes, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        return null;
                    }
                } else {
                    output += utf8String.charAt(i++);
                }
            }
            return output;
        }
    };
    
    private final GroupState PUBLISHING = new GroupState("PUBLISHING") {
        @Override
        GroupState onFailure(Context context, int reason) {
            Log.e(TAG, "addLocalService() failed: " + reason);
            mHasPreviousError = true;
            mWifiP2pDnsSdServiceInfo = null;
            requestConnectionInfo();
            return REMOVE_GROUP;
        }

        @Override
        GroupState onSuccess(Context context) {
            // we expect nobody can connect earlier than this point
            if (mIsPendingRemoveGroup) {
                return PUBLISHED.onRemoveGroup(context);
            } else {
                return PUBLISHED;
            }
        }

        @Override
        GroupState onGroupTimerExpired(Context context) {
            Log.w(TAG, "Group timer has expired in PUBLISHING => removing group");
            return onRemoveGroup(context);  // only set pending
        }

        private void requestConnectionInfo() {
            Log.v(TAG, toString() + ".requestConnectionInfo");

            mWifiP2pManager.requestConnectionInfo(mWifiP2pChannel, mReceiver);
        }
    };
    
    private final GroupState PUBLISHED = new GroupState("PUBLISHED") {
        private int mGroupClientsCount;

        @Override
        GroupState onGroupInfoAvailable(final Context context, WifiP2pGroup group) {
            if (group != null) {
                int clients = group.getClientList().size();
                if (clients != mGroupClientsCount) {
                    if (clients == 0) {
                        if (mGroupClientsCount > 0) {
                            Log.d(TAG, "0 clients reported, waiting for DropConnection...");
                            // it's important to realize that if there's a problem with the client,
                            // i.e. it doesn't make it even up to the socket connection but past
                            // the wifi connection, we'd wait here forever, so reschedule another timer!
                            scheduleTimer(context);
                        } else {
                            Log.d(TAG, "0 clients reported, waiting for the timer to expire...");
                        }
                    } else if (clients > mGroupClientsCount) {
                        if (mPreferenceHelper.getWakeUpOnFormedGroupEnabled()) {
                            // somebody has connected, wake up device for a moment
                            Log.i(TAG, "Acquiring the wake lock");
                            mWakeLock.acquire();
                        }

                        Log.i(TAG, "Some client has connected, currently clients: " + clients);
                        // if there's a client connected, we don't need the timer anymore
                        cancelCurrentlyScheduledTask();
                    }
                    mGroupClientsCount = clients;
                }
            } else {
                requestGroupInfo();
            }
            return this;
        }

        @Override
        GroupState onPeersChanged(Context context) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN) {
                // this hack is purely for Android 4.1 devices which do not report changed client list
                // in WIFI_P2P_CONNECTION_CHANGED_ACTION but does report WIFI_P2P_PEERS_CHANGED_ACTION at least
                requestGroupInfo();
            }
            return this;
        }

        @Override
        GroupState onRemoveGroup(Context context) {
            cancelCurrentlyScheduledTask();
            removeLocalService();
            return REMOVE_SERVICE;
        }

        @Override
        GroupState onDropConnection(Context context) {
            new PublishProgress().send(context, ConnectionProgress.CREATED_GROUP_WAITING_FOR_REMOVAL);

            while (mWakeLock.isHeld()) {
                mWakeLock.release();
            }

            // no questions asked, just remove
            return onRemoveGroup(context);
        }

        @Override
        GroupState onGroupTimerExpired(final Context context) {
            Log.w(TAG, "Group timer has expired in PUBLISHED => removing group");
            return onRemoveGroup(context);
        }

        private void removeLocalService() {
            Log.v(TAG, toString() + ".removeLocalService");

            mWifiP2pManager.removeLocalService(mWifiP2pChannel, mWifiP2pDnsSdServiceInfo, mReceiver);
        }

        private void requestGroupInfo() {
            Log.v(TAG, toString() + ".requestGroupInfo");

            mWifiP2pManager.requestGroupInfo(mWifiP2pChannel, mReceiver);
        }

        private void scheduleTimer(final Context context) {
            // don't wait for whole minute because the failing client might reconnect in the meantime
            // causing an infinite loop
            scheduleTask(new Runnable() {
                @Override
                public void run() {
                    new TimerExpired().send(context);
                }
            }, 15);
        }
    };
    
    private final GroupState REMOVE_SERVICE = new GroupState("REMOVE_SERVICE") {
        // we get here by a removeLocalService() from another state

        @Override
        GroupState onConnectionInfoAvailable(Context context, WifiP2pInfo info) {
            // not interested yet
            return this;
        }

        @Override
        GroupState onFailure(Context context, int reason) {
            Log.e(TAG, "removeLocalService() failed: " + reason);
            mHasPreviousError = true;
            return onSuccess(context);
        }

        @Override
        GroupState onSuccess(Context context) {
            mWifiP2pDnsSdServiceInfo = null;

            requestConnectionInfo();
            return REMOVE_GROUP;
        }

        private void requestConnectionInfo() {
            Log.v(TAG, toString() + ".requestConnectionInfo");

            mWifiP2pManager.requestConnectionInfo(mWifiP2pChannel, mReceiver);
        }
    };
    
    private final GroupState REMOVE_GROUP = new GroupState("REMOVE_GROUP") {
        // we get here by a requestConnectionInfo() from another state
        @Override
        GroupState onConnectionInfoAvailable(Context context, WifiP2pInfo info) {
            if (info.groupFormed) {
                removeGroup();
                return this;
            } else if (mHasPreviousError) {
                return failure(context);
            } else {
                // if there's a failure it hardly make sense to show the last step
                new PublishProgress().send(context, ConnectionProgress.CREATED_GROUP_REMOVED);
                return finish(context);
            }
        }

        // requestConnectionInfo() can't fail
        @Override
        GroupState onFailure(Context context, int reason) {
            Log.e(TAG, "removeGroup() failed: " + reason);
            return failure(context);
        }

        private void removeGroup() {
            Log.v(TAG, toString() + ".removeGroup");

            mWifiP2pManager.removeGroup(mWifiP2pChannel, mReceiver);
        }
    };

    abstract class GroupState extends State {
        public GroupState(String name) {
            super(name);
        }

        protected GroupState failure(Context context) {
            mReceiver.unregister();
            new HandlerFailed().send(context);
            return READY;
        }

        protected GroupState finish(Context context) {
            mReceiver.unregister();
            new HandlerFinished().send(context);
            return READY;
        }

        GroupState onPeersChanged(Context context) {
            return this;
        }

        GroupState onFailure(Context context, int reason) {
            Log.e(TAG, "onFailure() in state " + this + ": " + reason);
            return this;
        }

        GroupState onSuccess(Context context) {
            return this;
        }

        GroupState onConnectionInfoAvailable(Context context, WifiP2pInfo info) {
            if (!info.groupFormed) {
                Log.w(TAG, "Group abruptly removed");
                mHasPreviousError = true;

                cancelCurrentlyScheduledTask();
                if (mWifiP2pDnsSdServiceInfo != null) {
                    removeLocalService();
                    return REMOVE_SERVICE;
                } else {
                    return REMOVE_SERVICE.onSuccess(context);
                }
            }
            return this;
        }

        GroupState onGroupInfoAvailable(Context context, WifiP2pGroup group) {
            return this;
        }

        GroupState onGroupTimerExpired(Context context) {
            return this;
        }

        GroupState onRemoveGroup(Context context) {
            mIsPendingRemoveGroup = true;
            return this;
        }

        GroupState onDropConnection(Context context) {
            return this;
        }

        private void removeLocalService() {
            Log.v(TAG, toString() + ".removeLocalService");

            mWifiP2pManager.removeLocalService(mWifiP2pChannel, mWifiP2pDnsSdServiceInfo, mReceiver);
        }
    }

    public GroupHandler(final Context context) {
        super(context, TAG);

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        //noinspection deprecation
        mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, getClass().getName());
        mWakeLock.setReferenceCounted(true);    // should be by default but ...

        mReceiver = new GroupHandlerReceiver(context, TAG, READY);
    }

    @Override
    public void start() {
        Log.v(TAG, getClass().getSimpleName() + ".start");

        mReceiver.register();
        mIsStarted = true;
    }

    @Override
    public void stop() {
        Log.v(TAG, getClass().getSimpleName() + ".stop");

        new Stop().send(context);
        mIsStarted = false;
    }

    public void cleanup() {
        Log.v(TAG, getClass().getSimpleName() + ".cleanup");

        // don't make this more complicated than needed...
        mWifiP2pManager.removeGroup(mWifiP2pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                new HandlerFinished().send(context);
            }
            @Override
            public void onFailure(int reason) {
                Log.w(TAG, "removeGroup() failed: " + reason);
                new HandlerFailed().send(context);
            }
        });
    }
}
