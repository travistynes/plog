package plog;

import org.apache.commons.io.IOUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.sql.Timestamp;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/*
PLog is used for simple logging and provides a web interface at:
http://localhost:50001/plog/

The logs are stored in a SQLite database.
*/
public class PLog {
    private static final Properties properties = new Properties();
    private static String LOG_DIR = "./plog"; // Log directory for database file.
    protected static String LOG_NAME = "plog"; // Log name. Will be used as the database name and context root for requests.
    
    private static BlockingQueue Q = new LinkedBlockingQueue(); // Holds log messages prior to database insertion.
    private static boolean Q_PUMP = true; // Process log queue while true.
    private static Thread QPump = null; // The log queue processing thread.
    private static PServer Server = null; // HttpServer that serves log messages on request.
    
    private String name; // The name of this logger.
    
    /*
    Static initializer.
    */
    static {
        try {
            // Load application properties, if exists.
            InputStream is = new FileInputStream("./plog.properties");
            PLog.properties.load(is);
            is.close();
            
            // Set log directory from property file.
            String dir = PLog.properties.getProperty("log.path");
            if(dir != null) {
                PLog.LOG_DIR = dir;
                System.out.println("Using PLog.LOG_DIR=" + dir);
            }
        } catch(Exception e) {
            System.out.println("Couldn't load plog.properties.");
        }
        
        try {
            // Create log directory (recursively) in case it doesn't exist.
            new File(PLog.LOG_DIR).mkdirs();
            
            // Setup the database if it hasn't been created.
            SetupDatabase();
            
            // Start the log queue processor.
            StartQPump();
            
            // Start log server.
            Server = new PServer();
            
            // Load sqlite driver.
            Class.forName("org.sqlite.JDBC").newInstance();
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void main(String[] args) throws Exception {
        final java.util.Random ran = new java.util.Random();
        
        final PLog log = new PLog("main");
        final PLog trace = new PLog("trace");
        
        for(int a = 0; a < 1000000; a++) {
            double i = Math.random();
            if(i < .05) {
                log.debug("Message " + a + ". This is a debug log message.");
            } else if(i < .2) {
                try {
                    Integer.parseInt("a", 10); // Generate an error.
                } catch(Exception e) {
                    log.error("Message " + a + ". Error!");
                    trace.error("Oops (message " + a + ")", e);
                }
            } else if(i < .3) {
                log.warn("Warning, this is message " + a);
            } else {
                log.info("Message " + a);
            }
            
            Thread.sleep(1000);
        }
        
        PLog.Shutdown();
    }
    
    /*
    Create a logger with the given name.
    */
    public PLog(String name) {
        this.name = name;
    }
    
    public void info(String message) {
        Message m = new Message(Message.Level.INFO, name, message);
        log(m);
    }
    
    public void warn(String message) {
        Message m = new Message(Message.Level.WARN, name, message);
        log(m);
    }
    
    public void warn(String message, Throwable t) {
        String spaces = "";
        StackTraceElement[] stack = t.getStackTrace();
        for(int a = 0; a < stack.length; a++) {
            StackTraceElement ste = stack[a];
            spaces += "  ";
            message += "\n" + spaces + ste.toString();
        }
        
        Message m = new Message(Message.Level.WARN, name, message);
        log(m);
    }
    
        public void debug(String message) {
        Message m = new Message(Message.Level.DEBUG, name, message);
        log(m);
    }
    
    public void debug(String message, Throwable t) {
        String spaces = "";
        StackTraceElement[] stack = t.getStackTrace();
        for(int a = 0; a < stack.length; a++) {
            StackTraceElement ste = stack[a];
            spaces += "  ";
            message += "\n" + spaces + ste.toString();
        }
        
        Message m = new Message(Message.Level.DEBUG, name, message);
        log(m);
    }
    
    public void error(String message) {
        Message m = new Message(Message.Level.ERROR, name, message);
        log(m);
    }
    
    public void error(String message, Throwable t) {
        String spaces = "";
        StackTraceElement[] stack = t.getStackTrace();
        for(int a = 0; a < stack.length; a++) {
            StackTraceElement ste = stack[a];
            spaces += "  ";
            message += "\n" + spaces + ste.toString();
        }
        
        Message m = new Message(Message.Level.ERROR, name, message);
        log(m);
    }
    
    private void log(Message m) {
        try {
            // Put message on queue. Blocking operation if queue is full (which is unlikely).
            Q.put(m);
        } catch(InterruptedException e) {
            
        }
    }
    
    /*
    Call shutdown to properly stop the queue pump and HTTP log server.
    */
    public static void Shutdown() {
        // Stop log queue pump processing.
        try {
            Q_PUMP = false;
            QPump.join(); // Wait for QPump to finish.
        } catch(InterruptedException e) {}
        
        // Stop HTTP log server if it is running.
        Server.stop();
    }
    
    /*
    Start processing messages on queue. Inserts messages to database in a transaction.
    */
    private static void StartQPump() {
        // Create and start the log queue pump thread.
        QPump = new Thread() {
            public void run() {
                boolean safetyPass = true;
                long totalMessages = 0; // Count total messages processed.
                
                try {
                    while(Q_PUMP || safetyPass) {
                        /*
                        Sleep for a bit. Under high concurrency, this will let us insert a larger number of
                        messages into the database in a single transaction for more efficiency.
                        */
                        Thread.sleep(500);
                        
                        // Take all messages from queue.
                        List<Message> msgs = new ArrayList<Message>();
                        int count = Q.drainTo(msgs);
                        totalMessages += count;
                        
                        if(count > 0) {
                            // Insert messages into database.
                            Connection c = null;
                            PreparedStatement s = null;
                            
                            try {
                                c = GetLogConnection();
                                
                                // Start transaction.
                                c.setAutoCommit(false);
                                
                                s = c.prepareStatement("insert into log(ts, level, logger, message) values(?, ?, ?, ?)");
                                
                                for(Message m : msgs) {
                                    s.setString(1, m.ts);
                                    s.setString(2, m.level.toString());
                                    s.setString(3, m.logger);
                                    s.setString(4, m.message);
                                    
                                    s.executeUpdate();
                                }
                                
                                // Commit transaction.
                                c.commit();
                                c.setAutoCommit(true);
                            } catch(SQLException e) {
                                // Rollback transaction.
                                try {
                                    if(c != null) {
                                        c.rollback();
                                        c.setAutoCommit(true);
                                    }
                                    
                                    System.out.println(e.getMessage());
                                    System.out.println("Error. Transaction rolled back.");
                                } catch(SQLException ex) {}
                                
                                /*
                                This batch of messages failed to go in the database. Add them back to the queue so they aren't lost.
                                This can be tested by opening the database using sqlite3 and running:
                                sqlite> pragma locking_mode = EXCLUSIVE;
                                Then doing a select. The database will be locked. Inserts here will fail and the transaction will rollback.
                                */
                                for(Message m : msgs) {
                                    Q.put(m);
                                }
                                totalMessages -= count;
                                System.out.println(count + " messages added back to queue.");
                            } finally {
                                // Cleanup.
                                try { if(s != null) s.close(); } catch(SQLException e) {}
                                try { if(c != null) c.close(); } catch(SQLException e) {}
                            }
                            
                            if(!Q_PUMP) {
                                // Log queue pump has been requested to stop, but more messages may be on the queue.
                                System.out.println("Log queue pump - Safety pass prior to shutdown.");
                            }
                        } else {
                            // No messages to process.
                            if(!Q_PUMP) {
                                // Log queue pump requested to stop. We'll assume no more incoming messages.
                                safetyPass = false;
                            }
                        }
                    }
                    
                    System.out.println("Log queue pump shutdown. Processed " + totalMessages + " messages in total.");
                } catch(InterruptedException e) {
                    // Thread interuppted.
                }
            }
        };
        
        QPump.setDaemon(true);
        QPump.start();
    }
    
    /*
    Create and setup the database if it doesn't exist yet.
    */
    private static void SetupDatabase() {
        Connection c = null;
        PreparedStatement s = null;
        
        try {
            // If the database doesn't exist, it will be created on the first connection.
            c = GetLogConnection();
            
            // Create table.
            s = c.prepareStatement("create table if not exists log( ts text, level text, logger text, message text )");
            s.executeUpdate();
            
            // Create index.
            s = c.prepareStatement("create index if not exists idx_ts_level_logger on log ( ts, level, logger )");
            s.executeUpdate();
            s = c.prepareStatement("create index if not exists idx_logger on log ( logger )");
            s.executeUpdate();
            
            // Create purge trigger on inserts.
            s = c.prepareStatement("create trigger if not exists log_insert after insert on log begin delete from log where ts < datetime('now', '-30 days'); end");
            s.executeUpdate();
            
            /*
            Set WAL mode (https://www.sqlite.org/wal.html). With the normal "delete" journal mode,
            I get "Error: database is locked" randomly when selecting while inserts are in progress
            from old laptop. No locking issues in WAL mode so far.
            */
            s = c.prepareStatement("PRAGMA journal_mode=WAL");
            s.executeQuery();
        } catch(Exception e) {
            System.out.println(e);
            throw new RuntimeException(e);
        } finally {
            // Cleanup.
            try { if(s != null) s.close(); } catch(SQLException e) {}
            try { if(c != null) c.close(); } catch(SQLException e) {}
        }
    }
    
    /*
    Returns connection to log database.
    */
    public static Connection GetLogConnection() {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + LOG_DIR + "/" + LOG_NAME + ".db"); // Database file will be created if it doesn't exist.
            return c;
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
}
