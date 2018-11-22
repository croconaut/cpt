package com.croconaut.cpt.common;

public abstract class State {
    private final String name;

    public State(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
