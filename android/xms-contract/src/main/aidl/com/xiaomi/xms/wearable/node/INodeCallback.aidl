package com.xiaomi.xms.wearable.node;

import com.xiaomi.xms.wearable.Status;
import com.xiaomi.xms.wearable.node.Node;

interface INodeCallback {
    void onNodesConnected(in List<Node> nodes);
    void onFailure(in Status status);
}
