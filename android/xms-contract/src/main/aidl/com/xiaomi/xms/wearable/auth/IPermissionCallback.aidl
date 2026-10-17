package com.xiaomi.xms.wearable.auth;

import com.xiaomi.xms.wearable.Status;
import com.xiaomi.xms.wearable.auth.Permission;

interface IPermissionCallback {
    void onPermissionGranted(in Permission[] permissions);
    void onFailure(in Status status);
}
