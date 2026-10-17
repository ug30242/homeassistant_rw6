package com.xiaomi.xms.wearable.node;

import com.xiaomi.xms.wearable.Status;

interface IWearAppInstalledCallback {
    void onWearAppInstalled(boolean installed);
    void onFailure(in Status status);
}
