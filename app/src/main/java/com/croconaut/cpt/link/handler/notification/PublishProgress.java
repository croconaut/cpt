package com.croconaut.cpt.link.handler.notification;

import android.content.Context;
import android.content.Intent;

import com.croconaut.cpt.common.intent.CptBroadcastReceiver;
import com.croconaut.cpt.common.intent.LocalIntent;

public class PublishProgress extends LocalIntent {
    private static final String EXTRA_PROGRESS = "progress";
    private static final String EXTRA_TEXT = "text";

    public interface Receiver {
        void onPublishProgress(Context context, ConnectionProgress progress, String connectionInfoText);
    }

    public void send(Context context, ConnectionProgress progress) {
        send(context, progress, null);
    }

    public void send(Context context, ConnectionProgress progress, String connectionInfoText) {
        super.send(context,
                getIntent()
                    .putExtra(EXTRA_PROGRESS, progress)
                    .putExtra(EXTRA_TEXT, connectionInfoText)
        );
    }

    @Override
    public void onReceive(Context context, Intent intent, CptBroadcastReceiver targetReceiver) {
        ConnectionProgress progress = (ConnectionProgress) intent.getSerializableExtra(EXTRA_PROGRESS);
        String connectionInfoText = intent.getStringExtra(EXTRA_TEXT);
        ((Receiver) targetReceiver).onPublishProgress(context, progress, connectionInfoText);
    }
}
