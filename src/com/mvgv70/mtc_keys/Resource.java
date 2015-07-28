package com.mvgv70.mtc_keys;

import android.os.Build;
import android.util.Log;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LayoutInflated;

public class Resource implements IXposedHookInitPackageResources {
	
  private static int id_back = 0;
  private static int id_home = 0;
  private static int id_menu = 0;
  private final static String SYSTEM_UI_PACKAGE = "com.android.systemui";
  private final static String TAG = "mtc-keys";
		
  @Override
  public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable 
  {
    if (!resparam.packageName.equals(SYSTEM_UI_PACKAGE)) return;
	resparam.res.hookLayout(SYSTEM_UI_PACKAGE, "layout", "status_bar", new XC_LayoutInflated() {
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
    });
    Log.d(TAG,"com.android.systemui resource hook OK");
  }
  
  public static int getBackButton()
  {
    return id_back;
  }
  
  public static int getHomeButton()
  {
    return id_home;
  }
  
  public static int getMenuButton()
  {
    return id_menu;
  }
  
}
