package com.eftabsprodns.aio.myhotspot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import com.tpv.plus.R;

public class ProxyService extends Service {
    final String LOG_TAG = "myLogs";
    private int port;
    private ServerThreadTask serverThreadTask;

    public void onCreate() {
        super.onCreate();
        //Log.d(LOG_TAG, "onCreate");
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        //Log.d(LOG_TAG, "onStartCommand");
        port = intent.getIntExtra("port", 8080);
        Intent notificationIntent = new Intent(this, MainActivityWifi.class);
        int flag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE | 0 : 0;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flag);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(getPackageName(),
                    "ProxyService", NotificationManager.IMPORTANCE_NONE);
            notificationChannel.setLightColor(Color.BLUE);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(notificationChannel);
        }
        startForeground(1,
                new NotificationCompat.Builder(this, getPackageName()).setOngoing(true)
                        .setSmallIcon(R.drawable.icon_main).setContentTitle("Hotshare")
                        .setContentText("Hotshare service is running").setContentIntent(pendingIntent).build());
        serverThreadTask = new ServerThreadTask(port);
        serverThreadTask.setDaemon(true);
        serverThreadTask.start();
        return super.onStartCommand(intent, flags, startId);
    }

    public void onDestroy() {
        serverThreadTask.interrupt();
        super.onDestroy();

        Log.d(LOG_TAG, "onDestroy");
    }

    public IBinder onBind(Intent intent) {
        Log.d(LOG_TAG, "onBind");
        return null;
    }

    class ServerThreadTask extends Thread {
        private final int port;

        public ServerThreadTask(int port) {
            this.port = port;
        }

        @Override
        public void run() {
            try {
                ServerSocket httpSocket = new ServerSocket(port);
                Socket clientSocket;
                while (!interrupted()) {
                    clientSocket = httpSocket.accept();
                    System.out.println("Socket accepted");
                    new ClientSocketHandler(clientSocket).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

/*	class ServerTask extends AsyncTask<Void, Void, Void> {
		private int port;

		@Override
		protected void onPreExecute() {
			port = ProxyService.this.port;
		}

		@Override
		protected Void doInBackground(Void... voids) {
			try {
				ServerSocket httpSocket = new ServerSocket(port);
				Socket clientSocket;
				while (FlagSingleton.getInstance().isFlag()) {
					clientSocket = httpSocket.accept();
					System.out.println("Socket accepted");
					new ClientSocketHandler(clientSocket).start();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}
	}*/
}
