package com.croconaut.cpt.data;

import android.os.Parcel;
import android.os.Parcelable;

public class NearbyUser implements Parcelable {
    public final String crocoId;
    public final String username;

    public NearbyUser(String crocoId, String username) {
        this.crocoId = crocoId;
        this.username = username;
    }

    protected NearbyUser(Parcel in) {
        crocoId = in.readString();
        username = in.readString();
    }

    public static final Creator<NearbyUser> CREATOR = new Creator<NearbyUser>() {
        @Override
        public NearbyUser createFromParcel(Parcel in) {
            return new NearbyUser(in);
        }

        @Override
        public NearbyUser[] newArray(int size) {
            return new NearbyUser[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(crocoId);
        dest.writeString(username);
    }
}
