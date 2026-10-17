package com.xiaomi.xms.wearable.node;

import android.os.Bundle;
import com.xiaomi.xms.wearable.node.DataItem;

interface IDataListener {
    void onDataChanged(String nodeId, in DataItem dataItem, in Bundle values);
}
