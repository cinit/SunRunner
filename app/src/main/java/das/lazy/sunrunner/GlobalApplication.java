package das.lazy.sunrunner;

import android.app.Application;

public class GlobalApplication extends Application {
    private static GlobalApplication self = null;
    private String code;

    public void setCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        self = this;
    }

    public static GlobalApplication getInstance() {
        return self;
    }
}
