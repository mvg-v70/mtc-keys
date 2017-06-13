package com.mvgv70.utils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import android.text.TextUtils;
import android.util.Log;

//
// version 1.2.3
//

public class Utils 
{
  private final static String INI_FILE_SD_BUILDPROP = "persist.sys.mvgv70.card";	
  private final static String EXTERNAL_SD = "/mnt/external_sd/";
  private final static String XPOSED_MAP_PATH = "/system/etc/mvgv70.xposed.map";
  private final static String CLASS_PARAM = ".class";
  private static IniFile xposedMap = new IniFile();
  private static String TAG = "mvgv70-xposed";
	
  // TAG
  public static void setTag(String newTag)
  {
    TAG = newTag;
  }
  
  // системный параметр из build.prop
  public static String getSystemProperty(String key) 
  {
    String value = null;
    try 
    {
      value = (String)Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, key);
    } 
    catch (Exception e) 
    {
      Log.e(TAG,e.getMessage());
    }
    return value;
  }
  
  // sd-карта для чтения файла настроек
  public static String getModuleSdCard()
  {
    String value = getSystemProperty(INI_FILE_SD_BUILDPROP);
    Log.d(TAG,INI_FILE_SD_BUILDPROP+"="+value);
    if (TextUtils.isEmpty(value))
      return EXTERNAL_SD;
    else
    {
      if (!value.endsWith("/")) value = value.concat("/");
      Log.d(TAG,"value="+value);
      return value;
    }
  }
  
  // чтение карты полей и функций xposed
  public static void readXposedMap()
  {
    xposedMap.clear();
    try 
    {
      Log.d(TAG,"read xposed map from "+XPOSED_MAP_PATH);
      xposedMap.loadFromFile(XPOSED_MAP_PATH);
    } 
    catch (Exception e) 
    {
      Log.w(TAG,e.getMessage());
    }
  }
  
  // отладочный вывод карты полей и функций
  public static void LogXposedMap()
  {
    Log.d(TAG,"");
    Log.d(TAG,XPOSED_MAP_PATH);
    xposedMap.LogProps(TAG);
    Log.d(TAG,"");
  }
  
  // получение обфусцированного имени класса
  public static String getXposedMapClass(String TAG, String className)
  {
    String value = xposedMap.getValue(className,CLASS_PARAM,className);
    return value;
  }
  
  // получение обфусцированного имени функции или поля
  public static String getXposedMapValue(String TAG, String section, String key)
  {
    // если нет строки вернет null
    String value = xposedMap.getValue(section, key, null);
    if (value == null)
      return key;
    else
      return value;
  }
  
  // перехват вызова метода
  public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) 
  {
    String nameOfMethod = getXposedMapValue(TAG, className, methodName);
    if (!nameOfMethod.isEmpty())
    {
      String nameOfClass = getXposedMapClass(TAG, className);
      Log.d(TAG,"findAndHook "+nameOfClass+"."+nameOfMethod);
      return XposedHelpers.findAndHookMethod(nameOfClass, classLoader, nameOfMethod, parameterTypesAndCallback);
    }
    else
    {
      Log.w(TAG,className+"."+methodName+" not hooked");
      return null;
    }
  }
  
  // перехват вызова конструктора
  public static XC_MethodHook.Unhook findAndHokConstructor(String className, ClassLoader classLoader, Object... parameterTypesAndCallback)
  {
    String nameOfClass = getXposedMapClass(TAG, className);
    Log.d(TAG,"findAndHok "+nameOfClass+" constructor");
    return XposedHelpers.findAndHookConstructor(nameOfClass, classLoader, parameterTypesAndCallback);
  }
  
  // вызов метода
  public static Object callMethod(Object obj, String methodName, Object... args) 
  {
    String className = obj.getClass().getName();
    String nameOfMethod = getXposedMapValue(TAG, className, methodName);
    if (nameOfMethod.isEmpty()) return null;
    try
    {
      return XposedHelpers.callMethod(obj, nameOfMethod, args);
    }
    catch (Error e)
    {
      Log.e(TAG,obj.getClass().getName()+":"+nameOfMethod+" -> "+e.getMessage());
      return null;
    }
  }
  
  // получение объектного поля
  public static Object getObjectField(Object obj, String fieldName)
  {
    String nameOfFiled = getXposedMapValue(TAG, obj.getClass().getName(), fieldName);
    if (nameOfFiled.isEmpty()) return null;
    try
    {
      return XposedHelpers.getObjectField(obj, nameOfFiled);
    }
    catch (Error e)
    {
      Log.e(TAG,obj.getClass().getName()+":"+nameOfFiled+" -> "+e.getMessage());
      return null;
    }
  }
  
  // установка объектного поля
  public static void setObjectField(Object obj, String fieldName, Object value)
  {
    String nameOfFiled = getXposedMapValue(TAG, obj.getClass().getName(), fieldName);
    if (nameOfFiled.isEmpty()) return;
    try
    {
      XposedHelpers.setObjectField(obj, nameOfFiled, value);
    }
    catch (Error e)
    {
      Log.e(TAG,obj.getClass().getName()+":"+nameOfFiled+" -> "+e.getMessage());
    }
  }
  
  // получение целочисленного поля
  public static int getIntField(Object obj, String fieldName)
  {
    String nameOfFiled = getXposedMapValue(TAG, obj.getClass().getName(), fieldName);
    if (nameOfFiled.isEmpty()) return 0;
    try
    {
      return XposedHelpers.getIntField(obj, nameOfFiled);
    }
    catch (Error e)
    {
      Log.e(TAG,obj.getClass().getName()+":"+nameOfFiled+" -> "+e.getMessage());
      return 0;
    }
  }
  
  // установка целочисленного поля
  public static void setIntField(Object obj, String fieldName, int value)
  {
    String nameOfFiled = getXposedMapValue(TAG, obj.getClass().getName(), fieldName);
    if (nameOfFiled.isEmpty()) return;
    try
    {
      XposedHelpers.setIntField(obj, nameOfFiled, value);
    }
    catch (Error e)
    {
      Log.e(TAG,obj.getClass().getName()+":"+nameOfFiled+" -> "+e.getMessage());
    }
  }
  
  //получение boolean поля
  public static Boolean getBooleanField(Object obj, String fieldName)
  {
    String nameOfFiled = getXposedMapValue(TAG, obj.getClass().getName(), fieldName);
    if (nameOfFiled.isEmpty()) return false;
    try
    {
      return XposedHelpers.getBooleanField(obj, nameOfFiled);
    }
    catch (Error e)
    {
      Log.e(TAG,obj.getClass().getName()+":"+nameOfFiled+" -> "+e.getMessage());
      return false;
    }
  }
  
  // получение поля типа boolean
  public static void setBooleanField(Object obj, String fieldName, boolean value)
  {
    String nameOfFiled = getXposedMapValue(TAG, obj.getClass().getName(), fieldName);
    if (nameOfFiled.isEmpty()) return;
    try
    {
      XposedHelpers.setBooleanField(obj, nameOfFiled, value);
    }
    catch (Error e)
    {
      Log.e(TAG,obj.getClass().getName()+":"+nameOfFiled+" -> "+e.getMessage());
    }
  }
  
}
