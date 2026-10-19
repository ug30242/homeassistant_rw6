package com.vela.rw6ha;
import static org.junit.Assert.*;
import android.view.*;
import android.widget.*;
import java.io.StringReader;
import java.util.*;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Resetter;
import org.robolectric.util.ReflectionHelpers;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class MainActivityTest {
    @Before public void resetVaultDouble(){ShadowVault.reset();}
    private Button button(View root,String label){if(root instanceof Button&&label.equals(((Button)root).getText().toString()))return (Button)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){Button found=button(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    @Test public void nativeTabsRenderWithoutBackgroundServicesAndWindowProtectsToken(){
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();assertTrue((activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0);
            for(String tab:new String[]{"엔티티","선택목록","워치","서버"}){Button b=button(activity.getWindow().getDecorView(),tab);assertNotNull(b);b.performClick();}
            assertNotNull(button(activity.getWindow().getDecorView(),"암호화 저장"));controller.pause().stop();
        }
    }
    private Button domain(View root,String prefix){if(root instanceof Button&&((Button)root).getText().toString().startsWith(prefix))return (Button)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){Button found=domain(group.getChildAt(i),prefix);if(found!=null)return found;}}return null;}
    private List<CheckBox> choices(View view){List<CheckBox> result=new ArrayList<>();if(view instanceof CheckBox&&((CheckBox)view).getText().toString().contains("\n"))result.add((CheckBox)view);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)result.addAll(choices(group.getChildAt(i)));}return result;}
    @Test public void largeCatalogStartsCollapsedAndDomainPagesDoNotMixEntities() throws Exception {
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();EntityCatalog catalog=EntityCatalog.read(new StringReader(EntityCatalogTest.manyEntities()));
            ReflectionHelpers.setField(activity,"discovered",catalog);button(activity.getWindow().getDecorView(),"엔티티").performClick();View root=activity.getWindow().getDecorView();
            assertTrue(choices(root).isEmpty());assertNotNull(domain(root,"센서 · sensor · 2600개"));
            domain(root,"센서 · sensor").performClick();assertEquals(50,choices(root).size());assertTrue(choices(root).stream().allMatch(c->c.getText().toString().contains("sensor.temp_")));
            button(root,"다음").performClick();assertTrue(choices(root).get(0).getText().toString().endsWith("sensor.temp_50"));
            domain(root,"조명 · light").performClick();assertEquals(50,choices(root).size());assertTrue(choices(root).stream().allMatch(c->c.getText().toString().contains("light.lamp_")));
            button(root,"다음").performClick();assertEquals(5,choices(root).size());assertFalse(button(root,"다음").isEnabled());
            button(root,"이전").performClick();assertEquals(50,choices(root).size());domain(root,"조명 · light").performClick();assertTrue(choices(root).isEmpty());
        }
    }
    @Test public void searchFindsLateEntitiesAndSelectionSurvivesPagingAndTabChanges() throws Exception {
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();com.vela.rw6ha.Config config=ConfigTest.fixture();config.entities.clear();config.entities.add(new com.vela.rw6ha.Config.Entity("sensor.temp_2599","온도"));
            ReflectionHelpers.setField(activity,"config",config);ReflectionHelpers.setField(activity,"discovered",EntityCatalog.read(new StringReader(EntityCatalogTest.manyEntities())));
            button(activity.getWindow().getDecorView(),"엔티티").performClick();View root=activity.getWindow().getDecorView();EditText search=ReflectionHelpers.getField(activity,"searchInput");search.setText("TEMP_2599");
            domain(root,"센서 · sensor").performClick();assertEquals(1,choices(root).size());assertTrue(choices(root).get(0).isChecked());
            button(root,"서버").performClick();button(activity.getWindow().getDecorView(),"엔티티").performClick();root=activity.getWindow().getDecorView();
            assertEquals("TEMP_2599",((EditText)ReflectionHelpers.getField(activity,"searchInput")).getText().toString());assertEquals(1,choices(root).size());assertTrue(choices(root).get(0).isChecked());
            search=ReflectionHelpers.getField(activity,"searchInput");search.setText("no-such-entity");assertTrue(choices(root).isEmpty());
        }
    }
    @Implements(value=ConfigVault.class,isInAndroidSdk=false)
    public static class ShadowVault {
        static String saved;static boolean fail;
        @Implementation protected com.vela.rw6ha.Config read() throws Exception {
            return saved==null?ConfigTest.fixture():com.vela.rw6ha.Config.parse(saved);
        }
        @Implementation protected void write(com.vela.rw6ha.Config value) throws Exception {
            if(fail)throw new java.io.IOException("test storage failure");saved=value.payload();
        }
        @Resetter public static void reset(){saved=null;fail=false;}
    }
    private CheckBox favorite(View root){
        if(root instanceof CheckBox&&"즐겨찾기 · 워치 Favorites".equals(((CheckBox)root).getText().toString()))return (CheckBox)root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){CheckBox found=favorite(group.getChildAt(i));if(found!=null)return found;}}
        return null;
    }
    @Test @Config(sdk=28,shadows=ShadowVault.class)
    public void favoriteIsVisibleSavedAndRestoredAndRollsBackOnStorageFailure() throws Exception {
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();button(activity.getWindow().getDecorView(),"엔티티").performClick();button(activity.getWindow().getDecorView(),"선택목록 검토").performClick();
            CheckBox check=favorite(activity.getWindow().getDecorView());assertNotNull(check);assertTrue(check.isChecked());
            check.performClick();assertFalse(com.vela.rw6ha.Config.parse(ShadowVault.saved).entities.get(0).favorite);
            check=favorite(activity.getWindow().getDecorView());assertFalse(check.isChecked());check.performClick();assertTrue(com.vela.rw6ha.Config.parse(ShadowVault.saved).entities.get(0).favorite);
            check=favorite(activity.getWindow().getDecorView());ShadowVault.fail=true;check.performClick();assertTrue(check.isChecked());assertTrue(com.vela.rw6ha.Config.parse(ShadowVault.saved).entities.get(0).favorite);ShadowVault.fail=false;
            button(activity.getWindow().getDecorView(),"서버").performClick();button(activity.getWindow().getDecorView(),"선택목록").performClick();assertTrue(favorite(activity.getWindow().getDecorView()).isChecked());
        }
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();button(activity.getWindow().getDecorView(),"선택목록").performClick();assertTrue(favorite(activity.getWindow().getDecorView()).isChecked());
        }
    }
    private boolean hasText(View view,String value){
        if(view instanceof TextView&&value.equals(((TextView)view).getText().toString()))return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(hasText(group.getChildAt(i),value))return true;}
        return false;
    }
    @Test @Config(sdk=28,shadows=ShadowVault.class)
    public void domainSelectionCountsUpdateAcrossPagingSearchFailureAndReviewRemoval() throws Exception {
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();ReflectionHelpers.setField(activity,"discovered",EntityCatalog.read(new StringReader(EntityCatalogTest.manyEntities())));
            button(activity.getWindow().getDecorView(),"엔티티").performClick();View root=activity.getWindow().getDecorView();
            assertTrue(domain(root,"조명 · light").getText().toString().contains("선택 1개"));
            domain(root,"조명 · light").performClick();choices(root).get(0).performClick();assertTrue(domain(root,"조명 · light").getText().toString().contains("선택 2개"));
            button(root,"다음").performClick();choices(root).get(0).performClick();assertTrue(domain(root,"조명 · light").getText().toString().contains("선택 3개"));
            EditText search=ReflectionHelpers.getField(activity,"searchInput");search.setText("lamp_54");assertTrue(domain(root,"조명 · light").getText().toString().contains("1 / 55개 · 선택 3개"));
            ShadowVault.fail=true;choices(root).get(0).performClick();assertFalse(choices(root).get(0).isChecked());assertTrue(domain(root,"조명 · light").getText().toString().contains("선택 3개"));ShadowVault.fail=false;
            search.setText("not-a-result");assertTrue(domain(root,"조명 · light").getText().toString().contains("0 / 55개 · 선택 3개"));assertTrue(choices(root).isEmpty());
            button(root,"선택목록").performClick();LinearLayout rows=ReflectionHelpers.getField(activity,"selected");button(rows.getChildAt(0),"삭제").performClick();
            button(activity.getWindow().getDecorView(),"엔티티").performClick();assertTrue(domain(activity.getWindow().getDecorView(),"조명 · light").getText().toString().contains("선택 2개"));
        }
    }
    @Test @Config(sdk=28,shadows=ShadowVault.class)
    public void reviewWorksWithoutCatalogAndFiltersManagesAndRetainsSelections() throws Exception {
        com.vela.rw6ha.Config value=ConfigTest.fixture();value.entities.add(new com.vela.rw6ha.Config.Entity("sensor.temp_2599","온도"));
        com.vela.rw6ha.Config.Entity desk=new com.vela.rw6ha.Config.Entity("light.desk","Desk");desk.group="Office";value.entities.add(desk);ShadowVault.saved=value.payload();
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();button(activity.getWindow().getDecorView(),"선택목록").performClick();LinearLayout rows=ReflectionHelpers.getField(activity,"selected");
            assertEquals(3,rows.getChildCount());assertTrue(hasText(rows,"light.living_room"));assertTrue(hasText(rows,"sensor.temp_2599"));assertTrue(hasText(rows,"light.desk"));assertFalse((boolean)ReflectionHelpers.getField(activity,"networkBusy"));
            EditText search=ReflectionHelpers.getField(activity,"reviewSearchInput");search.setText("OFFICE");assertEquals(1,rows.getChildCount());assertTrue(hasText(rows,"light.desk"));search.setText("");
            Spinner filter=ReflectionHelpers.getField(activity,"reviewDomainInput");filter.setSelection(com.vela.rw6ha.Config.DOMAINS.indexOf("sensor")+1);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(1,rows.getChildCount());assertTrue(hasText(rows,"sensor.temp_2599"));
            CheckBox onlyFavorites=ReflectionHelpers.getField(activity,"reviewFavoriteInput");onlyFavorites.setChecked(true);assertTrue(hasText(rows,"필터에 맞는 선택 항목이 없습니다. 검색이나 필터를 변경하세요."));
            filter.setSelection(0);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertTrue(hasText(rows,"light.living_room"));favorite(rows).performClick();assertTrue(hasText(rows,"필터에 맞는 선택 항목이 없습니다. 검색이나 필터를 변경하세요."));
            onlyFavorites.setChecked(false);assertEquals(3,rows.getChildCount());button(rows.getChildAt(0),"↓").performClick();assertEquals("sensor.temp_2599",com.vela.rw6ha.Config.parse(ShadowVault.saved).entities.get(0).id);
            button(rows.getChildAt(0),"삭제").performClick();assertEquals(2,rows.getChildCount());assertFalse(hasText(rows,"sensor.temp_2599"));assertEquals(2,com.vela.rw6ha.Config.parse(ShadowVault.saved).entities.size());
            search.setText("Office");button(activity.getWindow().getDecorView(),"엔티티").performClick();button(activity.getWindow().getDecorView(),"선택목록").performClick();rows=ReflectionHelpers.getField(activity,"selected");assertEquals(1,rows.getChildCount());assertTrue(hasText(rows,"light.desk"));assertEquals("Office",((EditText)ReflectionHelpers.getField(activity,"reviewSearchInput")).getText().toString());
        }
    }
    @Test @Config(sdk=28,shadows=ShadowVault.class)
    public void reviewEmptyStateAndDomainCountsAreAvailableBeforeFetching() throws Exception {
        com.vela.rw6ha.Config value=ConfigTest.fixture();value.entities.clear();ShadowVault.saved=value.payload();
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();button(activity.getWindow().getDecorView(),"선택목록").performClick();assertTrue(hasText(activity.getWindow().getDecorView(),"아직 선택한 엔티티가 없습니다. 엔티티 추가에서 선택하세요."));
            button(activity.getWindow().getDecorView(),"엔티티 추가").performClick();assertTrue(domain(activity.getWindow().getDecorView(),"조명 · light").getText().toString().contains("미조회 · 선택 0개"));assertFalse((boolean)ReflectionHelpers.getField(activity,"networkBusy"));
        }
    }
    @Test public void watchNamePreviewMatchesWatchFormatting(){
        assertEquals("Living room",MainActivity.watchName(new com.vela.rw6ha.Config.Entity("light.living_room","거실")));
        assertEquals("Warm Light",MainActivity.watchName(new com.vela.rw6ha.Config.Entity("light.living_room","  Warm   Light  ")));
        assertEquals("Temp upstairs",MainActivity.watchName(new com.vela.rw6ha.Config.Entity("sensor.temp__upstairs","위층 온도")));
        assertEquals("Sensor.___",MainActivity.watchName(new com.vela.rw6ha.Config.Entity("sensor.___","")));
    }
}
