package com.vela.rw6ha;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import com.xiaomi.xms.wearable.IServiceConnectedListener;
import com.xiaomi.xms.wearable.IWearableInterface;
import com.xiaomi.xms.wearable.Status;
import com.xiaomi.xms.wearable.auth.Permission;
import com.xiaomi.xms.wearable.auth.IPermissionCallback;
import com.xiaomi.xms.wearable.auth.IPermissionCheckCallback;
import com.xiaomi.xms.wearable.message.IMessageCallback;
import com.xiaomi.xms.wearable.message.IMessageListener;
import com.xiaomi.xms.wearable.node.INodeCallback;
import com.xiaomi.xms.wearable.node.IWearAppInstalledCallback;
import com.xiaomi.xms.wearable.node.IWearAppLaunchedCallback;
import com.xiaomi.xms.wearable.node.Node;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small foreground client for the XMS 1.4 public Binder contract. No network API. */
final class XmsClient {
    interface Events {
        void nodes(List<Node> nodes);
        void ready(String name);
        void error(String message);
        void message(String json);
        void authorizing();
    }
    private final Context context;
    private final Events events;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    // XMS registers the caller's live Binder in addition to its message listener.
    // Keep it alive for the client lifetime, including while the watch replies.
    private final IServiceConnectedListener connectionListener = new IServiceConnectedListener.Stub() {
        @Override public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == IBinder.FIRST_CALL_TRANSACTION) {
                // XMS 1.4 sends a one-way registration acknowledgement on slot 1.
                // Its Java interface has no method; the generated empty AIDL Stub
                // alone would reject this transaction. Match the public SDK wire ABI.
                data.enforceInterface("com.xiaomi.xms.wearable.IServiceConnectedListener");
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    };
    private IWearableInterface service;
    private ServiceConnection binding;
    private String nodeId;
    private boolean ready;
    private volatile int generation;

