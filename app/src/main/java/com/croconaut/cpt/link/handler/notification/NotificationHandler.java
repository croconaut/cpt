package com.croconaut.cpt.link.handler.notification;

import android.app.Service;

import com.croconaut.cpt.link.handler.Handler;

public class NotificationHandler extends Handler {
    private static final String TAG = "link.notify";

    private final NotificationHandlerReceiver receiver;

    public NotificationHandler(Service service) {
        super(service, TAG);

        receiver = new NotificationHandlerReceiver(service, TAG);
    }

    @Override
    public void start() {
        receiver.register();
    }

    @Override
    public void stop() {
        receiver.unregister();
    }
}
