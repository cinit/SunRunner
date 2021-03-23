package das.lazy.sunrunner.cap.bio;

import android.net.VpnService;
import android.os.Build;
import android.util.Log;
import das.lazy.sunrunner.cap.CaptureController;
import das.lazy.sunrunner.cap.LocalCaptureService;
import das.lazy.sunrunner.cap.config.Config;
import das.lazy.sunrunner.cap.protocol.tcpip.IpUtil;
import das.lazy.sunrunner.cap.protocol.tcpip.Packet;
import das.lazy.sunrunner.cap.protocol.tcpip.TCBStatus;
import das.lazy.sunrunner.cap.util.ByteBufferPool;
import das.lazy.sunrunner.cap.util.ProxyException;
import das.lazy.sunrunner.util.Utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class BioTcpHandler implements Runnable {

    private static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE;
    private static final String TAG = BioTcpHandler.class.getSimpleName();
    private final VpnService vpnService;
    public BlockingQueue<String> tunnelCloseMsgQueue = new ArrayBlockingQueue<>(1024);
    BlockingQueue<Packet> devToNetQueue;
    ConcurrentHashMap<String, TcpTunnel> tunnels = new ConcurrentHashMap<>();
    BlockingQueue<ByteBuffer> networkToDeviceQueue;

    public BioTcpHandler(BlockingQueue<Packet> devToNetQueue, BlockingQueue<ByteBuffer> networkToDeviceQueue, VpnService vpnService) {
        this.devToNetQueue = devToNetQueue;
        this.vpnService = vpnService;
        this.networkToDeviceQueue = networkToDeviceQueue;
    }

    private static void sendTcpPack(TcpTunnel tunnel, byte flag, byte[] data) {

        int dataLen = 0;
        if (data != null) {
            dataLen = data.length;
        }
        Packet packet = IpUtil.buildTcpPacket(tunnel.destinationAddress, tunnel.sourceAddress, flag,
                tunnel.myAcknowledgementNum, tunnel.mySequenceNum, tunnel.packId);
        tunnel.packId += 1;
        ByteBuffer byteBuffer = ByteBufferPool.acquire();
        //
        byteBuffer.position(HEADER_SIZE);
        if (data != null) {
            if (byteBuffer.remaining() < data.length) {
                System.currentTimeMillis();
            }
            byteBuffer.put(data);
        }

        packet.updateTCPBuffer(byteBuffer, flag, tunnel.mySequenceNum, tunnel.myAcknowledgementNum, dataLen);
        byteBuffer.position(HEADER_SIZE + dataLen);

        tunnel.networkToDeviceQueue.offer(byteBuffer);

        if ((flag & (byte) Packet.TCPHeader.SYN) != 0) {
            tunnel.mySequenceNum += 1;
        }
        if ((flag & (byte) Packet.TCPHeader.FIN) != 0) {
            tunnel.mySequenceNum += 1;
        }
        if ((flag & (byte) Packet.TCPHeader.ACK) != 0) {
            tunnel.mySequenceNum += dataLen;
        }
    }

    public static boolean isClosedTunnel(TcpTunnel tunnel) {
        return !tunnel.upActive && !tunnel.downActive;
    }

    private static void closeDownStream(TcpTunnel tunnel) {
        synchronized (tunnel) {
            Log.i(TAG, String.format("closeDownStream %d", tunnel.tunnelId));
            try {
                if (tunnel.destSocket != null && tunnel.destSocket.isOpen()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        tunnel.destSocket.shutdownInput();
                    } else {
                        tunnel.destSocket.close();
                        tunnel.destSocket = null;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            sendTcpPack(tunnel, (byte) (Packet.TCPHeader.FIN | Packet.TCPHeader.ACK), null);
            tunnel.downActive = false;
            if (isClosedTunnel(tunnel)) {
                tunnel.tunnelCloseMsgQueue.add(tunnel.tunnelKey);
            }
        }
    }

    private static void closeUpStream(TcpTunnel tunnel) {
        synchronized (tunnel) {
            Log.i(TAG, String.format("closeUpStream %d", tunnel.tunnelId));
            try {
                if (tunnel.destSocket != null && tunnel.destSocket.isOpen()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        tunnel.destSocket.shutdownOutput();
                    } else {
                        tunnel.destSocket.close();
                        tunnel.destSocket = null;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            Log.i(TAG, String.format("closeUpStream %d", tunnel.tunnelId));
            tunnel.upActive = false;
            if (isClosedTunnel(tunnel)) {
                tunnel.tunnelCloseMsgQueue.add(tunnel.tunnelKey);
            }
        }
    }

    private static void closeRst(TcpTunnel tunnel) {
        synchronized (tunnel) {
            Log.i(TAG, String.format("closeRst %d", tunnel.tunnelId));
            try {
                if (tunnel.destSocket != null && tunnel.destSocket.isOpen()) {
                    tunnel.destSocket.close();
                    tunnel.destSocket = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            sendTcpPack(tunnel, (byte) Packet.TCPHeader.RST, null);
            tunnel.upActive = false;
            tunnel.downActive = false;
        }
    }

    private TcpTunnel initTunnel(Packet packet) {
        TcpTunnel tunnel = new TcpTunnel();
        tunnel.sourceAddress = new InetSocketAddress(packet.ip4Header.sourceAddress, packet.tcpHeader.sourcePort);
        tunnel.destinationAddress = new InetSocketAddress(packet.ip4Header.destinationAddress, packet.tcpHeader.destinationPort);
        tunnel.vpnService = vpnService;
        tunnel.networkToDeviceQueue = networkToDeviceQueue;
        tunnel.tunnelCloseMsgQueue = tunnelCloseMsgQueue;
        Thread t = new Thread(new UpStreamWorker(tunnel));
        t.start();

        return tunnel;
    }

    @Override
    public void run() {

        while (true) {
            try {
                Packet currentPacket = devToNetQueue.take();

                InetAddress destinationAddress = currentPacket.ip4Header.destinationAddress;

                Packet.TCPHeader tcpHeader = currentPacket.tcpHeader;
                //Log.d(TAG, String.format("get pack %d tcp " + tcpHeader.printSimple() + " ", currentPacket.packId));

                int destinationPort = tcpHeader.destinationPort;
                int sourcePort = tcpHeader.sourcePort;
                String ipAndPort = destinationAddress.getHostAddress() + ":" +
                        destinationPort + ":" + sourcePort;

                while (true) {
                    String s = this.tunnelCloseMsgQueue.poll();
                    if (s == null) {
                        break;
                    } else {
                        tunnels.remove(ipAndPort);
                        Log.i(TAG, String.format("remove tunnel %s", ipAndPort));
                    }
                }

                if (!tunnels.containsKey(ipAndPort)) {
                    TcpTunnel tcpTunnel = initTunnel(currentPacket);
                    tcpTunnel.tunnelKey = ipAndPort;
                    tunnels.put(ipAndPort, tcpTunnel);
                }
                TcpTunnel tcpTunnel = tunnels.get(ipAndPort);
                //
                tcpTunnel.tunnelInputQueue_upload.offer(currentPacket);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static class TcpTunnel {

        static AtomicInteger tunnelIds = new AtomicInteger(0);
        public final int tunnelId = tunnelIds.addAndGet(1);

        public long mySequenceNum = 0;
        public long theirSequenceNum = 0;
        public long myAcknowledgementNum = 0;
        public long theirAcknowledgementNum = 0;

        public TCBStatus tcbStatus = TCBStatus.SYN_SENT;
        public BlockingQueue<Packet> tunnelInputQueue_upload = new ArrayBlockingQueue<Packet>(1024);
        public InetSocketAddress sourceAddress;
        public InetSocketAddress destinationAddress;
        public SocketChannel destSocket;
        public int packId = 1;
        public boolean upActive = true;
        public boolean downActive = true;
        public String tunnelKey;
        public BlockingQueue<String> tunnelCloseMsgQueue;
        BlockingQueue<ByteBuffer> networkToDeviceQueue;
        private VpnService vpnService;
    }

    private static class UpStreamWorker implements Runnable {

        TcpTunnel tunnel;
        int synCount = 0;

        public UpStreamWorker(TcpTunnel tunnel) {
            this.tunnel = tunnel;
        }

        private void startDownStream() {
            Thread t = new Thread(new DownStreamWorker(tunnel));
            t.start();
        }

        private void connectRemote() {
            try {
                //connect
                SocketChannel remote = SocketChannel.open();
                tunnel.vpnService.protect(remote.socket());
                InetSocketAddress address = tunnel.destinationAddress;

                Long ts = System.currentTimeMillis();
                remote.socket().connect(address, 5000);
                Long te = System.currentTimeMillis();

                Log.i(TAG, String.format("connectRemote %d cost %d  remote %s", tunnel.tunnelId, te - ts, tunnel.destinationAddress.toString()));
                tunnel.destSocket = remote;

                startDownStream();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                throw new ProxyException("connectRemote fail" + tunnel.destinationAddress.toString());
            }
        }

        private void handleSyn(Packet packet) {
            if (tunnel.tcbStatus == TCBStatus.SYN_SENT) {
                tunnel.tcbStatus = TCBStatus.SYN_RECEIVED;
            }
            Log.i(TAG, String.format("handleSyn %d %d", tunnel.tunnelId, packet.packId));
            Packet.TCPHeader tcpHeader = packet.tcpHeader;
            if (synCount == 0) {
                tunnel.mySequenceNum = 1;
                tunnel.theirSequenceNum = tcpHeader.sequenceNumber;
                tunnel.myAcknowledgementNum = tcpHeader.sequenceNumber + 1;
                tunnel.theirAcknowledgementNum = tcpHeader.acknowledgementNumber;
                sendTcpPack(tunnel, (byte) (Packet.TCPHeader.SYN | Packet.TCPHeader.ACK), null);
            } else {
                tunnel.myAcknowledgementNum = tcpHeader.sequenceNumber + 1;
            }
            synCount += 1;
        }

        private void writeToRemote(ByteBuffer buffer) throws IOException {
            if (tunnel.upActive) {
                int payloadSize = buffer.remaining();
                int write = tunnel.destSocket.write(buffer);
            }
        }

        private void handleAck(Packet packet) throws IOException {

            if (tunnel.tcbStatus == TCBStatus.SYN_RECEIVED) {
                tunnel.tcbStatus = TCBStatus.ESTABLISHED;
            }

            if (Config.logAck) {
                Log.d(TAG, String.format("handleAck %d ", packet.packId));
            }

            Packet.TCPHeader tcpHeader = packet.tcpHeader;
            int payloadSize = packet.backingBuffer.remaining();

            if (payloadSize == 0) {
                return;
            }

            long newAck = tcpHeader.sequenceNumber + payloadSize;
            if (newAck <= tunnel.myAcknowledgementNum) {
                if (Config.logAck) {
                    Log.d(TAG, String.format("handleAck duplicate ack", tunnel.myAcknowledgementNum, newAck));
                }
                return;
            }

            tunnel.myAcknowledgementNum = tcpHeader.sequenceNumber;
            tunnel.theirAcknowledgementNum = tcpHeader.acknowledgementNumber;

            tunnel.myAcknowledgementNum += payloadSize;
            writeToRemote(packet.backingBuffer);
            String url = MidUtils.getClearTextHttpUrl(packet.backingBuffer);
            if (url != null) {
                Log.i("MidUtils", "URL -> " + tunnel.destinationAddress + url);
                if (url.contains("/api/%7Btoken%7D/QM_Users/Login_AndroidSchool?IMEICode=")) {
                    Utils.execute(new Runnable() {
                        @Override
                        public void run() {
                            LocalCaptureService svc = LocalCaptureService.getInstance();
                            if (svc != null) {
                                svc.stopCapture();
                                int start = url.indexOf("IMEICode=") + 9;
                                CaptureController.onCaptureImeiCode(url.substring(start, start + 32));
                            }
                        }
                    });

                }
            }

            sendTcpPack(tunnel, (byte) Packet.TCPHeader.ACK, null);

            System.currentTimeMillis();
        }

        private void handleFin(Packet packet) {
            Log.i(TAG, String.format("handleFin %d", tunnel.tunnelId));
            tunnel.myAcknowledgementNum = packet.tcpHeader.sequenceNumber + 1;
            tunnel.theirAcknowledgementNum = packet.tcpHeader.acknowledgementNumber;
            sendTcpPack(tunnel, (byte) (Packet.TCPHeader.ACK), null);
            //closeTunnel(tunnel);
            //closeDownStream();
            closeUpStream(tunnel);
            tunnel.tcbStatus = TCBStatus.CLOSE_WAIT;
        }

        private void handleRst(Packet packet) {
            Log.i(TAG, String.format("handleRst %d", tunnel.tunnelId));
            try {
                synchronized (tunnel) {
                    if (tunnel.destSocket != null) {
                        tunnel.destSocket.close();
                    }

                }
            } catch (IOException e) {
                Log.e(TAG, "close error", e);
            }

            synchronized (tunnel) {
                tunnel.upActive = false;
                tunnel.downActive = false;
                tunnel.tcbStatus = TCBStatus.CLOSE_WAIT;
            }
        }

        private void loop() {
            while (true) {
                Packet packet = null;
                try {
                    packet = tunnel.tunnelInputQueue_upload.take();

                    //Log.i(TAG, "lastIdentification " + tunnel.lastIdentification);
                    synchronized (tunnel) {
                        boolean end = false;
                        Packet.TCPHeader tcpHeader = packet.tcpHeader;

                        if (tcpHeader.isSYN()) {
                            handleSyn(packet);
                            end = true;
                        }
                        if (!end && tcpHeader.isRST()) {
                            //
                            //Log.i(TAG, String.format("handleRst %d", tunnel.tunnelId));
                            //tunnel.destSocket.close();
                            handleRst(packet);
                            end = true;
                            break;
                        }
                        if (!end && tcpHeader.isFIN()) {
                            handleFin(packet);
                            end = true;
                        }
                        if (!end && tcpHeader.isACK()) {
                            handleAck(packet);
                        }
//                        if (!tunnel.downActive && !tunnel.upActive) {
//                            closeTotalTunnel();
//                            break;
//                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
            Log.i(TAG, String.format("UpStreamWorker quit"));
        }

        @Override
        public void run() {
            try {
                connectRemote();
                loop();
            } catch (ProxyException e) {
                //closeTotalTunnel();
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class DownStreamWorker implements Runnable {
        final TcpTunnel tunnel;

        public DownStreamWorker(TcpTunnel tunnel) {
            this.tunnel = tunnel;
        }

        @Override
        public void run() {
            ByteBuffer buffer = ByteBuffer.allocate(4 * 1024);

            String quitType = "rst";

            try {
                while (true) {
                    buffer.clear();
                    if (tunnel.destSocket == null) {
                        throw new ProxyException("tunnel maybe closed");
                    }
                    int n = BioUtil.read(tunnel.destSocket, buffer);

                    synchronized (tunnel) {
                        if (n == -1) {
                            quitType = "fin";
                            break;
                        } else {
                            if (tunnel.tcbStatus != TCBStatus.CLOSE_WAIT) {
                                buffer.flip();
                                byte[] data = new byte[buffer.remaining()];
                                buffer.get(data);
                                sendTcpPack(tunnel, (byte) (Packet.TCPHeader.ACK), data);
                            }
                        }
                    }
                }
            } catch (ClosedChannelException e) {
                Log.w(TAG, String.format("channel closed %s", e.getMessage()));
                quitType = "rst";
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
                quitType = "rst";
            } catch (Exception e) {
                quitType = "rst";
                Log.e(TAG, "DownStreamWorker fail", e);
            }
            //Log.i(TAG, String.format("DownStreamWorker quit %d", tunnel.tunnelId));
            synchronized (tunnel) {
                if (quitType.equals("fin")) {
                    closeDownStream(tunnel);
                    //closeUpStream(tunnel);
                    //closeRst(tunnel);
                } else if (quitType.equals("rst")) {
                    closeRst(tunnel);
                }

            }

        }
    }
}
