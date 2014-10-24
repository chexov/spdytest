package com.vg.live;

import static org.apache.commons.io.filefilter.FileFilterUtils.suffixFileFilter;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.io.ClientConnectionFactory;
import org.eclipse.jetty.io.NegotiatingClientConnectionFactory;
import org.eclipse.jetty.spdy.api.BytesDataInfo;
import org.eclipse.jetty.spdy.api.DataInfo;
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
import org.eclipse.jetty.spdy.api.SynInfo;
import org.eclipse.jetty.spdy.client.SPDYClient;
import org.eclipse.jetty.spdy.client.SPDYClientConnectionFactory;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Fields;

public class SPDYUploader {

    public static void main(String[] args) throws Exception {

        // this listener receives data from the server. It then prints out the data
        StreamFrameListener streamListener = new StreamFrameListener.Adapter() {

            @Override
            public void onData(Stream stream, DataInfo dataInfo) {
                // Data received from server
                String content = dataInfo.asString("UTF-8", true);
                System.out.println("SPDY content: " + content);
            }
        };

        SessionFrameListener sessionListener = new SessionFrameListener.Adapter() {
            @Override
            public StreamFrameListener onSyn(Stream stream, SynInfo synInfo) {
                System.out.println("syn=" + synInfo);
                try {
                    stream.reply(new ReplyInfo(false));
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }

                return new StreamFrameListener.Adapter() {
                    @Override
                    public void onReply(Stream stream, ReplyInfo replyInfo) {
                        super.onReply(stream, replyInfo);
                        System.out.println("reply " + replyInfo);

                    }

                    @Override
                    public void onData(Stream stream, DataInfo dataInfo) {
                        String clientData = dataInfo.asString("UTF-8", true);
                        System.out.println("Received the following client data: " + clientData);
                    }
                };
            }

            @Override
            public void onRst(Session session, RstInfo rstInfo) {
                System.out.println("rst");
            }

            @Override
            public void onSettings(Session session, SettingsInfo settingsInfo) {
                Settings settings = settingsInfo.getSettings();
                System.out.println("settings: " + settings + " " + settingsInfo);
                Fields fields = new Fields();
                fields.put("name", "val");
                //                try {
                //                    session.getStream(4).reply(new ReplyInfo(fields, false));
                //                } catch (Exception e) {
                //                    e.printStackTrace();
                //                    throw new RuntimeException(e);
                //                }
            }

            @Override
            public void onPing(Session session, PingResultInfo pingResultInfo) {
                System.out.println("ping");
            }

            @Override
            public void onGoAway(Session session, GoAwayResultInfo goAwayResultInfo) {
                System.out.println("away");
            }

            @Override
            public void onFailure(Session session, Throwable x) {
                System.out.println("fail");

            }
        };

        // Create client
        SPDYClient.Factory clientFactory = new SPDYClient.Factory();
        clientFactory.start();
        org.eclipse.jetty.spdy.client.SPDYClient client = clientFactory.newSPDYClient(SPDY.V3);

        Session session = client.connect(new InetSocketAddress("localhost", 8181), sessionListener);
        File tsDir = new File("/Users/chexov/work/vig/idea/goprolive/goprolive/testdata/gopro/25fps/");
        ArrayList<File> ls = new ArrayList<File>(ls(tsDir, suffixFileFilter(".ts")));

        long time = System.currentTimeMillis();
        for (File ts : ls) {
            System.out.println(time + " == " + ts);

            Fields headers = new Fields();
            String streamId = ts.getParentFile().getName() + "/" + ts.getName();
            headers.add(":path", "/spdy/gopro/" + streamId);
            final Stream stream = session.syn(new SynInfo(headers, false), streamListener);
            byte[] bytes1 = IOUtils.toByteArray(new InputStreamReader(new FileInputStream(ts)));

            BytesDataInfo dataInfo = new BytesDataInfo(10, java.util.concurrent.TimeUnit.SECONDS, bytes1, false);
            Callback.Adapter callback = new Callback.Adapter() {
                @Override
                public void succeeded() {
                    System.out.println(Thread.currentThread().getName() + " data ok");
                }

                @Override
                public void failed(Throwable x) {
                    System.out.println("data fail ");
                    x.printStackTrace();
                }
            };

            stream.data(dataInfo, callback);
        }

    }

    public static List<File> ls(File dir, FileFilter filter) {
        File[] listFiles = dir.listFiles(filter);
        if (listFiles != null) {
            return Arrays.asList(listFiles);
        } else {
            return new ArrayList<File>();
        }
    }
}
