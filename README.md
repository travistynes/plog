# PLog

PLog is a Java logging library.

- No configuration is required.
- Log messages are stored in a SQLite database with a 30 day retention period.
- Easy to view logs in the browser at http://localhost:50001/plog/


# How to use

'''java
import plog.PLog;

public static void main(String[] args) {
    PLog log1 = new PLog("main");
    PLog log2 = new PLog("trace");
    
    log1.info("Info level message.");
    log2.error("This message will go to the trace logger at the error log level.");
    log1.warn("Warning!");
    log1.debug("Debug message on the main logger.");
    
    PLog.Shutdown();
}
'''


# Build

Build with Gradle. Cd into the project directory and type:

'''
$ gradle
'''

The jar will be created for use in your project at build/libs.

This code is free to use for any purpose.
