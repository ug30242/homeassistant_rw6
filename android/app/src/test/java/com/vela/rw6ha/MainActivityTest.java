package com.vela.rw6ha;
import static org.junit.Assert.*;
import android.view.*;
import android.widget.*;
import java.io.StringReader;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Resetter;
import org.robolectric.util.ReflectionHelpers;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class MainActivityTest {
    private Button button(View root,String label){if(root instanceof Button&&label.equals(((Button)root).getText().toString()))return (Button)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){Button found=button(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    @Test public void nativeTabsRenderWithoutBackgroundServicesAndWindowProtectsToken(){
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();assertTrue((activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0);
            for(String tab:new String[]{"엔티티","워치","서버"}){Button b=button(activity.getWindow().getDecorView(),tab);assertNotNull(b);b.performClick();}
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
            MainActivity activity=controller.get();button(activity.getWindow().getDecorView(),"엔티티").performClick();
            CheckBox check=favorite(activity.getWindow().getDecorView());assertNotNull(check);assertTrue(check.isChecked());
            check.performClick();assertFalse(check.isChecked());assertFalse(com.vela.rw6ha.Config.parse(ShadowVault.saved).entities.get(0).favorite);
            check.performClick();assertTrue(com.vela.rw6ha.Config.parse(ShadowVault.saved).entities.get(0).favorite);
            ShadowVault.fail=true;check.performClick();assertTrue(check.isChecked());assertTrue(com.vela.rw6ha.Config.parse(ShadowVault.saved).entities.get(0).favorite);ShadowVault.fail=false;
            button(activity.getWindow().getDecorView(),"서버").performClick();button(activity.getWindow().getDecorView(),"엔티티").performClick();assertTrue(favorite(activity.getWindow().getDecorView()).isChecked());
        }
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();button(activity.getWindow().getDecorView(),"엔티티").performClick();assertTrue(favorite(activity.getWindow().getDecorView()).isChecked());
        }
    }
}
