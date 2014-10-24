import static org.apache.commons.io.filefilter.FileFilterUtils.suffixFileFilter;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okio.BufferedSink;
import okio.Okio;
import okio.Sink;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.Platform;
import com.squareup.okhttp.internal.http.SpdyTransport;
import com.squareup.okhttp.internal.spdy.SpdyConnection;
import com.squareup.okhttp.internal.spdy.SpdyStream;

public class Okhttpcli {
    public static void main(String[] args) throws Exception {
        //                post();
        spdy();
    }

    private static void spdy() throws IOException {
        OkHttpClient client = new OkHttpClient();
        client.setProtocols(Arrays.asList(Protocol.SPDY_3, Protocol.HTTP_1_1));

        Socket socket = new Socket(Proxy.NO_PROXY);
        socket.setSoTimeout(0);
        InetSocketAddress inetSocketAddress = new InetSocketAddress("localhost", 8181);
        int connectTimeout = 2000;
        Platform.get().connectSocket(socket, inetSocketAddress, connectTimeout);

        SpdyConnection spdyConnection = new SpdyConnection.Builder("localhost", true, socket).protocol(Protocol.SPDY_3).build();
        spdyConnection.sendConnectionPreface();

        File tsDir = new File("/Users/chexov/work/vig/idea/goprolive/goprolive/testdata/gopro/25fps/");
        ArrayList<File> ls = new ArrayList<>(ls(tsDir, suffixFileFilter(".ts")));

        for (File ts : ls) {
            System.out.println(ts);
            RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream"), ts);
            String sid = ts.getParentFile().getName() + "/" + ts.getName();
            Request request = new Request.Builder().url("http://localhost:8183/spdy/" + sid).post(body).build();

            SpdyStream stream = spdyConnection.newStream(SpdyTransport.writeNameValueBlock(request, spdyConnection.getProtocol(), "HTTP/1.1"), true, true);
            //        SpdyStream stream = connection.newStream(headerEntries("b", "banana"), true, true);

            Sink sink = stream.getSink();
            BufferedSink out = Okio.buffer(sink);
            out.write(Files.readAllBytes(ts.toPath()));
            //            out.close();
            System.out.println(stream.isOpen());
        }

        spdyConnection.close();
    }

    private static void post() throws InterruptedException {
        OkHttpClient client = new OkHttpClient();
        client.setProtocols(Arrays.asList(Protocol.SPDY_3, Protocol.HTTP_1_1));

        File ts = new File("/Users/chexov/work/vig/idea/goprolive/goprolive/testdata/gopro/25fps/1001.ts");

        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream"), ts);
        Request request = new Request.Builder().url("http://localhost:8181/spdy/gopro/25fps").post(body).build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Request request, IOException e) {
                System.out.println("fail " + request);
                e.printStackTrace();
            }

            @Override
            public void onResponse(Response response) throws IOException {
                System.out.println("resp " + response);
            }
        });

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
