package com.example.datarecorderapp.sntpclient;

//import com.jkoolcloud.tnt4j.core.OpLevel;
//import com.jkoolcloud.tnt4j.sink.DefaultEventSinkFactory;
//import com.jkoolcloud.tnt4j.sink.EventSink;
//import com.jkoolcloud.tnt4j.utils.Useconds;

import android.util.Log;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * This class implements a time service that delivers synchronized time using NTP. Developers should use
 * {@code TimeService.currentTimeMillis()} instead of calling {@code System.currentTimeMillis()} to obtain synchronized
 * and adjusted current time. To enable NTP time synchronization set the following property:
 * {@code tnt4j.time.server=ntp-server:port}, otherwise {@code System.currentTimeMillis()} is returned.
 *
 * @version $Revision: 1 $
 */
public class TimeService {

    protected static final int ONE_K = 1000;
    protected static final int ONE_M = 1000000;
    protected static final boolean TIME_SERVER_VERBOSE = Boolean.getBoolean("tnt4j.time.server.verbose");

    private static final String TIME_SERVER = System.getProperty("192.168.43.115:4409");
    private static final long TIME_SERVER_TIMEOUT = Long.getLong("tnt4j.time.server.timeout", 10000);

    static long timeOverheadNanos = 0;
    static long timeOverheadMillis = 0;
    static long adjustment = 0;
    static long updatedTime = 0;
    static ScheduledExecutorService scheduler;
    static ClockDriftMonitorTask clockSyncTask = null;

    static NTPUDPClient timeServer = new NTPUDPClient();
    static TimeInfo timeInfo;

    static {
        try {
            timeOverheadNanos = calculateOverhead(ONE_M);
            timeOverheadMillis = (timeOverheadNanos / ONE_M);
            updateTime();
        } catch (Throwable e) {
            Log.i(" Try update Time: ", e.getLocalizedMessage());
        } finally {
            scheduleUpdates();
        }
    }

    private TimeService() {
    }

    /**
     * Schedule automatic clock synchronization with NTP and internal clocks
     *
     */
    private static synchronized void scheduleUpdates() {
        if (scheduler == null) {
            scheduler = Executors.newScheduledThreadPool(1, new TimeServiceThreadFactory("TimeService/clock-sync"));
            //clockSyncTask = new ClockDriftMonitorTask(logger);
            //scheduler.submit(clockSyncTask);
        }
    }

    /**
     * Obtain NTP connection host:port of the time server.
     *
     * @return time server connection string
     */
    public static String getTimeServer() {
        return TIME_SERVER;
    }

    /**
     * Obtain time stamp when the NTP time was synchronized
     *
     * @return time stamp when NTP was updated
     */
    public static long getLastUpdatedMillis() {
        return updatedTime;
    }

    /**
     * Obtain configured NTP server timeout
     *
     * @return time server timeout in milliseconds
     */
    public static long getTimeServerTimeout() {
        return TIME_SERVER_TIMEOUT;
    }

    /**
     * Obtain NTP time and synchronize with NTP server
     *
     * @throws IOException
     *             if error accessing time server
     */
    public static void updateTime() throws IOException {
        if (TIME_SERVER != null) {
            timeServer.setDefaultTimeout((int) TIME_SERVER_TIMEOUT);
            String[] pair = TIME_SERVER.split(":");
            InetAddress hostAddr = InetAddress.getByName(pair[0]);
            timeInfo = pair.length < 2 ? timeServer.getTime(hostAddr)
                    : timeServer.getTime(hostAddr, Integer.parseInt(pair[1]));
            timeInfo.computeDetails();
            adjustment = timeInfo.getOffset() - timeOverheadMillis;
            updatedTime = currentTimeMillis();
            if (TIME_SERVER_VERBOSE) {
                Log.i("Time info: ",String.valueOf(timeInfo.getOffset()));
            }
        }
    }

    /**
     * Obtain measured overhead of calling {@link} in
     * nanoseconds.
     *
     * @return total measured overhead in nanoseconds
     */
    public static long getOverheadNanos() {
        return timeOverheadNanos;
    }

