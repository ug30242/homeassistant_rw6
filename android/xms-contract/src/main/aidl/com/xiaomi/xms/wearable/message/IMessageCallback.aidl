package com.xiaomi.xms.wearable.message;

import com.xiaomi.xms.wearable.Status;

interface IMessageCallback {
    void onMessageSent(in Status status);
}
