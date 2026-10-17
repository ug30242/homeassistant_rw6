package com.vela.rw6ha;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Versioned full replacement. No credentials are returned by toString or diagnostics. */
final class Config {
    static final int MAX_BYTES=49152;
    static final List<String> DOMAINS=Arrays.asList("light","switch","fan","cover","sensor","scene","script");
    long revision;
    String server="",token="";
    boolean allowInsecure,cleared=true;
    int refreshSeconds;
    final List<Entity> entities=new ArrayList<>();
    static final class Entity {
        String id,name,icon,group="";
        boolean favorite;
        Entity(String id,String name){this.id=id;this.name=name;this.icon=domain();}
        String domain(){return id.split("\\.")[0];}
        JSONObject json(int index) throws JSONException {
            return new JSONObject().put("id",id).put("name",name).put("icon",icon).put("group",group).put("favorite",favorite).put("order",index);
        }
    }
    static String requestId(){return UUID.randomUUID().toString().replace("-","");}
    void touch(){revision=Math.max(System.currentTimeMillis(),revision+1);}
    static String url(String value,boolean insecure) {
        if(value==null || value.length()>512 || value.matches(".*[\\s?#\\\\].*"))throw new IllegalArgumentException("서버 URL 형식을 확인하세요.");
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("^(https?)://(\\[[0-9a-fA-F:]+\\]|[a-zA-Z0-9.-]+)(:[0-9]{1,5})?(/[^\\s?#]*)?$").matcher(value);
        if(!m.matches() || (m.group(1).equals("http")&&!insecure))throw new IllegalArgumentException("HTTPS 주소를 입력하세요. HTTP는 별도 허용이 필요합니다.");
        if(m.group(3)!=null){int port=Integer.parseInt(m.group(3).substring(1));if(port<1||port>65535)throw new IllegalArgumentException("잘못된 포트입니다.");}
        return value.replaceAll("/+$","");
    }
    private static boolean text(String value,int max){return value!=null&&value.length()<=max&&!value.matches("(?s).*[\\x00-\\x1f\\x7f].*");}
    void validate(){
        if(revision<1||revision>9007199254740991L)throw new IllegalArgumentException("설정 버전 오류");
        if(!cleared)server=url(server,allowInsecure);
        if(!text(token,4096)||(!cleared&&!token.matches("[!-~]+"))||(cleared&&(!token.isEmpty()||!entities.isEmpty())))throw new IllegalArgumentException("유효한 토큰을 입력하세요.");
        if(entities.size()>80)throw new IllegalArgumentException("최대 80개 엔티티를 선택할 수 있습니다.");
        Set<String> ids=new HashSet<>();
        for(Entity e:entities){
            if(e.id==null||e.id.length()>160||!e.id.matches("[a-z_]+\\.[a-z0-9_]+")||!DOMAINS.contains(e.domain())||!ids.add(e.id)||!text(e.name,80)||!text(e.icon,32)||!e.icon.matches("[a-zA-Z0-9:_-]*")||!text(e.group,40))throw new IllegalArgumentException("엔티티 ID·이름·아이콘·그룹을 확인하세요.");
        }
        if(!Arrays.asList(0,30,60,120).contains(refreshSeconds))throw new IllegalArgumentException("갱신 주기 오류");
    }
    JSONObject json() throws JSONException {
        validate();JSONArray array=new JSONArray();for(int i=0;i<entities.size();i++)array.put(entities.get(i).json(i));
        return new JSONObject().put("schema",1).put("revision",revision).put("server",cleared?"":server).put("token",token).put("allowInsecure",allowInsecure).put("cleared",cleared).put("refreshSeconds",refreshSeconds).put("entities",array);
    }
    String payload() throws JSONException {
        String result=json().toString();if(result.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("설정이 48 KiB를 초과합니다. 엔티티 수 또는 이름 길이를 줄이세요.");return result;
    }
    static Config parse(String raw) throws JSONException {
        JSONObject o=new JSONObject(raw);if(o.getInt("schema")!=1)throw new IllegalArgumentException("설정 형식 오류");
        Config c=new Config();c.revision=o.getLong("revision");c.server=o.getString("server");c.token=o.getString("token");c.cleared=o.optBoolean("cleared",false);c.allowInsecure=o.optBoolean("allowInsecure",false);c.refreshSeconds=o.optInt("refreshSeconds",0);
        JSONArray a=o.getJSONArray("entities");if(a.length()>80)throw new IllegalArgumentException("엔티티 수 오류");
        for(int i=0;i<a.length();i++){JSONObject e=a.getJSONObject(i);Entity item=new Entity(e.getString("id"),e.getString("name"));item.icon=e.getString("icon");item.group=e.getString("group");item.favorite=e.optBoolean("favorite",false);c.entities.add(item);}
        c.validate();return c;
    }
    Config copy() throws JSONException {return parse(payload());}
    Entity find(String id){for(Entity e:entities)if(e.id.equals(id))return e;return null;}
}
