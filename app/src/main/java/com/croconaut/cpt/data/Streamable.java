package com.croconaut.cpt.data;

import android.content.Context;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface Streamable {
    // requires a public zero-argument constructor

    void readFrom(Context context, DataInputStream dis) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException;
    void writeTo(Context context, DataOutputStream dos) throws IOException;
}