    /**
     * Obtain number of milliseconds since NTP time was synchronized
     *
     * @return time (ms) since last NTP synchronization
     */
    public static long getUpdateAgeMillis() {
        return TimeService.getLastUpdatedMillis() > 0
                ? TimeService.currentTimeMillis() - TimeService.getLastUpdatedMillis() : -1;
    }

    /**
     * Obtain NTP synchronized current time in milliseconds
     *
     * @return current NTP synchronized time in milliseconds
     */
    public static long currentTimeMillis() {
        return System.currentTimeMillis() + adjustment;
    }

    /**
     * Obtain NTP synchronized current time in microseconds precision (but necessarily accuracy)
     *
     * @return current NTP synchronized time in microseconds
     */
    public static long currentTimeUsecs() {
        return (System.currentTimeMillis() + adjustment) * ONE_K;
    }

    /**
     * Obtain currently measured clock drift in milliseconds
     *
     * @return clock drift in milliseconds
     */
    public static long getDriftMillis() {
        return clockSyncTask.getDriftMillis();
    }

    /**
     * Obtain measured total clock drift in milliseconds since start up
     *
     * @return total clock drift since start up
     */
    public static long getTotalDriftMillis() {
        return clockSyncTask.getTotalDriftMillis();
    }

    /**
     * Obtain total number of times clocks have been updated to adjust for drift.
     *
     * @return number of times updated to adjust for cock drift
     */
    public static long getDriftUpdateCount() {
        return clockSyncTask.getDriftUpdateCount();
    }

    /**
     * Obtain currently measured clock drift interval in milliseconds
     *
     * @return clock drift interval in milliseconds
     */
    public static long getDriftIntervalMillis() {
        return clockSyncTask.getIntervalMillis();
    }

    /**
     * Calculate overhead of {@link }
     * of iterations.
     *
     * @param runs
     *            number of iterations
     * @return calculated overhead of getting timestamp
     */
    public static long calculateOverhead(long runs) {
        long start = System.nanoTime();
        _calculateOverheadCost(runs);
        for (int i = 0; i < runs; i++) {
            currentTimeMillis();
        }
        return ((System.nanoTime() - start) / runs);
    }

    private static long _calculateOverheadCost(long runs) {
        return runs;
    }
}

class TimeServiceThreadFactory implements ThreadFactory {
    int count = 0;
    String prefix;

    TimeServiceThreadFactory(String pfix) {
        prefix = pfix;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread task = new Thread(r, prefix + "-" + count++);
        task.setDaemon(true);
        return task;
    }
}

class ClockDriftMonitorTask implements Runnable {
    private static final long TIME_CLOCK_DRIFT_SAMPLE = Integer.getInteger("tnt4j.time.server.drift.sample.ms", 10000);
    private static final long TIME_CLOCK_DRIFT_LIMIT = Integer.getInteger("tnt4j.time.server.drift.limit.ms", 1);

    long interval, drift, updateCount = 0, totalDrift;

    ClockDriftMonitorTask() {

    }

    public long getIntervalMillis() {
        return interval;
    }

    public long getDriftMillis() {
        return drift;
    }

    public long getTotalDriftMillis() {
        return totalDrift;
    }

    public long getDriftUpdateCount() {
        return updateCount;
    }

    private void syncClocks() {
        try {
            TimeService.updateTime();
            //Useconds.CURRENT.sync();
            updateCount++;
            if (TimeService.TIME_SERVER_VERBOSE) {

            }
        } catch (Throwable ex) {
            ex.getStackTrace();
        }
    }

    @Override
    public void run() {
        long start = System.nanoTime();
        long base = System.currentTimeMillis() - (start / TimeService.ONE_M);

        while (true) {
            try {
                Thread.sleep(TIME_CLOCK_DRIFT_SAMPLE);
            } catch (InterruptedException e) {
            }
            long now = System.nanoTime();
            drift = System.currentTimeMillis() - (now / TimeService.ONE_M) - base;
            totalDrift += Math.abs(drift);
            interval = (now - start) / TimeService.ONE_M;
            if (Math.abs(drift) >= TIME_CLOCK_DRIFT_LIMIT) {
                syncClocks();
                start = System.nanoTime();
                base = System.currentTimeMillis() - (start / TimeService.ONE_M);
            }
        }
    }
}