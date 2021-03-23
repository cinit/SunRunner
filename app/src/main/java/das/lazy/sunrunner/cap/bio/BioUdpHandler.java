package das.lazy.sunrunner.cap.bio;

import android.net.VpnService;
import android.util.Log;
import das.lazy.sunrunner.cap.config.Config;
import das.lazy.sunrunner.cap.protocol.tcpip.IpUtil;
import das.lazy.sunrunner.cap.protocol.tcpip.Packet;
import das.lazy.sunrunner.cap.util.ByteBufferPool;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
public class BioUdpHandler implements Runnable {

    private static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.UDP_HEADER_SIZE;
    private static final String TAG = BioUdpHandler.class.getSimpleName();
    BlockingQueue<Packet> queue;
    BlockingQueue<ByteBuffer> networkToDeviceQueue;
    VpnService vpnService;
    Map<String, DatagramChannel> udpSockets = new HashMap();
    private Selector selector;

    public BioUdpHandler(BlockingQueue<Packet> queue, BlockingQueue<ByteBuffer> networkToDeviceQueue, VpnService vpnService) {
        this.queue = queue;
        this.networkToDeviceQueue = networkToDeviceQueue;
        this.vpnService = vpnService;
    }

    @Override
    public void run() {
        try {
            BlockingQueue<UdpTunnel> tunnelQueue = new ArrayBlockingQueue<>(100);
            selector = Selector.open();
            Thread t = new Thread(new UdpDownWorker(selector, networkToDeviceQueue, tunnelQueue));
            t.start();


            while (true) {
                Packet packet = queue.take();

                InetAddress destinationAddress = packet.ip4Header.destinationAddress;
                Packet.UDPHeader header = packet.udpHeader;

                //Log.d(TAG, String.format("get pack %d udp %d ", packet.packId, header.length));

                int destinationPort = header.destinationPort;
                int sourcePort = header.sourcePort;
                String ipAndPort = destinationAddress.getHostAddress() + ":" + destinationPort + ":" + sourcePort;
                if (!udpSockets.containsKey(ipAndPort)) {
                    DatagramChannel outputChannel = DatagramChannel.open();
                    vpnService.protect(outputChannel.socket());
                    outputChannel.socket().bind(null);
                    outputChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));

                    outputChannel.configureBlocking(false);

                    UdpTunnel tunnel = new UdpTunnel();
                    tunnel.local = new InetSocketAddress(packet.ip4Header.sourceAddress, header.sourcePort);
                    tunnel.remote = new InetSocketAddress(packet.ip4Header.destinationAddress, header.destinationPort);
                    tunnel.channel = outputChannel;
                    tunnelQueue.offer(tunnel);

                    selector.wakeup();

                    udpSockets.put(ipAndPort, outputChannel);
                }

                DatagramChannel outputChannel = udpSockets.get(ipAndPort);
                ByteBuffer buffer = packet.backingBuffer;
                try {
                    while (packet.backingBuffer.hasRemaining()) {
                        int w = outputChannel.write(buffer);
                        if (Config.logRW) {
                            Log.d(TAG, String.format("write udp pack %d len %d %s ", packet.packId, w, ipAndPort));
                        }

                    }
                } catch (IOException e) {
                    Log.e(TAG, "udp write error", e);
                    outputChannel.close();
                    udpSockets.remove(ipAndPort);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "error", e);
        }
    }

    private static class UdpDownWorker implements Runnable {

        private static final AtomicInteger ipId = new AtomicInteger();
        BlockingQueue<ByteBuffer> networkToDeviceQueue;
        BlockingQueue<UdpTunnel> tunnelQueue;
        Selector selector;

        public UdpDownWorker(Selector selector, BlockingQueue<ByteBuffer> networkToDeviceQueue, BlockingQueue<UdpTunnel> tunnelQueue) {
            this.networkToDeviceQueue = networkToDeviceQueue;
            this.tunnelQueue = tunnelQueue;
            this.selector = selector;
        }

        private void sendUdpPack(UdpTunnel tunnel, InetSocketAddress source, byte[] data) throws IOException {
            int dataLen = 0;
            if (data != null) {
                dataLen = data.length;
            }
            Packet packet = IpUtil.buildUdpPacket(tunnel.remote, tunnel.local, ipId.addAndGet(1));

            ByteBuffer byteBuffer = ByteBufferPool.acquire();
            //
            byteBuffer.position(HEADER_SIZE);
            if (data != null) {
                if (byteBuffer.remaining() < data.length) {
                    System.currentTimeMillis();
                }
                byteBuffer.put(data);
            }
            packet.updateUDPBuffer(byteBuffer, dataLen);
            byteBuffer.position(HEADER_SIZE + dataLen);
            this.networkToDeviceQueue.offer(byteBuffer);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    int readyChannels = selector.select();
                    while (true) {
                        UdpTunnel tunnel = tunnelQueue.poll();
                        if (tunnel == null) {
                            break;
                        } else {
                            try {
                                SelectionKey key = tunnel.channel.register(selector, SelectionKey.OP_READ, tunnel);
                                key.interestOps(SelectionKey.OP_READ);
                                boolean isvalid = key.isValid();
                            } catch (IOException e) {
                                Log.d(TAG, "register fail", e);
                            }
                        }
                    }
                    if (readyChannels == 0) {
                        selector.selectedKeys().clear();
                        continue;
                    }
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = keys.iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();
                        if (key.isValid() && key.isReadable()) {
                            try {
                                DatagramChannel inputChannel = (DatagramChannel) key.channel();

                                ByteBuffer receiveBuffer = ByteBufferPool.acquire();
                                int readBytes = inputChannel.read(receiveBuffer);
                                receiveBuffer.flip();
                                byte[] data = new byte[receiveBuffer.remaining()];
                                receiveBuffer.get(data);
                                // FIXME: 2020/12/7 inputChannel.getLocalAddress() not used, use null to support API 15
                                sendUdpPack((UdpTunnel) key.attachment(),/* (InetSocketAddress) inputChannel.getLocalAddress()*/null, data);
                            } catch (IOException e) {
                                Log.e(TAG, "error", e);
                            }

                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "error", e);
            } finally {
                Log.d(TAG, "BioUdpHandler quit");
            }


        }
    }

    private static class UdpTunnel {
        InetSocketAddress local;
        InetSocketAddress remote;
        DatagramChannel channel;

    }
}
