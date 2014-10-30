package com.vg.live;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.filefilter.FileFileFilter;
import org.eclipse.jetty.spdy.api.BytesDataInfo;
import org.eclipse.jetty.spdy.api.GoAwayInfo;
import org.eclipse.jetty.spdy.api.ReplyInfo;
import org.eclipse.jetty.spdy.api.SPDY;
import org.eclipse.jetty.spdy.api.Session;
import org.eclipse.jetty.spdy.api.Stream;
import org.eclipse.jetty.spdy.api.StreamFrameListener;
import org.eclipse.jetty.spdy.api.SynInfo;
import org.eclipse.jetty.spdy.client.SPDYClient;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Fields;

import com.vg.util.LogManager;
import com.vg.util.Logger;

public class SPDYUploader {
    public static final Logger log = LogManager.getLogger(SPDYUploader.class);
    private final URL url;

    ArrayList<File> tsDirList = new ArrayList<>();

    public SPDYUploader(URL url) {
        this.url = url;
    }

    public Session createSession() throws Exception {
        SPDYClient spdyClient = spdyClient();
        return spdyClient.connect(new InetSocketAddress(url.getHost(), url.getPort()), null);
    }

    public static SPDYClient spdyClient() throws Exception {
        SPDYClient.Factory clientFactory = new SPDYClient.Factory();
        clientFactory.setConnectTimeout(1000);
        clientFactory.start();
        return clientFactory.newSPDYClient(SPDY.V3);
    }

    public static void main(String[] args) throws Exception {
        SPDYUploader spdyUploader = new SPDYUploader(new URL("http://localhost:8181"));
        Session session = spdyUploader.createSession();

        File tsDir = new File("/Users/chexov/work/vig/idea/goprolive/goprolive/testdata/gopro/25fps/");
        try {
            System.out.println("tsDir = " + tsDir);
            System.out.println("session = " + session);

            for (File ts : tsDir.listFiles((FileFilter) FileFileFilter.FILE)) {
                spdyUploader.uploadFile(session, ts);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        session.goAway(new GoAwayInfo());
    }

    public void uploadFile(Session session, File ts) throws ExecutionException, InterruptedException, TimeoutException,
            IOException {
        log.debug("Uploading file " + ts);
        tsDirList.add(ts);
        Fields headers = new Fields();
        String streamId = ts.getParentFile().getName() + "/" + ts.getName();
        headers.add(":method", "POST");
        headers.add(":path", "/spdy/gopro/" + streamId);
        headers.add("ts-length", ts.length() + "");

        final Stream stream = session.syn(new SynInfo(headers, false), new StreamFrameListener.Adapter() {
            @Override
            public void onReply(Stream stream, ReplyInfo replyInfo) {
                log.debug(ts + " replied with " + replyInfo);
                tsDirList.remove(ts);
            }
        });

        byte[] bytes = Files.readAllBytes(ts.toPath());
        BytesDataInfo dataInfo = new BytesDataInfo(10, TimeUnit.SECONDS, bytes, true);
        Callback.Adapter callback = new Callback.Adapter() {
            @Override
            public void succeeded() {
                log.debug(ts + " upload ok " + bytes.length);
            }

            @Override
            public void failed(Throwable x) {
                System.out.println(ts + " upload fail ");
                x.printStackTrace();
            }
        };
        if (!stream.isClosed() || !stream.isReset()) {
            stream.data(dataInfo, callback);
        } else {
            throw new RuntimeException(stream + " is closed");
        }
    }

}