    XmsClient(Context context, Events events) { this.context = context; this.events = events; }
    boolean isReady() { return ready; }
    private void post(int epoch, Runnable action) {
        main.post(() -> { if (epoch == generation && binding != null) action.run(); });
    }
    private void fail(int epoch, String text) { post(epoch, () -> { ready = false; events.error(text); }); }
    private String explain(Status status) {
        int code = status == null ? -999 : status.getCode();
        switch (code) {
            case -6: return "앱 서명이 다릅니다. 같은 릴리즈의 APK와 RPK를 설치하세요.";
            case -7: return "Mi Fitness에서 동반 앱 권한을 허용하세요.";
            case -5: case -8: return "워치에 같은 릴리즈의 Home Assistant RPK를 설치하세요.";
            case -4: return "워치 연결이 끊겼습니다. Mi Fitness를 확인하세요.";
            default: return "Xiaomi 통신 오류 (" + code + "). 다시 연결하세요.";
        }
    }
    void connect() {
        disconnect();
        final int epoch = generation;
        Intent intent = null;
        for (String pkg : new String[]{"com.mi.health", "com.xiaomi.wearable"}) {
            Intent candidate = new Intent("com.xiaomi.wearable.XMS_WEARABLE_SERVICE").setPackage(pkg);
            if (context.getPackageManager().resolveService(candidate, 0) != null) { intent = candidate; break; }
        }
        if (intent == null) {
            events.error("Mi Fitness(중국판 운동건강)에 워치를 연결해야 합니다. Notify만으로는 전송할 수 없습니다.");
            return;
        }
        binding = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                if (epoch != generation) return;
                service = IWearableInterface.Stub.asInterface(binder);
                IWearableInterface remote = service;
                worker.execute(() -> {
                    try {
                        if(epoch != generation)return;
                        remote.registerServiceConnectedListener(connectionListener);
                        if(epoch != generation)return;
                        remote.getConnectedNodes(new INodeCallback.Stub() {
                            @Override public void onNodesConnected(List<Node> nodes) {
                                post(epoch, () -> {
                                    if (nodes == null || nodes.isEmpty()) events.error("연결된 워치가 없습니다. Mi Fitness에서 연결 후 다시 시도하세요.");
                                    else events.nodes(nodes);
                                });
                            }
                            @Override public void onFailure(Status status) { fail(epoch, explain(status)); }
                        });
                    } catch (Exception e) { fail(epoch, "Mi Fitness 서비스에 연결하지 못했습니다."); }
                });
            }
            @Override public void onServiceDisconnected(ComponentName name) { fail(epoch, "Mi Fitness 연결이 끊겼습니다. 다시 연결하세요."); }
            @Override public void onBindingDied(ComponentName name) { fail(epoch, "Mi Fitness 서비스를 다시 연결하세요."); }
            @Override public void onNullBinding(ComponentName name) { fail(epoch, "Mi Fitness가 통신 서비스를 제공하지 않습니다."); }
        };
        try {
            if (!context.bindService(intent, binding, Context.BIND_AUTO_CREATE)) {
                binding = null; events.error("Mi Fitness 서비스를 시작하지 못했습니다.");
            }
        } catch (SecurityException e) { binding = null; events.error("Mi Fitness 서비스 접근이 거부되었습니다."); }
        main.postDelayed(() -> {
            if (epoch == generation && binding != null && !ready) {
                disconnect(); events.error("XMS 연결 시간 초과. Mi Fitness 권한과 워치 연결 후 재시도하세요.");
            }
        }, 30000);
    }
    void select(Node node) {
        if (service == null || node == null || node.id == null) return;
        final int epoch = generation;
        final IWearableInterface remote = service;
        nodeId = node.id;
        worker.execute(() -> {
            try {
                if(epoch != generation)return;
                remote.checkPermission(node.id,Permission.DEVICE_MANAGER,new IPermissionCheckCallback.Stub() {
                    @Override public void onPermissionGranted(boolean granted) {
                        post(epoch,() -> {
                            if (granted) checkInstalled(remote,node,epoch);
                            else requestPermission(remote,node,epoch);
                        });
                    }
                    @Override public void onFailure(Status status) { fail(epoch,explain(status)); }
                });
            } catch (Exception e) { fail(epoch,"Mi Fitness 권한을 확인하지 못했습니다."); }
        });
    }
    private void requestPermission(IWearableInterface remote,Node node,int epoch) {
        events.authorizing();
        worker.execute(() -> {
            try {
                if(epoch != generation)return;
                remote.requestPermission(node.id,new Permission[]{Permission.DEVICE_MANAGER},new IPermissionCallback.Stub() {
                    @Override public void onPermissionGranted(Permission[] permissions) {
                        boolean allowed=false;
                        if(permissions!=null)for(Permission p:permissions)if(p!=null && Permission.DEVICE_MANAGER.getName().equals(p.getName()))allowed=true;
                        if(allowed)post(epoch,() -> checkInstalled(remote,node,epoch));
                        else fail(epoch,"Mi Fitness의 동반 앱 기기 통신 권한이 필요합니다.");
                    }
                    @Override public void onFailure(Status status) { fail(epoch,explain(status)); }
                });
            } catch (Exception e) { fail(epoch,"Mi Fitness 권한 요청에 실패했습니다."); }
        });
    }
    private void checkInstalled(IWearableInterface remote,Node node,int epoch) {
        worker.execute(() -> {
            try {
                if(epoch != generation)return;
                remote.isWearAppInstalled(node.id, new IWearAppInstalledCallback.Stub() {
                    @Override public void onWearAppInstalled(boolean installed) {
                        if (!installed) { fail(epoch, "워치에 Home Assistant RPK를 먼저 설치하세요."); return; }
                        post(epoch, () -> register(remote, node, epoch));
                    }
                    @Override public void onFailure(Status status) { fail(epoch, explain(status)); }
                });
            } catch (Exception e) { fail(epoch, "워치 앱을 확인할 수 없습니다. 다시 연결하세요."); }
        });
    }
    private void register(IWearableInterface remote, Node node, int epoch) {
        worker.execute(() -> {
            try {
                if(epoch != generation)return;
                remote.addMessageListener(node.id, new IMessageListener.Stub() {
                    @Override public void onMessageReceived(String id, byte[] bytes) {
                        if (!node.id.equals(id) || bytes == null || bytes.length > 2048) return;
                        String text = new String(bytes, StandardCharsets.UTF_8);
                        post(epoch, () -> events.message(text));
                    }
                }, new IMessageCallback.Stub() {
                    @Override public void onMessageSent(Status status) {
                        if (status != null && status.isSuccess()) post(epoch, () -> { ready = true; events.ready(node.name); });
                        else fail(epoch, explain(status));
                    }
                });
            } catch (Exception e) { fail(epoch, "수신 확인을 등록하지 못했습니다. 다시 연결하세요."); }
        });
    }
    void openWatch() {
        if (!ready) return;
        final int epoch = generation;
        final IWearableInterface remote = service;
        final String id = nodeId;
        worker.execute(() -> {
            try {
                if(epoch != generation)return;
                remote.launchWearApp(id, "/receive", new IWearAppLaunchedCallback.Stub() {
                    @Override public void onWearAppLaunched(Status status) {
                        // The hello/ready handshake also works if the user opens the watch manually.
                    }
                });
            } catch (Exception ignored) { /* SyncSession times out with the manual-open instruction. */ }
        });
    }
    void send(String json, boolean probe) {
        if (!ready) { events.error("워치를 먼저 연결하세요."); return; }
        final int epoch = generation;
        final IWearableInterface remote = service;
        final String id = nodeId;
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 2048) { events.error("설정 데이터가 너무 큽니다."); return; }
        worker.execute(() -> {
            try {
                if(epoch != generation)return;
                remote.sendMessage(id, bytes, new IMessageCallback.Stub() {
                    @Override public void onMessageSent(Status status) {
                        if (!probe && (status == null || !status.isSuccess())) fail(epoch, explain(status));
                    }
                });
            } catch (Exception e) { if (!probe) fail(epoch, "전송 실패. 다시 동기화하세요."); }
            finally { java.util.Arrays.fill(bytes, (byte) 0); }
        });
    }
    void disconnect() {
        generation++; ready = false;
        main.removeCallbacksAndMessages(null);
        IWearableInterface old = service;
        String oldId = nodeId;
        service = null; nodeId = null;
        if (old != null && oldId != null) worker.execute(() -> {
            try { old.removeMessageListener(oldId, new IMessageCallback.Stub() {
                @Override public void onMessageSent(Status ignored) {}
            }); } catch (Exception ignored) {}
        });
        if (binding != null) {
            try { context.unbindService(binding); } catch (IllegalArgumentException ignored) {}
            binding = null;
        }
    }
    void destroy() { disconnect(); worker.shutdown(); }
}
