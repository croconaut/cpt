package com.croconaut.cpt.network;

abstract class CrocoIdThread extends Thread {
    abstract void interruptIfEqualsTo(String crocoId);
}
