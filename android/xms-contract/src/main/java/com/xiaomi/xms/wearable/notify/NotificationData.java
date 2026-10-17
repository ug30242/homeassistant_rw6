package com.xiaomi.xms.wearable.notify;

import android.os.Parcel;
import android.os.Parcelable;

public class NotificationData implements Parcelable {
    public static final Creator<NotificationData> CREATOR = new Creator<>() {
        @Override
        public NotificationData createFromParcel(Parcel source) {
            return new NotificationData(source);
        }

        @Override
        public NotificationData[] newArray(int size) {
            return new NotificationData[size];
        }
    };

    public String title;
    public String message;

    public NotificationData() {}

    public NotificationData(Parcel source) {
        title = source.readString();
        message = source.readString();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(title);
        dest.writeString(message);
    }

    public void readFromParcel(Parcel source) {
        title = source.readString();
        message = source.readString();
    }
}
