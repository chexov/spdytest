package com.vg.live.spdy;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.spdy.api.BytesDataInfo;
import org.eclipse.jetty.spdy.api.ReplyInfo;
import org.eclipse.jetty.spdy.api.RstInfo;
import org.eclipse.jetty.spdy.api.Session;
import org.eclipse.jetty.spdy.api.Stream;
import org.eclipse.jetty.spdy.api.StreamFrameListener;
import org.eclipse.jetty.spdy.api.StreamStatus;
import org.eclipse.jetty.spdy.api.SynInfo;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Fields;

import com.vg.util.LogManager;
import com.vg.util.Logger;

public final class AsyncUploadCall implements Runnable {
    public static final Logger log = LogManager.getLogger(AsyncUploadCall.class);
    private final Callback callback;
    private AsyncDispatcher dispatcher;
    private final Session session;
    private final File ts;

    @Override
    public String toString() {
        return "AsyncUploadCall[" + ts.getName() + "]";
    }

    public AsyncUploadCall(AsyncDispatcher dispatcher, Session session, File ts, Callback callback) {
        this.dispatcher = dispatcher;
        this.session = session;
        this.ts = ts;
        this.callback = callback;
    }

    @Override
    public final void run() {
        try {
            log.debug("Uploading file " + ts);
            final AsyncUploadCall call = this;
            StreamFrameListener listener = new StreamFrameListener.Adapter() {
                @Override
                public void onReply(Stream stream, ReplyInfo replyInfo) {
                    log.debug("got reply " + replyInfo);
                    callback.succeeded();
                    dispatcher.finished(call);
                }

                @Override
                public void onFailure(Stream stream, Throwable x) {
                    log.error(ts.getName() + " onFailure " + x);
                    try {
                        session.rst(new RstInfo(stream.getId(), StreamStatus.CANCEL_STREAM));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    callback.failed(x);
                    dispatcher.finished(call);
                    dispatcher.enqueueFirst(call);
                }
            };

            String streamId = ts.getParentFile().getName() + "/" + ts.getName();
            Fields headers = new Fields();
            headers.add(":method", "POST");
            headers.add(":path", "/spdy/gopro/" + streamId);
            headers.add("ts-length", ts.length() + "");

            SynInfo synInfo = new SynInfo(0, TimeUnit.SECONDS, headers, false, (byte) 0);
            final Stream stream = session.syn(synInfo, listener);

            byte[] bytes = Files.readAllBytes(ts.toPath());
            //            BytesDataInfo dataInfo = new BytesDataInfo(bytes, true);
            BytesDataInfo dataInfo = new BytesDataInfo(10, TimeUnit.SECONDS, bytes, true);

            if (!stream.isClosed() || !stream.isReset()) {
                stream.data(dataInfo, new Callback() {
                    @Override
                    public void succeeded() {
                        log.debug(ts.getName() + " sent but no reply yet");
                        // XXX: fires when data sent but not confirmed as received. use Stream.onReply event instead
                    }

                    @Override
                    public void failed(Throwable x) {
                        log.debug(ts.getName() + " failed upload " + x);
                    }
                });
            } else {
                throw new RuntimeException(stream + " is closed");
            }

            log.debug("Open streams count = " + session.getStreams().size());
        } catch (Exception e) {
            callback.failed(e);
        }
    }
}
