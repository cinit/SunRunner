package das.lazy.sunrunner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.UiThread;
import com.topjohnwu.superuser.Shell;
import das.lazy.sunrunner.data.RunConfig;
import das.lazy.sunrunner.data.UserInfo;
import das.lazy.sunrunner.data.UserToken;
import das.lazy.sunrunner.util.BadResponseException;
import das.lazy.sunrunner.util.HttpStringGet;
import das.lazy.sunrunner.util.NonUiThread;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class LoginRunLogic {

    @SuppressLint("SdCardPath")
    private static final String SUN_RUN_IMEI_CODE_FILE_PATH = "/data/data/com.aipao.hanmoveschool/shared_prefs/loginIMEICode.xml";

    @NonUiThread
    public static UserToken requestLoginWithWxCode(String wxCode) throws IOException, JSONException, BadResponseException {
        HttpStringGet req = new HttpStringGet("http://client3.aipao.me/api/" + wxCode + "/QM_Users/Login_Android?wxCode=" + wxCode
                + "&IMEI=" + "Android:" + UUID.randomUUID().toString()).setHeader("version", "2.40");
        if (req.execute() == 200 && isRespSuccess(req.getResponse())) {
            JSONObject retObj = new JSONObject(req.getResponse());
            UserToken result = new UserToken();
            result.imeiCode = retObj.getJSONObject("Data").getString("IMEICode");
            result.token = retObj.getJSONObject("Data").getString("Token");
            result.userId = retObj.getJSONObject("Data").getInt("UserId");
            return result;
        } else {
            throw new BadResponseException(req.getStatus(), req.getResponse());
        }
    }

    @NonUiThread
    public static UserToken requestLoginWithImeiCode(String imeiCode) throws IOException, JSONException, BadResponseException {
        HttpStringGet req = new HttpStringGet("http://client3.aipao.me/api/%7Btoken%7D/QM_Users/Login_AndroidSchool?IMEICode="
                + imeiCode).setHeader("version", "2.40");
        if (req.execute() == 200 && isRespSuccess(req.getResponse())) {
            JSONObject retObj = new JSONObject(req.getResponse());
            UserToken result = new UserToken();
            result.imeiCode = retObj.getJSONObject("Data").getString("IMEICode");
            result.token = retObj.getJSONObject("Data").getString("Token");
            result.userId = retObj.getJSONObject("Data").getInt("UserId");
            return result;
        } else {
            throw new BadResponseException(req.getStatus(), req.getResponse());
        }
    }

    @NonUiThread
    public static UserInfo requestUserInfo(UserToken token) throws IOException, JSONException, BadResponseException {
        HttpStringGet req = new HttpStringGet("http://client3.aipao.me/api/" + token.token + "/QM_Users/GS").setHeader("version", "2.40");
        if (req.execute() == 200 && isRespSuccess(req.getResponse())) {
            JSONObject retObj = new JSONObject(req.getResponse());
            UserInfo result = new UserInfo();
            JSONObject user = retObj.getJSONObject("Data").getJSONObject("User");
            result.uid = user.getInt("UserID");
            result.nickName = user.getString("NickName");
            result.userName = user.getString("UserName");
            result.isSchoolMode = Integer.parseInt(user.getString("IsSchoolMode"));
            if (result.isSchoolMode == 1) {
                JSONObject school = retObj.getJSONObject("Data").getJSONObject("SchoolRun");
                result.schoolId = school.getString("SchoolId");
                result.schoolName = school.getString("SchoolName");
                result.schoolRunLength = school.getInt("Lengths");
                result.schoolRunMinSpeed = (float) school.getDouble("MinSpeed");
                result.schoolRunMaxSpeed = (float) school.getDouble("MaxSpeed");
            }
            return result;
        } else {
            throw new BadResponseException(req.getStatus(), req.getResponse());
        }
    }

    @NonUiThread
    public static String requestValidRecords(UserToken token) throws IOException, BadResponseException, JSONException {
        HttpStringGet req = new HttpStringGet("http://client3.aipao.me/api/" + token.token + "/QM_Runs/getResultsofValidByUser?UserId=" + token.userId + "&pageIndex=1&pageSize=1000").setHeader("version", "2.40");
        if (req.execute() == 200 && isRespSuccess(req.getResponse())) {
            return req.getResponse();
        } else {
            throw new BadResponseException(req.getStatus(), req.getResponse());
        }
    }

    @NonUiThread
    public static String requestInvalidRecords(UserToken token) throws IOException, BadResponseException, JSONException {
        HttpStringGet req = new HttpStringGet("http://client3.aipao.me/api/" + token.token + "/QM_Runs/getResultsofInValidByUser?UserId=" + token.userId + "&pageIndex=1&pageSize=1000").setHeader("version", "2.40");
        if (req.execute() == 200 && isRespSuccess(req.getResponse())) {
            return req.getResponse();
        } else {
            throw new BadResponseException(req.getStatus(), req.getResponse());
        }
    }


    /**
     * @return as a slice of n ints, a pseudo-random permutation of the integers [0,n).
     */
    private static int[] perm(int n) {
        int[] m = new int[n];
        // In the following loop, the iteration when i=0 always swaps m[0] with m[0].
        // A change to remove this useless iteration is to assign 1 to i in the init
        // statement. But perm also effects r. Making this change will affect
        // the final state of r. So this change can't be made for compatibility
        // reasons for Go 1.
        for (int i = 0; i < n; i++) {
            int j = (int) (Math.random() * (i + 1));
            m[i] = m[j];
            m[j] = i;
        }
        return m;
    }

    public static int randomBetween(int a, int b) {
        return (int) (Math.random() * Math.abs(b - a) + Math.min(b, a));
    }

    @NonUiThread
    public static String submitRunRecord(UserToken token, UserInfo info, RunConfig config) throws IOException, BadResponseException, JSONException {
        StringBuilder sb = new StringBuilder();
        char[] table = new char[10];
        char[] alphabet = "abcdefghijklmnopqrstuvwxyz".toCharArray();
        int[] tmp = perm(26);
        for (int i = 0; i < 10; i++) {
            table[i] = alphabet[tmp[i]];
        }
        //
        int runDistance = (int) (Math.random() * config.maxRunTail + info.schoolRunLength);
        int runTime = randomBetween(config.minRunTime, config.maxRunTime);
        int runStep = randomBetween(config.minRunSteps, config.maxRunSteps);
        double deltaX = (Math.random() > 0.5 ? 1.0 : -1.0) * Math.random() * 0.001;
        double deltaY = (Math.random() > 0.5 ? 1.0 : -1.0) * Math.random() * 0.001;
        //
        HttpStringGet req = new HttpStringGet("http://client3.aipao.me/api/" + token.token + "/QM_Runs/SRS?S1=" +
                (config.latitude + deltaX) + "&S2=" + (config.longitude + deltaY) + "&S3=" + info.schoolRunLength).setHeader("version", "2.40");
        if (req.execute() != 200 || !isRespSuccess(req.getResponse())) {
            throw new BadResponseException(req.getStatus(), req.getResponse());
        }
        sb.append(req.getResponse());
        JSONObject data = new JSONObject(req.getResponse()).getJSONObject("Data");
        String runId = data.getString("RunId");
        String routes = data.getString("Routes");
        req = new HttpStringGet("http://client3.aipao.me/api/" + token.token + "/QM_Runs/ES?S1=" + runId + "&S4=" +
                encrypt("" + runTime, table) + "&S5=" + encrypt("" + runDistance, table) +
                "&S6=" + routes + "&S7=1&S8=" +
                new String(table) + "&S9=" + encrypt("" + runStep, table)).setHeader("version", "2.40");
        if (req.execute() != 200 || !isRespSuccess(req.getResponse())) {
            throw new BadResponseException(req.getStatus(), req.getResponse());
        }
        sb.append('\n');
        sb.append(req.getResponse());
        return sb.toString();
    }

    public static String encrypt(String s, char[] vtable) {
        char[] res = new char[s.length()];
        for (int i = 0; i < s.length(); i++) {
            res[i] = vtable[(s.charAt(i) + 2) % 10];
        }
        return new String(res);
    }


    @UiThread
    public static void onWxLogin(Context ctx, final String wxCode) {
        ctx.startActivity(new Intent(ctx, SplashActivity.class).putExtra("wxLoginCode", wxCode));
    }

    private static boolean isRespSuccess(String data) {
        try {
            return new JSONObject(data).getBoolean("Success");
        } catch (JSONException e) {
            return false;
        }
    }

    public static String getImeiCodeWithRoot() throws FileNotFoundException, NoRootShellException {
        if (!Shell.su("id").exec().isSuccess()) {
            throw new NoRootShellException();
        }
        if (!Shell.su("ls " + SUN_RUN_IMEI_CODE_FILE_PATH).exec().isSuccess()) {
            throw new FileNotFoundException(SUN_RUN_IMEI_CODE_FILE_PATH);
        }
        List<String> ret = Shell.su("cat " + SUN_RUN_IMEI_CODE_FILE_PATH).exec().getOut();
        StringBuilder sb = new StringBuilder();
        for (String s : ret) {
            sb.append(s);
            sb.append('\n');
        }
        String data = sb.toString().trim();
        int tag = data.indexOf("\"IMEICode\"");
        if (tag == -1) {
            throw new IllegalArgumentException("IMEICode not found");
        }
        int end = data.indexOf("</string>", tag);
        int start = data.indexOf('>', tag);
        return data.substring(start + 1, end).trim();
    }

    public static class NoRootShellException extends Exception {
    }
}
