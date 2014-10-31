package com.vg.live;

import static org.apache.commons.io.FilenameUtils.getBaseName;
import static org.apache.commons.lang3.math.NumberUtils.toInt;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.filefilter.FileFileFilter;
import org.eclipse.jetty.spdy.api.BytesDataInfo;
import org.eclipse.jetty.spdy.api.GoAwayResultInfo;
import org.eclipse.jetty.spdy.api.PingResultInfo;
import org.eclipse.jetty.spdy.api.ReplyInfo;
import org.eclipse.jetty.spdy.api.RstInfo;
import org.eclipse.jetty.spdy.api.SPDY;
import org.eclipse.jetty.spdy.api.Session;
import org.eclipse.jetty.spdy.api.SessionFrameListener;
import org.eclipse.jetty.spdy.api.Settings;
import org.eclipse.jetty.spdy.api.SettingsInfo;
import org.eclipse.jetty.spdy.api.Stream;
import org.eclipse.jetty.spdy.api.StreamFrameListener;
import org.eclipse.jetty.spdy.api.StreamStatus;
import org.eclipse.jetty.spdy.api.SynInfo;
import org.eclipse.jetty.spdy.client.SPDYClient;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Fields;
import org.junit.Test;

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
        SessionFrameListener sessionFrameListener = new SessionFrameListener() {
            @Override
            public void onGoAway(Session session, GoAwayResultInfo goAwayResultInfo) {
                log.debug(session + "onGoAway " + goAwayResultInfo.getSessionStatus());
                throw new RuntimeException("server told us to go away");
            }

            @Override
            public StreamFrameListener onSyn(Stream stream, SynInfo synInfo) {
                log.debug("onSyn " + synInfo);
                return null;
            }

            @Override
            public void onRst(Session session, RstInfo rstInfo) {
                log.debug("onRst " + rstInfo);
            }

            @Override
            public void onSettings(Session session, SettingsInfo settingsInfo) {
                log.debug("onSettings " + settingsInfo.getSettings());
                Settings sessionSettings = settingsInfo.getSettings();
                Settings.Setting setting = sessionSettings.get(Settings.ID.MAX_CONCURRENT_STREAMS);
                if (setting != null) {
                    int MAX_CONCURRENT_STREAMS = setting.value();
                    log.debug("session MAX_CONCURRENT_STREAMS value updated to " + MAX_CONCURRENT_STREAMS);
                }
            }

            @Override
            public void onPing(Session session, PingResultInfo pingResultInfo) {
                log.debug("onPing " + pingResultInfo);
            }

            @Override
            public void onFailure(Session session, Throwable x) {
                log.error(x + " onFailure " + session);
            }
        };

        //        URL url1 = new URL("http://localhost:8181");
        URL url = new URL("http://cloud.videogorillas.com:8181");

        SPDYClient.Factory clientFactory = new SPDYClient.Factory();
        clientFactory.setConnectTimeout(1000);
        clientFactory.start();
        SPDYClient spdyClient = clientFactory.newSPDYClient(SPDY.V3);

        InetSocketAddress address = new InetSocketAddress(url.getHost(), url.getPort());
        Session session = spdyClient.connect(address, sessionFrameListener);
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

        Semaphore semaphore = new Semaphore(10);

        File tsDir = new File("/Users/chexov/live/774128639/GOPRO20140821063020.MP4");
        File[] files = tsDir.listFiles((FileFilter) FileFileFilter.FILE);
        Arrays.sort(files, (f1, f2) -> {
            int int1 = toInt(getBaseName(f1.getName()));
            int int2 = toInt(getBaseName(f2.getName()));
            return int1 - int2;
        });

        for (File ts : files) {
            Callback.Adapter callback = new Callback.Adapter() {
                @Override
                public void succeeded() {
                    System.err.println(Thread.currentThread().getName() + " ===> " + ts.getName() + " UPLOAD OK");
                    semaphore.release();
                }

                @Override
                public void failed(Throwable x) {
                    System.err.println(Thread.currentThread().getName() + " ===> " + ts.getName() + " UPLOAD FAILED "
                            + x);
                    semaphore.release();
                }
            };

            log.debug("sendFile " + ts.getName());

            log.debug("streams count " + session.getStreams().size());
            semaphore.acquire();
            sendFile(session, ts, callback);
        }
    }

    public void sendFile(Session session, final File ts, Callback callback) throws ExecutionException,
            InterruptedException, TimeoutException, IOException {

        StreamFrameListener listener = new StreamFrameListener.Adapter() {
            @Override
            public void onReply(Stream stream, ReplyInfo replyInfo) {
                log.debug(Thread.currentThread().getName() + " got reply " + replyInfo);
                callback.succeeded();
            }

            @Override
            public void onFailure(Stream stream, Throwable x) {
                log.error(Thread.currentThread().getName() + " " + ts.getName() + " onFailure " + x);
                try {
                    if (!stream.isClosed()) {
                        session.rst(new RstInfo(stream.getId(), StreamStatus.CANCEL_STREAM));
                    }
                } catch (Exception e) {
                    log.error("stream failure " + e);
                }

                callback.failed(x);
            }
        };

        String streamId = ts.getParentFile().getName() + "/" + ts.getName();
        Fields headers = new Fields();
        headers.add(":method", "POST");
        headers.add(":path", "/spdy/gopro/" + streamId);
        headers.add("ts-length", ts.length() + "");

        SynInfo synInfo = new SynInfo(10, TimeUnit.SECONDS, headers, false, (byte) 0);


        final Stream stream = session.syn(synInfo, listener);

        byte[] bytes = Files.readAllBytes(ts.toPath());
        BytesDataInfo dataInfo = new BytesDataInfo(10, TimeUnit.SECONDS, bytes, true);

        if (!stream.isClosed() || !stream.isReset()) {
            log.debug(stream.getId() + " :: " + "stream.data " + ts.getName() + " " + dataInfo.length());
            stream.data(dataInfo, new Callback() {
                @Override
                public void succeeded() {
                    log.debug(stream.getId() + " :: " + ts.getName() + " sent but no reply yet");
                    // XXX: fires when data sent but not confirmed as received. use Stream.onReply event instead
                }

                @Override
                public void failed(Throwable x) {
                    log.debug(stream.getId() + " :: " + ts.getName() + " failed upload " + x + " " + stream.isClosed()
                            + " " + stream.isReset() + " " + stream);
                    callback.failed(x);
                }
            });
        } else {
            throw new RuntimeException(stream + " is closed");
        }
    }
}