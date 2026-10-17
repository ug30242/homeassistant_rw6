package com.vela.rw6ha;

import android.util.JsonReader;
import android.util.JsonToken;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/** Read /api/states incrementally; large state/attribute values are skipped, never retained. */
final class EntityCatalog {
    static final int MAX_ENTITIES=50000;
    static final int PAGE_SIZE=50;
    private static final Pattern ENTITY_ID=Pattern.compile("[a-z_]+\\.[a-z0-9_]+"),CONTROLS=Pattern.compile("[\\x00-\\x1f\\x7f]");
    static final class Item {
        final String id,name,domain,search;
        Item(String id,String name,String domain){
            this.id=id;this.name=name;this.domain=domain;
            search=(id+" "+name).toLowerCase(Locale.ROOT);
        }
    }
    final int totalCount,supportedCount;
    private final Map<String,List<Item>> groups;
    private EntityCatalog(int totalCount,Map<String,List<Item>> groups){
        this.totalCount=totalCount;this.groups=groups;
        int count=0;for(List<Item> items:groups.values())count+=items.size();supportedCount=count;
    }
    static EntityCatalog empty(){return new EntityCatalog(0,new LinkedHashMap<>());}
    List<Item> matching(String domain,String query){
        List<Item> items=groups.get(domain);if(items==null)return Collections.emptyList();
        String normalized=query.trim().toLowerCase(Locale.ROOT);
        if(normalized.isEmpty())return items;
        List<Item> result=new ArrayList<>();for(Item item:items)if(item.search.contains(normalized))result.add(item);
        return result;
    }
    static String label(String domain){
        switch(domain){
            case "light":return "조명 · light";
            case "switch":return "스위치 · switch";
            case "fan":return "팬 · fan";
            case "cover":return "커튼 · cover";
            case "sensor":return "센서 · sensor";
            case "scene":return "장면 · scene";
            case "script":return "스크립트 · script";
            default:return domain;
        }
    }
    static EntityCatalog read(Reader input) throws IOException {
        Map<String,List<Item>> groups=new LinkedHashMap<>();Set<String> ids=new HashSet<>();int total=0;
        try(JsonReader reader=new JsonReader(input)){
            reader.beginArray();
            while(reader.hasNext()){
                if(++total>MAX_ENTITIES)throw new IOException("엔티티 수가 너무 많습니다. 최대 50,000개까지 조회할 수 있습니다.");
                String id="",name="";
                reader.beginObject();
                while(reader.hasNext()){
                    String key=reader.nextName();
                    if(key.equals("entity_id")&&reader.peek()==JsonToken.STRING)id=reader.nextString();
                    else if(key.equals("attributes")&&reader.peek()==JsonToken.BEGIN_OBJECT){
                        reader.beginObject();
                        while(reader.hasNext()){
                            if(reader.nextName().equals("friendly_name")&&reader.peek()==JsonToken.STRING)name=reader.nextString();
                            else reader.skipValue();
                        }
                        reader.endObject();
                    }else reader.skipValue();
                }
                reader.endObject();
                if(id.length()>160||!ENTITY_ID.matcher(id).matches())continue;
                String domain=id.substring(0,id.indexOf('.'));
                if(!Config.DOMAINS.contains(domain)||!ids.add(id))continue;
                name=CONTROLS.matcher(name).replaceAll(" ").trim();if(name.isEmpty())name=id;
                if(name.length()>80)name=name.substring(0,Character.isHighSurrogate(name.charAt(79))?79:80);
                List<Item> items=groups.get(domain);if(items==null){items=new ArrayList<>();groups.put(domain,items);}
                items.add(new Item(id,name,domain));
            }
            reader.endArray();
            if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("엔티티 목록 응답 형식 오류.");
        }
        for(Map.Entry<String,List<Item>> group:groups.entrySet())group.setValue(Collections.unmodifiableList(group.getValue()));
        return new EntityCatalog(total,Collections.unmodifiableMap(groups));
    }
}
