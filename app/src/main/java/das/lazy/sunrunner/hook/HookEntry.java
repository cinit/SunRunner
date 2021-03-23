package das.lazy.sunrunner.hook;

import das.lazy.sunrunner.util.Utils;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {
    public static final String PACKAGE_NAME_MM = "com.tencent.mm";
    public static final String PACKAGE_NAME_SELF = "das.lazy.sunrunner";
    public static final String PACKAGE_NAME_XPOSED_INSTALLER = "de.robv.android.xposed.installer";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        //dumpProcessInfo(lpparam.isFirstApplication);
        switch (lpparam.packageName) {
            case PACKAGE_NAME_SELF:
                XposedHelpers.findAndHookMethod("das.lazy.sunrunner.util.Utils", lpparam.classLoader,
                        "getActiveModuleVersion", XC_MethodReplacement.returnConstant(Utils.CURRENT_MODULE_VERSION));
                break;
            case PACKAGE_NAME_MM:
                TrustAppHook.getInstance().doInit(lpparam.classLoader);
                break;
            default: {
                break;
            }
        }
    }
}
