package com.croconaut.cpt.network;

import android.content.Context;

import com.croconaut.cpt.link.PreferenceHelper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

abstract class AbstractHelper {
    protected final String TAG;
    protected final Context context;
    protected final DataInputStream dis;
    protected final DataOutputStream dos;

    protected final PreferenceHelper helper;

    protected String crocoId;

    protected AbstractHelper(String TAG, Context context, DataInputStream dis, DataOutputStream dos) {
        this.TAG = TAG;
        this.context = context;
        this.dis = dis;
        this.dos = dos;

        helper = new PreferenceHelper(context);
    }

    protected void setCrocoId(String crocoId) {
        this.crocoId = crocoId;
    }

    protected int getAndIncrement(int base, AtomicInteger atomicInteger) {
        for (;;) {
            int current = atomicInteger.get();
            int next = (current + 1) % 100;
            if (atomicInteger.compareAndSet(current, next))
                return base + current;
        }
    }
}
