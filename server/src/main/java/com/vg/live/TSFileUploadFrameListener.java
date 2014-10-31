package com.vg.live;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.eclipse.jetty.spdy.api.DataInfo;
import org.eclipse.jetty.spdy.api.GoAwayInfo;
import org.eclipse.jetty.spdy.api.HeadersInfo;
import org.eclipse.jetty.spdy.api.PushInfo;
import org.eclipse.jetty.spdy.api.ReplyInfo;
import org.eclipse.jetty.spdy.api.RstInfo;
import org.eclipse.jetty.spdy.api.Stream;
import org.eclipse.jetty.spdy.api.StreamFrameListener;

import com.vg.util.LogManager;
import com.vg.util.Logger;
import org.eclipse.jetty.spdy.api.StreamStatus;
import org.eclipse.jetty.util.Fields;

class TSFileUploadFrameListener implements StreamFrameListener {
    public static final Logger log = LogManager.getLogger(TSFileUploadFrameListener.class);
    private final FileChannel channel;
    private final File tsFile;
    private int expectedSize;

    public TSFileUploadFrameListener(File tsFile, int expectedSize) {
        this.tsFile = tsFile;
        this.expectedSize = expectedSize;
        try {
            channel = new FileOutputStream(tsFile).getChannel();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onReply(Stream stream, ReplyInfo replyInfo) {
        log.debug("onReply " + replyInfo);
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onHeaders(Stream stream, HeadersInfo headersInfo) {
        log.debug("onHeaders " + headersInfo);
    }

    @Override
    public StreamFrameListener onPush(Stream stream, PushInfo pushInfo) {
        return null;
    }

    @Override
    public void onData(Stream stream, DataInfo dataInfo) {
        try {
            String tsName = tsFile.getName();
            log.debug(tsName + ": got bytes " + dataInfo.length());
            ByteBuffer byteBuffer = dataInfo.asByteBuffer(false);
            while (byteBuffer.hasRemaining()) {
                long b = channel.size();
                channel.write(byteBuffer);
                int consumed = (int) (channel.size() - b);
                dataInfo.consume(consumed);

                log.debug(tsName + ": actual = " + channel.size() + " expected = " + expectedSize + " consumed = "
                        + consumed);

            }

            if (channel.isOpen() && channel.size() == expectedSize) {
                log.debug(tsName + ": got whole file. sending reply for " + stream.getId());
                channel.close();
                stream.reply(uploadOkInfo(tsName));
            } else if (!channel.isOpen()) {
                log.error(tsName + ": channel is closed but got data " + dataInfo.length());
                stream.getSession().rst(new RstInfo(stream.getId(), StreamStatus.INTERNAL_ERROR));
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public ReplyInfo uploadOkInfo(String tsName) {
        Fields hdrs = new Fields();
        hdrs.add("file", tsName);
        hdrs.add("upload", "ok");
        return new ReplyInfo(hdrs, true);
    }

    @Override
    public void onFailure(Stream stream, Throwable x) {
        x.printStackTrace();
    }
}
