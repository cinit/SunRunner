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
            date.setText(record.resultDate.replaceAll("[??????]", "-").replace("???", "") + " " + String.format("%02d", record.resultHour) + "???");
            lengths.setText(record.costDistance + "/" + record.avaLength + "m");
            if (record.notCountReason == 0) {
                valid.setVisibility(View.VISIBLE);
                invalid.setVisibility(View.GONE);
            } else {
                valid.setVisibility(View.GONE);
                invalid.setVisibility(View.VISIBLE);
                invalid.setText(String.format("??????(%d)", record.notCountReason));
            }
            duration.setText("??????" + record.costTime.replaceAll("[??????]", ":").replaceAll("[???]", ""));
            steps.setText(record.stepNum + "???");
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
        loginProgressDialog = ProgressDialog.show(this, "?????????", "????????????...", false, false);
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
                            Toast.makeText(SplashActivity.this, "????????????", Toast.LENGTH_SHORT).show();
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
            tvMainTitle.setText("?????????");
            tvSubTitle.setText("????????????");
            tvExtraInfo.setText("");
        } else {
            tvMainTitle.setText(userInfo.nickName);
            if (userInfo.isSchoolMode == 1) {
                tvSubTitle.setText(String.format("????????????: %dm\n????????????: %.1f - %.1f m/s",
                        userInfo.schoolRunLength, userInfo.schoolRunMinSpeed, userInfo.schoolRunMaxSpeed));
                tvExtraInfo.setText(userInfo.userName + " " + userInfo.schoolName);
            } else {
                tvSubTitle.setText("??????: ??????????????????????????????");
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
        menu.add(Menu.CATEGORY_ALTERNATIVE, R.id.menu_showLoginInfo, 0, "????????????");
        menu.add(Menu.CATEGORY_ALTERNATIVE, R.id.menu_logout, 0, "????????????");
        if (CaptureController.isCaptureServiceRunning()) {
            menu.add(Menu.CATEGORY_ALTERNATIVE, R.id.menu_stopCapture, 0, "????????????");
        }
        menu.add(Menu.CATEGORY_SYSTEM, R.id.menu_about, 0, "??????");
        menu.add(Menu.CATEGORY_SECONDARY, R.id.menu_help, 0, "??????");
        menu.add(Menu.CATEGORY_SYSTEM, R.id.menu_exit, 0, "??????");
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
                            .setTitle("????????????").setPositiveButton(android.R.string.ok, null).setCancelable(true);
                    Context ctx = dialogBuilder.getContext();
                    LinearLayout main = new LinearLayout(ctx);
                    main.setOrientation(LinearLayout.VERTICAL);
                    ResUtils.newDialogClickableItemClickToCopy(ctx, "UserId", "" + token.userId, main, true);
                    ResUtils.newDialogClickableItemClickToCopy(ctx, "IMEICode", token.imeiCode, main, true);
                    ResUtils.newDialogClickableItemClickToCopy(ctx, "Token", token.token, main, true);
                    {
                        TextView tv = new TextView(ctx);
                        tv.setText("???????????????");
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
                Toast.makeText(this, "????????????...", Toast.LENGTH_SHORT).show();
                break;
            }
            case R.id.menu_logout: {
                DaoManager.killCurrentSession();
                updateTitleInfo();
                mRunRecords = null;
                mRunRecordAdapter.notifyDataSetChanged();
                tvInvalidCount.setText("");
                tvValidCount.setText("");
                Toast.makeText(this, "??????????????????", Toast.LENGTH_SHORT).show();
                break;
            }
            case R.id.menu_about: {
                new AlertDialog.Builder(SplashActivity.this).setTitle("??????")
                        .setMessage("?????????????????????????????????????????????????????????, ??????????????????????????????, ?????????????????????????????????????????????????????????, ?????????????????????????????????, ??????24???????????????")
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
                    new AlertDialog.Builder(this).setTitle("??????").setMessage("????????????????????????Xposed??????????????????, ????????????????????????????????????\n" +
                            "??????????????????????????????????????? 'Xposed??????' ?????? 'Xposed Installer' ???(??????)???????????????, ????????????????????????????????????")
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    scheduleStartWxLogin();
                                    Toast.makeText(SplashActivity.this, "??????????????????...", Toast.LENGTH_SHORT).show();
                                }
                            }).setCancelable(true).show();
                } else {
                    scheduleStartWxLogin();
                    Toast.makeText(this, "??????????????????...", Toast.LENGTH_SHORT).show();
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
                    runningProgressDialog = ProgressDialog.show(this, "?????????", "???????????????, ?????????????????????????????????...", false, false);
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
                                        new AlertDialog.Builder(SplashActivity.this).setTitle("??????")
                                                .setMessage("???????????????, ????????????10????????????????????????????????????????????????")
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
                loginProgressDialog = ProgressDialog.show(this, "?????????", "????????????...", false, false);
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
                                        Toast.makeText(SplashActivity.this, "????????????", Toast.LENGTH_SHORT).show();
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
                new AlertDialog.Builder(SplashActivity.this).setTitle("??????").setMessage("??????????????????")
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
                Toast.makeText(this, "????????????...", Toast.LENGTH_SHORT).show();
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
        loginProgressDialog = ProgressDialog.show(this, "?????????", "????????????...", false, false);
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
                            Toast.makeText(getApplicationContext(), "????????????", Toast.LENGTH_SHORT).show();
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
                    tvValidCount.setText(String.format("?????? %d ???", validCount));
                    tvInvalidCount.setText(String.format("?????? %d ???", invalidCount));
                    if (!silent) {
                        Toast.makeText(SplashActivity.this, "????????????", Toast.LENGTH_SHORT).show();
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
                new AlertDialog.Builder(SplashActivity.this).setTitle("??????").setMessage("????????????").setCancelable(true)
                        .setPositiveButton(android.R.string.ok, null).show();
            }
        });
    }

    private void showNoRootAccessDialog() {
        Utils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(SplashActivity.this).setTitle("??????").setMessage("????????????root??????, ????????????????????????root?????????????????????root??????")
                        .setCancelable(true).setPositiveButton(android.R.string.ok, null).show();
            }
        });
    }

    private void showSunRunNotInstalledDialog() {
        Utils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(SplashActivity.this).setTitle("??????").setMessage("?????????  '????????????????????????' APP ????????????, ????????? '????????????????????????' APP?????????????????????????????????????????????????????????")
                        .setCancelable(true).setPositiveButton(android.R.string.ok, null).show();
            }
        });
    }

    private void showTokenOutdatedDialog(final BadResponseException e) {
        Utils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(SplashActivity.this).setTitle("??????").setMessage("???????????????, ???????????????\n" + e.getCode() + " " + e.getResponse()).setCancelable(true)
                        .setPositiveButton(android.R.string.ok, null).show();
            }
        });
    }

    private void showGeneralErrorDialog(final Throwable e) {
        Utils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(SplashActivity.this).setTitle("??????").setMessage(e.toString()).setCancelable(true)
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
                .setTitle("?????????IMEICode").setCancelable(true);
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
                    new AlertDialog.Builder(SplashActivity.this).setTitle("??????").setMessage("IMEICode????????????").setCancelable(true)
                            .setPositiveButton(android.R.string.ok, null).show();
                } else {
                    loginProgressDialog = ProgressDialog.show(SplashActivity.this, "?????????", "????????????...", false, false);
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
                                        Toast.makeText(SplashActivity.this, "????????????", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } catch (IOException | JSONException e) {
                                showGeneralErrorDialog(e);
                            } catch (BadResponseException e) {
                                Utils.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        new AlertDialog.Builder(SplashActivity.this).setTitle("??????").setMessage("IMEICode??????????????????").setCancelable(true)
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
