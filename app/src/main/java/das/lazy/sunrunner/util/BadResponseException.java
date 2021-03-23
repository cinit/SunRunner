package das.lazy.sunrunner.util;

public class BadResponseException extends Exception {
    int code;
    String response;

    public BadResponseException(int code, String response) {
        this.code = code;
        this.response = response;
    }

    public int getCode() {
        return code;
    }

    public String getResponse() {
        return response;
    }

    @Override
    public String toString() {
        return "BadResponseException{" +
                "code=" + code +
                ", response='" + response + '\'' +
                '}';
    }
}
