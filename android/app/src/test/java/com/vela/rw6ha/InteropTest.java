package com.vela.rw6ha;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
/** Export actual Java producer packets; Node consumes them through the watch receiver. */
public class InteropTest {
    @Test public void exportsActualChunkedJavaProducer() throws Exception {
        SyncSessionTest.Fixture f=new SyncSessionTest.Fixture();Config config=ConfigTest.fixture();config.token="test-token-not-valid-"+"x".repeat(3000);f.session.start(config);
        int cursor=0;while(f.session.isRunning())f.ack(f.sent.get(cursor++));assertEquals(1,f.successes);
        JSONArray packets=new JSONArray();for(JSONObject p:f.sent)packets.put(p);
        String output=System.getProperty("rw6.interop.output");assertNotNull(output);Path path=Path.of(output);Files.createDirectories(path.getParent());Files.write(path,new JSONObject().put("packets",packets).put("config",config.json()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
