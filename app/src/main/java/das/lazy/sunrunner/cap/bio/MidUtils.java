package das.lazy.sunrunner.cap.bio;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;

public class MidUtils {

    @Nullable
    public static String getClearTextHttpUrl(ByteBuffer buf) {
        if (buf.limit() > 20 + 20 + 4) {
            int offset = 20 + 20;
            if ((0xFF & buf.get(offset)) == 'G' && (0xFF & buf.get(offset + 3)) == ' '
                    && (0xFF & buf.get(offset + 4)) == '/') {
                ByteBuffer buf2 = buf.duplicate();
                buf2.position(offset);
                byte[] bytes = new byte[buf2.remaining()];
                buf2.get(bytes, 0, buf2.remaining());
                String str = new String(bytes);
                if (str.contains("\r\n")) {
                    str = str.split("\r\n")[0];
                }
                if (str.startsWith("GET ")) {
                    return str.substring(4).split(" ")[0];
                }
            }
        }
        return null;
    }


}
