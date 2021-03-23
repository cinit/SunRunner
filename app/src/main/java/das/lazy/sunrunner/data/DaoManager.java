package das.lazy.sunrunner.data;

import android.app.Application;
import androidx.annotation.Nullable;
import das.lazy.sunrunner.GlobalApplication;
import das.lazy.sunrunner.util.Utf8JceUtils;

import java.io.*;

public class DaoManager {

    @Nullable
    public static UserToken getLastSession() {
        Application ctx = GlobalApplication.getInstance();
        File dataFile = new File(ctx.getFilesDir(), "session");
        if (dataFile.exists()) {
            try {
                FileInputStream fin = new FileInputStream(dataFile);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[128];
                int i;
                while ((i = fin.read(buf)) > 0) {
                    baos.write(buf, 0, i);
                }
                fin.close();
                String[] str = baos.toString().split(",");
                UserToken result = new UserToken();
                result.imeiCode = str[0];
                result.token = str[1];
                result.userId = Integer.parseInt(str[2]);
                return result;
            } catch (IOException e) {
                throw new IOError(e);
            }
        }
        return null;
    }

    public static void killCurrentSession() {
        Application ctx = GlobalApplication.getInstance();
        File dataFile = new File(ctx.getFilesDir(), "session");
        if (dataFile.exists()) {
            dataFile.delete();
        }
    }

    public static void saveSession(UserToken session) {
        Application ctx = GlobalApplication.getInstance();
        File dataFile = new File(ctx.getFilesDir(), "session");
        try {
            if (!dataFile.exists()) {
                if (!dataFile.getParentFile().exists()) {
                    dataFile.getParentFile().mkdirs();
                }
                dataFile.createNewFile();
            }
            FileOutputStream fout = new FileOutputStream(dataFile);
            fout.write((session.imeiCode + "," + session.token + "," + session.userId).getBytes());
            fout.flush();
            fout.close();
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    public static void saveUserInfo(UserInfo info) {
        Application ctx = GlobalApplication.getInstance();
        File dataFile = new File(ctx.getFilesDir(), "uid_" + info.uid);
        try {
            if (!dataFile.exists()) {
                if (!dataFile.getParentFile().exists()) {
                    dataFile.getParentFile().mkdirs();
                }
                dataFile.createNewFile();
            }
            FileOutputStream fout = new FileOutputStream(dataFile);
            fout.write(Utf8JceUtils.encodeJceStruct(info));
            fout.flush();
            fout.close();
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    @Nullable
    public static UserInfo getUserInfo(int uid) {
        Application ctx = GlobalApplication.getInstance();
        File dataFile = new File(ctx.getFilesDir(), "uid_" + uid);
        if (dataFile.exists()) {
            try {
                FileInputStream fin = new FileInputStream(dataFile);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[128];
                int i;
                while ((i = fin.read(buf)) > 0) {
                    baos.write(buf, 0, i);
                }
                fin.close();
                return Utf8JceUtils.decodeJceStruct(new UserInfo(), baos.toByteArray());
            } catch (IOException e) {
                throw new IOError(e);
            }
        }
        return null;
    }

    public static void saveUserRunConfig(UserInfo info) {
        Application ctx = GlobalApplication.getInstance();
        File dataFile = new File(ctx.getFilesDir(), "uid_" + info.uid);
        try {
            if (!dataFile.exists()) {
                if (!dataFile.getParentFile().exists()) {
                    dataFile.getParentFile().mkdirs();
                }
                dataFile.createNewFile();
            }
            FileOutputStream fout = new FileOutputStream(dataFile);
            fout.write(Utf8JceUtils.encodeJceStruct(info));
            fout.flush();
            fout.close();
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    @Nullable
    public static UserInfo getUserRunConfig(int uid) {
        Application ctx = GlobalApplication.getInstance();
        File dataFile = new File(ctx.getFilesDir(), "uid_" + uid);
        if (dataFile.exists()) {
            try {
                FileInputStream fin = new FileInputStream(dataFile);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[128];
                int i;
                while ((i = fin.read(buf)) > 0) {
                    baos.write(buf, 0, i);
                }
                fin.close();
                return Utf8JceUtils.decodeJceStruct(new UserInfo(), baos.toByteArray());
            } catch (IOException e) {
                throw new IOError(e);
            }
        }
        return null;
    }


}
