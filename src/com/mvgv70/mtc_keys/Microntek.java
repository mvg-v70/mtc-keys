package com.mvgv70.mtc_keys;

import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import com.mvgv70.utils.IniFile;
import com.mvgv70.utils.Utils;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.media.AudioManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserHandle;
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
	
  private static Context mContext;
  private static Service mtcService;
  private static IniFile props = new IniFile();
  private static ActivityManager am;
  private static AudioManager mcu;
  private static Object im = null;
  private static String topActivity;
  private static String nextActivity;
  // пауза между нажатиями в мс
  private static int clickDelay = 600;
  // обработчик нажатий
  private static Handler handler = null;
  // параметры последнего нажатия
  private static int lastClickCode = 0;
  private static long lastClickTime = 0;
  private static int clickCount = 1;
  // constants
  private static String EXTERNAL_SD = "/mnt/external_sd/";
  private static String MTC_KEYS_INI = EXTERNAL_SD+"mtc-keys/mtc-keys.ini";
  private final static String SETTINGS_SECTION = "settings";
  private final static String CLICK_SECTION = "click.";
  private final static String INTENT_MTC_KEYS_EVENT = "com.mvgv70.mtc-keys.event";
  private final static String TAG = "mtc-keys";
    
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    // MTCManager.onCreate()
    XC_MethodHook onCreate = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onCreate");
        // mContext = (Context)XposedHelpers.getObjectField(param.thisObject, "mContext");
        mContext = (Context)param.thisObject;
        am = (ActivityManager)mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mcu = ((AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE));
        mtcService = ((Service)param.thisObject);
        // показать версию модуля
        try 
        {
          Context context = mtcService.createPackageContext(getClass().getPackage().getName(), Context.CONTEXT_IGNORE_SECURITY);
          String version = context.getString(R.string.app_version_name);
          Log.d(TAG,"version="+version);
          Log.d(TAG,"android "+Build.VERSION.RELEASE);
        } catch (NameNotFoundException e) {}
        // путь к файлу из build.prop
        EXTERNAL_SD = Utils.getModuleSdCard();
        MTC_KEYS_INI = EXTERNAL_SD+"mtc-keys/mtc-keys.ini";
        // 
        Log.d(TAG,EXTERNAL_SD+" "+Environment.getStorageState(new File(EXTERNAL_SD)));
        if (Environment.getStorageState(new File(EXTERNAL_SD)).equals(Environment.MEDIA_MOUNTED))
          readSettings();
        else
          // прочитаем настройки при подключении карты
          createMediaReceiver();
        // создадим BroadcastReceivers и Handler
        createReceivers();
      }
    };
    
    // ContextWrapper.SendBroadcastAsUser()
    XC_MethodHook sendBroadcast = new XC_MethodHook() {
	           
      @Override
      protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        Intent intent = (Intent)param.args[0];
        String action = intent.getAction();
        Context context = (Context)param.thisObject;
        if (action.equals("com.microntek.irkeyDown"))
        {
          int keyCode = intent.getIntExtra("keyCode", 0);
          long currentTime = SystemClock.uptimeMillis();
          getActivityList();
          if ((keyCode == lastClickCode) && ((currentTime-lastClickTime) < clickDelay))
            // быстрое нажатие
            clickCount++;
          else
            // обычное нажатие
            clickCount = 1;
          lastClickTime = currentTime;
          lastClickCode = keyCode;
          Log.d(TAG,"keyCode="+keyCode+" ("+clickCount+")");
          // если есть event_xx переопределить keyCode и продолжить выполнение
          int decodeKey = keyNeedDecode(topActivity, clickCount, keyCode);
          if (decodeKey > 0)
          {
            // заменяем код кнопки
            intent.removeExtra("keyCode");
            intent.putExtra("keyCode",decodeKey);
            Log.d(TAG,"event_"+keyCode+"="+decodeKey);
          }
          else
          {
            // если есть другой обработчик не выполнять далее
            int keyHandle = keyNeedHandle(topActivity, clickCount, keyCode);
            if (keyHandle > 0)
            {
              Intent keyIntent = new Intent(INTENT_MTC_KEYS_EVENT);
              keyIntent.putExtra("keyCode", keyCode);
              keyIntent.putExtra("section", keyHandle);
              keyIntent.putExtra("click", clickCount);
              keyIntent.putExtra("topActivity", topActivity);
              context.sendBroadcast(keyIntent);
              if (!props.getBoolValue("click."+clickCount, "default_"+keyCode, false))
                // не выполняем далее SendBroadcast
                param.setResult(null);
            }
          }
        }
      }
    };
    
    // start hooks
    if (!lpparam.packageName.equals("android.microntek.service")) return;
    Utils.readXposedMap();
    Utils.setTag(TAG);
    Log.d(TAG,"package android.microntek.service");
    XposedHelpers.findAndHookMethod("android.microntek.service.MicrontekServer", lpparam.classLoader, "onCreate", onCreate);
    XposedHelpers.findAndHookMethod("android.content.ContextWrapper", lpparam.classLoader, "sendBroadcastAsUser", Intent.class, UserHandle.class, sendBroadcast);
    try
    {
      // InputManager для эмуляции нажатий
      Class<?> clazz = XposedHelpers.findClass("android.hardware.input.InputManager", lpparam.classLoader);
      im = XposedHelpers.callStaticMethod(clazz, "getInstance");
    }
    catch (Error e)
    {
      Log.e(TAG,e.getMessage());
    }
    Log.d(TAG,"com.microntek.service hook OK");
  }
  
  // чтение настроечного файла
  private static void readSettings()
  {
    props.clear();
    // mtc-keys.ini
    try
    {
      Log.d(TAG,"inifile load from "+MTC_KEYS_INI);
      // загрузка из файла
      props.loadFromFile(MTC_KEYS_INI);
      Log.d(TAG,"ini file loaded");
      // параметры
      clickDelay = props.getIntValue(SETTINGS_SECTION, "doubleclick.time", 400);
      Log.d(TAG,"doubleclick.time="+clickDelay);
    } 
    catch (Exception e) 
    {
      Log.e(TAG,e.getMessage());
    }
  }
  
  // есть ли обработчик кнопки: 0-нет обработчика, 1-в общей секции, 2-в локальной секции пакета
  private static int keyNeedHandle(String packageName, int click, int keyCode)
  {
    if (findAnyKeySection(packageName+"."+click, keyCode))
      // нашли в секции для пакета
      return 2;
    if (findAnyKeySection(CLICK_SECTION+click, keyCode))
      // нашли в общей секции 
      return 1;
    // нигде не нашли обработчика event_xx
    return 0;
  }
  
  // нужно ли переопределение кода кнопки
  private static int keyNeedDecode(String packageName, int click, int keyCode)
  {
    int result = findEventKeySection(packageName+"."+click, keyCode);
    if (result > 0)
      // нашли в секции для пакета
      return result;
    result = findEventKeySection(CLICK_SECTION+click, keyCode);
    if (result > 0)
      // нашли в общей секции 
      return result;
    // нигде не нашли обработчика event_xx
    return 0;
  }
  
  // поиск в секции настройки для кода НЕ event_xx
  private static boolean findAnyKeySection(String section, int keyCode)
  {
    String key;
    boolean result = false;
    Iterator<String> iterator = props.enumKeys(section);
    while (iterator.hasNext())
    {
      key = (String)iterator.next();
      if (key.endsWith("_"+keyCode))
        if (!key.equals("event_"+keyCode))
        {
          result = true;
          break;
        }
    }
    return result;
  }
  
  // поиск в секции настройки для кода event_xx
  private static int findEventKeySection(String section, int keyCode)
  {
    String key;
    int result = 0;
    Iterator<String> iterator = props.enumKeys(section);
    if (iterator == null) return 0;
    while (iterator.hasNext())
    {
      key = (String)iterator.next();
      if (key.equals("event_"+keyCode))
      {
        String value = props.getValue(section,"event_"+keyCode,"0");
        result = Integer.parseInt(value);
        break;
      }
    }
    return result;
  }
 
  // обработчик нажатий 
  private static class KeyHandler extends Handler
  {
    public void handleMessage(Message msg)
    {
      int keyCode = msg.what;
      int clickNo = msg.arg1;
      String topActivity = (String)msg.obj;
      Log.d(TAG,"handle "+keyCode+" ("+clickNo+") "+msg.arg2+" "+topActivity);
      if (msg.arg2 == 1)
        // глобальный обработчик
        buttonPressSection(keyCode, mtcService, CLICK_SECTION+clickNo);
      else
       // локальный обработчик для приложения
       buttonPressSection(keyCode, mtcService, topActivity+"."+clickNo);
    }
  };
  
  // реакция на нажатие: обработка секции
  private static boolean buttonPressSection(int keyCode, Context context, String section)
  {
    boolean result = true;
    // app = приложение, action = {back,home,tasks,apps,menu,sleep}, activity, intent, event, command, mcucmd, finction injectkey
    String app = props.getValue(section,"app_"+keyCode);
    String action = props.getValue(section,"action_"+keyCode).trim();
    String activity = props.getValue(section,"activity_"+keyCode);
    String intentName = props.getValue(section,"intent_"+keyCode);
    String media = props.getValue(section,"media_"+keyCode).trim();
    int keyevent = props.getIntValue(section,"keyevent_"+keyCode,0);
    String command = props.getValue(section,"command_"+keyCode);
    String mcucmd = props.getValue(section,"mcu_"+keyCode);
    String function = props.getValue(section,"function_"+keyCode).trim();
    int injectkey = props.getIntValue(section,"inject_"+keyCode,0);
    if (!app.isEmpty())
    {
      // запуск приложения
      if (!app.equals("null"))
        runApp(context, app);
    }
    else if (!activity.isEmpty())
      // запуск activity
      runActivity(context, activity);
    else if (!action.isEmpty())
      // действие
      runAction(context, action);
    else if (!intentName.isEmpty())
      // интент
      sendIntent(context, intentName);
    else if (!media.isEmpty())
      // управление внешним плеером
      mediaPress(context, media);
    else if (keyevent > 0)
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
    else if (injectkey > 0)
      // key event injection
      injectKey(injectkey);
    else
    {
      Log.w(TAG,"can not handle "+keyCode+" in ["+section+"]");
      result = false;
    }
    return result;
  }  
    
  // запуск приложения
  private static void runApp(Context context, String appName)
  {
    String runApp = appName;
    if (appName.equals(topActivity) || appName.isEmpty())
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
  private static void runActivity(Context context, String activity)
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
  private static void sendIntent(Context context, String intentName)
  {
    Log.d(TAG,"intent "+intentName);
    context.sendBroadcast(new Intent(intentName));
  }
    
  // обработка action: back, home, menu, apps
  private static void runAction(Context context, String action)
  {
	Log.d(TAG,"action="+action);
    if (action.equals("back"))
      injectKey(KeyEvent.KEYCODE_BACK);
    else if (action.equals("home"))
      injectKey(KeyEvent.KEYCODE_HOME);
    else if (action.equals("home"))
      injectKey(KeyEvent.KEYCODE_MENU);
    else if (action.equals("apps"))
      injectKey(KeyEvent.KEYCODE_APP_SWITCH);
    else if (action.equals("menu"))
      injectKey(KeyEvent.KEYCODE_MENU);
    else if (action.equals("screenshot"))
      callFunction("startScreenShot");
    else if (action.equals("sleep"))
      sendMcuCommand("ctl_key->power");
    else if (action.equals("screenoff"))
      sendMcuCommand("ctl_key->screenbrightness");
    else if (action.equals("settings"))
      readSettings();
    else if (action.equals("switch"))
      runApp(context,"");
    else if (action.equals("null"))
    {
      // ничего не делаем
    }
    else
      Log.w(TAG,"invalid action: "+action);
  }
    
  // список top активити
  private static void getActivityList()
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

  // выход в лаунчер
  private static void goLauncher(Context context)
  {
    try
    {
      Intent intent = new Intent("android.intent.action.MAIN");
      intent.addCategory("android.intent.category.HOME");
      intent.addFlags(270532608);
      context.startActivity(intent);
    }
    catch (Exception e) { }
  }
  
  // послать ACTION_MEDIA_BUTTON с управлением медиаплеером
  public static void mediaPress(Context context, String command)
  {
    if (command.equalsIgnoreCase("play"))
      injectKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    else if (command.equalsIgnoreCase("next"))
      injectKey(KeyEvent.KEYCODE_MEDIA_NEXT);
    else if (command.equalsIgnoreCase("prev"))
      injectKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    else
      Log.e(TAG,"invalid media key code "+command);
  }
  
  // послать ACTION_MEDIA_BUTTON с заданным кодом
  public static void keyPress(Context context, int keyCode)
  {
    sendMediaKey(context, keyCode);
  }
  
  // отсылка ACTION_MEDIA_BUTTON
  public static void sendMediaKey(Context context, int keyCode) 
  {
    Log.d(TAG,"send media key "+keyCode);
    long eventTime = SystemClock.uptimeMillis();
    // down
    Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
    KeyEvent downEvent = new KeyEvent(eventTime-20, eventTime-20, KeyEvent.ACTION_DOWN, keyCode, 0);
    downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
    context.sendOrderedBroadcast(downIntent, null);
    // up
    Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
    KeyEvent upEvent = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0);
    upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
    context.sendOrderedBroadcast(upIntent, null);
  }
  
  // инъекция события
  private static void injectKey(int keyCode)
  {
    if (im == null) return;
    try
    {
      Log.d(TAG,"inject key "+keyCode);
      long eventTime = SystemClock.uptimeMillis();
      // down
      KeyEvent downEvent = new KeyEvent(eventTime-20, eventTime-20, KeyEvent.ACTION_DOWN, keyCode, 0);
      XposedHelpers.callMethod(im, "injectInputEvent", downEvent, 0);
      // app
      KeyEvent upEvent = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0);
      XposedHelpers.callMethod(im, "injectInputEvent", upEvent, 0);
    }
    catch (Exception e) 
    {
      Log.e(TAG,"injectKey: "+e.getMessage());
    }
  }
  
  // выполнение команды с привилегиями root
  private static void executeCmd(String command)
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
  private static void sendMcuCommand(String command)
  {
    command = command.replaceAll("->", "=");
    Log.d(TAG,"am.setParameters("+command+")");
    mcu.setParameters(command);
  }
  
  private static void callFunction(String function)
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
      Log.e(TAG,e.getMessage());
    }
  }
  
  // создание Handler
  private void createReceivers()
  {
    // create receiver
    IntentFilter ki = new IntentFilter();
    ki.addAction(INTENT_MTC_KEYS_EVENT);
    mtcService.registerReceiver(keysReceiver, ki);
    Log.d(TAG,"key receiver created");
    // create handler
    handler = new KeyHandler();
    Log.d(TAG,"KeyHandler created");
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
      Log.d(TAG,"media mounted:"+drivePath+" "+action);
      if (action.equals(Intent.ACTION_MEDIA_MOUNTED))
        // если подключается EXTERNAL_SD
        if (MTC_KEYS_INI.startsWith(drivePath))
        {
          // читаем настройки
          readSettings();
          // убрать MEDIA_MOUNT receiver
          mtcService.unregisterReceiver(this);
        }
    }
  };
  
  // обработчик INTENT_MTC_KEYS_EVENT
  private BroadcastReceiver keysReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      int keyCode = intent.getIntExtra("keyCode", 0);
      int clickNo = intent.getIntExtra("click", 1);
      int section = intent.getIntExtra("section", 1);
      String topActivity = intent.getStringExtra("topActivity");
      // если на кнопку есть действие в какой-нибудь секции
  	  handler.removeMessages(keyCode);
      Message msg = Message.obtain(handler, keyCode, clickNo, section, topActivity);
      // отложенное выполнение через handler
      handler.sendMessageDelayed(msg, clickDelay);
    }
  };
  
};

