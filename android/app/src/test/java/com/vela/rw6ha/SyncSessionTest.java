package com.vela.rw6ha;
import static org.junit.Assert.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
public class SyncSessionTest {
    static final class Fixture implements SyncSession.Transport,SyncSession.Events,SyncSession.Timer {
        final List<JSONObject> sent=new ArrayList<>();final List<Runnable> timers=new ArrayList<>();final List<String> status=new ArrayList<>();final SyncSession session=new SyncSession(this,this,this);int successes,failures,opens;
        public void send(String raw,boolean probe){try{sent.add(new JSONObject(raw));}catch(Exception e){throw new AssertionError(e);}}
        public void openWatch(){opens++;}public void status(String s){status.add(s);}public void finished(boolean success){if(success)successes++;else failures++;}public void after(Runnable r,long ms){timers.add(r);}
        void ack(JSONObject request) throws Exception {
            JSONObject p=new JSONObject().put("version",1).put("id",request.getString("id"));String type=request.getString("type");
            if(type.equals("ha-hello"))p.put("type","ha-ready");
            else{String phase=type.substring(3);p.put("type","ha-ack").put("phase",phase).put("transfer",request.getString("transfer")).put("status",phase.equals("commit")?"saved":"ok");if(phase.equals("chunk"))p.put("index",request.getInt("index"));if(phase.equals("commit"))p.put("revision",10);}
            session.receive(new JSONObject().put("data",p.toString()).toString());
        }
    }
    @Test public void noCredentialsBeforeReadyAndOnlyCommitSavedFinishes() throws Exception {
        Fixture f=new Fixture();f.session.start(ConfigTest.fixture());assertEquals(1,f.sent.size());assertFalse(f.sent.get(0).toString().contains("token"));
        int cursor=0;while(f.session.isRunning()){JSONObject p=f.sent.get(cursor++);if(p.getString("type").equals("ha-commit"))assertEquals(0,f.successes);f.ack(p);}
        assertEquals(4,cursor);assertEquals(1,f.successes);assertEquals(0,f.failures);for(Runnable timer:new ArrayList<>(f.timers))timer.run();assertEquals(4,f.sent.size());
    }
    @Test public void boundedRetryUsesSameIdAndRejectsWrongAck() throws Exception {
        Fixture f=new Fixture();f.session.start(ConfigTest.fixture());f.ack(f.sent.get(0));JSONObject begin=f.sent.get(1);int timer=f.timers.size()-1;
        JSONObject wrong=new JSONObject().put("version",1).put("id",Config.requestId()).put("type","ha-ack").put("transfer",begin.getString("transfer")).put("phase","begin").put("status","ok");f.session.receive(wrong.toString());assertEquals(2,f.sent.size());
        f.timers.get(timer).run();assertEquals(begin.toString(),f.sent.get(2).toString());f.timers.get(f.timers.size()-1).run();f.timers.get(f.timers.size()-1).run();assertFalse(f.session.isRunning());assertEquals(1,f.failures);assertEquals(4,f.sent.size());
    }
    @Test public void handshakeTimesOutWithoutLeakingCredentials() throws Exception {
        Fixture f=new Fixture();f.session.start(ConfigTest.fixture());for(int i=0;i<15;i++)f.timers.get(i).run();assertEquals(1,f.opens);assertEquals(1,f.failures);assertEquals(15,f.sent.size());assertTrue(f.sent.stream().allMatch(p->p.optString("type").equals("ha-hello")));
    }
    @Test public void chunkingStaysUnderXmsByteLimitAndCancelledRepliesAreIgnored() throws Exception {
        Fixture f=new Fixture();Config c=ConfigTest.fixture();c.token="x".repeat(3500);f.session.start(c);int cursor=0;while(f.session.isRunning())f.ack(f.sent.get(cursor++));assertTrue(cursor>4);for(JSONObject p:f.sent)assertTrue(p.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=2048);
        f.session.start(c);JSONObject hello=f.sent.get(f.sent.size()-1);f.session.cancel();f.ack(hello);assertEquals(1,f.successes);assertEquals(1,f.failures);
    }
}
