package com.vg.live;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.eclipse.jetty.spdy.api.DataInfo;
import org.eclipse.jetty.spdy.api.HeadersInfo;
import org.eclipse.jetty.spdy.api.PushInfo;
import org.eclipse.jetty.spdy.api.ReplyInfo;
import org.eclipse.jetty.spdy.api.Stream;
import org.eclipse.jetty.spdy.api.StreamFrameListener;

import com.vg.util.LogManager;
import com.vg.util.Logger;

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

        //        String clientData = dataInfo.asString("UTF-8", true);
        //        System.out.println("Received the following client data: " + clientData);

        String shortName = tsFile.getName();
        log.debug(shortName + ": got bytes " + dataInfo.length());
        ByteBuffer byteBuffer = dataInfo.asByteBuffer(false);
        while (byteBuffer.hasRemaining()) {
            try {
                long b = channel.size();
                channel.write(byteBuffer);
                int consumed = (int) (channel.size() - b);
                dataInfo.consume(consumed);

                log.debug(shortName + ": actual = " + channel.size() + " expected = " + expectedSize + " consumed = "
                        + consumed);
                if (channel.size() == expectedSize) {
                    log.debug("got whole file");
                    channel.close();

                    try {
                        stream.reply(new ReplyInfo(true));
                    } catch (Exception e) {
                        e.printStackTrace();
                        log.debug("stream not replied for " + tsFile);
                        throw new RuntimeException(e);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onFailure(Stream stream, Throwable x) {
        x.printStackTrace();
    }
}
