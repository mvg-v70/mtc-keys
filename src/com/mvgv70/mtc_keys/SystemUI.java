package com.mvgv70.mtc_keys;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

public class SystemUI implements IXposedHookLoadPackage
{
  
  private static int id_back = 0;
  private static int id_home = 0;
  private static int id_menu = 0;
  private static View btnBack = null;
  private static View btnHome = null;
  private static View btnMenu = null;
  private static Object phoneStatusBar;
  private final static String TAG = "mtc-keys";
  public final static String KEYS_ACTION = "com.mvgv70.mtckeys.action";
   
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    
    // SystemUIService.onCreate()
    XC_MethodHook onCreate = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	Service systemUi = (Service)param.thisObject;
        // создать BroadcastReceiver
        IntentFilter li = new IntentFilter(KEYS_ACTION);
        systemUi.registerReceiver(actionReceiver,li);
        Log.d(TAG,"action receiver created");
        // кнопки
	    id_back = systemUi.getResources().getIdentifier("back", "id", systemUi.getPackageName());
	    id_home = systemUi.getResources().getIdentifier("home", "id", systemUi.getPackageName());
	    id_menu = systemUi.getResources().getIdentifier("menu", "id", systemUi.getPackageName());
      }
    };
    
    // PhoneStatusBar.makeStatusBarView()
    XC_MethodHook makeStatusBarView = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	Log.d(TAG,"PhoneStatusBar");
    	phoneStatusBar = param.thisObject;
        Object mStatusBarWindow = XposedHelpers.getObjectField(phoneStatusBar, "mStatusBarWindow");
        if (mStatusBarWindow != null)
        {
          // кнопки	
          btnBack = (View)XposedHelpers.callMethod(mStatusBarWindow, "findViewById", id_back);
          if (btnBack != null) Log.d(TAG,"btnBack.id="+btnBack.getId()); else Log.d(TAG,"btnBack = NULL ("+id_back+")");
          btnHome = (View)XposedHelpers.callMethod(mStatusBarWindow, "findViewById", id_home);
          if (btnHome != null) Log.d(TAG,"btnHome.id="+btnHome.getId()); else Log.d(TAG,"btnHome = NULL ("+id_home+")");
          btnMenu = (View)XposedHelpers.callMethod(mStatusBarWindow, "findViewById", id_menu);
          if (btnMenu != null) Log.d(TAG,"btnMenu.id="+btnMenu.getId()); else Log.d(TAG,"btnMenu = NULL ("+id_menu+")");
          Log.d(TAG,"PhoneStatusBar: buttons found");
        }
        else
          Log.e(TAG,"mStatusBarWindow not found");
      }
    };
        
    // start hooks
    if (!lpparam.packageName.equals("com.android.systemui")) return;
    Log.d(TAG,"package com.android.systemui");
    XposedHelpers.findAndHookMethod("com.android.systemui.SystemUIService", lpparam.classLoader, "onCreate", onCreate);
    XposedHelpers.findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBar", lpparam.classLoader, "makeStatusBarView", makeStatusBarView);
    Log.d(TAG,"com.android.systemui hook OK");
  }
  
  private BroadcastReceiver actionReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      String action = intent.getStringExtra("action");
      Log.d(TAG,"action receier: "+action);
      // выходим, если не определено
      if (phoneStatusBar == null)
      {
    	Log.e(TAG,"phoneStatusBar == null");
        return;
      }
      if (action.equalsIgnoreCase("back"))
      {
    	if (btnBack != null)
    	  clickButton(btnBack);
      }
      else if (action.equalsIgnoreCase("home"))
      {
    	if (btnHome != null)
    	  clickButton(btnHome);
      }
      else if (action.equalsIgnoreCase("menu"))
      {
    	if (btnMenu != null)
    	  clickButton(btnMenu);
      }
      else if (action.equalsIgnoreCase("screenshot"))
      {
    	XposedHelpers.callMethod(phoneStatusBar, "takeScreenshot");
      }
      else if (action.equalsIgnoreCase("apps"))
      {
        XposedHelpers.callMethod(phoneStatusBar, "toggleRecentApps");
      }
      else if (action.equalsIgnoreCase("sleep"))
      {
        ((AudioManager)context.getSystemService(Context.AUDIO_SERVICE)).setParameters("ctl_key=power");
      }
      else
        Log.w(TAG,"unknown action "+action);
    }
    
    // нажатие на кнопку
    private void clickButton(View button)
    {
      // выходим, если button = NULL
      if (button == null) return;
      XposedHelpers.callMethod(button, "sendEvent", KeyEvent.ACTION_DOWN, 0);
      XposedHelpers.callMethod(button, "sendEvent", KeyEvent.ACTION_UP, 0);    	
    }
  };
  
};
