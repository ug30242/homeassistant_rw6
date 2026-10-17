package com.vela.rw6ha;
import static org.junit.Assert.*;
import android.view.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
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
}
