package com.mvgv70.mtc_keys;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.os.Environment;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.Service;
import android.app.ActivityManager;
import android.util.SparseArray;

public class Microntek implements IXposedHookLoadPackage 
{
	
  private static BroadcastReceiver mtcReceiver;
  private static Context mContext;
  private static Service mtcService;
  private static Properties props = null;
  private static ActivityManager am;
  SparseArray<String> appMap = new SparseArray<String>();
  private final static String INI_FILE_NAME = Environment.getExternalStorageDirectory().getPath()+"/mtc-keys/mtc-keys.ini"; 
  private final static String TAG = "mtc-keys";
  
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    // MTCManager.onCreate
    XC_MethodHook onCreateManager = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
      	mContext = (Context)XposedHelpers.getObjectField(param.thisObject, "mContext");
      	am = (ActivityManager)mContext.getSystemService(Context.ACTIVITY_SERVICE);
      	mtcService = ((Service)param.thisObject);
      	mtcReceiver = (BroadcastReceiver)XposedHelpers.getObjectField(param.thisObject, "CarkeyProc");
      	if (mtcReceiver != null)
      	{
      	  // выключаем receiver
       	  mtcService.unregisterReceiver(mtcReceiver);
          // включим его с нулевым набором событий
      	  IntentFilter ni = new IntentFilter();
      	  mtcService.registerReceiver(mtcReceiver, ni);  
          // измен€ем список интентов, кроме интентов кнопок
    	  IntentFilter mi = new IntentFilter();
          mi.addAction("com.microntek.dvdClosed");
          mi.addAction("com.microntek.startApp");
          mi.addAction("com.microntek.light");
          mtcService.registerReceiver(otherReceiver, mi);
      	  // включаем receiver по обработке кнопок
      	  IntentFilter ki = new IntentFilter();
      	  ki.addAction("com.microntek.irkeyDown");
      	  mtcService.registerReceiver(keyServiceReceiver, ki);
      	  //
      	  Log.d(TAG,"Manager.Receiver changed");
      	}
      	try
      	{
      	  Log.d(TAG,"inifile load from "+INI_FILE_NAME);
      	  props = new Properties();
      	  props.load(new FileInputStream(INI_FILE_NAME));
      	  Log.d(TAG,"ini file loaded, line count="+props.size());
      	} catch (Exception e) {
			Log.e(TAG,e.getMessage());
		}
      }
    };
    
    if (!lpparam.packageName.equals("android.microntek.service")) return;
    Log.d(TAG,"package android.microntek.service");
    findAndHookMethod("android.microntek.service.MicrontekServer", lpparam.classLoader, "onCreate", onCreateManager);
    Log.d(TAG,"com.microntek.service hook OK");
  }
  
  // общий обработкчик остальных событий: dvdClosed, startApp, light
  private BroadcastReceiver otherReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      // вызываем обработчик microntek
      mtcReceiver.onReceive(context, intent);
    }
  };
    
  // общий обработчик нажати€ и переопределени€ кнопок
  private BroadcastReceiver keyServiceReceiver = new BroadcastReceiver()
  {
	  
    public void onReceive(Context context, Intent intent)
    {
      int keyCode = intent.getIntExtra("keyCode", -1);
      Log.d(TAG,"keyCode="+keyCode);
      String mapApp = props.getProperty("app_"+keyCode, "");
      if (mapApp.isEmpty())
        // выполним обработчик по-умолчанию
        mtcReceiver.onReceive(context, intent);
      else
        // запуск приложени€ mapApp
    	RunApp(context, mapApp, keyCode);
    }
    
    private void RunApp(Context context, String appName, int keyCode)
    {
      String runApp;
      String topApp = getTopActivityPackageName();
      if (topApp.equals(appName))
      {
    	// возвращаем приложение из которого было запущено
        runApp = appMap.get(keyCode);
      }
      else
      {
    	// сохран€ем текущее приложение
    	appMap.put(keyCode, topApp);
    	runApp = appName;
      }
      Log.d(TAG,"run app="+runApp);
      Intent appIntent = context.getPackageManager().getLaunchIntentForPackage(runApp);
      if (appIntent != null)
      {
        context.startActivity(appIntent);
      }
      else
      {
        Log.w(TAG,"no activity found for "+runApp);
        goLauncher(context);
      }
    }
    
    private String getTopActivityPackageName()
    {
      try {
        return  ((ActivityManager.RunningTaskInfo)am.getRunningTasks(1).get(0)).topActivity.getPackageName();
      } catch (Exception localException) {}
      return null;
    }
    
    private void goLauncher(Context context)
    {
      XposedHelpers.callMethod(mtcService, "startHome", new Object[] {});
    }
    
  };
  
};
