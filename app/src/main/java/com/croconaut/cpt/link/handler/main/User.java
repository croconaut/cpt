package com.croconaut.cpt.link.handler.main;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import com.croconaut.cpt.link.Settings;

import java.net.InetAddress;

public class User implements Comparable<User>, Parcelable {
    public final String crocoId;
    public String hash;
    public String ssid;
    public String passphrase;
    public InetAddress networkAddress;
    public int port;
    public boolean isGroupOwner;
    public String username;
    public String targetAp;

    public User(String crocoId) {
        this.crocoId = crocoId;
    }

    public User(String crocoId, boolean isGroupOwner) {
        this(crocoId);
        this.isGroupOwner = isGroupOwner;
    }

    public User(String crocoId, String hash, InetAddress networkAddress, int port, boolean isGroupOwner, String username, String targetAp) {
        this(crocoId, isGroupOwner);
        this.hash = hash;
        this.networkAddress = networkAddress;
        this.port = port;
        this.username = username;
        this.targetAp = targetAp;
    }

    public User(String crocoId, String ssid, String passphrase, boolean isGroupOwner) {
        this(crocoId, isGroupOwner);
        this.ssid = ssid;
        this.passphrase = passphrase;
    }

    boolean isAvailable() {
        return ssid != null && passphrase != null;
    }

    boolean isReady() {
        return ssid != null && passphrase != null && isGroupOwner;
    }

    boolean isClientOf(User another) {
        // lower mac address => client
        Settings.getInstance().reverseConnectionMode = true;
        return Settings.getInstance().reverseConnectionMode ? compareTo(another) > 0 : compareTo(another) < 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        User user = (User) o;

        return crocoId.equals(user.crocoId);
    }

    @Override
    public int hashCode() {
        return crocoId.hashCode();
    }

    @Override
    public String toString() {
        return "User{" +
                "crocoId='" + crocoId + '\'' +
                ", hash='" + hash + '\'' +
                ", ssid='" + ssid + '\'' +
                ", passphrase='" + passphrase + '\'' +
                ", networkAddress='" + (networkAddress != null ? networkAddress.getHostAddress() : null) + '\'' +
                ", port=" + port +
                ", isGroupOwner=" + isGroupOwner +
                ", username='" + username + '\'' +
                ", targetAp='" + targetAp + '\'' +
                '}';
    }

    @Override
    public int compareTo(@NonNull User another) {
        return crocoId.compareTo(another.crocoId);
    }

    // Parcelable

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(crocoId);
        dest.writeString(hash);
        dest.writeString(ssid);
        dest.writeString(passphrase);
        dest.writeSerializable(networkAddress);
        dest.writeInt(port);
        dest.writeInt(isGroupOwner ? 1 : 0);
        dest.writeString(username);
        dest.writeString(targetAp);
    }

    public static final Parcelable.Creator<User> CREATOR = new Parcelable.Creator<User>() {
        @Override
        public User createFromParcel(Parcel source) {
            return new User(source);
        }

        @Override
        public User[] newArray(int size) {
            return new User[size];
        }
    };

    private User(Parcel in) {
        crocoId = in.readString();
        hash = in.readString();
        ssid = in.readString();
        passphrase = in.readString();
        networkAddress = (InetAddress) in.readSerializable();
        port = in.readInt();
        isGroupOwner = in.readInt() == 1;
        username = in.readString();
        targetAp = in.readString();
    }
}
