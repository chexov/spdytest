package com.vg.live.spdy;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

import com.vg.util.LogManager;
import com.vg.util.Logger;

public class SPDYUploaderQueue {

    public static final Logger log = LogManager.getLogger(SPDYUploaderQueue.class);
    private final URL url;

    private static SPDYClient.Factory clientFactory;
    private final SPDYClient spdyClient;
    private final Session session;
    private Settings sessionSettings;
    int MAX_CONCURRENT_STREAMS = 1;

    private final ExecutorService executorService = Executors.newFixedThreadPool(1);
    private final AsyncDispatcher dispatcher;

    public SPDYUploaderQueue(URL url) throws Exception {
        this.url = url;
        this.spdyClient = spdyClient();
        spdyClient.setIdleTimeout(10000);
        this.session = createSession();
        this.dispatcher = new AsyncDispatcher(executorService);
    }

    public void newCall(File ts, Callback callback) {
        AsyncUploadCall asyncUploadCall = new AsyncUploadCall(dispatcher, session, ts, callback);
        dispatcher.enqueue(asyncUploadCall);
    }

    public void blockTillAllDone() throws Exception {
        getExecutorService().awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public Session createSession() throws Exception {
        SessionFrameListener sessionFrameListener = new SessionFrameListener.Adapter() {
            public final Logger log = LogManager.getLogger(SessionFrameListener.class);

            @Override
            public void onRst(Session session, RstInfo rstInfo) {
                log.debug("onRst " + rstInfo);
            }

            @Override
            public void onSettings(Session session, SettingsInfo settingsInfo) {
                log.debug("onSettings " + settingsInfo.getSettings());
                sessionSettings = settingsInfo.getSettings();
                Settings.Setting setting = sessionSettings.get(Settings.ID.MAX_CONCURRENT_STREAMS);
                if (setting != null) {
                    MAX_CONCURRENT_STREAMS = setting.value();
                    log.debug("session MAX_CONCURRENT_STREAMS value updated to " + MAX_CONCURRENT_STREAMS);
                    ((ThreadPoolExecutor) executorService).setMaximumPoolSize(MAX_CONCURRENT_STREAMS);
                    dispatcher.setMaxRequests(MAX_CONCURRENT_STREAMS);
                }
            }

            @Override
            public void onFailure(Session session, Throwable x) {
                log.error(x + " onFailure " + session);

            }
        };
        SessionFrameListener sessionListener = sessionFrameListener;
        return spdyClient.connect(new InetSocketAddress(url.getHost(), url.getPort()), sessionListener);
    }

    public static SPDYClient spdyClient() throws Exception {
        clientFactory = new SPDYClient.Factory();
        clientFactory.setConnectTimeout(1000);
        clientFactory.start();
        return clientFactory.newSPDYClient(SPDY.V3);
    }
}
