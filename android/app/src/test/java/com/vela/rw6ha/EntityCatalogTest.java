package com.vela.rw6ha;

import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class EntityCatalogTest {
    static String manyEntities(){
        StringBuilder json=new StringBuilder("[");String large="x".repeat(1200);
        for(int i=0;i<2600;i++){
            if(i>0)json.append(',');
            json.append("{\"entity_id\":\"sensor.temp_").append(i).append("\",\"state\":\"22.1\",\"attributes\":{\"history\":[{\"payload\":\"").append(large).append("\"}],\"friendly_name\":\"온도 ").append(i).append("\"}}");
        }
        for(int i=0;i<55;i++)json.append(",{\"entity_id\":\"light.lamp_").append(i).append("\",\"attributes\":{\"friendly_name\":\"Lamp ").append(i).append("\"}}");
        return json.append(",{\"entity_id\":\"climate.unsupported\",\"attributes\":{\"data\":[1,2,{\"nested\":true}]}}]").toString();
    }
    @Test public void readsMoreThanTwoMiBAndRetainsOnlySupportedEntityMetadata() throws Exception {
        byte[] encoded=manyEntities().getBytes(StandardCharsets.UTF_8);assertTrue(encoded.length>2*1024*1024);
        HaApiTest.Connection connection=new HaApiTest.Connection(new URL("https://ha.example.test/api/states")){
            @Override public InputStream getInputStream(){return new ByteArrayInputStream(encoded);}
        };
        EntityCatalog result=new HaApi(url->connection).states(ConfigTest.fixture());
        assertEquals(2656,result.totalCount);assertEquals(2655,result.supportedCount);
        assertEquals(2600,result.matching("sensor","").size());assertEquals(55,result.matching("light","").size());
        assertEquals("온도 2599",result.matching("sensor","temp_2599").get(0).name);
        assertEquals(30000,connection.getReadTimeout());assertEquals("gzip",connection.getRequestProperty("Accept-Encoding"));
        assertEquals("Bearer test-token-not-valid",connection.getRequestProperty("Authorization"));assertTrue(connection.disconnected);
    }
    @Test public void decodesGzipAndHandlesOutOfOrderNullAndUnknownFields() throws Exception {
        String json="[{\"attributes\":{\"friendly_name\":\"거실\\n조명\",\"ignored\":{\"list\":[1,null,true]}},\"entity_id\":\"light.living\"},{\"attributes\":null,\"entity_id\":\"sensor.temp\"},{\"entity_id\":\"light.living\"},{\"entity_id\":\"sensor.bad-id\"}]";
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(GZIPOutputStream gzip=new GZIPOutputStream(out)){gzip.write(json.getBytes(StandardCharsets.UTF_8));}
        HaApiTest.Connection connection=new HaApiTest.Connection(new URL("https://ha.example.test")){
            @Override public String getContentEncoding(){return "gzip";}
            @Override public InputStream getInputStream(){return new ByteArrayInputStream(out.toByteArray());}
        };
        EntityCatalog result=new HaApi(url->connection).states(ConfigTest.fixture());assertEquals(4,result.totalCount);assertEquals(2,result.supportedCount);
        assertEquals("거실 조명",result.matching("light","").get(0).name);assertEquals("sensor.temp",result.matching("sensor","").get(0).name);
    }
    @Test public void searchesEntireDomainBeyondOldDisplayLimitWithoutLosingOtherGroups() throws Exception {
        EntityCatalog catalog=EntityCatalog.read(new StringReader(manyEntities()));
        assertEquals(1,catalog.matching("sensor","  TEMP_2599  ").size());assertEquals(1,catalog.matching("light","lamp 54").size());
        assertTrue(catalog.matching("light","온도").isEmpty());assertEquals(55,catalog.matching("light","").size());
        assertThrows(UnsupportedOperationException.class,()->catalog.matching("light","").clear());
    }
    @Test public void rejectsTruncatedAndTrailingResponsesInsteadOfPublishingPartialCatalog() {
        for(String json:Arrays.asList("[{\"entity_id\":\"sensor.temp\"}","[] {}","{\"message\":\"not an array\"}"))assertThrows(Exception.class,()->EntityCatalog.read(new StringReader(json)));
    }
    @Test public void boundsDecodedBytesAndEntityCountAndClosesFailedRequest() throws Exception {
        try(InputStream limited=new HaApi.LimitedInputStream(new ByteArrayInputStream(new byte[33]),32,1000)){
            assertEquals(32,limited.read(new byte[32]));assertThrows(IOException.class,limited::read);
        }
        StringBuilder json=new StringBuilder("[");for(int i=0;i<=EntityCatalog.MAX_ENTITIES;i++){if(i>0)json.append(',');json.append("{}");}json.append(']');
        IOException error=assertThrows(IOException.class,()->EntityCatalog.read(new StringReader(json.toString())));assertTrue(error.getMessage().contains("50,000"));
        HaApiTest.Connection connection=new HaApiTest.Connection(new URL("https://ha.example.test")){
            @Override public InputStream getInputStream(){return new ByteArrayInputStream("[{".getBytes(StandardCharsets.UTF_8));}
        };
        assertThrows(Exception.class,()->new HaApi(url->connection).states(ConfigTest.fixture()));assertTrue(connection.disconnected);
    }
}
