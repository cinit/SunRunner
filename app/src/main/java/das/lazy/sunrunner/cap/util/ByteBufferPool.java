package das.lazy.sunrunner.cap.util;

import java.nio.ByteBuffer;

public class ByteBufferPool {
    // XXX: Is this ideal?
    private static final int BUFFER_SIZE = 2048;

    public static ByteBuffer acquire() {
        return ByteBuffer.allocate(BUFFER_SIZE);
    }
}

