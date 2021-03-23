package das.lazy.sunrunner;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import das.lazy.sunrunner.cap.CaptureController;
import das.lazy.sunrunner.cap.LocalCaptureService;
import das.lazy.sunrunner.data.*;
import das.lazy.sunrunner.util.BadResponseException;
import das.lazy.sunrunner.util.NonUiThread;
import das.lazy.sunrunner.util.ResUtils;
import das.lazy.sunrunner.util.Utils;
import das.lazy.sunrunner.wxapi.WxApiTransactor;
import das.lazy.sunrunner.wxapi.cmd.SendAuth;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class SplashActivity extends AppCompatActivity {
    @SuppressLint("StaticFieldLeak")
    private static volatile SplashActivity SELF = null;

    private static final int VPN_REQUEST_CODE = 0x0F;

    private UserToken token;
    private UserInfo userInfo;
    private TextView tvMainTitle, tvSubTitle, tvExtraInfo, tvValidCount, tvInvalidCount;
    private volatile AlertDialog loginProgressDialog;
    private volatile AlertDialog runningProgressDialog;
    private ListView lvRunRecords;
    private ArrayList<RunRecord> mRunRecords;
    private LayoutInflater inflater;
    private boolean reloginRequired = true;

    private final BaseAdapter mRunRecordAdapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return mRunRecords == null ? 0 : mRunRecords.size();
        }

        @Override
        public Object getItem(int position) {
            return mRunRecords.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.run_record_item, parent, false);
            }
            convertView.setBackgroundColor(position % 2 == 0 ? 0x12FFFFFF : 0x12000000);
            RunRecord record = mRunRecords.get(position);
            TextView date, lengths, valid, invalid, duration, steps, speed;
            date = convertView.findViewById(R.id.runRecordItem_date);
            lengths = convertView.findViewById(R.id.runRecordItem_length);
            valid = convertView.findViewById(R.id.runRecordItem_valid);
            invalid = convertView.findViewById(R.id.runRecordItem_invalid);
            duration = convertView.findViewById(R.id.runRecordItem_duration);
            steps = convertView.findViewById(R.id.runRecordItem_steps);
            speed = convertView.findViewById(R.id.runRecordItem_speed);
            date.setText(record.resultDate.replaceAll("[年月]", "-").replace("日", "") + " " + String.format("%02d", record.resultHour) + "时");
            lengths.setText(record.costDistance + "/" + record.avaLength + "m");
            if (record.notCountReason == 0) {
                valid.setVisibility(View.VISIBLE);
                invalid.setVisibility(View.GONE);
            } else {
                valid.setVisibility(View.GONE);
                invalid.setVisibility(View.VISIBLE);
                invalid.setText(String.format("无效(%d)", record.notCountReason));
            }
            duration.setText("用时" + record.costTime.replaceAll("[时分]", ":").replaceAll("[秒]", ""));
            steps.setText(record.stepNum + "步");
            speed.setText(String.format("%.2f m/s", record.speed));
            return convertView;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        SELF = this;
        super.onCreate(savedInstanceState);
        inflater = LayoutInflater.from(this);
        setContentView(R.layout.main_activity);
        tvMainTitle = findViewById(R.id.mainActivity_mainTitleTextView);
        tvSubTitle = findViewById(R.id.mainActivity_subTitleTextView);
        lvRunRecords = findViewById(R.id.mainActivity_recordListView);
        tvExtraInfo = findViewById(R.id.mainActivity_additionalTextView);
        tvValidCount = findViewById(R.id.mainActivity_validCountTextView);
        tvInvalidCount = findViewById(R.id.mainActivity_invalidCountTextView);
        lvRunRecords.setAdapter(mRunRecordAdapter);
        String wxc = getIntent().getStringExtra("wxLoginCode");
        if (wxc != null) {
            startSecondaryLogin(wxc);
            getIntent().removeExtra("wxLoginCode");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String wxc = intent.getStringExtra("wxLoginCode");
        if (wxc != null) {
            startSecondaryLogin(wxc);
            intent.removeExtra("wxLoginCode");
        }
    }

    @UiThread
    private void startSecondaryLogin(final String wxCode) {
        Utils.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                Utils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        executeSecondaryLogin(wxCode);
                    }
                });
            }
        });
    }

    @UiThread
    private void executeSecondaryLogin(final String wxCode) {
        loginProgressDialog = ProgressDialog.show(this, "请稍后", "正在登录...", false, false);
        Utils.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    UserToken result = LoginRunLogic.requestLoginWithWxCode(wxCode);
                    DaoManager.saveSession(result);
                    reloginRequired = false;
                    SplashActivity.this.token = result;
                    UserInfo user = LoginRunLogic.requestUserInfo(token);
                    SplashActivity.this.userInfo = user;
                    DaoManager.saveUserInfo(user);
                    Utils.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (loginProgressDialog != null) {
                                loginProgressDialog.dismiss();
                                loginProgressDialog = null;
                            }
                            Toast.makeText(SplashActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                            updateTitleInfo();
                        }
                    });
                } catch (final Exception e) {
                    Utils.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showGeneralErrorDialog(e);
                            if (loginProgressDialog != null) {
                                loginProgressDialog.dismiss();
                                loginProgressDialog = null;
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateTitleInfo();
    }

    @UiThread
    private void updateTitleInfo() {
        token = DaoManager.getLastSession();
        if (token == null || (userInfo = DaoManager.getUserInfo(token.userId)) == null) {
            tvMainTitle.setText("未登录");
            tvSubTitle.setText("请先登录");
            tvExtraInfo.setText("");
        } else {
            tvMainTitle.setText(userInfo.nickName);
            if (userInfo.isSchoolMode == 1) {
                tvSubTitle.setText(String.format("路程要求: %dm\n速度要求: %.1f - %.1f m/s",
                        userInfo.schoolRunLength, userInfo.schoolRunMinSpeed, userInfo.schoolRunMaxSpeed));
                tvExtraInfo.setText(userInfo.userName + " " + userInfo.schoolName);
            } else {
                tvSubTitle.setText("提示: 本软件只适用于校园版");
                tvExtraInfo.setText(userInfo.userName);
            }
            Utils.execute(new Runnable() {
                @Override
                public void run() {
                    if (reloginRequired) {
                        if (doReloginWithImeiCode()) {
                            doRequestGetRecords(true);
                        }
                    } else {
                        doRequestGetRecords(true);
                    }
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.CATEGORY_ALTERNATIVE, R.id.menu_showLoginInfo, 0, "登录信息");
        menu.add(Menu.CATEGORY_ALTERNATIVE, R.id.menu_logout, 0, "退出登录");
        if (CaptureController.isCaptureServiceRunning()) {
            menu.add(Menu.CATEGORY_ALTERNATIVE, R.id.menu_stopCapture, 0, "停止抓包");
        }
        menu.add(Menu.CATEGORY_SYSTEM, R.id.menu_about, 0, "关于");
        menu.add(Menu.CATEGORY_SECONDARY, R.id.menu_help, 0, "帮助");
        menu.add(Menu.CATEGORY_SYSTEM, R.id.menu_exit, 0, "退出");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_showLoginInfo: {
                if (token == null) {
                    showPleaseLoginDialog();
                } else {
                    androidx.appcompat.app.AlertDialog.Builder dialogBuilder = new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("登录信息").setPositiveButton(android.R.string.ok, null).setCancelable(true);
                    Context ctx = dialogBuilder.getContext();
                    LinearLayout main = new LinearLayout(ctx);
                    main.setOrientation(LinearLayout.VERTICAL);
                    ResUtils.newDialogClickableItemClickToCopy(ctx, "UserId", "" + token.userId, main, true);
                    ResUtils.newDialogClickableItemClickToCopy(ctx, "IMEICode", token.imeiCode, main, true);
                    ResUtils.newDialogClickableItemClickToCopy(ctx, "Token", token.token, main, true);
                    {
                        TextView tv = new TextView(ctx);
                        tv.setText("长按可复制");
                        tv.setGravity(Gravity.CENTER);
                        main.addView(tv, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    }
                    dialogBuilder.setView(main).show();
                }
                break;
            }
            case R.id.menu_exit: {
                if (CaptureController.isCaptureServiceRunning()) {
                    LocalCaptureService service = LocalCaptureService.getInstance();
                    if (service != null) {
                        service.stopCapture();
                    }
                    stopService(new Intent(this, LocalCaptureService.class));
                }
                finish();
                break;
            }
            case R.id.menu_help: {
                Toast.makeText(this, "暂无帮助...", Toast.LENGTH_SHORT).show();
                break;
            }
            case R.id.menu_logout: {
                DaoManager.killCurrentSession();
                updateTitleInfo();
                mRunRecords = null;
                mRunRecordAdapter.notifyDataSetChanged();
                tvInvalidCount.setText("");
                tvValidCount.setText("");
                Toast.makeText(this, "退出登录成功", Toast.LENGTH_SHORT).show();
                break;
            }
            case R.id.menu_about: {
                new AlertDialog.Builder(SplashActivity.this).setTitle("关于")
                        .setMessage("本软件适用于阳光体育服务平台校园版长跑, 仅供内部成员学习交流, 如作他用所承受的法律责任一概与作者无关, 严禁使用于任何商业用途, 请于24小时内删除")
                        .setCancelable(true).setPositiveButton(android.R.string.ok, null).show();
                break;
            }
            case R.id.menu_stopCapture: {
                LocalCaptureService service = LocalCaptureService.getInstance();
                if (service != null) {
                    service.stopCapture();
                }
                stopService(new Intent(this, LocalCaptureService.class));
                break;
            }
            default: {
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    public void handleOnClickEvent(View view) {
        switch (view.getId()) {
            case R.id.mainActivity_wxLoginButton: {
                if (Utils.getActiveModuleVersion() == 0) {
                    new AlertDialog.Builder(this).setTitle("提示").setMessage("本模块可能没有在Xposed中被正确激活, 这会导致拉起微信登录失败\n" +
                            "请在您的设备或模拟器中安装 'Xposed框架' 后在 'Xposed Installer' 中(重新)勾选本应用, 然后重启模拟器或您的设备")
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    scheduleStartWxLogin();
                                    Toast.makeText(SplashActivity.this, "正在拉起微信...", Toast.LENGTH_SHORT).show();
                                }
                            }).setCancelable(true).show();
                } else {
                    scheduleStartWxLogin();
                    Toast.makeText(this, "正在拉起微信...", Toast.LENGTH_SHORT).show();
                }
                break;
            }
            case R.id.mainActivity_GetRecordButton: {
                if (token == null) {
                    showPleaseLoginDialog();
                    break;
                } else {
                    Utils.execute(new Runnable() {
                        @Override
                        public void run() {
                            doRequestGetRecords(false);
                        }
                    });
                }
                break;
            }
            case R.id.mainActivity_RunNowButton: {
                if (token == null) {
                    showPleaseLoginDialog();
                    break;
                } else {
                    runningProgressDialog = ProgressDialog.show(this, "请稍候", "正在跑步中, 请保持在本页面不要退出...", false, false);
                    Utils.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                long startTime = System.currentTimeMillis();
                                final String ret = LoginRunLogic.submitRunRecord(token, userInfo, new RunConfig());
                                long delta = System.currentTimeMillis() - startTime;
                                if (delta < 0) {
                                    delta = 0;
                                }
                                int dest = (int) (1000 + Math.random() * 1000);
                                int left = (int) (dest - delta);
                                if (left > 0) {
                                    try {
                                        Thread.sleep(left);
                                    } catch (InterruptedException ignored) {
                                    }
                                }
                                Utils.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        new AlertDialog.Builder(SplashActivity.this).setTitle("提示")
                                                .setMessage("跑步已完成, 请等待约10秒后刷新跑步记录确认记录是否有效")
                                                .setCancelable(true).setPositiveButton(android.R.string.ok, null).show();
                                    }
                                });
                            } catch (IOException | JSONException e) {
                                showGeneralErrorDialog(e);
                            } catch (BadResponseException e) {
                                showTokenOutdatedDialog(e);
                            }
                            if (runningProgressDialog != null) {
                                runningProgressDialog.dismiss();
                                runningProgressDialog = null;
                            }
                            doRequestGetRecords(true);
                        }
                    });
                }
                break;
            }
            case R.id.mainActivity_rootLoginButton: {
                loginProgressDialog = ProgressDialog.show(this, "请稍后", "正在登录...", false, false);
                Utils.execute(new Runnable() {
                    @Override
                    public void run() {
                        String data = null;
                        Exception exception = null;
                        try {
                            data = LoginRunLogic.getImeiCodeWithRoot();
                        } catch (Exception e) {
                            exception = e;
                        }
                        if (exception != null) {
                            if (exception instanceof LoginRunLogic.NoRootShellException) {
                                showNoRootAccessDialog();
                            } else if (exception instanceof FileNotFoundException) {
                                showSunRunNotInstalledDialog();
                            } else {
                                showGeneralErrorDialog(exception);
                            }
                        } else {
                            try {
                                token = LoginRunLogic.requestLoginWithImeiCode(data);
                                DaoManager.saveSession(token);
                                reloginRequired = false;
                                userInfo = LoginRunLogic.requestUserInfo(token);
                                DaoManager.saveUserInfo(userInfo);
                                Utils.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateTitleInfo();
                                        Toast.makeText(SplashActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } catch (IOException | JSONException e) {
                                showGeneralErrorDialog(e);
                            } catch (BadResponseException e) {
                                showTokenOutdatedDialog(e);
                            }
                        }
                        if (loginProgressDialog != null) {
                            loginProgressDialog.dismiss();
                            loginProgressDialog = null;
                        }
                    }
                });
                break;
            }
            case R.id.mainActivity_manualLoginButton: {
                showManualLoginDialog();
                break;
            }
            case R.id.mainActivity_RunConfig: {
                new AlertDialog.Builder(SplashActivity.this).setTitle("提示").setMessage("暂无可设置项")
                        .setCancelable(true).setPositiveButton(android.R.string.ok, null).show();
                break;
            }
            case R.id.mainActivity_captureLoginButton: {
                Intent vpnIntent = VpnService.prepare(this);
                if (vpnIntent != null) {
                    startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
                } else {
                    onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
                }
                break;
            }
            default: {
                Toast.makeText(this, "暂不支持...", Toast.LENGTH_SHORT).show();
                break;
            }
        }
    }

    @NonUiThread
    private boolean doReloginWithImeiCode() {
        if (token != null) {
            try {
                token = LoginRunLogic.requestLoginWithImeiCode(token.imeiCode);
                DaoManager.saveSession(token);
                reloginRequired = false;
                return true;
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            } catch (BadResponseException e) {
                showTokenOutdatedDialog(e);
            }
        }
        return false;
    }

    @UiThread
    public void loginWithCapturedImeiCode(final String code) {
        loginProgressDialog = ProgressDialog.show(this, "请稍后", "正在登录...", false, false);
        Utils.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    token = LoginRunLogic.requestLoginWithImeiCode(code);
                    DaoManager.saveSession(token);
                    reloginRequired = false;
                    userInfo = LoginRunLogic.requestUserInfo(token);
                    DaoManager.saveUserInfo(userInfo);
                    Utils.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateTitleInfo();
                            Toast.makeText(getApplicationContext(), "登录成功", Toast.LENGTH_SHORT).show();
                            Utils.postDelayed(() -> {
                                Intent i = new Intent(SplashActivity.this, SplashActivity.class);
                                i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                i.setAction(Intent.ACTION_MAIN);
                                i.addCategory(Intent.CATEGORY_LAUNCHER);
                                getApplicationContext().startActivity(i);
                            }, 3000);
                        }
                    });
                } catch (IOException | JSONException e) {
                    showGeneralErrorDialog(e);
                } catch (BadResponseException e) {
                    showTokenOutdatedDialog(e);
                }
                if (loginProgressDialog != null) {
                    loginProgressDialog.dismiss();
                    loginProgressDialog = null;
                }
            }
        });
    }

    private void doRequestGetRecords(final boolean silent) {
        try {
            JSONObject validJson = new JSONObject(LoginRunLogic.requestValidRecords(token));
            JSONObject invalidJson = new JSONObject(LoginRunLogic.requestInvalidRecords(token));
            final int validCount = validJson.getInt("AllCount");
            final int invalidCount = invalidJson.getInt("AllCount");
            JSONArray validList = validJson.getJSONArray("listValue");
            JSONArray invalidList = invalidJson.getJSONArray("listInValue");
            ArrayList<RunRecord> recordList = new ArrayList<>();
            for (int i = 0; i < validList.length(); i++) {
                JSONObject o = validList.getJSONObject(i);
                RunRecord r = new RunRecord(o);
                recordList.add(r);
            }
            for (int i = 0; i < invalidList.length(); i++) {
                JSONObject o = invalidList.getJSONObject(i);
                RunRecord r = new RunRecord(o);
                recordList.add(r);
            }
            Collections.sort(recordList);
            mRunRecords = recordList;
            Utils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mRunRecordAdapter.notifyDataSetChanged();
                    tvValidCount.setText(String.format("有效 %d 次", validCount));
                    tvInvalidCount.setText(String.format("无效 %d 次", invalidCount));
                    if (!silent) {
                        Toast.makeText(SplashActivity.this, "刷新成功", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (IOException | JSONException e) {
            if (!silent) {
                showGeneralErrorDialog(e);
            }
        } catch (final BadResponseException e) {
            if (!silent) {
                showTokenOutdatedDialog(e);
            } else {
                Utils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(SplashActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startService(new Intent(this, LocalCaptureService.class));
        }
    }

    private void showPleaseLoginDialog() {
        Utils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(SplashActivity.this).setTitle("错误").setMessage("请先登录").setCancelable(true)
                        .setPositiveButton(android.R.string.ok, null).show();
            }
        });
    }

    private void showNoRootAccessDialog() {
        Utils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(SplashActivity.this).setTitle("错误").setMessage("无法获取root权限, 请确保您的设备已root且已授予本应用root权限")
                        .setCancelable(true).setPositiveButton(android.R.string.ok, null).show();
            }
        });
    }

    private void showSunRunNotInstalledDialog() {
        Utils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(SplashActivity.this).setTitle("错误").setMessage("找不到  '阳光体育服务平台' APP 登录文件, 请确认 '阳光体育服务平台' APP已安装并已在阳光体育服务平台登录内登录")
                        .setCancelable(true).setPositiveButton(android.R.string.ok, null).show();
            }
        });
    }

    private void showTokenOutdatedDialog(final BadResponseException e) {
        Utils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(SplashActivity.this).setTitle("错误").setMessage("身份已过期, 请重新登录\n" + e.getCode() + " " + e.getResponse()).setCancelable(true)
                        .setPositiveButton(android.R.string.ok, null).show();
            }
        });
    }

    private void showGeneralErrorDialog(final Throwable e) {
        Utils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(SplashActivity.this).setTitle("错误").setMessage(e.toString()).setCancelable(true)
                        .setPositiveButton(android.R.string.ok, null).show();
            }
        });
    }

    private void scheduleStartWxLogin() {
        SendAuth.Req req = new SendAuth.Req();
        req.scope = "snsapi_userinfo";
        req.state = "wechat_sdk_demo_test";
        WxApiTransactor.getInstance().sendReq(req);
    }

    private void showManualLoginDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(SplashActivity.this)
                .setTitle("请输入IMEICode").setCancelable(true);
        final EditText editText = new EditText(builder.getContext());
        String possibleValue = Utils.getPossibleIMEICodeFromClipboard(this);
        if (possibleValue != null) {
            editText.setText(possibleValue);
        }
        editText.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        builder.setView(editText).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final String code = editText.getText().toString().trim();
                if (code.length() == 0) {
                    new AlertDialog.Builder(SplashActivity.this).setTitle("错误").setMessage("IMEICode不能为空").setCancelable(true)
                            .setPositiveButton(android.R.string.ok, null).show();
                } else {
                    loginProgressDialog = ProgressDialog.show(SplashActivity.this, "请稍后", "正在登录...", false, false);
                    Utils.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                token = LoginRunLogic.requestLoginWithImeiCode(code);
                                DaoManager.saveSession(token);
                                reloginRequired = false;
                                userInfo = LoginRunLogic.requestUserInfo(token);
                                DaoManager.saveUserInfo(userInfo);
                                Utils.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateTitleInfo();
                                        Toast.makeText(SplashActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } catch (IOException | JSONException e) {
                                showGeneralErrorDialog(e);
                            } catch (BadResponseException e) {
                                Utils.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        new AlertDialog.Builder(SplashActivity.this).setTitle("错误").setMessage("IMEICode无效或已过期").setCancelable(true)
                                                .setPositiveButton(android.R.string.ok, null).show();
                                    }
                                });
                            }
                            if (loginProgressDialog != null) {
                                loginProgressDialog.dismiss();
                                loginProgressDialog = null;
                            }
                        }
                    });
                }
            }
        }).show();
    }

    @Override
    protected void onDestroy() {
        SELF = null;
        super.onDestroy();
    }

    @Nullable
    public static SplashActivity getInstance() {
        return SELF;
    }
}
