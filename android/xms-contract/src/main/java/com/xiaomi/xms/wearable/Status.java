package com.xiaomi.xms.wearable;

import android.os.Parcel;
import android.os.Parcelable;

public final class Status implements Parcelable {
    public static final Status RESULT_SUCCESS = new Status(0);
    public static final Status RESULT_CANCELLED = new Status(-1);
    public static final Status RESULT_TIMEOUT = new Status(-2);
    public static final Status RESULT_INTERRUPTED = new Status(-3);
    public static final Status RESULT_DISCONNECTED = new Status(-4);
    public static final Status RESULT_PACKAGE_NOT_INSTALLED = new Status(-5);
    public static final Status RESULT_SIGNATURE_VERIFY_FAILED = new Status(-6);
    public static final Status RESULT_PERMISSION_DENIED = new Status(-7);
    public static final Status RESULT_APP_NOT_INSTALLED = new Status(-8);

    public static final Creator<Status> CREATOR = new Creator<>() {
        @Override
        public Status createFromParcel(Parcel source) {
            return new Status(source);
        }

        @Override
        public Status[] newArray(int size) {
            return new Status[size];
        }
    };

    private final int code;

    private Status(int code) {
        this.code = code;
    }

    public Status(Parcel source) {
        code = source.readInt();
    }

    public boolean isSuccess() {
        return code == RESULT_SUCCESS.code;
    }

    public int getCode() {
        return code;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(code);
    }
}
