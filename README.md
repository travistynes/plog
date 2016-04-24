# PLog

PLog is a Java logging library.

- No configuration required.
- Log messages are stored in a SQLite database with a 30 day retention period.
- Easy to view logs in the browser at http://localhost:50001/plog/

**Purpose**

PLog was created because I wanted a simple way to quickly begin logging - and easily watch those logs - in my applications with no setup, especially during early development and in one-off applications. I also prefer database backed logs for SQL querying rather than plain text files.

PLog runs its own HTTP server and provides a web interface to query the database with a number of filter options. If you need more, you can get the database file and work with it directly in SQL.

The log database is created with a number of indexes and a trigger to purge messages older than 30 days. These are general default settings. Depending on your logging needs, you can change any of these, as well as the HTTP server port and path settings, or log file name, and rebuild PLog easily with a single command.

**How to use**

```java
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
```

With sqlite3 you can access the database and query the log table directly:

```
$ sqlite3 plog.db
> select * from log;
```

You can create any number of loggers with any name you choose. The log can then be filtered based on the logger name to see only those messages sent to that specific logger.

**Build**

You will need to update the gradle.properties file to point to your JDK. After that, build with Gradle. Cd into the project directory and type:

```
$ gradle
```

The jar will be created for use in your project at build/libs.

**More details**

PLog is thread safe. You can log as many messages from as many threads as you want. PLog internally queues log messages and writes them to the database in a single thread using transactions.

I have tested with a very large amount of concurrent logging (over 100,000 inserts per transaction) and have not been able to break or even slow it down.

**Why PLog.Shutdown()**

PLog is designed to handle a large number of log messages quickly. To do that it queues and writes messages to the database in a separate thread.
In a longer running application your log messages will safely be written to the database.

You don't *have* to call PLog.Shutdown(), but if your application logs a message and immediately terminates, you will probably lose that message. Calling shutdown just waits on the queue (typically a few milliseconds) to finish processing any remaining messages.

**Free to use**

This code is free to use for any purpose.
