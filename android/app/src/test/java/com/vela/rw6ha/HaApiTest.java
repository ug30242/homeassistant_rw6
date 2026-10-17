package com.vela.rw6ha;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.util.*;
import org.junit.Test;
public class HaApiTest {
    static class Connection extends HttpURLConnection {
        int response=200;String body="{\"message\":\"API running.\"}";boolean disconnected;
        Connection(URL url){super(url);}public void connect(){}public void disconnect(){disconnected=true;}public boolean usingProxy(){return false;}
        public int getResponseCode() throws IOException{return response;}public InputStream getInputStream(){return new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }
    @Test public void authenticatesWithoutRedirectsAndUsesTimeouts() throws Exception {
        List<Connection> calls=new ArrayList<>();HaApi api=new HaApi(url->{Connection c=new Connection(url);calls.add(c);return c;});api.ping(ConfigTest.fixture());Connection c=calls.get(0);assertEquals("https://ha.example.test/api/",c.getURL().toString());assertEquals("Bearer test-token-not-valid",c.getRequestProperty("Authorization"));assertFalse(c.getInstanceFollowRedirects());assertEquals(12000,c.getReadTimeout());assertTrue(c.disconnected);
    }
    @Test public void distinguishesAuthenticationRedirectAndTlsWithoutLeakingServerBody() throws Exception {
        for(int code:List.of(401,403,302,404,500)){
            HaApi api=new HaApi(url->{Connection c=new Connection(url);c.response=code;c.body="sensitive-server-error";return c;});Exception e=assertThrows(Exception.class,()->api.ping(ConfigTest.fixture()));assertFalse(e.getMessage().contains("sensitive"));if(code==401||code==403)assertTrue(e.getMessage().startsWith("인증 오류"));
        }
        HaApi tls=new HaApi(url->new Connection(url){@Override public int getResponseCode() throws IOException{throw new javax.net.ssl.SSLHandshakeException("sensitive-internal-error");}});
        Exception error=assertThrows(Exception.class,()->tls.ping(ConfigTest.fixture()));assertTrue(error.getMessage().startsWith("TLS 인증서"));assertFalse(error.getMessage().contains("sensitive"));
    }
}
