package com.mvgv70.mtc_keys;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.media.AudioManager;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
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
  private static Properties props = new Properties();
  private static ActivityManager am;
  private static AudioManager mcu;
  private static String topActivity;
  private static String nextActivity;
  private final static String EXTERNAL_SD = "/mnt/external_sd";
  private final static String MTC_KEYS_INI = EXTERNAL_SD+"/mtc-keys/mtc-keys.ini";
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
      	mcu = ((AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE));
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
      	}
      	// чтение настроечного файла
      	if (Environment.getStorageState(new File(EXTERNAL_SD)).equals(Environment.MEDIA_MOUNTED))
      	  readSettings();
      	else
      	  // прочитаем настройки при подключении external_sd
      	  createMediaReceiver();
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
    return MTC_KEYS_INI;
  }
  
  // чтение настроечного файла
  private void readSettings()
  {
	props.clear();
	// mtc-keys.ini
    try
    {
      Log.d(TAG,"inifile load from "+MTC_KEYS_INI);
      // загрузка из файла
      props.load(new FileInputStream(MTC_KEYS_INI));
      Log.d(TAG,"ini file loaded, line count="+props.size());
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
      // app = приложение, action = {back,home,tasks,apps,menu,sleep}, activity, intent, event
      String app = props.getProperty("app_"+keyCode, "").trim();
      String action = props.getProperty("action_"+keyCode, "").trim();
      String activity = props.getProperty("activity_"+keyCode, "").trim();
      String intentName = props.getProperty("intent_"+keyCode, "").trim();
      String event = props.getProperty("event_"+keyCode, "").trim();
      String media = props.getProperty("media_"+keyCode, "").trim();
      String keyevent = props.getProperty("keyevent_"+keyCode, "").trim();
      String command = props.getProperty("command_"+keyCode, "").trim();
      String mcucmd = props.getProperty("mcu_"+keyCode, "").trim();
      String function = props.getProperty("function_"+keyCode, "").trim();
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
      else if (!media.isEmpty())
        // управление внешним плеером
        mediaPress(context, media);
      else if (!keyevent.isEmpty())
        // отсылка KeyEvent
        keyPress(context, keyevent);
      else if (!command.isEmpty())
        // выполнение команды
        executeCmd(command);
      else if (!mcucmd.isEmpty())
        // выполнение команды mcu
        sendMcuCommand(mcucmd);
      else if (!function.isEmpty())
        // выполнение функции MicrontekServer
        callFunction(function);
      else
        // выполним обработчик по-умолчанию, если на клавишу ничего не назначено
        mtcReceiver.onReceive(context, intent);
    }
  };
    
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
      //
      Intent appIntent = new Intent();
      ComponentName cn = new ComponentName(packageName, className);
      try 
      {
        context.getPackageManager().getActivityInfo(cn, PackageManager.GET_META_DATA);
        appIntent.setComponent(cn);
        appIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_NEW_TASK);
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
      Log.w(TAG,"wrong format for activity: "+activity);
  }
    
  // послать intent без параметров
  private void sendIntent(Context context, String intentName)
  {
    Log.d(TAG,"intent "+intentName);
    context.sendBroadcast(new Intent(intentName));
  }
    
  // обработка action: back, home, menu, apps
  private void runAction(Context context, String action)
  {
    // остальные action обрабатываются в SystemUI
    Intent intent = new Intent(SystemUI.KEYS_ACTION);
    intent.putExtra("action", action);
    context.sendBroadcast(intent);
  }
    
  // список top активити
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
   
  // выход в лаунчер
  private void goLauncher(Context context)
  {
    XposedHelpers.callMethod(mtcService, "startHome");
  }
  
  // послать ACTION_MEDIA_BUTTON с управлением медиаплеером
  public void mediaPress(Context context, String command)
  {
    if (command.equalsIgnoreCase("play"))
      sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    else if (command.equalsIgnoreCase("next"))
      sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT);
    else if (command.equalsIgnoreCase("prev"))
      sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    else
      Log.e(TAG,"invalid media key code "+command);
  }
  
  // послать ACTION_MEDIA_BUTTON с заданным кодом
  public void keyPress(Context context, String command)
  {
	try
	{
      int keyCode = Integer.decode(command);
      sendMediaKey(context, keyCode);
	}
	catch (Exception e) 
	{
      Log.w(TAG,"invalid keyCode "+command);
	}
  }
  
  // отсылка ACTION_MEDIA_BUTTON
  public void sendMediaKey(Context context, int keyCode) 
  {
    Log.d(TAG,"send media key "+keyCode);
    long eventTime = SystemClock.uptimeMillis();
    // down
    Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
    // KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
    KeyEvent downEvent = new KeyEvent(eventTime-20, eventTime-20, KeyEvent.ACTION_DOWN, keyCode, 0);
    downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
    context.sendOrderedBroadcast(downIntent, null);
    // up
    Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
    // KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
    KeyEvent upEvent = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0);
    upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
    context.sendOrderedBroadcast(upIntent, null);
  }
  
  // выполнение команды с привилегиями root
  private void executeCmd(String command)
  {
    Log.d(TAG,"> "+command);
    // su (as root)
    Process process = null;
 	DataOutputStream os = null;
    InputStream err = null;
 	try 
 	{
 	  process = Runtime.getRuntime().exec("su");
 	  os = new DataOutputStream(process.getOutputStream());
 	  err = process.getErrorStream();
 	  os.writeBytes(command+" \n");
      os.writeBytes("exit \n");
      os.flush();
      os.close();
      process.waitFor();
      // анализ ошибок
      byte[] buffer = new byte[1024];
      int len = err.read(buffer);
      if (len > 0)
      {
        String errmsg = new String(buffer,0,len);
        Log.e(TAG,errmsg);
      } 
    } 
 	catch (Exception e) 
 	{
      Log.e(TAG,e.getMessage());
    }
  }
  
  // посылка команды mcu с помощью AudioManager
  private void sendMcuCommand(String command)
  {
    command = command.replaceAll("->", "=");
    Log.d(TAG,"am.setParameters("+command+")");
    mcu.setParameters(command);
  }
  
  private void callFunction(String function)
  {
    Log.d(TAG,function+"();");
    try
    {
      XposedHelpers.callMethod(mtcService, function);
    }
    catch (Error e)
    {
      Log.d(TAG,e.getMessage());
    }
    catch (Exception e)
    {
      Log.d(TAG,e.getMessage());
    }
  }

  // включить обработчик подключения носителей
  private void createMediaReceiver()
  {
	IntentFilter ui = new IntentFilter();
    ui.addAction(Intent.ACTION_MEDIA_MOUNTED);
    ui.addDataScheme("file");
    mtcService.registerReceiver(mediaReceiver, ui);
    Log.d(TAG,"media mount receiver created");
  }
  
  // обработчик MEDIA_MOUNT
  private BroadcastReceiver mediaReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      String action = intent.getAction(); 
      String drivePath = intent.getData().getPath();
      Log.d(TAG,"media receiver:"+drivePath+" "+action);
      if (action.equals(Intent.ACTION_MEDIA_MOUNTED))
    	// если подключается external_sd
        if (MTC_KEYS_INI.startsWith(drivePath))
          // читаем настройки
          readSettings();
    }
  };
    
};

