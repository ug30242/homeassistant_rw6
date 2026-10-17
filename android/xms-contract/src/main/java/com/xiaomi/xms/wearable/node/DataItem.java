package com.xiaomi.xms.wearable.node;

import android.os.Parcel;
import android.os.Parcelable;

public final class DataItem implements Parcelable {
    public static final String KEY_CONNECTION_STATUS = "connection_status";
    public static final String KEY_CHARGING_STATUS = "charging_status";
    public static final String KEY_SLEEP_STATUS = "sleep_status";
    public static final String KEY_WEARING_STATUS = "wearing_status";
    public static final String KEY_BATTERY_STATUS = "battery_status";

    public static final DataItem ITEM_CONNECTION = new DataItem(1);
    public static final DataItem ITEM_CHARGING = new DataItem(2);
    public static final DataItem ITEM_SLEEP = new DataItem(3);
    public static final DataItem ITEM_WEARING = new DataItem(4);
    public static final DataItem ITEM_BATTERY = new DataItem(5);

    public static final Creator<DataItem> CREATOR = new Creator<>() {
        @Override
        public DataItem createFromParcel(Parcel source) {
            return new DataItem(source);
        }

        @Override
        public DataItem[] newArray(int size) {
            return new DataItem[size];
        }
    };

    private final int type;

    private DataItem(int type) {
        this.type = type;
    }

    public DataItem(Parcel source) {
        type = source.readInt();
    }

    public int getType() {
        return type;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(type);
    }
}
