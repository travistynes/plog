package plog;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import org.apache.commons.io.IOUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.sql.Timestamp;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import com.google.gson.Gson;

/*
HTTP server used to provide a web interface for logs.
*/
public class PServer {
    private int port = 50001;
    private HttpServer server = null;
    
    public PServer() {
        this.start();
    }
    
    /*
    Start the server.
    */
    private void start() {
        this.stop();
        
        System.out.println("Starting log server.");
        
        final String contextRoot = "/" + PLog.LOG_NAME + "/";
        int maxConnections = 0; // Maximum incoming connections to queue on the socket. Value of 0 will use a system default.
        
        try {
            InetSocketAddress addr = new InetSocketAddress(port); // Create socket address where the IP address is the wildcard address (listen on any interface).
            HttpServer server = HttpServer.create(addr, maxConnections);
            
            server.createContext(contextRoot, new HttpHandler() {
                public void handle(HttpExchange exchange) {
                    try {
                        // Get request.
                        String requestBody = IOUtils.toString(exchange.getRequestBody(), "UTF-8");
                        String method = exchange.getRequestMethod();
                        URI uri = exchange.getRequestURI();
                        String res = uri.toString();
                        
                        // Get the resource requested relative (to the right) of the context root.
                        String[] parts = res.split(contextRoot);
                        String rel = parts.length > 0 ? parts[1] : "";
                        
                        // Split the query string out of the resource name.
                        Map<String, String> query = new HashMap<String, String>();
                        parts = rel.split("\\?");
                        if(parts.length == 2) {
                            rel = parts[0];
                            
                            // Build query parameter map.
                            String[] pairs = uri.getQuery().split("&");
                            for(String pair : pairs) {
                                parts = pair.split("=");
                                query.put(parts[0], parts[1]);
                            }
                        }
                        
                        Headers headers = exchange.getResponseHeaders();
                        OutputStream out = exchange.getResponseBody();
                        int responseCode = 200; // HTTP 200 OK
                        int responseLength = 0; // -1 indicates no response body is being sent. 0 indicates an arbitrary amount of data may be sent.
                        
                        if(res.equals(contextRoot)) {
                            // Root request. Send index.html.
                            headers.set("content-type", "text/html; charset=utf-8");
                            exchange.sendResponseHeaders(responseCode, responseLength);
                            
                            InputStream is = PLog.class.getResourceAsStream("/web/index.html");
                            byte[] bytes = IOUtils.toByteArray(is);
                            IOUtils.write(bytes, out);
                        } else if(rel.equals("logs")) {
                            // Request for log messages.
                            headers.set("content-type", "text/JSON; charset=utf-8");
                            exchange.sendResponseHeaders(responseCode, responseLength);
                            
                            // Get logs.
                            List<Message> messages = getLogs(query);
                            String json = new Gson().toJson(messages);
                            IOUtils.write(json, out, "UTF-8");
                        } else {
                            // Some other resource requested. Look for it in the web directory and send it, if it exists.
                            InputStream is = PLog.class.getResourceAsStream("/web/" + rel);
                            if(is != null) {
                                // Found resource. Send it.
                                exchange.sendResponseHeaders(responseCode, responseLength);
                                byte[] bytes = IOUtils.toByteArray(is);
                                IOUtils.write(bytes, out);
                            } else {
                                // Resource not found.
                                responseCode = 404;
                                exchange.sendResponseHeaders(responseCode, responseLength);
                                System.out.println("Not found: /web/" + rel);
                            }
                        }
                    } catch(IOException e) {
                        // Exception can occur when trying to send response.
                        throw new RuntimeException(e);
                    } finally {
                        IOUtils.closeQuietly(exchange.getRequestBody());
                        IOUtils.closeQuietly(exchange.getResponseBody());
                    }
                }
            });
            
            this.server = server;
            
            // Start listening for connections.
            server.start();
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /*
    Stop the server.
    */
    public void stop() {
        if(this.server != null) {
            System.out.println("Shutting down log server.");
            
            this.server.stop(1); // Blocks for specified number of seconds or until all current handlers have completed, whichever is first.
            this.server = null;
        }
    }
    
    private List<Message> getLogs(Map<String, String> query) {
        // Specify the format and timezone (utc) of the timestamp string as it is stored in the database.
        DateFormat dfIn = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
        dfIn.setTimeZone(TimeZone.getTimeZone("UTC"));
        
        // Specify the format and timezone that we want to display the timestamp as.
        DateFormat dfOut = new SimpleDateFormat("yyyy-MM-dd h:mm:ss.S a");
        dfOut.setTimeZone(TimeZone.getDefault());
        
        Connection c = null;
        PreparedStatement s = null;
        ResultSet rs = null;
        
        try {
            // Get query parameters.
            String log_level = query.get("level");
            String from = query.get("from");
            String to = query.get("to");
            String logger_name = query.get("logger");
            
            if(log_level.equalsIgnoreCase("all")) { log_level = "%"; }
            if(logger_name.equalsIgnoreCase("all")) { logger_name = "%"; }
            
            c = PLog.GetLogConnection();
            
            // Query statement. Sort rows by ts desc. If ts is not unique, further sort by special column rowid desc (see: https://www.sqlite.org/autoinc.html)
            String sql = "select ts, level, logger, message from log where datetime(ts, 'localtime') >= ? and datetime(ts, 'localtime') <= ? and level like ? and logger like ? order by ts desc, rowid desc";
            s = c.prepareStatement(sql);
            
            s.setString(1, from);
            s.setString(2, to);
            s.setString(3, log_level + "%");
            s.setString(4, logger_name + "%");
            
            rs = s.executeQuery();
            
            List<Message> messages = new ArrayList<Message>();
            
            while(rs.next()) {
                Timestamp ts = new Timestamp(dfIn.parse(rs.getString("ts")).getTime());
                String level = rs.getString("level");
                String logger = rs.getString("logger");
                String message = rs.getString("message");
                
                Message m = new Message();
                m.ts = dfOut.format(ts);
                m.level = Message.Level.valueOf(level);
                m.logger = logger;
                m.message = message;
                
                messages.add(m);
            }
            
            return messages;
        } catch(Exception e) {
            System.out.println(e);
            throw new RuntimeException(e);
        } finally {
            // Cleanup.
            try { if(rs != null) rs.close(); } catch (SQLException e) {}
            try { if(s != null) s.close(); } catch(SQLException e) {}
            try { if(c != null) c.close(); } catch(SQLException e) {}
        }
    }
}
