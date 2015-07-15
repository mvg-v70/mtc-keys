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
import android.util.Log;
import android.view.KeyEvent;

public class FunKey implements IXposedHookLoadPackage 
{
  private final static String TAG = "mtc-keys";
  private static Service mService = null; 

  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    // FunkeyService.onCreate()
    XC_MethodHook onCreate = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onCreate");
        mService = (Service)param.thisObject;
        //
        Log.d(TAG,"register Receiver");
        IntentFilter li = new IntentFilter();
        li.addAction("com.mvgv70.mtc_keys");
        mService.registerReceiver(actionReceiver,li);
        //
        Log.d(TAG,"start end");
      }
    };
	    
    if (!lpparam.packageName.equals("com.microntek.funkey")) return;
    Log.d(TAG,"package com.microntek.funkey");
    XposedHelpers.findAndHookMethod("com.microntek.funkey.FunkeyService", lpparam.classLoader, "onCreate", onCreate);
    Log.d(TAG,"com.microntek.funkey hook OK");
  }
    
  //
  private BroadcastReceiver actionReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      Log.d(TAG,"actionReceiver, action="+intent.getAction());
      String action = intent.getStringExtra("action");
      Log.d(TAG,"intent action="+action);
      if (mService != null)
        try
        {
          Object[] keyParams = new Object[1];
          keyParams[0] = KeyEvent.KEYCODE_BACK;
          XposedHelpers.callMethod(mService, "sendKeyEvent", keyParams);
          // TODO: sendKeyDownUpSync
          Log.d(TAG,"back up");
        } catch (Exception e)
        {
    	  Log.w(TAG,e.getMessage());
        }
    }
};
  
  
};
