package com.vg.live;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.spdy.api.DataInfo;
import org.eclipse.jetty.spdy.api.ReplyInfo;
import org.eclipse.jetty.spdy.api.SPDY;
import org.eclipse.jetty.spdy.api.Session;
import org.eclipse.jetty.spdy.api.Settings;
import org.eclipse.jetty.spdy.api.SettingsInfo;
import org.eclipse.jetty.spdy.api.Stream;
import org.eclipse.jetty.spdy.api.StreamFrameListener;
import org.eclipse.jetty.spdy.api.SynInfo;
import org.eclipse.jetty.spdy.api.server.ServerSessionFrameListener;
import org.eclipse.jetty.spdy.server.SPDYServerConnectionFactory;
import org.eclipse.jetty.spdy.server.SPDYServerConnector;
import org.eclipse.jetty.util.Fields;

public class SPDYServer {
    static {
        DefaultConfiguration cfg = new DefaultConfiguration();
        PatternLayout layout = PatternLayout.newBuilder().withPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} %msg%n").build();
        ConsoleAppender appender = ConsoleAppender.createAppender(layout, null, "SYSTEM_OUT", "Console", "false", "true");

        cfg.getRootLogger().setLevel(Level.ALL);
        cfg.getRootLogger().addAppender(appender, Level.ALL, null);
    }

    public static void main(String[] args) throws Exception {

        ServerSessionFrameListener listener = new ServerSessionFrameListener.Adapter() {
            @Override
            public void onConnect(Session session) {
                System.out.println("connect " + session);

                //                Settings settings = new Settings();
                //                settings.put(new Settings.Setting(Settings.ID.MAX_CONCURRENT_STREAMS, 100));
                //
                //                try {
                //                    session.settings(new SettingsInfo(settings));
                //                } catch (ExecutionException | InterruptedException | TimeoutException e) {
                //                    e.printStackTrace();
                //                }
                //
            }

            @Override
            public StreamFrameListener onSyn(Stream stream, SynInfo synInfo) {
                System.out.println("syn=" + synInfo);
                System.out.println(synInfo.getHeaders());
                System.out.println("path=" + synInfo.getHeaders().get(":path").getValue());

                Fields headers = new Fields();
                headers.add(":status", "ok");

                try {
                    stream.reply(new ReplyInfo(headers, false));
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }

                return new StreamFrameListener.Adapter() {
                    @Override
                    public void onData(Stream stream, DataInfo dataInfo) {
                        System.out.println(Thread.currentThread().getName() + " Received the following client data: "
                                + dataInfo.length());
                    }
                };
            }

        };

        Server server = new Server();

        //        SPDYServerConnectionFactory spdy3 = new SPDYServerConnectionFactory(SPDY.V3, listener);
        //        ServerConnector connector = new ServerConnector(server, new ConnectionFactory[] { spdy3 });
        //        connector.setPort(8181);
        //        server.addConnector(connector);

        SPDYServerConnector spdyServerConnector = new SPDYServerConnector(server, listener);
        spdyServerConnector.setPort(8181);
        server.addConnector(spdyServerConnector);

        //                HTTPSPDYServerConnector httpspdyServerConnector = new HTTPSPDYServerConnector(server);
        //                httpspdyServerConnector.setPort(8181);
        //                server.addConnector(httpspdyServerConnector);

        //        server.setHandler(new AbstractHandler() {
        //            public void handle(String target, Request baseRequest, HttpServletRequest request,
        //                    HttpServletResponse response) throws IOException, ServletException {
        //                response.setContentType("text/html;charset=utf-8");
        //                response.setStatus(HttpServletResponse.SC_OK);
        //                baseRequest.setHandled(true);
        //                response.getWriter().println("<h1>Hello World</h1>");
        //                System.out.println(baseRequest);
        //            }
        //        });

        server.start();
        server.join();

    }
}
