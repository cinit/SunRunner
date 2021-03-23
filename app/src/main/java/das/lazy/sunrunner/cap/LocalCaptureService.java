package das.lazy.sunrunner.cap;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import das.lazy.sunrunner.cap.bio.BioTcpHandler;
import das.lazy.sunrunner.cap.bio.BioUdpHandler;
import das.lazy.sunrunner.cap.config.Config;
import das.lazy.sunrunner.cap.protocol.tcpip.Packet;
import das.lazy.sunrunner.cap.util.ByteBufferPool;
import das.lazy.sunrunner.util.Utils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalCaptureService extends VpnService {
    private static final String TAG = LocalCaptureService.class.getSimpleName();
    private static final String VPN_ADDRESS = "10.0.0.2"; // Only IPv4 support for now
    private static final String VPN_ROUTE = "0.0.0.0"; // Intercept everything

    private volatile static LocalCaptureService SELF = null;

    private ParcelFileDescriptor vpnInterface = null;

    private PendingIntent pendingIntent;

    private BlockingQueue<Packet> deviceToNetworkUDPQueue;
    private BlockingQueue<Packet> deviceToNetworkTCPQueue;
    private BlockingQueue<ByteBuffer> networkToDeviceQueue;
    private ExecutorService executorService;

    // TODO: Move this to a "utils" class for reuse
    private static void closeResources(Closeable... resources) {
        for (Closeable resource : resources) {
            try {
                resource.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SELF = this;
        setupVPN();
        deviceToNetworkUDPQueue = new ArrayBlockingQueue<Packet>(1000);
        deviceToNetworkTCPQueue = new ArrayBlockingQueue<Packet>(1000);
        networkToDeviceQueue = new ArrayBlockingQueue<>(1000);

        //noinspection AlibabaThreadPoolCreation
        executorService = Executors.newFixedThreadPool(10);
        executorService.submit(new BioUdpHandler(deviceToNetworkUDPQueue, networkToDeviceQueue, this));
        executorService.submit(new BioTcpHandler(deviceToNetworkTCPQueue, networkToDeviceQueue, this));

        executorService.submit(new VPNRunnable(vpnInterface.getFileDescriptor(),
                deviceToNetworkUDPQueue, deviceToNetworkTCPQueue, networkToDeviceQueue));

        Log.i(TAG, "Started");
    }

    private void setupVPN() {
        try {
            if (vpnInterface == null) {
                Builder builder = new Builder();
                builder.addAddress(VPN_ADDRESS, 32);
                builder.addRoute(VPN_ROUTE, 0);
                builder.addDnsServer(Config.dns);
                if (Config.testLocal) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        builder.addAllowedApplication("com.aipao.hanmoveschool");
                    }
                }
                vpnInterface = builder.setSession("IMEICode Capture").setConfigureIntent(pendingIntent).establish();
                if (vpnInterface != null) {
                    CaptureController.setCaptureServiceRunning(true);
                    Utils.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(LocalCaptureService.this, "开始抓包", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "error", e);
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    public void stopCapture() {
        executorService.shutdownNow();
        cleanup();
        CaptureController.setCaptureServiceRunning(false);
        Utils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LocalCaptureService.this, "已停止", Toast.LENGTH_SHORT).show();
            }
        });
        stopSelf();
    }

    @Override
    public void onDestroy() {
        executorService.shutdownNow();
        cleanup();
        CaptureController.setCaptureServiceRunning(false);
        Utils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LocalCaptureService.this, "已停止", Toast.LENGTH_SHORT).show();
            }
        });
        Log.i(TAG, "Stopped");
        SELF = null;
        super.onDestroy();
    }

    private void cleanup() {
        deviceToNetworkTCPQueue = null;
        deviceToNetworkUDPQueue = null;
        networkToDeviceQueue = null;
        closeResources(vpnInterface);
    }

    private static class VPNRunnable implements Runnable {
        private static final String TAG = VPNRunnable.class.getSimpleName();

        private final FileDescriptor vpnFileDescriptor;

        private final BlockingQueue<Packet> deviceToNetworkUDPQueue;
        private final BlockingQueue<Packet> deviceToNetworkTCPQueue;
        private final BlockingQueue<ByteBuffer> networkToDeviceQueue;

        public VPNRunnable(FileDescriptor vpnFileDescriptor,
                           BlockingQueue<Packet> deviceToNetworkUDPQueue,
                           BlockingQueue<Packet> deviceToNetworkTCPQueue,
                           BlockingQueue<ByteBuffer> networkToDeviceQueue) {
            this.vpnFileDescriptor = vpnFileDescriptor;
            this.deviceToNetworkUDPQueue = deviceToNetworkUDPQueue;
            this.deviceToNetworkTCPQueue = deviceToNetworkTCPQueue;
            this.networkToDeviceQueue = networkToDeviceQueue;
        }

        @Override
        public void run() {
            Log.i(TAG, "Started");

            FileChannel vpnInput = new FileInputStream(vpnFileDescriptor).getChannel();
            FileChannel vpnOutput = new FileOutputStream(vpnFileDescriptor).getChannel();
            @SuppressWarnings("AlibabaAvoidManuallyCreateThread")
            Thread t = new Thread(new WriteVpnThread(vpnOutput, networkToDeviceQueue));
            t.start();

            try {
                ByteBuffer bufferToNetwork;

                while (!Thread.interrupted()) {
                    bufferToNetwork = ByteBufferPool.acquire();
                    int readBytes = vpnInput.read(bufferToNetwork);

                    if (readBytes > 0) {
                        bufferToNetwork.flip();

                        Packet packet = new Packet(bufferToNetwork);
                        if (packet.isUDP()) {
                            if (Config.logRW) {
                                Log.i(TAG, "read udp" + readBytes);
                            }
                            deviceToNetworkUDPQueue.offer(packet);
                        } else if (packet.isTCP()) {
                            if (Config.logRW) {
                                Log.i(TAG, "read tcp " + readBytes);
                            }
                            deviceToNetworkTCPQueue.offer(packet);
                        } else {
                            Log.w(TAG, String.format("Unknown packet protocol type %d", packet.ip4Header.protocolNum));
                        }
                    } else {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, e.toString(), e);
            } finally {
                closeResources(vpnInput, vpnOutput);
            }
        }

        static class WriteVpnThread implements Runnable {
            private final BlockingQueue<ByteBuffer> networkToDeviceQueue;
            FileChannel vpnOutput;

            WriteVpnThread(FileChannel vpnOutput, BlockingQueue<ByteBuffer> networkToDeviceQueue) {
                this.vpnOutput = vpnOutput;
                this.networkToDeviceQueue = networkToDeviceQueue;
            }

            @Override
            public void run() {
                while (true) {
                    try {
                        ByteBuffer bufferFromNetwork = networkToDeviceQueue.take();
                        bufferFromNetwork.flip();

                        while (bufferFromNetwork.hasRemaining()) {
                            int w = vpnOutput.write(bufferFromNetwork);

                            if (Config.logRW) {
                                Log.d(TAG, "vpn write " + w);
                            }
                        }
                    } catch (Exception e) {
                        Log.i(TAG, "WriteVpnThread fail", e);
                    }

                }

            }
        }
    }

    @Nullable
    public static LocalCaptureService getInstance() {
        return SELF;
    }

}
