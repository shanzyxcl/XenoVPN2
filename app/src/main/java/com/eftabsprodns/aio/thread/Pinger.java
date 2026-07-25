package com.eftabsprodns.aio.thread;

import android.annotation.SuppressLint;

import android.os.Message;
import app.tunnel.vpncommons.vpnstatus.TkLogStatus;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.LocalPortForwarder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Random;

import java.io.*;
import java.net.*;
import java.util.*;

public class Pinger extends Thread {
    private final Connection a;
    private final String b;
    private boolean d;

    public Pinger(Connection aVar, String str) {
        this.a = aVar;
        this.b = str;
    }

    private int generateRandomSleepInterval() {
        return (new Random().nextInt(4) + 5) * 300;
    }

    public void interrupt() {
        this.d = false;
    }

    public void run() {
    try {
        LocalPortForwarder c = this.a.createLocalPortForwarder(9395, this.b, 80);
        this.d = true;
        int failedPings = 0;

        while (this.d) {
            try {
                long startTime = System.currentTimeMillis();
                try (Socket socket = new Socket("127.0.0.1", 9395);
                        OutputStream outputStream = socket.getOutputStream();
                        BufferedReader bufferedReader =
                                new BufferedReader(
                                        new InputStreamReader(socket.getInputStream()))) {

                    String request = "GET / HTTP/1.1\r\nHost: " + this.b + "\r\n\r\n";
                    outputStream.write(request.getBytes());
                    outputStream.flush();

                    String responseLine = bufferedReader.readLine();
                    String[] responseParts = responseLine.split(" ");
                    int responseCode = Integer.parseInt(responseParts[1]);

                    long pingTime = System.currentTimeMillis() - startTime;

                    String pingColor;

                    if (pingTime < 300) {
                        pingColor = "#31A952"; // Green
                    } else if (pingTime < 500) {
                        pingColor = "#FFD700"; // Yellow
                    } else {
                        pingColor = "#C61C19"; // Red
                    }

                    // Get the IP address of the host
                    String ipAddress = InetAddress.getByName(b).getHostAddress();

                    // Check if the ping time is beyond an acceptable threshold
                    if (pingTime > 1000) {
                        failedPings++;
                    } else {
                        failedPings = 0;
                    }

                    // Modify this line to generate the desired ping message format
                    String pingMessage =
                            String.format(
                                    "Ping (%s) - Status %d <font color='%s'>(%d ms)</font>",
                                    this.b, responseCode , pingColor, pingTime );
                    TkLogStatus.logInfo(pingMessage);

                    // Handle poor connection scenario
                    if (failedPings >= 3) {
                        TkLogStatus.logWarning("Poor internet connection detected.");
                        // Implement additional action like pausing certain operations
                    }

                } catch (IOException e) {
                    TkLogStatus.logWarning("Ping Timeout");
                }

                // Introduce sleep between pings if desired
                int sleepInterval = generateRandomSleepInterval();
                try {
                    Thread.sleep(sleepInterval);
                } catch (InterruptedException e) {
                    this.interrupt();
                }

            } catch (Exception e) {
                TkLogStatus.logWarning("Ping: " + e.toString());
            }
        }
    } catch (Exception e) {
        TkLogStatus.logWarning("Ping: " + e.toString());
    }
}

}
