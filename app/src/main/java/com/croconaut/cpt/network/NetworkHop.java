package com.croconaut.cpt.network;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import com.croconaut.cpt.data.Streamable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

public class NetworkHop implements Parcelable, Streamable {
    public /*final*/ String crocoId;
	public /*final*/ double latitude;			// GPS coordinates at the time of reaching a device
	public /*final*/ double longitude;
	public /*final*/ Date locationTime;		// when it was acquired Locations
	public /*final*/ String androidOsVersion;	// Android OS version
	public /*final*/ Date receivedTime;		// when the message reached device
	public /*final*/ String userName;			// who is the owner of the phone

    public NetworkHop(String crocoId, double latitude, double longitude, Date locationTime, String androidOsVersion, Date receivedTime, String userName) {
        this.crocoId = crocoId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationTime = locationTime;
        this.androidOsVersion = androidOsVersion;
        this.receivedTime = receivedTime;
        this.userName = userName;
    }

	@Override
	public String toString() {
		return "NetworkHop{" +
                "crocoId='" + crocoId + '\'' +
				", latitude=" + latitude +
				", longitude=" + longitude +
				", locationTime=" + locationTime +
				", androidOsVersion='" + androidOsVersion + '\'' +
				", receivedTime=" + receivedTime +
				", userName='" + userName + '\'' +
				'}';
	}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NetworkHop that = (NetworkHop) o;

        if (crocoId != null ? !crocoId.equals(that.crocoId) : that.crocoId != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return crocoId != null ? crocoId.hashCode() : 0;
    }

    // Parcelable

	protected NetworkHop(Parcel in) {
        crocoId = in.readString();
		latitude = in.readDouble();
		longitude = in.readDouble();
        locationTime = new Date(in.readLong());
		androidOsVersion = in.readString();
        receivedTime = new Date(in.readLong());
		userName = in.readString();
	}

	public static final Creator<NetworkHop> CREATOR = new Creator<NetworkHop>() {
		@Override
		public NetworkHop createFromParcel(Parcel in) {
			return new NetworkHop(in);
		}

		@Override
		public NetworkHop[] newArray(int size) {
			return new NetworkHop[size];
		}
	};

	@Override
	public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(crocoId);
		dest.writeDouble(latitude);
		dest.writeDouble(longitude);
		dest.writeLong(locationTime.getTime());
		dest.writeString(androidOsVersion);
        dest.writeLong(receivedTime.getTime());
		dest.writeString(userName);
	}

    @Override
    public int describeContents() {
        return 0;
    }

	// Streamable

	public NetworkHop() {
	}

    @Override
    public void readFrom(Context context, DataInputStream dis) throws IOException, ClassNotFoundException {
        if (dis.readBoolean()) {
            crocoId = dis.readUTF();
        }
        latitude = dis.readDouble();
        longitude = dis.readDouble();
        locationTime = new Date(dis.readLong());
        androidOsVersion = dis.readUTF();
        receivedTime = new Date(dis.readLong());
        userName = dis.readUTF();
    }

    @Override
    public void writeTo(Context context, DataOutputStream dos) throws IOException {
        dos.writeBoolean(crocoId != null);
        if (crocoId != null) {
            dos.writeUTF(crocoId);
        }
        dos.writeDouble(latitude);
        dos.writeDouble(longitude);
        dos.writeLong(locationTime.getTime());
        dos.writeUTF(androidOsVersion);
        dos.writeLong(receivedTime.getTime());
        dos.writeUTF(userName);
    }
}
