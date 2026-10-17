package com.xiaomi.xms.wearable.node;

import android.os.Parcel;
import android.os.Parcelable;

public class Node implements Parcelable {
    public static final Creator<Node> CREATOR = new Creator<>() {
        @Override
        public Node createFromParcel(Parcel source) {
            return new Node(source);
        }

        @Override
        public Node[] newArray(int size) {
            return new Node[size];
        }
    };

    public String id;
    public String name;

    public Node(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public Node(Parcel source) {
        id = source.readString();
        name = source.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
    }

    @Override
    public String toString() {
        return "Node{id='" + id + "', name='" + name + "'}";
    }
}
