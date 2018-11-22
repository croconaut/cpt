package com.croconaut.cpt.common.intent;

import android.content.Intent;

abstract class ImplicitIntent extends CptIntent {
    protected Intent getIntent() {
        return new Intent(getAction());
    }
}
