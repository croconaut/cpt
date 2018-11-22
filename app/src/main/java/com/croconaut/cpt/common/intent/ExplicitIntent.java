package com.croconaut.cpt.common.intent;

import android.content.Context;
import android.content.Intent;

abstract class ExplicitIntent extends CptIntent {
    protected Intent getIntent(Context context, Class<?> cls) {
        return new Intent(getAction()).setClass(context, cls);
    }
}
