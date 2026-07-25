package app.tunnel.vpncommons.vpnstatus;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

public class TrafficData {
    public static final int INTERVAL_TIME = 2 * 1000;
    private static final Vector<TrafficDataListener> mListener;
    private static long inBytesCache = 0;
    private static Timer mTimer = null;
    private static long outBytesCache = 0;

    static {
        mListener = new Vector<>();
    }

    public TrafficData() {
    }

    public static synchronized void startTimer() {
        synchronized (TrafficData.class) {
            if (mTimer != null) {
                stopTimer();
            }

            mTimer = new Timer();
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    for (TrafficDataListener updateByteCount : TrafficData.mListener)
                        updateByteCount.updateByteCount(TrafficData.getBytesIn(), TrafficData.getBytesOut());
                }
            };

            mTimer.schedule(timerTask, 0, INTERVAL_TIME);
        }
    }

    public static synchronized void stopTimer() {
        synchronized (TrafficData.class) {
            if (mTimer != null) {
                mTimer.cancel();
                mTimer = null;
            }
        }
    }

    public static synchronized void addListener(TrafficDataListener trafficDataListener) {
        synchronized (TrafficData.class) {
            mListener.add(trafficDataListener);
        }
    }

    public static synchronized void removeListener(TrafficDataListener trafficDataListener) {
        synchronized (TrafficData.class) {
            mListener.remove(trafficDataListener);
        }
    }

    public static void addBytesSend(long j) {
        outBytesCache += j;
    }

    public static void addBytesDownload(long j) {
        inBytesCache += j;
    }

    public static long getBytesIn() {
        return inBytesCache;
    }

    public static long getBytesOut() {
        return outBytesCache;
    }

    public static void clearLastSessionTraffic() {
        inBytesCache = 0;
        outBytesCache = 0;
    }

    public interface TrafficDataListener {
        void updateByteCount(long j, long j2);
    }
}
