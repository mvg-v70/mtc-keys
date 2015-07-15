package com.mvgv70.mtc_keys;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.KeyEvent;

public class SystemUI implements IXposedHookLoadPackage 
{
  private final static String TAG = "mtc-keys";
  private static Object mWm = null;
  private static Context mContext = null; 

@Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    // BaseStatusBar.start()
    XC_MethodHook start = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"start begin");
        mWm = XposedHelpers.getObjectField(param.thisObject, "mWindowManagerService");
        Log.d(TAG,"IWindowManager found");
        mContext = (Context)XposedHelpers.getObjectField(param.thisObject, "mContext");
        Log.d(TAG,"Context found");
        //
        Log.d(TAG,"register Receiver");
        IntentFilter li = new IntentFilter();
        li.addAction("com.mvgv70.mtc_keys");
        mContext.registerReceiver(actionReceiver,li);
        //
        Log.d(TAG,"start end");
      }
    };
	    
    if (!lpparam.packageName.equals("com.android.systemui")) return;
    // Log.d(TAG,"package com.android.systemui");
    // XposedHelpers.findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBar", lpparam.classLoader, "start", start);
    // Log.d(TAG,"com.android.systemui hook OK");
  }
    
  //
  private BroadcastReceiver actionReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      Log.d(TAG,"actionReceiver, action="+intent.getAction());
      if (mWm == null) Log.d(TAG,"mWm is NULL");
      String action = intent.getStringExtra("action");
      Log.d(TAG,"intent action="+action);
      if (mWm != null)
        try
        {
          Log.d(TAG,"1");
          XposedHelpers.callMethod(mWm, "isViewServerRunning", new Object[] {});
          Log.d(TAG,"2");
          //
          KeyEvent eventDown = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK);
          Object[] downParams = new Object[2];
          downParams[0] = eventDown;
          downParams[1] = true;
          XposedHelpers.callMethod(mWm, "injectKeyEvent", downParams);
          Log.d(TAG,"back down");
          //
          KeyEvent eventUp = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK);
          Object[] upParams = new Object[2];
          upParams[0] = eventUp;
          upParams[1] = true;
          XposedHelpers.callMethod(mWm, "injectKeyEvent", upParams);
          Log.d(TAG,"back up");
        } catch (Exception e)
        {
    	  Log.w(TAG,e.getMessage());
        }
    }
};
  
  
};
