package com.xiaomi.xms.wearable.message;

interface IMessageListener {
    void onMessageReceived(String nodeId, in byte[] message);
}
