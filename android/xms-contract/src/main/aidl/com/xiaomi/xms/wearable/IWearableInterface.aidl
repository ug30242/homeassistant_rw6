package com.xiaomi.xms.wearable;

import com.xiaomi.xms.wearable.IServiceConnectedListener;
import com.xiaomi.xms.wearable.auth.IPermissionCallback;
import com.xiaomi.xms.wearable.auth.IPermissionCheckCallback;
import com.xiaomi.xms.wearable.auth.IPermissionsCheckCallback;
import com.xiaomi.xms.wearable.auth.Permission;
import com.xiaomi.xms.wearable.message.IMessageCallback;
import com.xiaomi.xms.wearable.message.IMessageListener;
import com.xiaomi.xms.wearable.node.DataItem;
import com.xiaomi.xms.wearable.node.IDataCallback;
import com.xiaomi.xms.wearable.node.IDataListener;
import com.xiaomi.xms.wearable.node.INodeCallback;
import com.xiaomi.xms.wearable.node.IWearAppInstalledCallback;
import com.xiaomi.xms.wearable.node.IWearAppLaunchedCallback;
import com.xiaomi.xms.wearable.notify.INotifyCallback;
import com.xiaomi.xms.wearable.notify.NotificationData;

interface IWearableInterface {
    int getServiceApiLevel();
    void checkPermission(String nodeId, in Permission permission, IPermissionCheckCallback callback);
    void checkPermissions(String nodeId, in Permission[] permissions, IPermissionsCheckCallback callback);
    void requestPermission(String nodeId, in Permission[] permissions, IPermissionCallback callback);
    void getConnectedNodes(INodeCallback callback);
    void isWearAppInstalled(String nodeId, IWearAppInstalledCallback callback);
    void launchWearApp(String nodeId, String uri, IWearAppLaunchedCallback callback);
    void query(String nodeId, in DataItem dataItem, IDataCallback callback);
    void subscribe(String nodeId, in DataItem dataItem, IDataListener listener);
    void unsubscribe(String nodeId, in DataItem dataItem);
    void sendMessage(String nodeId, in byte[] message, IMessageCallback callback);
    void addMessageListener(String nodeId, IMessageListener listener, IMessageCallback callback);
    void removeMessageListener(String nodeId, IMessageCallback callback);
    void registerServiceConnectedListener(IServiceConnectedListener callback);
    void sendNotify(String nodeId, in NotificationData notification, INotifyCallback callback);
}
