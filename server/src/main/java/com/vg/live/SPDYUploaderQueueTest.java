package com.vg.live;

import static org.apache.commons.io.FilenameUtils.getBaseName;
import static org.apache.commons.lang3.math.NumberUtils.toInt;

import java.io.File;
import java.io.FileFilter;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.filefilter.FileFileFilter;
import org.eclipse.jetty.spdy.api.GoAwayResultInfo;
import org.eclipse.jetty.spdy.api.PingResultInfo;
import org.eclipse.jetty.spdy.api.RstInfo;
import org.eclipse.jetty.spdy.api.SPDY;
import org.eclipse.jetty.spdy.api.Session;
import org.eclipse.jetty.spdy.api.SessionFrameListener;
import org.eclipse.jetty.spdy.api.Settings;
import org.eclipse.jetty.spdy.api.SettingsInfo;
import org.eclipse.jetty.spdy.api.Stream;
import org.eclipse.jetty.spdy.api.StreamFrameListener;
import org.eclipse.jetty.spdy.api.SynInfo;
import org.eclipse.jetty.spdy.client.SPDYClient;
import org.eclipse.jetty.util.Callback;
import org.junit.Test;

import com.vg.live.spdy.SPDYUploader;
import com.vg.live.spdy.SPDYUploaderQueue;
import com.vg.util.LogManager;
import com.vg.util.Logger;

public class SPDYUploaderQueueTest {
    public static final Logger log = LogManager.getLogger(SPDYUploaderQueueTest.class);

    @Test
    public void testUpload() throws Exception {
        //        URL url1 = new URL("http://localhost:8181");
        URL url1 = new URL("http://cloud.videogorillas.com:8181");

        SPDYUploaderQueue spdyUploaderQueue = new SPDYUploaderQueue(url1);

        File tsDir = new File("/Users/chexov/work/vig/idea/goprolive/goprolive/testdata/gopro/25fps/");
        for (File ts : tsDir.listFiles((FileFilter) FileFileFilter.FILE)) {
            Callback.Adapter callback = new Callback.Adapter() {
                @Override
                public void succeeded() {
                    System.out.println(ts + " upload ok");
                }

                @Override
                public void failed(Throwable x) {
                    System.out.println(ts.getName() + " upload fail " + x);
                    //                    x.printStackTrace();
                }
            };

            spdyUploaderQueue.newCall(ts, callback);
        }
        spdyUploaderQueue.blockTillAllDone();
    }

    @Test
    public void testUpload2() throws Exception {
        //        URL url1 = new URL("http://localhost:8181");
        URL url1 = new URL("http://cloud.videogorillas.com:8181");

        SPDYUploaderQueue spdyUploaderQueue = new SPDYUploaderQueue(url1);

        File tsDir = new File("/Users/chexov/live/774128639/GOPRO20140821063020.MP4");
        for (File ts : tsDir.listFiles((FileFilter) FileFileFilter.FILE)) {
            Callback.Adapter callback = new Callback.Adapter() {
                @Override
                public void succeeded() {
                    System.out.println(Thread.currentThread().getName() + " " + ts + " upload ok");
                }

                @Override
                public void failed(Throwable x) {
                    System.out.println(Thread.currentThread().getName() + " " + ts.getName() + " upload fail " + x);
                }
            };
            System.out.println(ts.getName());
            spdyUploaderQueue.newCall(ts, callback);
        }
        spdyUploaderQueue.blockTillAllDone();
    }

    @Test
    public void testUpload3() throws Exception {
        URL url = new URL("http://cloud.videogorillas.com:8181");

        SPDYUploader uploader = new SPDYUploader(url);
        Session session = uploader.connect();

        session.addListener(new Session.StreamListener() {
            @Override
            public void onStreamCreated(Stream stream) {
                log.debug("onStreamCreated " + stream);
            }

            @Override
            public void onStreamClosed(Stream stream) {
                log.debug("onStreamClosed " + stream);
            }
        });

        Semaphore semaphore = new Semaphore(9);

        File tsDir = new File("/Users/chexov/live/774128639/GOPRO20140821063020.MP4");
        File[] files = tsDir.listFiles((FileFilter) FileFileFilter.FILE);
        Arrays.sort(files, (f1, f2) -> {
            int int1 = toInt(getBaseName(f1.getName()));
            int int2 = toInt(getBaseName(f2.getName()));
            return int1 - int2;
        });

        long startTime = System.currentTimeMillis();
        AtomicInteger bytes = new AtomicInteger(0);
        for (final File ts : files) {
            Callback.Adapter callback = new Callback.Adapter() {
                boolean semaphoreReleased = false;

                @Override
                public void succeeded() {
                    int i = bytes.addAndGet((int) ts.length());
                    long now = System.currentTimeMillis();
                    long kbps = i / (now - startTime);

                    System.err.println(Thread.currentThread().getName() + " ===> " + ts.getName() + " UPLOAD OK "
                            + kbps + " KB/s");
                    if (!semaphoreReleased) {
                        semaphoreReleased = true;
                        semaphore.release();

                    }
                }

                @Override
                public void failed(Throwable x) {
                    System.err.println(Thread.currentThread().getName() + " ===> " + ts.getName() + " UPLOAD FAILED "
                            + x);
                    if (!semaphoreReleased) {
                        semaphoreReleased = true;
                        semaphore.release();
                    }
                }
            };

            log.debug("sendFile " + ts.getName());
            log.debug("streams count " + session.getStreams().size());
            semaphore.acquire();
            SPDYUploader.sendFile(session, ts, callback);
        }
    }
}