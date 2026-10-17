package com.vela.rw6ha;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Foreground requests only. Platform TLS trust is never overridden; redirects never receive a token. */
final class HaApi {
    interface ConnectionFactory {HttpURLConnection open(URL url) throws IOException;}
    private final ConnectionFactory factory;
    HaApi(){this(url->(HttpURLConnection)url.openConnection());}
    HaApi(ConnectionFactory factory){this.factory=factory;}
    String get(Config config,String path) throws IOException {
        String base=Config.url(config.server,config.allowInsecure);
        HttpURLConnection connection=factory.open(new URL(base+path));
        try{
            connection.setConnectTimeout(12000);connection.setReadTimeout(12000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Authorization","Bearer "+config.token);connection.setRequestProperty("Accept","application/json");
            int code=connection.getResponseCode();
            if(code==401||code==403)throw new IOException("인증 오류: Home Assistant 토큰을 확인하세요.");
            if(code>=300&&code<400)throw new IOException("리디렉션을 허용하지 않습니다. 최종 서버 주소를 입력하세요.");
            if(code==404)throw new IOException("API 주소를 찾을 수 없습니다 (404).");
            if(code<200||code>=300)throw new IOException("서버 HTTP 오류 ("+code+").");
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1){if(out.size()+count>2*1024*1024)throw new IOException("서버 응답이 너무 큽니다.");out.write(buffer,0,count);}
                return out.toString(StandardCharsets.UTF_8.name());
            }
        }catch(javax.net.ssl.SSLException e){throw new IOException("TLS 인증서 검증 실패. 서버 인증서를 확인하세요.");}
        catch(SocketTimeoutException e){throw new IOException("서버 연결 시간이 초과되었습니다.");}
        catch(UnknownHostException e){throw new IOException("서버 이름을 찾을 수 없습니다.");}
        finally{connection.disconnect();}
    }
    void ping(Config config) throws Exception {
        if(!"API running.".equals(new JSONObject(get(config,"/api/")).optString("message")))throw new IOException("Home Assistant API 응답이 아닙니다.");
    }
    JSONArray states(Config config) throws Exception {return new JSONArray(get(config,"/api/states"));}
}
