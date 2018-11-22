package com.croconaut.cpt.common.intent;

import android.content.Context;
import android.content.Intent;

interface CptIntentReceiver {
    void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver);
}
