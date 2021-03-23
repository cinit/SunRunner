package das.lazy.sunrunner.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class HttpStringGet {
    private final String url;
    private final HashMap<String, String> headers = new HashMap<>();
    private int status = -1;
    private String response;

    public HttpStringGet(String url) {
        this.url = url;
    }

    public HttpStringGet setHeader(String key, String value) {
        headers.put(key, value);
        return this;
    }

    public int execute() throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        http.setRequestMethod("GET");
        for (Map.Entry<String, String> item : headers.entrySet()) {
            http.addRequestProperty(item.getKey(), item.getValue());
        }
        http.connect();
        status = http.getResponseCode();
        InputStream in = http.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int i;
        byte[] buf = new byte[128];
        while ((i = in.read(buf)) > 0) {
            baos.write(buf, 0, i);
        }
        in.close();
        http.disconnect();
        response = baos.toString();
        return status;
    }

    public String getResponse() {
        return response;
    }

    public int getStatus() {
        return status;
    }
}
