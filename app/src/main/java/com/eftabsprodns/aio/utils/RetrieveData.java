package com.eftabsprodns.aio.utils;

import android.net.TrafficStats;

import java.util.ArrayList;
import java.util.List;

public class RetrieveData {


    static long totalUpload = 0;
    static long totalDownload = 0;

    public static List<Long> findData() {
        List<Long> allData = new ArrayList<>();

        long newTotalDownload, incDownload, newTotalUpload, incUpload;

        if (totalDownload == 0)
            totalDownload = TrafficStats.getTotalRxBytes();

        if (totalUpload == 0)
            totalUpload = TrafficStats.getTotalTxBytes();

        newTotalDownload = TrafficStats.getTotalRxBytes();
        incDownload = newTotalDownload - totalDownload;
        newTotalUpload = TrafficStats.getTotalTxBytes();
        incUpload = newTotalUpload - totalUpload;
        totalDownload = newTotalDownload;
        totalUpload = newTotalUpload;
        allData.add(incDownload);
        allData.add(incUpload);
        return allData;
    }


}

