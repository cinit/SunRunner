package das.lazy.sunrunner.cap.protocol.tcpip;

public enum TCBStatus {
    SYN_SENT,
    SYN_RECEIVED,
    ESTABLISHED,
    CLOSE_WAIT,
    LAST_ACK,
    //new
    CLOSED,
}
