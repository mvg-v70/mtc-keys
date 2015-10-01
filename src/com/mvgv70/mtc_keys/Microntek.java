package com.mvgv70.mtc_keys;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Properties;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.app.Service;
import android.app.ActivityManager;

public class Microntek implements IXposedHookLoadPackage 
{
	
  private static BroadcastReceiver mtcReceiver;
  private static Context mContext;
  private static Service mtcService;
  private static Properties props = null;
  private static ActivityManager am;
  private static String topActivity;
  private static String nextActivity;
  private static String iniFileName;
  private static boolean inifileLoaded = false;
  private final static String TAG = "mtc-keys";
    
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    // MTCManager.onCreate()
    XC_MethodHook onCreate = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	Log.d(TAG,"onCreate");
      	mContext = (Context)XposedHelpers.getObjectField(param.thisObject, "mContext");
      	am = (ActivityManager)mContext.getSystemService(Context.ACTIVITY_SERVICE);
      	mtcService = ((Service)param.thisObject);
        // показать версию модуля
        try 
        {
     	  Context context = mtcService.createPackageContext(getClass().getPackage().getName(), Context.CONTEXT_IGNORE_SECURITY);
     	  String version = context.getString(R.string.app_version_name);
          Log.d(TAG,"version="+version);
     	} catch (NameNotFoundException e) {}
      	mtcReceiver = (BroadcastReceiver)XposedHelpers.getObjectField(param.thisObject, "CarkeyProc");
      	if (mtcReceiver != null)
      	{
      	  // выключаем receiver
       	  mtcService.unregisterReceiver(mtcReceiver);
          // включим его с нулевым набором событий
      	  IntentFilter ni = new IntentFilter();
      	  mtcService.registerReceiver(mtcReceiver, ni);  
          // изменяем список интентов, кроме интентов кнопок
    	  IntentFilter oi = new IntentFilter();
          oi.addAction("com.microntek.dvdClosed");
          oi.addAction("com.microntek.startApp");
          oi.addAction("com.microntek.light");
          mtcService.registerReceiver(otherReceiver, oi);
      	  // включаем receiver по обработке кнопок
      	  IntentFilter ki = new IntentFilter();
      	  ki.addAction("com.microntek.irkeyDown");
      	  mtcService.registerReceiver(keyServiceReceiver, ki);
      	  Log.d(TAG,"Manager.Receivers changed");
          // ресивер подключения дисков
          IntentFilter mi = new IntentFilter();
          mi.addAction(Intent.ACTION_MEDIA_MOUNTED);
          mi.addDataScheme("file");
          mtcService.registerReceiver(mediaReceiver, mi);
          Log.d(TAG,"Manager. media receiver created");
          // ресивер обработки изменения громкости звука от скорости
      	  Log.d(TAG,"Build.VERSION.SDK_INT="+Build.VERSION.SDK_INT);
      	  if (Build.VERSION.SDK_INT > 17)
      	  {
      		// для версий 4.4
      		IntentFilter vi = new IntentFilter();
        	vi.addAction("com.microntek.VOLUME_CHANGED");
        	mtcService.registerReceiver(volumeReceiver, vi);
        	Log.d(TAG,"Manager. volume receiver created");
      	  }
      	}
      	// чтение настроечного файла
      	iniFileName = getIniFileName();
      	readSettings();
      }
    };
    
    // start hooks
    if (!lpparam.packageName.equals("android.microntek.service")) return;
    Log.d(TAG,"package android.microntek.service");
    XposedHelpers.findAndHookMethod("android.microntek.service.MicrontekServer", lpparam.classLoader, "onCreate", onCreate);
    Log.d(TAG,"com.microntek.service hook OK");
  }
  
  // имя настроечного файла
  public static String getIniFileName()
  {
	String fileName = Environment.getExternalStorageDirectory().getPath()+"/mtc-keys/mtc-keys.ini";
	File f = new File(fileName);
	if (!f.exists())
      // ищем настроечный файл на внешней карте, если нет на внутренней
	  fileName = "/mnt/external_sd/mtc-keys/mtc-keys.ini";
	return fileName;
  }
  
  // чтение настроечного файла
  private void readSettings()
  {
	try
	{
	  Log.d(TAG,"inifile load from "+iniFileName);
	  //
	  props = new Properties();
	  props.load(new FileInputStream(iniFileName));
	  Log.d(TAG,"ini file loaded, line count="+props.size());
	  inifileLoaded = true;
	} 
	catch (Exception e) 
	{
      Log.e(TAG,e.getMessage());
	}
  }

  
  // общий обработчик остальных событий: dvdClosed, startApp, light
  private BroadcastReceiver otherReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      // вызываем обработчик microntek
      mtcReceiver.onReceive(context, intent);
    }
  };
    
  // общий обработчик нажатия и переопределения кнопок
  private BroadcastReceiver keyServiceReceiver = new BroadcastReceiver()
  {
	  
    public void onReceive(Context context, Intent intent)
    {
      int keyCode = intent.getIntExtra("keyCode", -1);
      Log.d(TAG,"keyCode="+keyCode);
      // app = приложение, action = {back,home,tasks,apps,menu}, activity, intent
      String app = props.getProperty("app_"+keyCode, "").trim();
      String action = props.getProperty("action_"+keyCode, "").trim();
      String activity = props.getProperty("activity_"+keyCode, "").trim();
      String intentName = props.getProperty("intent_"+keyCode, "").trim();
      String event = props.getProperty("event_"+keyCode, "").trim();
      if (!app.isEmpty())
    	// запуск приложения 
      	runApp(context, app);
      else if (!activity.isEmpty())
      	// запуск activity
        runActivity(context, activity);
      else if (!action.isEmpty())
        // действие
    	runAction(context, action);
      else if (!intentName.isEmpty())
        // интент
      	sendIntent(context, intentName);
      else if (!event.isEmpty())
        // переопределение кнопки
        buttonPress(context, intent, event);
      else
        // выполним обработчик по-умолчанию, если на клавишу ничего не назначено
        mtcReceiver.onReceive(context, intent);
    }
    
    // запуск приложения
    private void runApp(Context context, String appName)
    {
      String runApp = appName;
      getActivityList();
      if (appName.equals(topActivity))
        // запускаем предыдущее приложение в списке
        runApp = nextActivity;
      Log.d(TAG,"run app="+runApp);
      Intent appIntent = context.getPackageManager().getLaunchIntentForPackage(runApp);
      if (appIntent != null)
      {
    	appIntent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(appIntent);
      }
      else
      {
        Log.w(TAG,"no activity found for "+runApp);
        goLauncher(context);
      }
    }
    
    // запуск activity
    private void runActivity(Context context, String activity)
    {
      int i = activity.indexOf("/");
      if (i > 0)
      {
        String packageName = activity.substring(0,i);
        String className = activity.substring(i+1);
        Log.d(TAG,"start activity "+packageName+"/"+className);
        // по-другому приводит к перезагрузке
        Intent appIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (appIntent != null)
        {
          ComponentName cn = new ComponentName(packageName, className);
          try 
          {
            context.getPackageManager().getActivityInfo(cn, PackageManager.GET_META_DATA);
            appIntent.setComponent(cn);
            appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            appIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            Log.d(TAG,appIntent.toString());
            context.startActivity(appIntent);
          } 
          catch (NameNotFoundException e) 
          {
            Log.w(TAG,"activity "+className+" not found");
            goLauncher(context);
          }
        }
        else
        {
          Log.w(TAG,"no activity found for package "+packageName);
          goLauncher(context);
        }
      }
      else
        Log.w(TAG,"wrong format for activity: "+activity);
    }
    
    // send intent
    private void sendIntent(Context context, String intentName)
    {
      Log.d(TAG,"intent "+intentName);
      context.sendBroadcast(new Intent(intentName));
    }
    
    // обработка action: back, home, menu, apps
    private void runAction(Context context, String action)
    {
      Intent intent = new Intent(SystemUI.KEYS_ACTION);
      intent.putExtra("action", action);
      context.sendBroadcast(intent);
    }
    
    private void getActivityList()
    {
      try 
      {
    	List<ActivityManager.RunningTaskInfo> taskList = am.getRunningTasks(2);
    	topActivity = taskList.get(0).topActivity.getPackageName();
    	if (taskList.size() > 1)
    	  nextActivity = taskList.get(1).baseActivity.getPackageName();
    	else
    	  nextActivity = "";
      } catch (Exception e) {}
    }
    
    // переопределение кнопки
    public void buttonPress(Context context, Intent intent, String event)
    {
      int keyCode;
      try
      {
        keyCode = Integer.parseInt(event);
        // изменим значение кода клавиши в интенте
        intent.putExtra("keyCode", keyCode);
        // выполним обработчик по-умолчанию
        Log.d(TAG,"emulate event "+keyCode);
        mtcReceiver.onReceive(context, intent);
      }
      catch (Exception e)
      {
        Log.d(TAG,"invalid event "+event);
      }
    }
    
    private void goLauncher(Context context)
    {
      XposedHelpers.callMethod(mtcService, "startHome", new Object[] {});
    }
        
  };
  
  // подключение дисков
  private BroadcastReceiver mediaReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      String drivePath = intent.getData().getPath();
      Log.d(TAG,"ACTION_MEDIA_MOUNTED: "+drivePath);
      // если ini-файл не загружен
      if (!inifileLoaded)
        // если подключилиск на котором находится настроечный файл
        if (iniFileName.startsWith(drivePath))
          readSettings();
    }
  };

  // дополнительно для mtc-service
  // установим внутреннюю переменную с величиной громкости по интенту com.microntek.VOLUME_CHANGED
  private BroadcastReceiver volumeReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      int mCurVolume = XposedHelpers.getIntField(mtcService,"mCurVolume");
      int mNewVolume = intent.getIntExtra("volume", mCurVolume);
      if (mNewVolume > 0) 
        XposedHelpers.setIntField(mtcService,"mCurVolume",mNewVolume);
    }
  };


};

