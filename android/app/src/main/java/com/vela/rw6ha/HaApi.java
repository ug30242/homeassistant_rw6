package com.vela.rw6ha;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import org.json.*;

/** Foreground requests only. Platform TLS trust is never overridden; redirects never receive a token. */
final class HaApi {
    static final long MAX_STATES_BYTES=128L*1024*1024;
    interface ConnectionFactory {HttpURLConnection open(URL url) throws IOException;}
    private interface ResponseReader<T> {T read(InputStream input) throws IOException;}
    private final ConnectionFactory factory;
    HaApi(){this(url->(HttpURLConnection)url.openConnection());}
    HaApi(ConnectionFactory factory){this.factory=factory;}
    String get(Config config,String path) throws IOException {
        return read(config,path,2L*1024*1024,input->{
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))!=-1)out.write(buffer,0,count);
            return out.toString(StandardCharsets.UTF_8.name());
        });
    }
    private <T> T read(Config config,String path,long limit,ResponseReader<T> parser) throws IOException {
        String base=Config.url(config.server,config.allowInsecure);
        boolean bulk=path.equals("/api/states");
        HttpURLConnection connection=factory.open(new URL(base+path));
        try{
            connection.setConnectTimeout(12000);connection.setReadTimeout(bulk?30000:12000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Authorization","Bearer "+config.token);connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("Accept-Encoding","gzip");
            int code=connection.getResponseCode();
            if(code==401||code==403)throw new IOException("인증 오류: Home Assistant 토큰을 확인하세요.");
            if(code>=300&&code<400)throw new IOException("리디렉션을 허용하지 않습니다. 최종 서버 주소를 입력하세요.");
            if(code==404)throw new IOException("API 주소를 찾을 수 없습니다 (404).");
            if(code<200||code>=300)throw new IOException("서버 HTTP 오류 ("+code+").");
            try(InputStream raw=connection.getInputStream()){
                String encoding=connection.getContentEncoding();
                if(encoding!=null&&!encoding.equalsIgnoreCase("gzip")&&!encoding.equalsIgnoreCase("identity"))throw new IOException("지원하지 않는 서버 응답 압축 형식입니다.");
                InputStream decoded="gzip".equalsIgnoreCase(encoding)?new GZIPInputStream(raw):raw;
                try(InputStream input=new LimitedInputStream(decoded,limit,bulk?90000:20000)){
                    return parser.read(input);
                }
            }
        }catch(javax.net.ssl.SSLException e){throw new IOException("TLS 인증서 검증 실패. 서버 인증서를 확인하세요.");}
        catch(SocketTimeoutException e){throw new IOException("서버 연결 시간이 초과되었습니다.");}
        catch(UnknownHostException e){throw new IOException("서버 이름을 찾을 수 없습니다.");}
        finally{connection.disconnect();}
    }
    void ping(Config config) throws Exception {
        if(!"API running.".equals(new JSONObject(get(config,"/api/")).optString("message")))throw new IOException("Home Assistant API 응답이 아닙니다.");
    }
    EntityCatalog states(Config config) throws IOException {
        return read(config,"/api/states",MAX_STATES_BYTES,input->EntityCatalog.read(new InputStreamReader(input,StandardCharsets.UTF_8)));
    }
    /** Bound decompressed bytes and elapsed reading time without buffering the response. */
    static final class LimitedInputStream extends FilterInputStream {
        private final long limit,deadline;private long count;
        LimitedInputStream(InputStream input,long limit,long timeoutMillis){super(input);this.limit=limit;deadline=System.nanoTime()+timeoutMillis*1_000_000L;}
        private void check() throws IOException {
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("요청이 취소되었습니다.");
            if(System.nanoTime()>deadline)throw new SocketTimeoutException();
        }
        private void add(int size) throws IOException {if(size>0&&((count+=size)>limit))throw new IOException("서버 응답이 너무 큽니다.");}
        @Override public int read() throws IOException {check();int value=in.read();if(value!=-1)add(1);return value;}
        @Override public int read(byte[] bytes,int offset,int length) throws IOException {
            check();int result=in.read(bytes,offset,(int)Math.min(length,limit-count+1));add(result);return result;
        }
    }
}
