package das.lazy.sunrunner.hook;

import das.lazy.sunrunner.util.Utils;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.*;

import static das.lazy.sunrunner.util.Utils.log;

public class TrustAppHook {


    public static final XC_MethodHook invokeRecord = new XC_MethodHook(200) {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws IllegalAccessException, IllegalArgumentException {
            Member m = param.method;
            StringBuilder ret = new StringBuilder(m.getDeclaringClass().getSimpleName() + "->" + ((m instanceof Method) ? m.getName() : "<init>") + "(");
            Class[] argt;
            if (m instanceof Method) {
                argt = ((Method) m).getParameterTypes();
            } else if (m instanceof Constructor) {
                argt = ((Constructor) m).getParameterTypes();
            } else {
                argt = new Class[0];
            }
            for (int i = 0; i < argt.length; i++) {
                if (i != 0) {
                    ret.append(",\n");
                }
                ret.append(param.args[i]);
            }
            ret.append(")=").append(param.getResult());
            Utils.logi(ret.toString());
            ret = new StringBuilder("↑dump object:" + m.getDeclaringClass().getCanonicalName() + "\n");
            Field[] fs = m.getDeclaringClass().getDeclaredFields();
            for (int i = 0; i < fs.length; i++) {
                fs[i].setAccessible(true);
                ret.append(i < fs.length - 1 ? "├" : "↓").append(fs[i].getName()).append("=").append(fs[i].get(param.thisObject)).append("\n");
            }
            Utils.logi(ret.toString());
            Utils.dumpTrace();
        }
    };

    public static TrustAppHook SELF;
    private boolean first_stage_inited = false;

    private TrustAppHook() {
    }

    public void doInit(ClassLoader rtLoader) throws Throwable {
        if (first_stage_inited) {
            return;
        }
        try {
            for (Method m : rtLoader.loadClass("com.tencent.mm.pluginsdk.model.app.q").getDeclaredMethods()) {
                if ("a".equals(m.getName()) && Modifier.isStatic(m.getModifiers()) && m.getReturnType() == boolean.class && m.getParameterTypes().length == 4) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String pkgName = (String) param.args[2];
                            Object appInfo = param.args[1];
                            if (HookEntry.PACKAGE_NAME_SELF.equals(pkgName)) {
                                XposedHelpers.setObjectField(appInfo, "field_packageName", HookEntry.PACKAGE_NAME_SELF);
                                param.setResult(true);
                            }
                        }
                    });
                    Utils.log("Hook isAppValid: " + m);
                    break;
                }
            }
            first_stage_inited = true;
        } catch (Throwable e) {
            if ((e + "").contains("com.google.android.webview")) {
                return;
            }
            log(e);
            throw e;
        }
    }

    public static TrustAppHook getInstance() {
        if (SELF == null) {
            SELF = new TrustAppHook();
        }
        return SELF;
    }
}
