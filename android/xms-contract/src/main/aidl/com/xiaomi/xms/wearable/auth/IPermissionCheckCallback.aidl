package com.xiaomi.xms.wearable.auth;

import com.xiaomi.xms.wearable.Status;

interface IPermissionCheckCallback {
    void onPermissionGranted(boolean granted);
    void onFailure(in Status status);
}
