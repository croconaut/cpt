package com.croconaut.cpt.link.handler.notification;

import android.content.Context;

import com.croconaut.cpt.R;

import static junit.framework.Assert.assertEquals;

public enum ConnectionProgress {
    DISCOVERING {
        @Override
        public String getDesc(Context context) {
            return context.getResources().getString(R.string.cpt_notif_DISCOVERING);
        }
    },
    DISCOVERING_WAITING_FOR_GO_AND_CREDENTIALS {
        @Override
        public String getDesc(Context context) {
            return context.getResources().getString(R.string.cpt_notif_DISCOVERING_WAITING_FOR_GO_AND_CREDENTIALS);
        }
        @Override
        public int getStep() {
            return 0;
        }
        @Override
        public int getMax() {
            return mConnectionMaximumStep;
        }
    },
    DISCOVERING_WAITING_FOR_GO {
        @Override
        public String getDesc(Context context) {
            return context.getResources().getString(R.string.cpt_notif_DISCOVERING_WAITING_FOR_GO);
        }
        @Override
        public int getStep() {
            return DISCOVERING_WAITING_FOR_GO_AND_CREDENTIALS.getStep() + 1;
        }
        @Override
        public int getMax() {
            return mConnectionMaximumStep;
        }
    },
    DISCOVERING_WAITING_FOR_CREDENTIALS {
        @Override
        public String getDesc(Context context) {
            return context.getResources().getString(R.string.cpt_notif_DISCOVERING_WAITING_FOR_CREDENTIALS);
        }
        @Override
        public int getStep() {
            return DISCOVERING_WAITING_FOR_GO_AND_CREDENTIALS.getStep() + 1;
        }
        @Override
        public int getMax() {
            return mConnectionMaximumStep;
        }
    },
    SCANNING {
        @Override
        public String getDesc(Context context) {
            return context.getResources().getString(R.string.cpt_notif_SCANNING);
        }
        @Override
        public int getStep() {
            assertEquals(DISCOVERING_WAITING_FOR_GO.getStep(), DISCOVERING_WAITING_FOR_CREDENTIALS.getStep());
            return DISCOVERING_WAITING_FOR_GO.getStep() + 1;
        }
        @Override
        public int getMax() {
            return mConnectionMaximumStep;
        }
    },
    CONNECTING1 {
        @Override
        public String getDesc(Context context) {
            return context.getResources().getString(R.string.cpt_notif_CONNECTING1);
        }
        @Override
        public int getStep() {
            return SCANNING.getStep() + 1;
        }
        @Override
        public int getMax() {
            return mConnectionMaximumStep;
        }
    },
    CONNECTING2 {
        @Override
        public String getDesc(Context context) {
            return context.getResources().getString(R.string.cpt_notif_CONNECTING2);
        }
        @Override
        public int getStep() {
            return CONNECTING1.getStep() + 1;
        }
        @Override
        public int getMax() {
            return mConnectionMaximumStep;
        }
    },
    CONNECTED {
        @Override
        public String getDesc(Context context) {
            return context.getResources().getString(R.string.cpt_notif_CONNECTED);
        }
        @Override
        public int getStep() {
            return CONNECTING2.getStep() + 1;
        }
        @Override
        public int getMax() {
            return mConnectionMaximumStep;
        }
    },
    CONNECTED_AS_CLIENT {
        @Override
        public String getDesc(Context context) {
            return context.getResources().getString(R.string.cpt_notif_CONNECTED_AS_CLIENT);
        }
        @Override
        public int getStep() {
            return CONNECTED.getStep() + 1;
        }
        @Override
        public int getMax() {
            return mConnectionMaximumStep;
        }
    },
    DISCONNECTING {
        @Override
        public String getDesc(Context context) {
            return context.getResources().getString(R.string.cpt_notif_DISCONNECTING);
        }
        @Override
        public int getStep() {
            return CONNECTED_AS_CLIENT.getStep() + 1;
        }
        @Override
        public int getMax() {
            return mConnectionMaximumStep;
        }
    },
    DISCONNECTED {
        @Override
        public String getDesc(Context context) {
            return context.getResources().getString(R.string.cpt_notif_DISCONNECTED);
        }
        @Override
        public int getStep() {
            return DISCONNECTING.getStep() + 1;
        }
        @Override
        public int getMax() {
            return mConnectionMaximumStep;
        }
    },
    CREATING_GROUP {
        @Override
        public String getDesc(Context context) {
            return context.getResources().getString(R.string.cpt_notif_CREATING_GROUP);
        }
        @Override
        public int getStep() {
            return 0;
        }
        @Override
        public int getMax() {
            return mGroupMaximumStep;
        }
    },
    CREATED_GROUP {
        @Override
        public String getDesc(Context context) {
            return context.getResources().getString(R.string.cpt_notif_CREATED_GROUP);
        }
        @Override
        public int getStep() {
            return CREATING_GROUP.getStep() + 1;
        }
        @Override
        public int getMax() {
            return mGroupMaximumStep;
        }
    },
    CREATED_GROUP_AS_SERVER {
        @Override
        public String getDesc(Context context) {
            return context.getResources().getString(R.string.cpt_notif_CREATED_GROUP_AS_SERVER);
        }
        @Override
        public int getStep() {
            return CREATED_GROUP.getStep() + 1;
        }
        @Override
        public int getMax() {
            return mGroupMaximumStep;
        }
    },
    CREATED_GROUP_WAITING_FOR_REMOVAL {
        @Override
        public String getDesc(Context context) {
            return context.getResources().getString(R.string.cpt_notif_CREATED_GROUP_WAITING_FOR_REMOVAL);
        }
        @Override
        public int getStep() {
            return CREATED_GROUP_AS_SERVER.getStep() + 1;
        }
        @Override
        public int getMax() {
            return mGroupMaximumStep;
        }
    },
    CREATED_GROUP_REMOVED {
        @Override
        public String getDesc(Context context) {
            return context.getResources().getString(R.string.cpt_notif_CREATED_GROUP_REMOVED);
        }
        @Override
        public int getStep() {
            return CREATED_GROUP_WAITING_FOR_REMOVAL.getStep() + 1;
        }
        @Override
        public int getMax() {
            return mGroupMaximumStep;
        }
    },
    BACKGROUND_MODE {
        @Override
        public String getDesc(Context context) {
            return context.getResources().getString(R.string.cpt_notif_BACKGROUND_MODE);
        }
    };

    private static final int mConnectionMaximumStep = 9;
    private static final int mGroupMaximumStep = 5;

    public abstract String getDesc(Context context);
    public int getStep() {
        return 0;
    }
    public int getMax() {
        // no step
        return 0;
    }
}
