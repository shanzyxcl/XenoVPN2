package com.eftabsprodns.aio.core.vpnutils;

import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.ParcelFileDescriptor;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

import app.tunnel.vpncommons.vpnstatus.TkLogStatus;
import com.eftabsprodns.aio.service.VPNService;


public class Tun2Socks extends Thread implements StreamGobbler.OnLineListener {


    private static final String TUN2SOCKS_BIN = "libtun2socks";
    private final ParcelFileDescriptor mVpnInterfaceFileDescriptor;
    private final int mVpnInterfaceMTU;
    private final String mVpnIpAddress;
    private final String mVpnNetMask;
    private final String mSocksServerAddress;
    private final String mUdpgwServerAddress;
    private final String mDnsResolverAddress;
    private final boolean mUdpgwTransparentDNS;
    private final Context mContext;
    private OnTun2SocksListener mListener;
    private Process tun2SocksProcess;
    private File fileTun2Socks;

    public Tun2Socks(Context context, ParcelFileDescriptor vpnInterfaceFileDescriptor, int vpnInterfaceMTU, String vpnIpAddress, String vpnNetMask, String socksServerAddress, String udpgwServerAddress, String dnsResolverAddress, boolean udpgwTransparentDNS) {
        mContext = context;
        mVpnInterfaceFileDescriptor = vpnInterfaceFileDescriptor;
        mVpnInterfaceMTU = vpnInterfaceMTU;
        mVpnIpAddress = vpnIpAddress;
        mVpnNetMask = vpnNetMask;
        mSocksServerAddress = socksServerAddress;
        mUdpgwServerAddress = udpgwServerAddress;
        mDnsResolverAddress = dnsResolverAddress;
        mUdpgwTransparentDNS = udpgwTransparentDNS;
    }

    @Override
    public void run() {
        super.run();
        if (!VPNService.isRunning) {
            interrupt();
            return;
        }
        if (mListener != null) {
            mListener.onStart();
        }
        try {

            StringBuilder cmd = new StringBuilder();

            fileTun2Socks = CustomNativeLoader.loadNativeBinary(mContext, TUN2SOCKS_BIN, new File(mContext.getFilesDir(), TUN2SOCKS_BIN));

            if (fileTun2Socks == null) {
                throw new IOException("Bin Tun2Socks was not found");
            }

            if (mVpnInterfaceFileDescriptor != null) {
                File file_path = new File(ContextCompat.getDataDir(mContext), "sock_path");

                try {
                    if (!file_path.exists())
                        file_path.createNewFile();
                } catch (IOException e) {
                    throw new IOException("Failed to create file: " + file_path.getCanonicalPath());
                }

                cmd.append(fileTun2Socks.getCanonicalPath());
                cmd.append(" --netif-ipaddr ").append(mVpnIpAddress);
                cmd.append(" --netif-netmask ").append(mVpnNetMask);
                cmd.append(" --socks-server-addr ").append(mSocksServerAddress);
                cmd.append(" --tunmtu ").append(mVpnInterfaceMTU);
                cmd.append(" --tunfd ").append(mVpnInterfaceFileDescriptor.getFd());
                cmd.append(" --sock ").append(file_path.getAbsolutePath());
                cmd.append(" --loglevel ").append(3);

                if (mUdpgwServerAddress != null) {
                    if (mUdpgwTransparentDNS) {
                        cmd.append(" --udpgw-transparent-dns");
                    }
                    cmd.append(" --udpgw-remote-server-addr ").append(mUdpgwServerAddress);
                }

                if (mDnsResolverAddress != null) {
                    cmd.append(" --dnsgw ").append(mDnsResolverAddress);
                }

                tun2SocksProcess = Runtime.getRuntime().exec(cmd.toString());

                StreamGobbler stdoutGobbler = new StreamGobbler(tun2SocksProcess.getInputStream(), this);
                StreamGobbler stderrGobbler = new StreamGobbler(tun2SocksProcess.getErrorStream(), this);

                stdoutGobbler.start();
                stderrGobbler.start();

                if (!sendFd(mVpnInterfaceFileDescriptor, file_path)) {
                    throw new IOException("Go to send Fd for sock, but this is not supported by your device. Get in touch with the designer.");
                }

                tun2SocksProcess.waitFor();
            }

        } catch (IOException e) {
            addLog("Tun2Socks Error: " + e.getMessage());
        } catch (Exception ex) {
            TkLogStatus.logDebug("Tun2Socks Error: " + ex);
        }

        tun2SocksProcess = null;
    }

    @Override
    public synchronized void interrupt() {
        super.interrupt();

        if (tun2SocksProcess != null)
            tun2SocksProcess.destroy();

        try {
            if (fileTun2Socks != null)
                VpnUtils.killProcess(fileTun2Socks);
        } catch (Exception ignored) {

        }

        tun2SocksProcess = null;
        fileTun2Socks = null;
    }

    public void setOnTun2SocksListener(OnTun2SocksListener listener) {
        this.mListener = listener;
    }

    @Override
    public void onLine(String log) {
        android.util.Log.d("Tun2Socks: ", log);
    }

    private boolean sendFd(ParcelFileDescriptor fileDescriptor, File toFile) throws InterruptedException {
        for (int tries = 10; tries >= 0; tries--) {
            try {
                LocalSocket localSocket = new LocalSocket();
                localSocket.connect(new LocalSocketAddress(toFile.getAbsolutePath(), LocalSocketAddress.Namespace.FILESYSTEM));
                localSocket.setFileDescriptorsForSend(new FileDescriptor[]{fileDescriptor.getFileDescriptor()});
                localSocket.getOutputStream().write(42);
                localSocket.shutdownOutput();
                localSocket.close();
                return true;
            } catch (IOException unused) {
                Thread.sleep(500);
            }
        }
        return false;
    }

    private void addLog(String log) {
        TkLogStatus.logInfo(log);
    }

    public interface OnTun2SocksListener {
        void onStart();
    }
}
