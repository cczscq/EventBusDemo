package cn.spade.android.eventbus.demo.tool;

import android.util.Log;

import cn.spade.android.eventbus.demo.BuildConfig;


/**
 *
 * @author zhaosc
 */
public class LogTool {
  public static boolean isPrint = BuildConfig.DEBUG;//是否输出日志

  public static void info(String tag, String msg) {
	if (isPrint) {
	  Log.i(tag, msg);
	}
  }

  public static void warn(String tag, String msg) {
	if (isPrint) {
	  Log.w(tag, msg);
	}
  }

  public static void error(String tag, String msg) {
	if (isPrint) {
	  Log.e(tag, msg);
	}
  }

  public static void error(String tag, Throwable e) {
	if (isPrint) {
	  Log.e(tag, tag, e);
	}
  }

  public static void exception(String tag, Throwable e) {
	if (isPrint) {
	  Log.e(tag, tag, e);
	}
  }
}
