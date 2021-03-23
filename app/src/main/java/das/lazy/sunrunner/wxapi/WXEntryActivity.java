package das.lazy.sunrunner.wxapi;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import das.lazy.sunrunner.GlobalApplication;
import das.lazy.sunrunner.LoginRunLogic;
import das.lazy.sunrunner.wxapi.cmd.BaseReq;
import das.lazy.sunrunner.wxapi.cmd.BaseResp;
import das.lazy.sunrunner.wxapi.cmd.SendAuth;

public class WXEntryActivity extends Activity implements IWXAPIEventHandler {
    private WxApiTransactor api;
    private String code;
    private GlobalApplication myApp;

    private static final String TAG = "WXEntryActivity";

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.myApp = (GlobalApplication) getApplication();
        this.api = WxApiTransactor.getInstance();
        try {
            this.api.handleIntent(getIntent(), this);
        } catch (Exception e) {
            Log.e(TAG, "onCreate: WxApiTransactor.handleIntent", e);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        this.api = WxApiTransactor.getInstance();
        this.api.handleIntent(intent, this);
    }

    @Override
    public void onReq(BaseReq baseReq) {
        switch (baseReq.getType()) {
            case 3:
            case 4:
                finish();
                break;
            default:
                finish();
        }
    }

    @Override
    public void onResp(BaseResp baseResp) {
        int i = baseResp.errCode;
        if (i == -4) {
            Toast.makeText(this, "用户拒绝...", Toast.LENGTH_SHORT).show();
            finish();
        } else if (i == -2) {
            Toast.makeText(this, "用户取消...", Toast.LENGTH_SHORT).show();
            finish();
        } else if (i != 0) {
            finish();
        } else {
            try {
                this.code = ((SendAuth.Resp) baseResp).code.trim();
                if (this.code == null || "".equals(this.code)) {
                    Toast.makeText(this, "分享微信成功...", Toast.LENGTH_SHORT).show();
                } else {
                    this.myApp.setCode(this.code);
                    Toast.makeText(this, code, Toast.LENGTH_SHORT).show();
                    LoginRunLogic.onWxLogin(this, this.code);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            finish();
        }
    }
}
