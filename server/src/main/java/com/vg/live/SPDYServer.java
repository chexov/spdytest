package com.vg.live;

import java.io.File;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.spdy.api.GoAwayResultInfo;
import org.eclipse.jetty.spdy.api.PingResultInfo;
import org.eclipse.jetty.spdy.api.RstInfo;
import org.eclipse.jetty.spdy.api.SPDY;
import org.eclipse.jetty.spdy.api.Session;
import org.eclipse.jetty.spdy.api.Settings;
import org.eclipse.jetty.spdy.api.SettingsInfo;
import org.eclipse.jetty.spdy.api.Stream;
import org.eclipse.jetty.spdy.api.StreamFrameListener;
import org.eclipse.jetty.spdy.api.SynInfo;
import org.eclipse.jetty.spdy.api.server.ServerSessionFrameListener;
import org.eclipse.jetty.spdy.server.SPDYServerConnectionFactory;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.Fields.Field;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import com.vg.util.LogManager;
import com.vg.util.Logger;

public class SPDYServer {

    public static final Logger log = LogManager.getLogger(SPDYServer.class);

    public static void main(String[] args) throws Exception {
        File liveDir = new File("/Users/chexov/live");
        if (args.length == 1) {
            liveDir = new File(args[0]);
        }
        liveDir.mkdirs();

        final File finalLiveDir = liveDir;
        ServerSessionFrameListener listener = new ServerSessionFrameListener() {
            @Override
            public void onConnect(Session session) {
                log.debug("onConnect " + session);
                Set<Stream> streams = session.getStreams();
                log.debug("streams " + streams);

                Settings s = new Settings();
                s.put(new Settings.Setting(Settings.ID.MAX_CONCURRENT_STREAMS, 10));
                SettingsInfo si = new SettingsInfo(s);
                try {
                    session.settings(si);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onRst(Session session, RstInfo rstInfo) {
                log.debug("onRst " + rstInfo);
            }

            @Override
            public void onSettings(Session session, SettingsInfo settingsInfo) {
                log.debug("onSettings " + settingsInfo.getSettings());
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
                log.debug("onFailure " + session);
                x.printStackTrace();
            }

            @Override
            public StreamFrameListener onSyn(Stream stream, SynInfo synInfo) {
                Session session = stream.getSession();
                log.debug(session.getRemoteAddress() + " session open streams " + session.getStreams().size());
                Fields headers = synInfo.getHeaders();

                log.debug(stream + " onSyn " + synInfo);

                Field tslen = headers.get("ts-length");
                Field path = headers.get(":path");
                File tsFile = null;
                int tslength = -1;
                if (tslen != null && path != null) {
                    tslength = Integer.parseInt(StringUtils.defaultString(tslen.getValue(), "-1"));
                    log.debug("got ts length " + tslength + " " + path);

                    String[] split = path.getValue().split("\\/");

                    //path=[/spdy/gopro/25fps/962.ts]
                    if (split.length > 2) {
                        String userId = split[split.length - 3];
                        String streamName = split[split.length - 2];
                        String tsName = split[split.length - 1];

                        File streamDir = new File(finalLiveDir, userId + "/" + streamName + "/");
                        if (!streamDir.exists()) {
                            streamDir.mkdirs();
                        }

                        tsFile = new File(streamDir, tsName);
                        log.debug("tsFile = " + tsFile);
                    } else {
                        throw new RuntimeException("not an upload path: " + split);
                    }
                }
                return new TSFileUploadFrameListener(tsFile, tslength);
            }

        };

        Server server = new Server();

        ServerConnector spdy3 = new ServerConnector(server, (SslContextFactory) null, new SPDYServerConnectionFactory(
                SPDY.V3, listener), new HttpConnectionFactory());
        spdy3.setPort(8181);
        server.addConnector(spdy3);

        server.start();
        server.join();
    }
}
