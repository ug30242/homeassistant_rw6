package com.vela.rw6ha;
import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;
public class ConfigTest {
    static Config fixture(){Config c=new Config();c.revision=10;c.server="https://ha.example.test";c.token="test-token-not-valid";c.cleared=false;Config.Entity e=new Config.Entity("light.living_room","거실");e.group="거실";e.favorite=true;c.entities.add(e);return c;}
    @Test public void fullConfigRoundTripsAndCopiesAreIndependent() throws Exception {Config c=fixture(),copy=c.copy();assertEquals(c.payload(),copy.payload());copy.entities.get(0).name="changed";assertEquals("거실",c.entities.get(0).name);copy.entities.clear();assertEquals(0,Config.parse(copy.payload()).entities.size());}
    @Test public void rejectsCredentialsInUrlAndInvalidMetadata(){for(String url:List.of("http://ha.test","https://user:pass@ha.test","https://ha.test/#fragment","https://ha.test:99999","https://ha.test?token=x"))assertThrows(IllegalArgumentException.class,()->Config.url(url,false));Config c=fixture();c.token="bad\nheader";assertThrows(IllegalArgumentException.class,c::validate);c.token="test-token-not-valid";c.entities.add(c.entities.get(0));assertThrows(IllegalArgumentException.class,c::validate);}
    @Test public void explicitHttpAndEraseAreValid() throws Exception {Config c=fixture();c.server="http://192.168.1.2:8123";assertThrows(IllegalArgumentException.class,c::validate);c.allowInsecure=true;c.validate();Config cleared=new Config();cleared.touch();assertTrue(Config.parse(cleared.payload()).cleared);assertTrue(cleared.token.isEmpty());}
    @Test public void protocolChecksumAndRevisionAreStable(){assertEquals("cbf43926",SyncSession.checksum("123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8)));Config c=fixture();c.touch();long v=c.revision;c.touch();assertTrue(c.revision>v);}
}
