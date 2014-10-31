package com.vg.live;

import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import com.vg.live.spdy.SPDYUploaderQueue;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.eclipse.jetty.util.Callback;
import org.junit.Test;

public class SPDYUploaderQueueTest {
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
}