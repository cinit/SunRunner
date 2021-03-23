package das.lazy.sunrunner.cap;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.widget.Toast;
import das.lazy.sunrunner.SplashActivity;
import das.lazy.sunrunner.util.Utils;

public class CaptureController {

    private static boolean sCaptureServiceRunning = false;

    public static void setCaptureServiceRunning(final boolean z) {
        sCaptureServiceRunning = z;
        final SplashActivity activity = SplashActivity.getInstance();
        if (activity != null) {
            Utils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.invalidateOptionsMenu();
                    if (z) {
                        Utils.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Intent i = new Intent();
                                    i.setComponent(new ComponentName("com.aipao.hanmoveschool", "com.aipao.hanmoveschool.activity.LoginActivity"));
                                    i.setAction(Intent.ACTION_MAIN);
                                    i.addCategory(Intent.CATEGORY_LAUNCHER);
                                    activity.startActivity(i);
                                    Toast.makeText(activity, "正在拉起阳光长跑APP", Toast.LENGTH_SHORT).show();
                                } catch (ActivityNotFoundException e) {
                                    Toast.makeText(activity, "请先安装并登录阳光长跑APP", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }, 1500);
                    }
                }
            });
        }
    }

    public static boolean isCaptureServiceRunning() {
        return sCaptureServiceRunning;
    }

    public static void onCaptureImeiCode(final String code) {
        SplashActivity activity = SplashActivity.getInstance();
        if (activity != null) {
            Utils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.loginWithCapturedImeiCode(code);
                }
            });
        }
    }
}
