package com.xiaomi.xms.wearable.node;

import android.os.Bundle;
import com.xiaomi.xms.wearable.Status;
import com.xiaomi.xms.wearable.node.DataItem;

interface IDataCallback {
    void onResult(in DataItem dataItem, in Bundle values);
    void onFailure(in Status status);
}
