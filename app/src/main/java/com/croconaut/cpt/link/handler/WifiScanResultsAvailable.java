package com.croconaut.cpt.link.handler;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.GlobalIntent;

import java.util.List;

public class WifiScanResultsAvailable extends GlobalIntent {
    public interface Receiver {
        void onScanResultsAvailable(Context context, List<ScanResult> scanResults);
    }

    public WifiScanResultsAvailable() {
        super(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        List<ScanResult> scanResults = wifiManager.getScanResults();
        ((Receiver) targetReceiver).onScanResultsAvailable(context, scanResults);
    }
}
