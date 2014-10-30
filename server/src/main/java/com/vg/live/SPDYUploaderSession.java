package com.vg.live;

import java.io.File;
import java.io.FileFilter;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.vg.live.spdy.AsyncUploadCall;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.eclipse.jetty.spdy.api.BytesDataInfo;
import org.eclipse.jetty.spdy.api.GoAwayInfo;
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
import org.eclipse.jetty.util.Fields;

import com.vg.util.LogManager;
import com.vg.util.Logger;

public class SPDYUploaderSession {

    public ThreadPoolExecutor getExecutorService() {
        return executorService;
    }

    public static final Logger log = LogManager.getLogger(SPDYUploaderSession.class);
    private final URL url;
    private final Session session;

    private final ThreadPoolExecutor executorService;
    private final Deque<AsyncUploadCall> runningCalls = new ArrayDeque<>();

    int MAX_CONCURRENT_STREAMS = 1;

    ArrayList<File> tsDirList = new ArrayList<>();
    private SPDYClient spdyClient;
    private Settings sessionSettings;

    public SPDYUploaderSession(URL url) throws Exception {
        this.url = url;
        session = createSession();

        executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());

    }

    synchronized void enqueue(AsyncUploadCall call) {
        if (session.getStreams().size() < getMaxConcurrentStreams()) {
            getExecutorService().execute(call);
        } else {
            runningCalls.add(call);
        }
    }

    public Session createSession() throws Exception {
        spdyClient = spdyClient();
        SessionFrameListener sessionFrameListener = new SessionFrameListener() {
            public final Logger log = LogManager.getLogger(SessionFrameListener.class);

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
                log.debug("onSettings " + settingsInfo);
                sessionSettings = settingsInfo.getSettings();
                Settings.Setting setting = sessionSettings.get(Settings.ID.MAX_CONCURRENT_STREAMS);
                if (setting != null) {
                    MAX_CONCURRENT_STREAMS = setting.value();
                    log.debug("session MAX_CONCURRENT_STREAMS value updated to " + MAX_CONCURRENT_STREAMS);
                }
            }

            @Override
            public void onPing(Session session, PingResultInfo pingResultInfo) {
                log.debug("onPing " + pingResultInfo);
            }

            @Override
            public void onGoAway(Session session, GoAwayResultInfo goAwayResultInfo) {
                log.debug("onGoAway " + goAwayResultInfo);
            }

            @Override
            public void onFailure(Session session, Throwable x) {
                log.error(x + " onFailure " + session);

            }
        };
        SessionFrameListener sessionListener = sessionFrameListener;
        Session session = spdyClient.connect(new InetSocketAddress(url.getHost(), url.getPort()), sessionListener);
        return session;
    }

    public static SPDYClient spdyClient() throws Exception {
        SPDYClient.Factory clientFactory = new SPDYClient.Factory();
        clientFactory.setConnectTimeout(1000);
        clientFactory.start();
        return clientFactory.newSPDYClient(SPDY.V3);
    }

    public static void main(String[] args) throws Exception {
        SPDYUploaderSession spdyUploaderSession = new SPDYUploaderSession(new URL("http://localhost:8181"));
        Session session = spdyUploaderSession.createSession();

        File tsDir = new File("/Users/chexov/work/vig/idea/goprolive/goprolive/testdata/gopro/25fps/");
        try {
            System.out.println("tsDir = " + tsDir);
            System.out.println("session = " + session);

            for (File ts : tsDir.listFiles((FileFilter) FileFileFilter.FILE)) {
                Callback.Adapter callback = new Callback.Adapter() {
                    @Override
                    public void succeeded() {
                        log.debug(ts + " upload ok");
                    }

                    @Override
                    public void failed(Throwable x) {
                        System.out.println(ts + " upload fail ");
                        x.printStackTrace();
                    }
                };

//                spdyUploaderSession.uploadFile(session, ts, callback);
                AsyncUploadCall asyncUploadCall = new AsyncUploadCall(session, ts, callback);
                asyncUploadCall.run();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        session.goAway(new GoAwayInfo());
    }

    public int getMaxConcurrentStreams() {
        return MAX_CONCURRENT_STREAMS;
    }

    public void uploadFile(Session session, File ts, Callback callback) throws Exception {
        //        Callback.Adapter callback = new Callback.Adapter() {
        //            @Override
        //            public void succeeded() {
        //                log.debug(ts + " upload ok " + ts.length);
        //                tsDirList.remove(ts);
        //            }
        //
        //            @Override
        //            public void failed(Throwable x) {
        //                System.out.println(ts + " upload fail ");
        //                x.printStackTrace();
        //            }
        //        };

        StreamFrameListener listener = new StreamFrameListener.Adapter() {
            //            public void onReply(Stream stream, ReplyInfo replyInfo) {
            //                log.debug("onReply " + replyInfo);
            //            }
            //
            //            @Override
            //            public void onFailure(Stream stream, Throwable x) {
            //                log.debug("Stream onFailure: " + stream.getId() + " " + x + "  " + stream);
            //                //                x.printStackTrace();
            //            }
        };

        log.debug("Uploading file " + ts);
        Fields headers = new Fields();
        String streamId = ts.getParentFile().getName() + "/" + ts.getName();
        headers.add(":method", "POST");
        headers.add(":path", "/spdy/gopro/" + streamId);
        headers.add("ts-length", ts.length() + "");
        //        SynInfo synInfo = new SynInfo(headers, false);
        SynInfo synInfo = new SynInfo(0, TimeUnit.SECONDS, headers, false, (byte) 0);
        final Stream stream = session.syn(synInfo, listener);

        byte[] bytes = Files.readAllBytes(ts.toPath());
        BytesDataInfo dataInfo = new BytesDataInfo(10, TimeUnit.SECONDS, bytes, true);

        if (!stream.isClosed() || !stream.isReset()) {
            stream.data(dataInfo, callback);
        } else {
            throw new RuntimeException(stream + " is closed");
        }
    }

}
