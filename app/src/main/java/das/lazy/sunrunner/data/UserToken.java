package das.lazy.sunrunner.data;

public class UserToken {
    public String imeiCode;
    public String token;
    public int userId;

    @Override
    public String toString() {
        return "UserToken{" +
                "imeiCode='" + imeiCode + '\'' +
                ", token='" + token + '\'' +
                ", userId=" + userId +
                '}';
    }
}
