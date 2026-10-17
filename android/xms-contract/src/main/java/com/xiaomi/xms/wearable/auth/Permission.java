package com.xiaomi.xms.wearable.auth;

import android.os.Parcel;
import android.os.Parcelable;

public final class Permission implements Parcelable {
    public static final Permission DEVICE_MANAGER = new Permission("data_manager");
    public static final Permission NOTIFY = new Permission("notify");

    public static final Creator<Permission> CREATOR = new Creator<>() {
        @Override
        public Permission createFromParcel(Parcel source) {
            return new Permission(source);
        }

        @Override
        public Permission[] newArray(int size) {
            return new Permission[size];
        }
    };

    private final String name;

    private Permission(String name) {
        this.name = name;
    }

    public Permission(Parcel source) {
        name = source.readString();
    }

    public String getName() {
        return name;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
    }
}
