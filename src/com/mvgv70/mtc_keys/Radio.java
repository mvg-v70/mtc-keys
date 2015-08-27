package com.mvgv70.mtc_keys;

import java.io.FileInputStream;
import java.util.Properties;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.os.Bundle;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.Activity;

public class Radio implements IXposedHookLoadPackage 
{
	
  private static BroadcastReceiver radioReceiver;
  private static Activity mtcRadio;
  private static Properties props = null;
  private final static String TAG = "mtc-keys";
  
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    // MTCRadio.onCreate(Bindle)
    XC_MethodHook onCreate = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	Log.d(TAG,"onCreate");
      	mtcRadio = ((Activity)param.thisObject);
      	radioReceiver = (BroadcastReceiver)XposedHelpers.getObjectField(param.thisObject, "mtckeyproc");
      	if (radioReceiver != null)
      	{
      	  // выключаем receiver
      	  mtcRadio.unregisterReceiver(radioReceiver);
      	  // включим его с нулевым набором событий
      	  IntentFilter ni = new IntentFilter();
    	  mtcRadio.registerReceiver(radioReceiver, ni);
      	  // включаем receiver по обработке кнопок
      	  IntentFilter ki = new IntentFilter();
      	  ki.addAction("com.microntek.irkeyUp");
      	  ki.addAction("com.microntek.irkeyDown");
      	  mtcRadio.registerReceiver(keyRadioReceiver, ki);
      	  //
      	  Log.d(TAG,"Radio.Receiver changed");
      	}
      	// настройка кнопок
      	try
      	{
      	  String iniFileName = Microntek.getIniFileName();
      	  props = new Properties();
      	  props.load(new FileInputStream(iniFileName));
      	} catch (Exception e) {
		  Log.e(TAG,e.getMessage());
		}
      }
    };
    // MTCRadio.onDestroy()
    XC_MethodHook onDestroy = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        // выключаем receiver
    	Log.d(TAG,"onDestroy");
    	mtcRadio.unregisterReceiver(keyRadioReceiver);
      }
    };
    
	// start hooks  
    if (!lpparam.packageName.equals("com.microntek.radio")) return;
    Log.d(TAG,"package com.microntek.radio");
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "onCreate", Bundle.class, onCreate);
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "onDestroy", onDestroy);
    Log.d(TAG,"com.microntek.radio hook OK");
  }
    
  // радио обработчик нажатия
  private BroadcastReceiver keyRadioReceiver = new BroadcastReceiver()
  {
	  
    public void onReceive(Context context, Intent intent)
    {
      int keyCode = intent.getIntExtra("keyCode", -1);
      String app = props.getProperty("app_"+keyCode, "").trim();
      String action = props.getProperty("action_"+keyCode, "").trim();
      String activity = props.getProperty("activity_"+keyCode, "").trim();
      String intentName = props.getProperty("intent_"+keyCode, "").trim();
      if (app.isEmpty() && action.isEmpty() && activity.isEmpty() && intentName.isEmpty())
        // выполним обработчик по-умолчанию если не заданы никакие действия на клавишу
        radioReceiver.onReceive(context, intent);
    }
  };
  
};
