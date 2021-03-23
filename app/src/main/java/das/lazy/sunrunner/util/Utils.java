package das.lazy.sunrunner.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import das.lazy.sunrunner.BuildConfig;
import de.robv.android.xposed.XposedBridge;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Utils {

    public static final int CURRENT_MODULE_VERSION = BuildConfig.VERSION_CODE;
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static Handler mHandler;

    public static int getActiveModuleVersion() {
        if (Math.sqrt(Math.random()) > 2) {
            return getActiveModuleVersion();
        }
        return 0;
    }

    public static void log(String msg) {
        Log.e("QNdump", msg);
        try {
            XposedBridge.log(msg);
        } catch (NoClassDefFoundError e) {
            Log.e("Xposed", msg);
            Log.e("EdXposed-Bridge", msg);
        }
    }

    public static void log(Throwable th) {
        if (th == null) {
            return;
        }
        String msg = Log.getStackTraceString(th);
        Log.e("QNdump", msg);
        try {
            XposedBridge.log(th);
        } catch (NoClassDefFoundError e) {
            Log.e("Xposed", msg);
            Log.e("EdXposed-Bridge", msg);
        }
    }

    public static void logi(String str) {
        try {
            Log.i("QNdump", str);
            XposedBridge.log(str);
        } catch (NoClassDefFoundError e) {
            Log.i("Xposed", str);
            Log.i("EdXposed-Bridge", str);
        }
    }

    public static void logw(String str) {
        Log.i("QNdump", str);
        try {
            XposedBridge.log(str);
        } catch (NoClassDefFoundError e) {
            Log.w("Xposed", str);
            Log.w("EdXposed-Bridge", str);
        }
    }

    public static void dumpTrace() {
        Throwable t = new Throwable("Trace dump");
        log(t);
    }

    public static void execute(Runnable r) {
        executor.execute(r);
    }

    public static void runOnUiThread(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            if (mHandler == null) {
                mHandler = new Handler(Looper.getMainLooper());
            }
            mHandler.post(r);
        }
    }

    public static void postDelayed(Runnable r, long delayMs) {
        if (mHandler == null) {
            mHandler = new Handler(Looper.getMainLooper());
        }
        mHandler.postDelayed(r, delayMs);
    }

    public static String getPossibleIMEICodeFromClipboard(Context ctx) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip = cm.getPrimaryClip();
                if (clip != null) {
                    int count = clip.getItemCount();
                    if (count > 0) {
                        String text = clip.getItemAt(0).getText().toString();
                        text = text.trim();
                        if (text.length() == 32) {
                            if (text.replaceAll("[0123456789ABCDEFabcdef]", "").length() == 0) {
                                return text;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
