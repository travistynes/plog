package plog;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.sql.Timestamp;

public class Message {
    protected enum Level {
        INFO,
        WARN,
        ERROR,
        DEBUG
    }
    
    private static DateFormat df;
    
    public String ts;
    public Level level;
    public String logger;
    public String message;
    
    static {
        // Set the format of the timestamp string, and timezone to UTC.
        df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
    
    public Message() {
        
    }
    
    public Message(Level level, String logger, String message) {
        // Get the calling method / line number.
        StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        
        this.ts = df.format(new Timestamp(System.currentTimeMillis()));
        this.level = level;
        this.logger = logger;
        this.message = ste.toString() + " - " + message;
    }
}