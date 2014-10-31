package com.vg.live;

import java.io.File;
import java.io.FileFilter;
import java.net.URI;
import java.net.URL;

import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.eclipse.jetty.util.Callback;
import org.junit.Test;

import com.vg.live.spdy.SPDYUploaderQueue;

public class SPDYUploaderQueueTest {
    public static void initl4j2Logger() {
        ConfigurationFactory factory = new ConfigurationFactory() {

            @Override
            protected String[] getSupportedTypes() {
                return null;
            }

            @Override
            public Configuration getConfiguration(ConfigurationSource source) {
                DefaultConfiguration cfg = new DefaultConfiguration();
                PatternLayout layout = PatternLayout.newBuilder().withPattern("%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n").build();
                String name = "";
                ConsoleAppender appender = ConsoleAppender.createAppender(layout, null, "SYSTEM_OUT", name, "false", "true");
                cfg.addAppender(appender);
                return cfg;
            }

            @Override
            public Configuration getConfiguration(String _name, URI configLocation) {
                DefaultConfiguration cfg = new DefaultConfiguration();
                PatternLayout layout = PatternLayout.newBuilder().withPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} %msg%n").build();
                ConsoleAppender appender = ConsoleAppender.createAppender(layout, null, "SYSTEM_OUT", "Console", "false", "true");

                cfg.getRootLogger().setLevel(Level.ALL);
                cfg.getRootLogger().addAppender(appender, Level.ALL, null);
                return cfg;
            }
        };
        ConfigurationFactory.setConfigurationFactory(factory);
    }

    @Test
    public void testUpload() throws Exception {
        //        URL url1 = new URL("http://localhost:8181");
        URL url1 = new URL("http://cloud.videogorillas.com:8181");

        SPDYUploaderQueue spdyUploaderQueue = new SPDYUploaderQueue(url1);

        File tsDir = new File("/Users/chexov/work/vig/idea/goprolive/goprolive/testdata/gopro/25fps/");
        for (File ts : tsDir.listFiles((FileFilter) FileFileFilter.FILE)) {
            Callback.Adapter callback = new Callback.Adapter() {
                @Override
                public void succeeded() {
                    System.out.println(ts + " upload ok");
                }

                @Override
                public void failed(Throwable x) {
                    System.out.println(ts.getName() + " upload fail " + x);
                    //                    x.printStackTrace();
                }
            };

            spdyUploaderQueue.newCall(ts, callback);
        }
        spdyUploaderQueue.blockTillAllDone();
    }

    @Test
    public void testUpload2() throws Exception {
        initl4j2Logger();
        //        URL url1 = new URL("http://localhost:8181");
        URL url1 = new URL("http://cloud.videogorillas.com:8181");

        SPDYUploaderQueue spdyUploaderQueue = new SPDYUploaderQueue(url1);

        File tsDir = new File("/Users/chexov/live/774128639/GOPRO20140821063020.MP4");
        for (File ts : tsDir.listFiles((FileFilter) FileFileFilter.FILE)) {
            Callback.Adapter callback = new Callback.Adapter() {
                @Override
                public void succeeded() {
                    System.out.println(ts + " upload ok");
                }

                @Override
                public void failed(Throwable x) {
                    System.out.println(ts.getName() + " upload fail " + x);
                }
            };

            spdyUploaderQueue.newCall(ts, callback);
        }
        spdyUploaderQueue.blockTillAllDone();
    }
}