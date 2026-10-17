package com.xiaomi.xms.wearable.notify;

import com.xiaomi.xms.wearable.Status;

interface INotifyCallback {
    void onResult(in Status status);
}
