package com.eftabsprodns.aio.thread;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import app.tunnel.vpncommons.vpnstatus.TkLogStatus;

public class PayloadInjector extends Thread {
    public static int status = 0;
    private boolean d;
    private boolean c;
    private Socket a;
    private Socket b;

    public PayloadInjector() {
    }

    public PayloadInjector(Socket input, Socket output, boolean clientToServer, boolean autoReplace) {
        this.a = input;
        this.b = output;
        this.c = clientToServer;
        this.d = autoReplace;
    }

    public static void connect(Socket socket, Socket socket2, boolean autoReplace) {
        new PayloadInjector(socket, socket2, true, autoReplace).start();
        new PayloadInjector(socket2, socket, false, autoReplace).start();
    }

    public final void run() {
        super.run();
        byte[] buffer = new byte[this.c ? 16384 : 32768];
        try {
            InputStream FromClient = this.a.getInputStream();
            OutputStream ToClient = this.b.getOutputStream();
            while (true) {
                int numberRead = FromClient.read(buffer);
                if (numberRead == -1) {
                    break;
                }
                try {
                    String result = new String(buffer, 0, numberRead);
                    if (this.c) {
                        ToClient.write(buffer, 0, numberRead);
                        ToClient.flush();
                    } else {
                        String[] split = result.split("\r\n");
                        if (split[0].startsWith("HTTP/")) {
                            String line = split[0];
                            int code = Integer.parseInt(line.substring(9, 12));
                            addLog(line);
                            if (code == 200) {
                                ToClient.write(buffer, 0, numberRead);
                                ToClient.flush();
                            } else {
                                addLog("Proxy: Auto Replace Header");
                                if (this.d) {
                                    addLog((split[0].split(" ")[0] + " 200 OK"));
                                    ToClient.write("HTTP/1.0 200 OK\r\n\r\n".getBytes());
                                    ToClient.flush();
                                } else {
                                    addLog((split[0].split(" ")[0] + " 200 Connection established"));
                                    ToClient.write("HTTP/1.0 200 Connection Established\r\n\r\n".getBytes());
                                    ToClient.flush();
                                }
                            }
                            status = code;
                        } else {
                            ToClient.write(buffer, 0, numberRead);
                            ToClient.flush();
                        }
                    }
                } catch (Exception e) {
                    try {
                        if (this.a != null) {
                            this.a.close();
                        }
                        if (this.b != null) {
                            this.b.close();
                            return;
                        }
                        return;
                    } catch (IOException e2) {
                        return;
                    }
                } catch (Throwable th) {
                    try {
                        if (this.a != null) {
                            this.a.close();
                        }
                        if (this.b != null) {
                            this.b.close();
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void interrupt() {
        super.interrupt();
        try {
            if (this.a != null) {
                this.a.close();
            }
            if (this.b != null) {
                this.b.close();
            }
        } catch (IOException ignored) {
        }
    }

    void addLog(String str) {
        TkLogStatus.logInfo(str);
    }
}

