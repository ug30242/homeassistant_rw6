package com.xiaomi.xms.wearable.auth;

import com.xiaomi.xms.wearable.Status;

interface IPermissionsCheckCallback {
    void onPermissionGranted(in boolean[] granted);
    void onFailure(in Status status);
}
