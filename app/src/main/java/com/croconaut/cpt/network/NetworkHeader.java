package com.croconaut.cpt.network;

import android.content.Context;

import com.croconaut.cpt.data.MessageIdentifier;
import com.croconaut.cpt.data.StreamUtil;
import com.croconaut.cpt.data.Streamable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class NetworkHeader implements Streamable, Comparable<NetworkHeader> {
    private long rowId;

    private /*final*/ MessageIdentifier identifier;
    private /*final*/ Type type;
    private /*final*/ int persistentId;

    public enum Type {
		NORMAL(0), ACK (1);

        static {
            // expensive initialization
            values = Type.values();
        }

        public static Type fromValue(int value) {
            return values[value];
        }
		
		Type(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}
		
		private final int value;
        private static final Type[] values;
	}
	
	public NetworkHeader(long rowId, MessageIdentifier messageIdentifier, Type type, int persistentId) {
        this.rowId = rowId;
        this.identifier = messageIdentifier;
		this.type = type;
        this.persistentId = persistentId;
	}

    public NetworkHeader flipped() {
        return new NetworkHeader(rowId, identifier, type == NetworkHeader.Type.NORMAL ? NetworkHeader.Type.ACK : NetworkHeader.Type.NORMAL, persistentId);
    }

    public long getRowId() {
        return rowId;
    }

    public int getPersistentId() {
        return persistentId;
    }

    public boolean isPersistent() {
        return persistentId != -1;
    }

    public String getAppId() {
        return identifier.appId;
    }

    public String getFrom() {
        return identifier.from;
    }

    public String getTo() {
        return identifier.to;
    }

    public long getCreationTime() {
        return identifier.creationTime;
    }

    public Type getType() {
        return type;
    }

    public MessageIdentifier getIdentifier() {
        return identifier;
    }

    @Override
    public String toString() {
        return "NetworkHeader{" +
                "identifier=" + identifier +
                ", type=" + type +
                ", persistentId=" + persistentId +
                ", rowId=" + rowId +
                '}';
    }

    /*
     * hashCode() is used for bucketing in Hash implementations like HashMap, HashTable, HashSet, etc.
     * The value received from hashCode() is used as the bucket number for storing elements of the set/map.
     * This bucket number is the address of the element inside the set/map.
     * When you do contains() it will take the hash code of the element, then look for the bucket where
     * hash code points to. If more than 1 element is found in the same bucket (multiple objects can have
     * the same hash code), then it uses the equals() method to evaluate if the objects are equal, and then
      * decide if contains() is true or false, or decide if element could be added in the set or not.
     */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NetworkHeader that = (NetworkHeader) o;

        if (persistentId != that.persistentId) return false;
        if (!identifier.equals(that.identifier)) return false;
        return type == that.type;

    }

    @Override
    public int hashCode() {
        int result = identifier.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + persistentId;
        return result;
    }

    @Override
    public int compareTo(NetworkHeader another) {
        // the order is important!
        int result = identifier.appId.compareTo(another.identifier.appId);
        if (result != 0) {
            return result;
        }
        result = identifier.from.compareTo(another.identifier.from);
        if (result != 0) {
            return result;
        }
        result = ((Integer) persistentId).compareTo(another.persistentId);
        if (result != 0) {
            return result;
        }
        result = ((Long) identifier.creationTime).compareTo(another.identifier.creationTime);
        if (result != 0) {
            return result;
        }
        // it's virtually impossible have a persistent message with the same appId+from+creationTime but different to/type
        // 'to' and 'type' may change with persistent messages (but not with the same 'creationTime', of course) so they
        // must be checked after 'creationTime'
        result = identifier.to.compareTo(another.identifier.to);
        if (result != 0) {
            return result;
        }
        result = type.compareTo(another.type);
        if (result != 0) {
            return result;
        }

        return 0;
    }

    // Streamable

    public NetworkHeader() {
    }

    @Override
    public void readFrom(Context context, DataInputStream dis) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        rowId = dis.readLong();
        identifier = (MessageIdentifier) StreamUtil.readStreamableFrom(context, dis);
        type = NetworkHeader.Type.fromValue(dis.readInt());
        persistentId = dis.readInt();
    }

    @Override
    public void writeTo(Context context, DataOutputStream dos) throws IOException {
        dos.writeLong(rowId);
        StreamUtil.writeStreamableTo(context, dos, identifier);
        dos.writeInt(type.getValue());
        dos.writeInt(persistentId);
    }
}
