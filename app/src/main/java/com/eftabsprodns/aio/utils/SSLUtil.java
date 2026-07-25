package com.eftabsprodns.aio.utils;

import android.annotation.SuppressLint;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.LinkedHashSet;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.eftabsprodns.aio.service.VPNService;

public class SSLUtil extends SSLSocketFactory {
    private final SSLContext mSSLContext;

    private final VPNService mInjector;

    public SSLUtil(VPNService mInjector) throws Exception {
        this.mInjector = mInjector;
        mSSLContext = SSLContext.getInstance("TLS");
        mSSLContext.init(null, new TrustManager[]{new MyX509TrustManager()}, new SecureRandom());
    }

    private void createSSLSocket(String host, int port, boolean z) throws IOException {
        VPNService.mSSLSocket = (SSLSocket) mSSLContext.getSocketFactory().createSocket(VPNService.server, host, port, z);
        LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>();
        Collections.addAll(linkedHashSet, VPNService.mSSLSocket.getEnabledProtocols());
        VPNService.mSSLSocket.setEnabledProtocols(linkedHashSet.toArray(new String[0]));
        VPNService.mSSLSocket.addHandshakeCompletedListener(new HandshakeTunnelCompletedListener(port));
    }

    public Socket createSocket(String host, int port) throws IOException {
        createSSLSocket(host, port, true);
        return VPNService.mSSLSocket;
    }

    public Socket createSocket(String str, int i, InetAddress inetAddress, int i2) {
        return null;
    }

    public Socket createSocket(InetAddress inetAddress, int i) {
        return null;
    }

    public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress2, int i2) {
        return null;
    }

    public Socket createSocket(Socket socket, String host, int port, boolean z) throws IOException {
        createSSLSocket(host, port, z);
        return VPNService.mSSLSocket;
    }

    public String[] getDefaultCipherSuites() {
        return new String[0];
    }

    public String[] getSupportedCipherSuites() {
        return new String[0];
    }

    @SuppressLint("CustomX509TrustManager")
    public static class MyX509TrustManager implements X509TrustManager {
        @SuppressLint({"TrustAllX509TrustManager"})
        public void checkClientTrusted(X509Certificate[] x509CertificateArr, String str) {
        }

        @SuppressLint({"TrustAllX509TrustManager"})
        public void checkServerTrusted(X509Certificate[] x509CertificateArr, String str) {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }

    class HandshakeTunnelCompletedListener implements HandshakeCompletedListener {
        private final int val$port;

        HandshakeTunnelCompletedListener(int i) {
            this.val$port = i;
        }

        public void handshakeCompleted(HandshakeCompletedEvent handshakeCompletedEvent) {
            mInjector.addLogInfo("<b>Established " + handshakeCompletedEvent.getSession().getProtocol() + " connection with " + "******" + ":" + this.val$port + " using " + handshakeCompletedEvent.getCipherSuite() + "</b>");
            mInjector.addLogInfo("SSL: Using cipher " + handshakeCompletedEvent.getSession().getCipherSuite());
            mInjector.addLogInfo("SSL: Using protocol " + handshakeCompletedEvent.getSession().getProtocol());
            mInjector.addLogInfo("SSL: Handshake finished");
        }
    }
}
