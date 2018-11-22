package com.croconaut.cpt.link;

import java.io.Serializable;

public class Settings implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Settings instance = new Settings();

    public volatile boolean useNewApi;
    public volatile boolean reverseConnectionMode;
    public volatile int mode = -1;
    public volatile boolean wakeUpOnFormedGroup = true;
    public volatile boolean useLocalOnly = false;
    public volatile boolean allowTracking = false;

    public volatile boolean force;

    private Settings() { }

    public static Settings getInstance() {
        return instance;
    }
}
