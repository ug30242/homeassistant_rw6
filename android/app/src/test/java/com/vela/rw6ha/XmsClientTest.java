package com.vela.rw6ha;

import static org.junit.Assert.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import com.xiaomi.xms.wearable.IServiceConnectedListener;
import com.xiaomi.xms.wearable.IWearableInterface;
import com.xiaomi.xms.wearable.Status;
import com.xiaomi.xms.wearable.auth.*;
import com.xiaomi.xms.wearable.message.*;
import com.xiaomi.xms.wearable.node.*;
import com.xiaomi.xms.wearable.notify.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/** Exercises generated AIDL Parcels, registration and reverse delivery, not just JSON. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class XmsClientTest {
    private static final String ACTION="com.xiaomi.wearable.XMS_WEARABLE_SERVICE";
    private static final String PACKAGE="com.mi.health";
    private static final Node WATCH=new Node("watch-6","Redmi Watch 6");

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+5_000_000_000L;
        while(System.nanoTime()<deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if(condition.getAsBoolean())return;
            Thread.sleep(5);
        }
        fail("Timed out waiting for XMS worker/main callbacks");
    }

    private static final class Service extends IWearableInterface.Stub {
        final List<String> calls=Collections.synchronizedList(new ArrayList<>());
        volatile boolean registered, rejectRegistration;
        volatile IServiceConnectedListener registration;
        volatile IMessageListener listener;
        volatile int reverseFailures, removed;
        long revision;
        final List<String> packets=Collections.synchronizedList(new ArrayList<>());

        // Force the generated Proxy/Stub wire path instead of a local Java shortcut.
        final IBinder wire=new Binder() {
            @Override public IInterface queryLocalInterface(String descriptor){return null;}
            @Override public boolean onTransact(int code,Parcel data,Parcel reply,int flags) throws RemoteException {
                return Service.this.onTransact(code,data,reply,flags);
            }
        };
        @Override public void registerServiceConnectedListener(IServiceConnectedListener callback) throws RemoteException {
            calls.add("register");
            if(rejectRegistration)throw new RemoteException("Registration rejected");
            registration=callback;
            Parcel ack=Parcel.obtain();
            try {
                ack.writeInterfaceToken("com.xiaomi.xms.wearable.IServiceConnectedListener");
                registered=callback.asBinder().transact(IBinder.FIRST_CALL_TRANSACTION,ack,null,IBinder.FLAG_ONEWAY);
            } finally {ack.recycle();}
        }
        @Override public void getConnectedNodes(INodeCallback callback) throws RemoteException {
            calls.add("nodes");callback.onNodesConnected(List.of(WATCH));
        }
        @Override public void checkPermission(String id,Permission permission,IPermissionCheckCallback callback) throws RemoteException {
            calls.add("permission");callback.onPermissionGranted(true);
        }
        @Override public void isWearAppInstalled(String id,IWearAppInstalledCallback callback) throws RemoteException {
            calls.add("installed");callback.onWearAppInstalled(true);
        }
        @Override public void addMessageListener(String id,IMessageListener value,IMessageCallback callback) throws RemoteException {
            calls.add("listen");listener=value;callback.onMessageSent(Status.RESULT_SUCCESS);
        }
        @Override public void sendMessage(String id,byte[] bytes,IMessageCallback callback) throws RemoteException {
            String raw=new String(bytes,StandardCharsets.UTF_8);packets.add(raw);
            callback.onMessageSent(Status.RESULT_SUCCESS); // Outbound succeeds even without registration.
            if(!registered){reverseFailures++;return;} // Watch send would fail with 1006.
            try {
                JSONObject packet=new JSONObject(raw);
                String type=packet.getString("type");
                JSONObject response=new JSONObject().put("version",1).put("id",packet.getString("id"));
                if("ha-hello".equals(type))response.put("type","ha-ready");
                else {
                    String phase=type.substring(3);
                    if(type.equals("ha-begin"))revision=packet.getLong("revision");
                    response.put("type","ha-ack").put("phase",phase).put("transfer",packet.getString("transfer")).put("status",phase.equals("commit")?"saved":"ok");
                    if(phase.equals("commit"))response.put("revision",revision);
                    if(phase.equals("chunk"))response.put("index",packet.getInt("index"));
                }
                listener.onMessageReceived(id,new JSONObject().put("data",response.toString()).toString().getBytes(StandardCharsets.UTF_8));
            } catch(RemoteException e){throw e;}
            catch(Exception e){throw new AssertionError(e);}
        }
        @Override public void removeMessageListener(String id,IMessageCallback callback) throws RemoteException {
            removed++;callback.onMessageSent(Status.RESULT_SUCCESS);
        }
        @Override public int getServiceApiLevel(){return 1;}
        @Override public void checkPermissions(String id,Permission[] permissions,IPermissionsCheckCallback callback){throw new AssertionError();}
        @Override public void requestPermission(String id,Permission[] permissions,IPermissionCallback callback){throw new AssertionError();}
        @Override public void launchWearApp(String id,String uri,IWearAppLaunchedCallback callback){throw new AssertionError("Already open receiver must answer first");}
        @Override public void query(String id,DataItem item,IDataCallback callback){throw new AssertionError();}
        @Override public void subscribe(String id,DataItem item,IDataListener listener){throw new AssertionError();}
        @Override public void unsubscribe(String id,DataItem item){throw new AssertionError();}
        @Override public void sendNotify(String id,NotificationData data,INotifyCallback callback){throw new AssertionError();}
    }

    private static final class Fixture implements AutoCloseable,XmsClient.Events,SyncSession.Events {
        final Service service=new Service();
        final List<String> messages=new ArrayList<>(),errors=new ArrayList<>();
        final XmsClient client;
        final SyncSession session;
        int ready,successes,failures,unbound;
        Fixture() {
            Context app=RuntimeEnvironment.getApplication();
            ResolveInfo resolved=new ResolveInfo();resolved.serviceInfo=new ServiceInfo();
            resolved.serviceInfo.packageName=PACKAGE;resolved.serviceInfo.name=PACKAGE+".XmsService";
            Shadows.shadowOf(app.getPackageManager()).addResolveInfoForIntent(new Intent(ACTION).setPackage(PACKAGE),resolved);
            Context context=new ContextWrapper(app) {
                @Override public boolean bindService(Intent intent,ServiceConnection connection,int flags) {
                    connection.onServiceConnected(new ComponentName(PACKAGE,PACKAGE+".XmsService"),service.wire);return true;
                }
                @Override public void unbindService(ServiceConnection connection){unbound++;}
            };
            client=new XmsClient(context,this);
            session=new SyncSession(new SyncSession.Transport() {
                @Override public void send(String packet,boolean probe){client.send(packet,probe);}
                @Override public void openWatch(){client.openWatch();}
            },this,(action,millis)->{});
        }
        @Override public void nodes(List<Node> nodes){client.select(nodes.get(0));}
        @Override public void ready(String name){ready++;}
        @Override public void error(String message){errors.add(message);}
        @Override public void message(String json){messages.add(json);session.receive(json);}
        @Override public void authorizing(){fail("Permission is already granted");}
        @Override public void status(String text){}
        @Override public void finished(boolean success){if(success)successes++;else failures++;}
        void connect() throws Exception {client.connect();await(()->ready>0 || !errors.isEmpty());}
        @Override public void close(){client.destroy();}
    }

    @Test public void registersLiveBinderAndAcceptsTheOfficialAckBeforeDiscoveringNodes() throws Exception {
        try(Fixture f=new Fixture()) {
            f.connect();assertTrue(f.errors.toString(),f.errors.isEmpty());assertTrue(f.client.isReady());
            assertEquals(List.of("register","nodes","permission","installed","listen"),f.service.calls);
            assertNotNull(f.service.registration);assertTrue("Slot-1 registration acknowledgement must be accepted",f.service.registered);
        }
    }
    @Test public void completesReadyAndSavedRoundTripThroughTheNativeTransport() throws Exception {
        try(Fixture f=new Fixture()) {
            f.connect();
            com.vela.rw6ha.Config c=ConfigTest.fixture(); f.session.start(c);
            await(()->f.successes>0 || !f.errors.isEmpty());
            assertEquals(1,f.successes);assertEquals(0,f.failures);assertEquals(0,f.service.reverseFailures);
            assertEquals(4,f.service.packets.size());assertEquals(4,f.messages.size());assertFalse(f.session.isRunning());
            assertFalse(f.service.packets.get(0).contains("secret"));
            assertEquals("ha-begin",new JSONObject(f.service.packets.get(1)).getString("type"));
        }
    }
    @Test public void registrationFailureCannotAdvertiseReadinessOrTransmitAccounts() throws Exception {
        try(Fixture f=new Fixture()) {
            f.service.rejectRegistration=true;f.connect();
            assertFalse(f.client.isReady());assertEquals(0,f.ready);assertEquals(1,f.errors.size());
            assertEquals(List.of("register"),f.service.calls);assertTrue(f.service.packets.isEmpty());
        }
    }
    @Test public void reconnectRegistersAgainAndDiscardsRepliesFromTheOldSession() throws Exception {
        try(Fixture f=new Fixture()) {
            f.connect();IMessageListener previous=f.service.listener;
            f.client.connect();await(()->f.ready==2);assertEquals(1,f.unbound);
            assertEquals(2,Collections.frequency(f.service.calls,"register"));assertEquals(1,f.service.removed);
            previous.onMessageReceived(WATCH.id,"old".getBytes(StandardCharsets.UTF_8));
            f.service.listener.onMessageReceived(WATCH.id,"current".getBytes(StandardCharsets.UTF_8));
            await(()->!f.messages.isEmpty());assertEquals(List.of("current"),f.messages);
        }
    }
}
