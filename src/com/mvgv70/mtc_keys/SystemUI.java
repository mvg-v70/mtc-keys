package com.mvgv70.mtc_keys;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LayoutInflated;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

public class SystemUI implements IXposedHookLoadPackage, IXposedHookInitPackageResources
{
  
  private static int id_back = 0;
  private static int id_home = 0;
  private static int id_menu = 0;
  private static View btnBack = null;
  private static View btnHome = null;
  private static View btnMenu = null;
  private static Object phoneStatusBar;
  private final static String TAG = "mtc-keys";
  private final static String SYSTEM_UI_PACKAGE = "com.android.systemui";
  public final static String KEYS_ACTION = "com.mvgv70.mtckeys.action";
  
  @Override
  public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable 
  {
	  
	// load resource
	XC_LayoutInflated loadPackage = new XC_LayoutInflated() {
			
	  @Override
	  public void handleLayoutInflated(LayoutInflatedParam liparam) throws Throwable 
      {
	    Log.d(TAG,"handleLayoutInflated");
	    if (Build.VERSION.SDK_INT > 17)
	    {
	      // Android 4.4
	      id_back = liparam.res.getIdentifier("back", "id", SYSTEM_UI_PACKAGE);
	      id_home = liparam.res.getIdentifier("home", "id", SYSTEM_UI_PACKAGE);
	      id_menu = liparam.res.getIdentifier("menu", "id", SYSTEM_UI_PACKAGE);
	    }
	    else
	    {
	      // Android 4.2
	      id_back = liparam.res.getIdentifier("status_bar_back", "id", SYSTEM_UI_PACKAGE);
	      id_home = liparam.res.getIdentifier("status_bar_home", "id", SYSTEM_UI_PACKAGE);
	      id_menu = liparam.res.getIdentifier("status_bar_menu", "id", SYSTEM_UI_PACKAGE);
	    }
      }
    };
    
    if (!resparam.packageName.equals(SYSTEM_UI_PACKAGE)) return;
	resparam.res.hookLayout(SYSTEM_UI_PACKAGE, "layout", "status_bar", loadPackage);
    Log.d(TAG,"com.android.systemui resource hook OK");
  }
 
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    
    // SystemUIService.onCreate()
    XC_MethodHook onCreate = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        // создать BroadcastReceiver
        IntentFilter li = new IntentFilter(KEYS_ACTION);
        ((Service)param.thisObject).registerReceiver(actionReceiver,li);
        Log.d(TAG,"action receiver created");
      }
    };
    
    // PhoneStatusBar.makeStatusBarView()
    XC_MethodHook makeStatusBarView = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	Log.d(TAG,"PhoneStatusBar");
    	phoneStatusBar = param.thisObject;
        Object mStatusBarWindow = XposedHelpers.getObjectField(param.thisObject, "mStatusBarWindow");
        if (mStatusBarWindow != null)
        {
          // кнопки	
          btnBack = (View)XposedHelpers.callMethod(mStatusBarWindow, "findViewById", new Object[] {Integer.valueOf(id_back)});
          if (btnBack != null) Log.d(TAG,"btnBack.id="+btnBack.getId()); else Log.d(TAG,"btnBack = NULL ("+id_back+")");
          btnHome = (View)XposedHelpers.callMethod(mStatusBarWindow, "findViewById", new Object[] {Integer.valueOf(id_home)});
          if (btnHome != null) Log.d(TAG,"btnHome.id="+btnHome.getId()); else Log.d(TAG,"btnHome = NULL ("+id_home+")");
          btnMenu = (View)XposedHelpers.callMethod(mStatusBarWindow, "findViewById", new Object[] {Integer.valueOf(id_menu)});
          if (btnMenu != null) Log.d(TAG,"btnMenu.id="+btnMenu.getId()); else Log.d(TAG,"btnMenu = NULL ("+id_menu+")");
          Log.d(TAG,"PhoneStatusBar: buttons found");
        }
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
    	XposedHelpers.callMethod(phoneStatusBar, "takeScreenshot", new Object[] {});
      }
      else if (action.equalsIgnoreCase("apps"))
      {
        XposedHelpers.callMethod(phoneStatusBar, "toggleRecentApps", new Object[] {});
      }
      else
        Log.w(TAG,"unknown action "+action);
    }
    
    // нажатие на кнопку
    private void clickButton(View button)
    {
      XposedHelpers.callMethod(button, "sendEvent", new Object[] {Integer.valueOf(KeyEvent.ACTION_DOWN), 0});
      XposedHelpers.callMethod(button, "sendEvent", new Object[] {Integer.valueOf(KeyEvent.ACTION_UP), 0});    	
    }
  };
  
};
