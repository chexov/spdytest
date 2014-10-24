import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.spdy.api.DataInfo;
import org.eclipse.jetty.spdy.api.ReplyInfo;
import org.eclipse.jetty.spdy.api.Session;
import org.eclipse.jetty.spdy.api.Stream;
import org.eclipse.jetty.spdy.api.StreamFrameListener;
import org.eclipse.jetty.spdy.api.SynInfo;
import org.eclipse.jetty.spdy.api.server.ServerSessionFrameListener;
import org.eclipse.jetty.spdy.server.SPDYServerConnector;

public class SPDYServerLauncher {

    public static void main(String[] args) throws Exception {

        ServerSessionFrameListener frameListener = new ServerSessionFrameListener.Adapter() {
            @Override
            public void onConnect(Session session) {
                super.onConnect(session);
                System.out.println("connect " + session);
            }

            @Override
            public StreamFrameListener onSyn(final Stream stream, SynInfo synInfo) {
                System.out.println("syn=" + synInfo);
                System.out.println("path=" + synInfo.getHeaders().get(":path").getValue());

                // Send a reply to this message
                try {
                    stream.reply(new ReplyInfo(false));
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }

                return new StreamFrameListener.Adapter() {
                    public void onData(Stream stream, DataInfo dataInfo) {
                        System.out.println("Received the following client data: " + dataInfo.length());
                    }
                };
            }
        };

        Server server = new Server();

        SPDYServerConnector spdyServerConnector = new SPDYServerConnector(server, frameListener);
        spdyServerConnector.setPort(8181);
        server.addConnector(spdyServerConnector);

        server.setHandler(new AbstractHandler() {
            public void handle(String target, Request baseRequest, HttpServletRequest request,
                    HttpServletResponse response) throws IOException, ServletException {
                response.setContentType("text/html;charset=utf-8");
                response.setStatus(HttpServletResponse.SC_OK);
                baseRequest.setHandled(true);
                response.getWriter().println("<h1>Hello World</h1>");
                System.out.println(baseRequest);
            }
        });

        server.start();
        server.join();

    }
}
