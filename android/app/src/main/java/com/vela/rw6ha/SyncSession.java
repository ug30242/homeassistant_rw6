package com.vela.rw6ha;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;
import org.json.*;

/** Stop-and-wait, bounded retries, correlated ACKs, full-config commit acknowledged after durable save. */
final class SyncSession {
    interface Transport {void send(String packet,boolean probe);void openWatch();}
    interface Events {void status(String text);void finished(boolean success);}
    interface Timer {void after(Runnable action,long millis);}
    private final Transport transport;private final Events events;private final Timer timer;
    private final List<String> chunks=new ArrayList<>();
    private boolean running;private int epoch,attempts,step,totalBytes;private long revision;
    private String helloId,requestId,transfer,packet,digest,phase;
    SyncSession(Transport transport,Events events,Timer timer){this.transport=transport;this.events=events;this.timer=timer;}
    boolean isRunning(){return running;}
    static String checksum(byte[] bytes){CRC32 crc=new CRC32();crc.update(bytes);return String.format(Locale.ROOT,"%08x",crc.getValue());}
    void start(Config config){
        if(running)return;
        try{
            byte[] bytes=config.payload().getBytes(StandardCharsets.UTF_8);totalBytes=bytes.length;revision=config.revision;digest=checksum(bytes);
            chunks.clear();for(int i=0;i<bytes.length;i+=768)chunks.add(Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes,i,Math.min(bytes.length,i+768))));
            Arrays.fill(bytes,(byte)0);
            epoch++;running=true;helloId=Config.requestId();transfer=Config.requestId();requestId=null;attempts=0;step=-1;
            events.status("워치 준비 응답 확인 중…");hello(epoch);
        }catch(Exception e){chunks.clear();events.status("설정 형식 또는 크기를 확인하세요.");events.finished(false);}
    }
    private void hello(int current){
        if(!running||current!=epoch||helloId==null)return;
        if(++attempts>15){fail("워치 준비 응답 없음. 워치의 Phone sync 화면과 Mi Fitness를 확인하세요. 1006이면 같은 APK/RPK와 연결 권한을 확인하세요.");return;}
        if(attempts==3)transport.openWatch();
        try{transport.send(new JSONObject().put("type","ha-hello").put("version",1).put("id",helloId).toString(),true);}
        catch(Exception e){fail("연결 확인 실패.");return;}
        timer.after(()->hello(current),1000);
    }
    void receive(String raw){
        if(!running||raw==null||raw.length()>2048)return;
        try{
            JSONObject p=decode(raw);if(p.optInt("version")!=1)return;
            if("ha-ready".equals(p.optString("type"))&&helloId!=null&&helloId.equals(p.optString("id"))){helloId=null;step=-1;next();return;}
            if(!"ha-ack".equals(p.optString("type"))||requestId==null||!requestId.equals(p.optString("id"))||!transfer.equals(p.optString("transfer"))||!phase.equals(p.optString("phase")))return;
            if("chunk".equals(phase)&&p.optInt("index",-1)!=step)return;
            String status=p.optString("status");
            if("busy".equals(status))return; // Keep the same request alive until the existing retry deadline.
            if("commit".equals(phase)){
                if(!"saved".equals(status)||p.optLong("revision",-1)!=revision){fail("워치 저장 확인 실패. 다시 동기화하세요.");return;}
                clear();events.status("동기화 완료: 워치 저장 응답 확인. Android 앱을 종료해도 됩니다.");events.finished(true);return;
            }
            if(!"ok".equals(status)){
                fail("stale".equals(status)?"워치에 더 최신 설정이 있습니다. Android에서 설정을 저장한 뒤 재시도하세요.":"워치가 설정을 받지 못했습니다. 기존 설정은 유지됩니다.");return;
            }
            requestId=null;step++;next();
        }catch(Exception ignored){}
    }
    private void next() throws JSONException {
        requestId=Config.requestId();attempts=0;
        JSONObject p=new JSONObject().put("version",1).put("id",requestId).put("transfer",transfer);
        if(step<0){phase="begin";p.put("type","ha-begin").put("revision",revision).put("total",chunks.size()).put("bytes",totalBytes).put("checksum",digest);}
        else if(step<chunks.size()){phase="chunk";p.put("type","ha-chunk").put("index",step).put("data",chunks.get(step));}
        else{phase="commit";p.put("type","ha-commit");}
        packet=p.toString();events.status("commit".equals(phase)?"워치 영구 저장 확인 중…":"설정 전송 "+Math.max(0,step)+" / "+chunks.size());
        send(epoch,requestId);
    }
    private void send(int current,String id){
        if(!running||current!=epoch||!id.equals(requestId))return;
        if(++attempts>3){fail("워치 응답 시간 초과. 다시 동기화하세요. 저장 완료 여부는 워치에서 확인하세요.");return;}
        transport.send(packet,false);
        timer.after(()->send(current,id),"commit".equals(phase)?15000:5000);
    }
    private JSONObject decode(String raw) throws Exception {
        Object value=raw;
        for(int depth=0;depth<6;depth++){
            if(value instanceof String){String s=(String)value;if(s.length()>2048)throw new IllegalArgumentException();value=new JSONTokener(s).nextValue();continue;}
            if(!(value instanceof JSONObject))break;JSONObject o=(JSONObject)value;if(o.has("type"))return o;value=o.opt("data");
        }
        throw new IllegalArgumentException();
    }
    void fail(String message){if(!running)return;clear();events.status(message);events.finished(false);}
    void cancel(){if(running){clear();events.status("동기화 취소됨. 워치 저장 상태를 확인하세요.");events.finished(false);}}
    private void clear(){epoch++;running=false;helloId=null;requestId=null;packet=null;transfer=null;chunks.clear();}
}
