import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.spdy.server.http.HTTPSPDYServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class SPDYServer {
    public static void main(String[] args) throws Exception {
        Server server = new Server();

        HTTPSPDYServerConnector connector = new HTTPSPDYServerConnector(server);
        connector.setPort(8181);
        server.addConnector(connector);

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