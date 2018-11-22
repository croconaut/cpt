package com.croconaut.cpt.ui;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class LinkLayerMode {
    @IntDef({OFF, BACKGROUND, FOREGROUND})
    @Retention(RetentionPolicy.SOURCE)
    // avoid enums to make it easy to process
    public @interface CptMode {}
    public static final int OFF = 0;
    public static final int BACKGROUND = 1;
    public static final int FOREGROUND = 2;
}
