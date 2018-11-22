package com.croconaut.cpt.link;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.util.Log;

public class PreferenceHelper {
    private static final String TAG = "link";

    // cpt variables (:cpt)
    private static final String CROCO_ID = "crocoId";
    private static final String APP_ID = "appId";
    private static final String CLASS_ID = "classId";
    private static final String USERNAME = "username";
    private static final String HASH = "hash";
    // link layer only variables (:cpt)
    private static final String WIFI_PEER_SSID = "wifi-peer-ssid";
    private static final String WIFI_TIMESTAMP = "wifi-timestamp";
    private static final String WIFI_HANDLER = "wifi-handler";
    private static final String WIFI_NEEDS_RESTART = "wifi-needs-restart";

    // cpt_ui variables
    private static final String MODE = "mode";
    private static final String LOCAL_ONLY = "local_only";
    private static final String TRACKING = "tracking";
    private static final String NEW_API = "new_api";
    private static final String REVERSE_MODE = "reverse_mode";
    private static final String WAKE_UP_ON_FORMED_GROUP = "wake-up-on-formed-group";

    private final OnSharedPreferenceChangeListener mListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            update(sharedPreferences, key);
        }
    };

    public PreferenceHelper(Context context) {
        this(context, "cpt");
    }

    public PreferenceHelper(Context context, final String name) {
        mPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE /*| Context.MODE_MULTI_PROCESS*/); // explicitly single process
        mPreferences.registerOnSharedPreferenceChangeListener(mListener);

        // first time run (update the fields with default values)
        update(MODE);
        update(CROCO_ID);
        update(APP_ID);
        update(CLASS_ID);
        update(USERNAME);
        update(HASH);
        update(WIFI_PEER_SSID);
        update(WIFI_TIMESTAMP);
        update(WIFI_HANDLER);
        update(WIFI_NEEDS_RESTART);
        update(LOCAL_ONLY);
        update(TRACKING);
        update(NEW_API);
        update(REVERSE_MODE);
        update(WAKE_UP_ON_FORMED_GROUP);
    }

    public int getMode() {
        return mMode;
    }

    public void setMode(int mode) {
        store(MODE, mode);
    }

    public String getAppId() {
        return mAppId;
    }

    public void setAppId(String id) {
        store(APP_ID, id);
    }

    public String getClassId() {
        return mClassId;
    }

    public void setClassId(String id) {
        store (CLASS_ID, id);
    }

    public String getCrocoId() {
        return mCrocoId;
    }

    public void setCrocoId(String id) {
        store(CROCO_ID, id);
    }

    public String getUsername() {
        return mName;
    }

    public void setUsername(String name) {
        store (USERNAME, name);
    }

    public String getHash() {
        return mHash;
    }

    public void setHash(String hash) {
        store (HASH, hash);
    }

    public String getWifiPeerSsid() {
        return mWifiPeerSsid;
    }

    public void setWifiPeerSsid(String ssid) {
        store(WIFI_PEER_SSID, ssid);
    }

    public void resetWifiPeerSsid() {
        mWifiPeerSsid = null;
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.remove(WIFI_PEER_SSID);
        editor.commit();
    }

    public long getWifiTimestamp() {
        return mWifiTimestamp;
    }

    public void setWifiTimestamp(long timestamp) {
        store(WIFI_TIMESTAMP, timestamp);
    }

    public String getWifiHandler() {
        return mWifiHandler;
    }

    public void setWifiHandler(String handler) {
        store(WIFI_HANDLER, handler);
    }

    public void resetWifiHandler() {
        mWifiHandler = null;
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.remove(WIFI_HANDLER);
        editor.commit();
    }

    public boolean getWifiNeedsRestart() {
        return mWifiNeedsRestart;
    }

    public void setWifiNeedsRestart(boolean needsRestart) {
        store(WIFI_NEEDS_RESTART, needsRestart);
    }

    public boolean getLocalNetworkOnlyEnabled() {
        return mLocalOnly;
    }

    public void setLocalNetworkOnlyEnabled(boolean enabled)  {
        store(LOCAL_ONLY, enabled);
    }

    public boolean getTrackingEnabled() {
        return mTracking;
    }

    public void setTrackingEnabled(boolean enabled) {
        store(TRACKING, enabled);
    }

    public boolean getNewApiCallsEnabled() {
        return mNewApi;
    }

    public void setNewApiCallsEnabled(boolean enabled) {
        store(NEW_API, enabled);
    }

    public boolean getReverseConnectionModeEnabled() {
        return mReverseMode;
    }

    public void setReverseConnectionModeEnabled(boolean enabled) {
        store(REVERSE_MODE, enabled);
    }

    public boolean getWakeUpOnFormedGroupEnabled() {
        return mWakeUpOnFormedGroup;
    }

    public void setWakeUpOnFormedGroupEnabled(boolean enabled) {
        store(WAKE_UP_ON_FORMED_GROUP, enabled);
    }

    private void store(String key, boolean value) {
        switch (key) {
            case LOCAL_ONLY:
                mLocalOnly = value;
                break;
            case TRACKING:
                mTracking = value;
                break;
            case NEW_API:
                mNewApi = value;
                break;
            case REVERSE_MODE:
                mReverseMode = value;
                break;
            case WIFI_NEEDS_RESTART:
                mWifiNeedsRestart = value;
                break;
            case WAKE_UP_ON_FORMED_GROUP:
                mWakeUpOnFormedGroup = value;
                break;
            default:
                Log.e(TAG, "Illegal boolean key: " + key + " / " + value);
                return;
        }
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(key, value);
        editor.commit();
    }

    private void store(String key, int value) {
        switch (key) {
            case MODE:
                mMode = value;
                break;
            default:
                Log.e(TAG, "Illegal int key: " + key + " / " + value);
                return;
        }
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putInt(key, value);
        editor.commit();
    }

    private void store(String key, long value) {
        switch (key) {
            case WIFI_TIMESTAMP:
                mWifiTimestamp = value;
                break;
            default:
                Log.e(TAG, "Illegal long key: " + key + " / " + value);
                return;
        }
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putLong(key, value);
        editor.commit();
    }

    private void store(String key, String value) {
        switch (key) {
            case CROCO_ID:
                mCrocoId = value;
                break;
            case APP_ID:
                mAppId = value;
                break;
            case CLASS_ID:
                mClassId = value;
                break;
            case USERNAME:
                mName = value;
                break;
            case HASH:
                mHash = value;
                break;
            case WIFI_PEER_SSID:
                mWifiPeerSsid = value;
                break;
            case WIFI_HANDLER:
                mWifiHandler = value;
                break;
            default:
                Log.e(TAG, "Illegal string key: " + key + " / " + value);
                return;
        }
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString(key, value);
        editor.commit();
    }

    private void update(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case MODE:
                mMode = sharedPreferences.getInt(key, -1);
                break;
            case CROCO_ID:
                mCrocoId = sharedPreferences.getString(key, null);
                break;
            case APP_ID:
                mAppId = sharedPreferences.getString(key, null);
                break;
            case CLASS_ID:
                mClassId = sharedPreferences.getString(key, null);
                break;
            case USERNAME:
                mName = sharedPreferences.getString(key, null);
                break;
            case HASH:
                mHash = sharedPreferences.getString(key, null);
                break;
            case WIFI_PEER_SSID:
                mWifiPeerSsid = sharedPreferences.getString(key, null);
                break;
            case WIFI_TIMESTAMP:
                mWifiTimestamp = sharedPreferences.getLong(key, 0);
                break;
            case WIFI_HANDLER:
                mWifiHandler = sharedPreferences.getString(key, null);
                break;
            case WIFI_NEEDS_RESTART:
                mWifiNeedsRestart = sharedPreferences.getBoolean(key, false);
                break;
            case LOCAL_ONLY:
                mLocalOnly = sharedPreferences.getBoolean(key, false);
                break;
            case TRACKING:
                mTracking = sharedPreferences.getBoolean(key, false);
                break;
            case NEW_API:
                mNewApi = sharedPreferences.getBoolean(key, true);
                //mNewApi = sharedPreferences.getBoolean(key, false);
                break;
            case REVERSE_MODE:
                mReverseMode = sharedPreferences.getBoolean(key, false);
                break;
            case WAKE_UP_ON_FORMED_GROUP:
                // TODO: Google Nexus' MANUFACTURER = LGE, test an LG device?
                mWakeUpOnFormedGroup = sharedPreferences.getBoolean(key, Build.MANUFACTURER.toLowerCase().contains("samsung") || Build.BRAND.toLowerCase().contains("google"));
                break;
        }
    }

    private void update(String key) {
        update(mPreferences, key);
    }

    private final SharedPreferences mPreferences;
    private volatile int mMode;
    private volatile long mWifiTimestamp;
    private volatile String mWifiPeerSsid;
    private volatile String mWifiHandler;
    private volatile boolean mWifiNeedsRestart;
    private volatile String mCrocoId;
    private volatile String mAppId;
    private volatile String mClassId;
    private volatile String mName;
    private volatile String mHash;
    private volatile boolean mLocalOnly;
    private volatile boolean mTracking;
    private volatile boolean mNewApi;
    private volatile boolean mReverseMode;
    private volatile boolean mWakeUpOnFormedGroup;
}
