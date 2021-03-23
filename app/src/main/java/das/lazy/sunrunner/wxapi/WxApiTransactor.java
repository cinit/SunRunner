package das.lazy.sunrunner.wxapi;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import das.lazy.sunrunner.GlobalApplication;
import das.lazy.sunrunner.wxapi.cmd.BaseReq;
import das.lazy.sunrunner.wxapi.cmd.SendAuth;

import java.security.MessageDigest;

public class WxApiTransactor {
    public static final String CONTENT = "_mmessage_content";
    public static final String SDK_VERSION = "_mmessage_sdkVersion";
    public static final String APP_PACKAGE = "_mmessage_appPackage";
    public static final String CHECK_SUM = "_mmessage_checksum";

    public static final String WXAPP_MSG_ENTRY_CLASSNAME = "com.tencent.mm.plugin.base.stub.WXEntryActivity";

    private static WxApiTransactor SELF = null;
    private static final String TAG = "WxApiTransactor";
    private static final String APP_ID = "wxf83de11533c17d91";

    public static synchronized WxApiTransactor getInstance() {
        if (SELF == null) {
            SELF = new WxApiTransactor();
        }
        return SELF;
    }

    public boolean handleIntent(Intent intent, IWXAPIEventHandler wxApiHandler) {
        String content = intent.getStringExtra(CONTENT);
        int sdkVersion = intent.getIntExtra(SDK_VERSION, 0);
        String appPkgName = intent.getStringExtra(APP_PACKAGE);
        if (appPkgName == null || appPkgName.length() == 0) {
            Log.e(TAG, "invalid argument");
            return false;
        } else {
            int cmd = intent.getIntExtra("_wxapi_command_type", 0);
            switch (cmd) {
                case 1:
                    wxApiHandler.onResp(new SendAuth.Resp(intent.getExtras()));
                    return true;
                default:
                    Log.e(TAG, "unknown cmd = " + cmd);
                    return false;
            }
        }
    }

    public boolean sendReq(BaseReq baseReq) {
        String str2;
        if (!baseReq.checkArgs()) {
            str2 = "sendReq checkArgs fail";
        } else {
            Log.e(TAG, "sendReq, req type = " + baseReq.getType());
            Bundle bundle = new Bundle();
            baseReq.toBundle(bundle);
            C1490a.C1491a aVar = new C1490a.C1491a();
            aVar.f5109n = bundle;
            aVar.f5108m = "weixin://sendreq?appid=" + APP_ID;
            aVar.targetPkgName_f5106k = "com.tencent.mm";
            aVar.targetClassName_f5107l = WXAPP_MSG_ENTRY_CLASSNAME;
            return C1490a.m4990a(GlobalApplication.getInstance(), aVar);
        }
        Log.w(TAG, str2);
        return false;
    }


    /* renamed from: com.tencent.mm.sdk.a.a */
    public static final class C1490a {

        /* renamed from: com.tencent.mm.sdk.a.a$a */
        public static class C1491a {
            public int flags = -1;

            /* renamed from: k */
            public String targetPkgName_f5106k;

            /* renamed from: l */
            public String targetClassName_f5107l;

            /* renamed from: m */
            public String f5108m;

            /* renamed from: n */
            public Bundle f5109n;
        }

        /* renamed from: a */
        @SuppressLint("WrongConstant")
        public static boolean m4990a(Context context, C1491a aVar) {
            if (context == null || aVar == null) {
                Log.e("MMessageAct", "send fail, invalid argument");
                return false;
            } else {
//                aVar.targetClassName_f5107l = aVar.targetPkgName_f5106k + ".wxapi.WXEntryActivity";
                aVar.targetClassName_f5107l = "com.tencent.mm.plugin.base.stub.WXEntryActivity";
                Log.i("MMessageAct", "send, targetPkgName = " + aVar.targetPkgName_f5106k + ", targetClassName = " + aVar.targetClassName_f5107l);
                Intent intent = new Intent();
                intent.setClassName(aVar.targetPkgName_f5106k, aVar.targetClassName_f5107l);
                if (aVar.f5109n != null) {
                    intent.putExtras(aVar.f5109n);
                }
                String packageName = context.getPackageName();
                intent.putExtra(SDK_VERSION, 570490883);
                intent.putExtra(APP_PACKAGE, packageName);
                intent.putExtra(CONTENT, aVar.f5108m);
                intent.putExtra(CHECK_SUM, C1494b.m4992a(aVar.f5108m, 570490883, packageName));
                if (aVar.flags == -1) {
                    intent.addFlags(268435456).addFlags(134217728);
                } else {
                    intent.setFlags(aVar.flags);
                }
                try {
                    context.startActivity(intent);
                    Log.i("MMessageAct", "send mm message, intent=" + intent);
                    return true;
                } catch (Exception e) {
                    Log.i("MMessageAct", "send fail, ex = %s", e);
                    return false;
                }
            }
        }
    }

    public static final class C1494b {
        /* renamed from: a */
        public static byte[] m4992a(String str, int i, String str2) {
            StringBuilder stringBuffer = new StringBuilder();
            if (str != null) {
                stringBuffer.append(str);
            }
            stringBuffer.append(i);
            stringBuffer.append(str2);
            stringBuffer.append("mMcShCsTr");
            return C1489b.m4989a(stringBuffer.substring(1, 9).getBytes()).getBytes();
        }
    }

    public static final class C1489b {
        /* renamed from: a */
        public static String m4989a(byte[] data) {
            char[] hexChars = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
            try {
                MessageDigest instance = MessageDigest.getInstance("MD5");
                instance.update(data);
                byte[] checksum = instance.digest();
                char[] cArr2 = new char[(checksum.length * 2)];
                int i = 0;
                for (byte b : checksum) {
                    int i2 = i + 1;
                    cArr2[i] = hexChars[(b >>> 4) & 15];
                    i = i2 + 1;
                    cArr2[i2] = hexChars[b & 15];
                }
                return new String(cArr2);
            } catch (Exception unused) {
                return null;
            }
        }
    }


}
