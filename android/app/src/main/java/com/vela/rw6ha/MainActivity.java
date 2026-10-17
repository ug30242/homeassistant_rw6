package com.vela.rw6ha;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import com.xiaomi.xms.wearable.node.Node;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Native foreground manager; never receives watch HTTP or control requests. */
public final class MainActivity extends Activity implements XmsClient.Events,SyncSession.Events {
    private static final int BG=0xff0b1220,CARD=0xff162338,TEXT=0xfff1f5f9,MUTED=0xff94a3b8,BLUE=0xff38bdf8;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Handler syncHandler=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private Config config;private ConfigVault vault;private XmsClient xms;private SyncSession sync;
    private LinearLayout body,selected,available;private TextView statusView,selectionSummary;private EditText urlInput,tokenInput,searchInput;
    private CheckBox insecureInput;private Spinner refreshInput,domainInput;
    private JSONArray discovered=new JSONArray();private int tab,networkEpoch;private boolean networkBusy,vaultFailed,destroyed;
    private String connectionName="연결된 워치 없음",lastServerInfo="연결 테스트 전",statusText="설정을 저장하고 워치에 동기화하세요.";
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        vault=new ConfigVault(this);
        try{config=vault.read();}catch(Exception e){config=new Config();config.touch();vaultFailed=true;statusText="저장소를 읽지 못했습니다. 워치 탭에서 로컬 저장소를 초기화하세요.";}
        xms=new XmsClient(this,this);
        sync=new SyncSession(new SyncSession.Transport(){public void send(String packet,boolean probe){xms.send(packet,probe);}public void openWatch(){xms.openWatch();}},this,(action,millis)->syncHandler.postDelayed(action,millis));
        render();
    }
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));return d;}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(6),0,dp(6));return v;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout card(String title){LinearLayout l=column();l.setPadding(dp(18),dp(12),dp(18),dp(16));l.setBackground(shape(CARD));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);body.addView(l,p);TextView t=text(title,19,TEXT);t.setTypeface(null,Typeface.BOLD);l.addView(t);return l;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(TEXT);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff12496b));b.setMinHeight(dp(48));b.setOnClickListener(v->action.run());return b;}
    private void weighted(LinearLayout row,View view){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(2),0,dp(2),0);row.addView(view,p);}
    private EditText field(String hint,String value,boolean secret){EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setTextSize(16);e.setHint(hint);e.setText(value);e.setPadding(dp(8),dp(8),dp(8),dp(8));e.setMinHeight(dp(52));e.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT);e.setSaveEnabled(false);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);return e;}
    private Spinner spinner(String[] labels){Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,labels){@Override public View getView(int position,View convertView,ViewGroup parent){View v=super.getView(position,convertView,parent);((TextView)v).setTextColor(TEXT);return v;} @Override public View getDropDownView(int position,View convertView,ViewGroup parent){View v=super.getDropDownView(position,convertView,parent);((TextView)v).setTextColor(TEXT);v.setBackgroundColor(CARD);return v;}};a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BLUE));return s;}
    private void note(LinearLayout parent,String message){parent.addView(text(message,14,MUTED));}
    private void setStatus(String message){statusText=message;if(statusView!=null)statusView.setText(message);}
    private boolean editable(){if(vaultFailed){setStatus("읽을 수 없는 저장소를 먼저 초기화하세요.");return false;}if(sync.isRunning()){setStatus("동기화 완료 후 설정을 변경하세요.");return false;}return true;}
    private boolean persist(Config next){
        if(!editable())return false;
        try{next.touch();next.validate();vault.write(next);config=next;networkEpoch++;return true;}
        catch(IllegalArgumentException e){setStatus(e.getMessage());return false;}
        catch(Exception e){setStatus("암호화 저장 실패. 기존 설정은 유지됩니다.");return false;}
    }
    private void render(){
        LinearLayout root=column();root.setPadding(dp(18),dp(12),dp(18),0);root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(18)+bars.left,dp(12)+bars.top,dp(18)+bars.right,bars.bottom);}else{v.setPadding(dp(18)+insets.getSystemWindowInsetLeft(),dp(12)+insets.getSystemWindowInsetTop(),dp(18)+insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());}return insets;});
        TextView heading=text("Home Assistant",28,TEXT);heading.setTypeface(null,Typeface.BOLD);root.addView(heading);root.addView(text("REDMI WATCH 6 · 설정 관리자",12,BLUE));
        LinearLayout tabs=row();String[] names={"서버","엔티티","워치"};for(int i=0;i<3;i++){final int n=i;Button b=button(names[i],()->{if(sync.isRunning()&&n!=2){setStatus("동기화 진행 중입니다.");return;}tab=n;render();});if(tab==i)b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff0369a1));weighted(tabs,b);}root.addView(tabs);
        statusView=text(statusText,14,BLUE);statusView.setMinHeight(dp(56));root.addView(statusView);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=column();body.setPadding(0,dp(10),0,dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        if(tab==0)serverPage();else if(tab==1)entityPage();else watchPage();
    }
    private void serverPage(){
        LinearLayout c=card("서버 연결");note(c,"최종 Home Assistant 주소를 입력하세요. HTTPS 인증서를 정상 검증합니다.");
        urlInput=field("https://home.example.com",config.server,false);urlInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);c.addView(urlInput);
        tokenInput=field(config.token.isEmpty()?"Long-Lived Access Token":"새 토큰 입력 · 비워두면 기존 토큰 유지","",true);c.addView(tokenInput);
        note(c,config.token.isEmpty()?"저장된 토큰 없음":"토큰이 Android Keystore로 암호화되어 있습니다.");
        insecureInput=new CheckBox(this);insecureInput.setText("신뢰하는 로컬 네트워크에서 HTTP 허용");insecureInput.setTextColor(TEXT);insecureInput.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));insecureInput.setChecked(config.allowInsecure);c.addView(insecureInput);note(c,"HTTP는 토큰을 평문으로 전송합니다. 공인 HTTPS 사용을 권장합니다.");
        refreshInput=spinner(new String[]{"수동 새로고침 (기본)","활성 화면에서 30초","활성 화면에서 60초","활성 화면에서 120초"});refreshInput.setSelection(Arrays.asList(0,30,60,120).indexOf(config.refreshSeconds));c.addView(refreshInput);
        c.addView(button("암호화 저장",()->saveServer(true)));
        c.addView(button("저장 후 서버 연결 테스트",()->{if(saveServer(false))testServer();}));
        note(c,lastServerInfo);LinearLayout security=card("인증정보 관리");note(security,"워치 일반 저장소는 보안 저장소가 아닙니다. 최소 권한 전용 계정을 사용하고 분실 시 Home Assistant에서 토큰을 폐기하세요.");security.addView(button("로컬 토큰 삭제 · 워치 삭제 설정 준비",this::confirmDelete));
    }
    private boolean saveServer(boolean redraw){
        if(!editable())return false;
        try{Config next=config.copy();next.server=urlInput.getText().toString().trim();next.allowInsecure=insecureInput.isChecked();String entered=tokenInput.getText().toString().trim();if(!entered.isEmpty())next.token=entered;next.cleared=false;next.refreshSeconds=Arrays.asList(0,30,60,120).get(refreshInput.getSelectedItemPosition());
            if(persist(next)){tokenInput.setText("");lastServerInfo="서버 설정 저장됨 · 연결 테스트 전";setStatus("설정 저장됨. 변경사항을 워치에 동기화하세요.");if(redraw)render();return true;}
        }catch(Exception e){setStatus("서버 주소와 토큰을 확인하세요.");}return false;
    }
    private void testServer(){runNetwork(true);}
    private void runNetwork(boolean ping){
        if(networkBusy){setStatus("서버 요청 처리 중입니다.");return;}if(config.cleared){setStatus("서버와 토큰을 먼저 저장하세요.");return;}
        final Config snapshot;try{snapshot=config.copy();}catch(Exception e){return;}
        final int epoch=++networkEpoch;networkBusy=true;setStatus(ping?"서버에 연결 중…":"엔티티를 불러오는 중…");
        network.execute(()->{
            try{
                HaApi api=new HaApi();if(ping)api.ping(snapshot);JSONArray result=ping?null:api.states(snapshot);
                handler.post(()->{if(destroyed||epoch!=networkEpoch){networkBusy=false;return;}networkBusy=false;
                    if(ping){lastServerInfo="연결 성공 · "+snapshot.server;setStatus("Home Assistant 인증 및 연결 성공.");if(tab==0)render();}
                    else{discovered=result;setStatus(result.length()+"개 엔티티 조회됨. 지원 도메인에서 최대 80개를 선택하세요.");if(tab==1)render();}
                });
            }catch(Exception e){final String message=safeNetworkError(e);handler.post(()->{networkBusy=false;if(!destroyed&&epoch==networkEpoch)setStatus(message);});}
        });
    }
    private static String safeNetworkError(Exception error){
        String message=error.getMessage();
        if(message!=null&&(message.startsWith("인증 오류")||message.startsWith("TLS 인증서")||message.startsWith("리디렉션")||message.startsWith("서버 HTTP")||message.startsWith("API 주소")||message.equals("서버 연결 시간이 초과되었습니다.")||message.equals("서버 이름을 찾을 수 없습니다.")||message.equals("서버 응답이 너무 큽니다.")||message.equals("Home Assistant API 응답이 아닙니다.")))return message;
        return "네트워크 또는 응답 오류. 서버 주소·인터넷 연결·응답 형식을 확인하세요.";
    }
    private void confirmDelete(){new AlertDialog.Builder(this).setTitle("Android 토큰 삭제").setMessage("로컬 토큰과 선택 목록을 삭제합니다. 워치의 기존 토큰은 동기화 또는 워치 Info > Erase로 따로 삭제해야 합니다. Home Assistant에서도 토큰을 폐기할 수 있습니다.").setNegativeButton("취소",null).setPositiveButton("삭제",(d,w)->{
        if(!editable())return;Config next=new Config();next.revision=config.revision;if(persist(next)){discovered=new JSONArray();lastServerInfo="연결 테스트 전";setStatus("로컬 자격 증명 삭제됨. 워치 탭에서 삭제 설정을 동기화하세요.");render();}
    }).show();}
    private void entityPage(){
        LinearLayout c=card("워치에 표시할 엔티티");selectionSummary=text("",14,BLUE);c.addView(selectionSummary);note(c,"위·아래 버튼으로 순서를 바꾸고 편집에서 이름·아이콘·즐겨찾기·공간을 지정하세요.");selected=column();c.addView(selected);renderSelected();
        LinearLayout catalog=card("Home Assistant 엔티티");catalog.addView(button("엔티티 목록 자동 조회",()->runNetwork(false)));
        searchInput=field("이름 또는 entity_id 검색","",false);catalog.addView(searchInput);
        domainInput=spinner(new String[]{"모든 지원 도메인","light","switch","fan","cover","sensor","scene","script"});catalog.addView(domainInput);available=column();catalog.addView(available);
        searchInput.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){renderAvailable();}public void afterTextChanged(Editable e){}});
        domainInput.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){renderAvailable();}public void onNothingSelected(android.widget.AdapterView<?> p){}});renderAvailable();
    }
    private void renderSelected(){
        selected.removeAllViews();if(selectionSummary!=null)selectionSummary.setText(config.entities.size()+" / 80개 선택됨");if(config.entities.isEmpty()){note(selected,"아직 선택한 엔티티가 없습니다.");return;}
        for(Config.Entity entity:config.entities){
            LinearLayout box=column();box.setPadding(0,dp(8),0,dp(12));box.addView(text((entity.favorite?"★ ":"")+entity.name,17,TEXT));note(box,entity.id+" · "+(entity.group.isEmpty()?"그룹 없음":entity.group)+" · "+entity.icon);
            LinearLayout actions=row();weighted(actions,button("↑",()->move(entity.id,-1)));weighted(actions,button("↓",()->move(entity.id,1)));weighted(actions,button("편집",()->editEntity(entity.id)));weighted(actions,button("삭제",()->remove(entity.id)));selected.addView(box);box.addView(actions);
        }
    }
    private void renderAvailable(){
        if(available==null||searchInput==null||domainInput==null)return;available.removeAllViews();String query=searchInput.getText().toString().toLowerCase(Locale.ROOT);int domain=domainInput.getSelectedItemPosition(),shown=0;
        for(int i=0;i<discovered.length();i++){
            JSONObject object=discovered.optJSONObject(i);if(object==null)continue;String id=object.optString("entity_id"),kind=id.contains(".")?id.split("\\.")[0]:"";
            if(!Config.DOMAINS.contains(kind)||!id.matches("[a-z_]+\\.[a-z0-9_]+")||id.length()>160||(domain>0&&!kind.equals(Config.DOMAINS.get(domain-1))))continue;
            JSONObject attr=object.optJSONObject("attributes");String rawName=attr==null?id:attr.optString("friendly_name",id);final String name=rawName.length()>80?rawName.substring(0,80):rawName;
            if(!(id+" "+name).toLowerCase(Locale.ROOT).contains(query))continue;
            if(++shown>200){note(available,"검색어로 결과를 좁히세요. 처음 200개를 표시합니다.");break;}
            CheckBox check=new CheckBox(this);check.setText(name+"\n"+id);check.setTextColor(TEXT);check.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));check.setMinHeight(dp(64));check.setChecked(config.find(id)!=null);check.setOnCheckedChangeListener((b,on)->{
                if(!editable()){b.setOnCheckedChangeListener(null);b.setChecked(config.find(id)!=null);return;}
                try{Config next=config.copy();Config.Entity existing=next.find(id);if(on&&existing==null){Config.Entity item=new Config.Entity(id,name);next.entities.add(item);}else if(!on&&existing!=null)next.entities.remove(existing);
                    if(persist(next)){renderSelected();setStatus("선택 저장됨. 워치 동기화가 필요합니다.");}
                    else{b.setOnCheckedChangeListener(null);b.setChecked(config.find(id)!=null);}
                }catch(Exception e){setStatus("엔티티 선택 실패.");}
            });available.addView(check);
        }
        if(shown==0)note(available,discovered.length()==0?"목록을 불러온 후 여러 엔티티를 선택하세요.":"검색 결과가 없습니다.");
    }
    private void move(String id,int direction){
        try{Config next=config.copy();Config.Entity e=next.find(id);int index=next.entities.indexOf(e),target=index+direction;if(index<0||target<0||target>=next.entities.size())return;Collections.swap(next.entities,index,target);if(persist(next))renderSelected();}catch(Exception e){setStatus("순서 저장 실패.");}
    }
    private void remove(String id){try{Config next=config.copy();next.entities.remove(next.find(id));if(persist(next)){renderSelected();renderAvailable();setStatus("삭제 저장됨. 다음 동기화에서 워치에 반영됩니다.");}}catch(Exception e){setStatus("삭제 저장 실패.");}}
    private void editEntity(String id){
        Config.Entity entity=config.find(id);if(entity==null||!editable())return;
        LinearLayout form=column();form.setPadding(dp(22),dp(10),dp(22),dp(10));form.setBackgroundColor(BG);
        EditText name=field("표시 이름 (80자)",entity.name,false),icon=field("아이콘: light / switch / mdi:...",entity.icon,false),group=field("공간 / 그룹 (40자)",entity.group,false);CheckBox favorite=new CheckBox(this);favorite.setText("즐겨찾기");favorite.setTextColor(TEXT);favorite.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));favorite.setChecked(entity.favorite);
        form.addView(name);form.addView(icon);form.addView(group);form.addView(favorite);note(form,"워치에서 한글 이름은 entity_id의 ASCII 이름으로 대체 표시합니다.");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("엔티티 편집").setView(form).setNegativeButton("취소",null).setPositiveButton("저장",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{Config next=config.copy();Config.Entity item=next.find(id);if(item==null)return;item.name=name.getText().toString().trim();item.icon=icon.getText().toString().trim();item.group=group.getText().toString().trim();item.favorite=favorite.isChecked();if(persist(next)){dialog.dismiss();renderSelected();renderAvailable();setStatus("표시 설정 저장됨. 워치에 동기화하세요.");}}
            catch(Exception e){setStatus("이름·아이콘·그룹 형식을 확인하세요.");}
        }));dialog.show();
    }
    private void watchPage(){
        LinearLayout c=card("워치 설정 동기화");note(c,connectionName);note(c,"Mi Fitness(중국판 운동건강) 연결이 필요합니다. 최초 연결 시 Mi Fitness에서 동반 앱 권한을 허용하세요. Notify는 RPK 설치에만 사용합니다.");
        c.addView(button("워치 연결 / 다시 연결",()->{if(sync.isRunning())sync.cancel();connectionName="연결 확인 중…";setStatus(connectionName);xms.connect();}));
        c.addView(button("전체 설정 워치 동기화",()->{
            if(vaultFailed){setStatus("로컬 저장소를 먼저 초기화하세요.");return;}if(!xms.isReady()){setStatus("워치를 먼저 연결하세요.");return;}if(sync.isRunning())return;
            new AlertDialog.Builder(this).setTitle(config.cleared?"워치 자격 증명 삭제":"워치로 토큰 전송").setMessage(config.cleared?"워치의 서버·토큰·엔티티를 삭제합니다. 저장 확인까지 Phone sync 화면을 유지하세요.":"토큰을 워치 일반 저장소에 보관합니다. 워치에 안전한 암호화 저장소가 있다고 가정하지 않습니다. APK와 RPK는 같은 릴리즈로 설치하세요.").setNegativeButton("취소",null).setPositiveButton("동기화",(d,w)->sync.start(config)).show();
        }));c.addView(button("동기화 취소",()->sync.cancel()));note(c,"설정 버전: "+config.revision+" · "+config.entities.size()+"개 · "+(config.cleared?"삭제 설정":"토큰 저장됨"));
        LinearLayout guide=card("저장 확인 후 Android 종료 가능");note(guide,"워치는 @system.fetch로 Home Assistant에 직접 연결합니다. Android 백그라운드 서비스는 없습니다. Mi Fitness 네트워크 경로는 계속 필요할 수 있습니다.");note(guide,"실기기 검증: 워치 Info에서 GET/POST 테스트 → Android 강제 종료 → 테스트와 기기 제어 반복. 중국 내수 펌웨어의 네트워크 동작은 아직 검증되지 않았습니다.");
        LinearLayout reset=card("로컬 저장소 복구");note(reset,"암호화 저장소 오류일 때만 사용하세요. 워치 데이터는 별도 삭제가 필요합니다.");reset.addView(button("Android 저장소 초기화",()->new AlertDialog.Builder(this).setTitle("로컬 설정 초기화").setMessage("이 Android 앱의 토큰과 선택 설정을 삭제합니다.").setNegativeButton("취소",null).setPositiveButton("초기화",(d,w)->{
            if(sync.isRunning()){setStatus("동기화를 먼저 취소하세요.");return;}try{vault.reset();config=new Config();config.touch();vaultFailed=false;networkEpoch++;discovered=new JSONArray();setStatus("로컬 저장소 초기화됨. 워치 자격 증명은 따로 삭제하세요.");render();}catch(Exception e){setStatus("초기화 실패.");}
        }).show()));
    }
    @Override public void nodes(List<Node> nodes){
        if(nodes.size()==1){xms.select(nodes.get(0));return;}String[] names=new String[nodes.size()];for(int i=0;i<nodes.size();i++)names[i]=nodes.get(i).name;
        new AlertDialog.Builder(this).setTitle("워치 선택").setItems(names,(d,w)->xms.select(nodes.get(w))).show();
    }
    @Override public void ready(String name){connectionName="연결됨: "+name;setStatus(connectionName+" · 워치 Phone sync 화면을 여세요.");if(tab==2)render();}
    @Override public void error(String message){connectionName="워치 연결 확인 필요";if(sync.isRunning())sync.fail(message);else setStatus(message);}
    @Override public void message(String json){sync.receive(json);}
    @Override public void authorizing(){setStatus("Mi Fitness에서 동반 앱 기기 통신 권한을 허용하세요.");}
    @Override public void status(String text){setStatus(text);}
    @Override public void finished(boolean success){syncHandler.removeCallbacksAndMessages(null);}
    @Override protected void onStop(){super.onStop();networkEpoch++;if(tokenInput!=null)tokenInput.setText("");sync.cancel();xms.disconnect();connectionName="연결된 워치 없음";}
    @Override protected void onDestroy(){destroyed=true;networkEpoch++;sync.cancel();xms.destroy();network.shutdownNow();handler.removeCallbacksAndMessages(null);syncHandler.removeCallbacksAndMessages(null);super.onDestroy();}
}
