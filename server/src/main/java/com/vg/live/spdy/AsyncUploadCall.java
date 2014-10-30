package com.vg.live.spdy;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import com.vg.live.SPDYUploaderSession;
import org.eclipse.jetty.spdy.api.BytesDataInfo;
import org.eclipse.jetty.spdy.api.Session;
import org.eclipse.jetty.spdy.api.Stream;
import org.eclipse.jetty.spdy.api.StreamFrameListener;
import org.eclipse.jetty.spdy.api.SynInfo;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Fields;

public final class AsyncUploadCall implements Runnable {
    private final Callback callback;
    private final Session session;
    private final File ts;

    public AsyncUploadCall(Session session, File ts, Callback callback) {
        this.session = session;
        this.ts = ts;
        this.callback = callback;
    }

    @Override
    public final void run() {
        try {
            SPDYUploaderSession.log.debug("Uploading file " + ts);

            StreamFrameListener listener = new StreamFrameListener.Adapter();
            String streamId = ts.getParentFile().getName() + "/" + ts.getName();
            Fields headers = new Fields();
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
        } catch (Exception e) {
            callback.failed(e);
        }
    }
}
