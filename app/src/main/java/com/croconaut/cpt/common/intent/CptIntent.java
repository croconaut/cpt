package com.croconaut.cpt.common.intent;

abstract class CptIntent implements CptIntentReceiver {
    private final String action;

    protected CptIntent() {
        // if no action is supplied, use class name
        action = getClass().getName();
    }

    protected CptIntent(String action) {
        this.action = action;
    }

    public String getAction() {
        return action;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CptIntent that = (CptIntent) o;

        return action.equals(that.action);

    }

    @Override
    public int hashCode() {
        return action.hashCode();
    }
}
