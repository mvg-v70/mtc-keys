package com.mvgv70.mtc_keys;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import java.io.FileInputStream;
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
import android.app.Activity;

public class Radio implements IXposedHookLoadPackage 
{
	
  private static BroadcastReceiver radioReceiver;
  private static Activity mtcRadio;
  private static Properties props = null;
  private final static String INI_FILE_NAME = Environment.getExternalStorageDirectory().getPath()+"/mtc-keys/mtc-keys.ini"; 
  private final static String TAG = "mtc-keys";
  
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    // MTCRadio.onCreate
    XC_MethodHook onCreateRadio = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
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
      	try
      	{
      	  props = new Properties();
      	  props.load(new FileInputStream(INI_FILE_NAME));
      	} catch (Exception e) {
			Log.e(TAG,e.getMessage());
		}
      }
    };
	    
    if (!lpparam.packageName.equals("com.microntek.radio")) return;
    Log.d(TAG,"package com.microntek.radio");
    findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "onCreate", "android.os.Bundle", onCreateRadio);
    Log.d(TAG,"com.microntek.radio hook OK");
  }
    
  // радио обработчик нажатия
  private BroadcastReceiver keyRadioReceiver = new BroadcastReceiver()
  {
	  
    public void onReceive(Context context, Intent intent)
    {
      int keyCode = intent.getIntExtra("keyCode", -1);
      String mapApp = props.getProperty("app_"+keyCode, "");
      if (mapApp.isEmpty())
        // выполним обработчик по-умолчанию если не задано действие на клавишу
        radioReceiver.onReceive(context, intent);
    }
  };
  
};
