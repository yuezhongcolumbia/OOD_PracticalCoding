/**
 * Prompt;
 * "Design a logging service. Or call it a logger, whichever you prefer."
 **/

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Requirements:
 * 1. each log has different levels, severity in ascending order:
 * DEBUG, INFO, WARN, ERROR, FATAL;
 * 2. each logs are send to different destinations, and each destination has different filter level;
 * 3.  logger can written in different file format, JSON, txt, file....;
 * 4. support concurrent logging
 * 5. configs are loaded at startup
 * <p>
 * flow: each destination has independent filter level and logging format. we write the logs, we send the logs, concurrently;
 * <p>
 * Entity:
 * 1. Logger;
 * 2. LogRecord:
 * - message;
 * - format;
 * - thread;
 * - logLevel;
 * 3. destination:
 * - formatter;
 * - sink;
 * = minLevel
 * 4. Enum LogLevel:
 **/

public class LoggingSystemOOD {
    public static void main(String[] args) throws Exception {
        //testing Fan-out
        MemorySink console = new MemorySink();
        MemorySink file = new MemorySink();

        Destination d1 = new Destination(console, new PlainTextFormatter(), LogLevel.DEBUG, 100);

        Destination d2 = new Destination(file, new JsonFormatter(), LogLevel.WARN, 100);

        Logger logger = new Logger(List.of(d1, d2));

        logger.log("Debug message", LogLevel.DEBUG);
        logger.log("Error message", LogLevel.ERROR);

        d1.close();
        d2.close();

        System.out.println("Console logs: " + console.getLogs().size());

        System.out.println("File logs: " + file.getLogs().size());


        //Testing
        MemorySink sink = new MemorySink();

        Destination destination = new Destination(sink, new PlainTextFormatter(), LogLevel.DEBUG, 1000);

        Logger loggerB = new Logger(List.of(destination));

        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            int id = i;

            Thread t = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    loggerB.log("Thread-" + id + "-Log-" + j, LogLevel.INFO);
                }
            });

            threads.add(t);
            t.start();
        }

// 等所有 Producer 提交完
        for (Thread t : threads) {
            t.join();
        }

// 等 Worker 消费完 Queue
        destination.close();

        System.out.println("Expected: 1000");
        System.out.println("Actual: " + sink.getLogs().size());

    }
}

class Logger {
    private final List<Destination> destinations;

    public Logger(List<Destination> destinations) {
        this.destinations = new ArrayList<>(destinations);
    }

    public void log(String message, LogLevel logLevel) {
        Log logRecord = new Log(message, logLevel, Instant.now(), Thread.currentThread().getName());
        for (Destination destination : destinations) {
            destination.write(logRecord);
        }
    }
}

class Destination implements AutoCloseable {
    private final Sink sink;
    private final Formatter formatter;
    private final LogLevel minLevel;
    private final BlockingQueue<Log> queue;
    private final Thread worker;
    private volatile boolean accepting;
    private final Lock lock;

    public Destination(Sink sink, Formatter formatter, LogLevel minLevel, int capacity) {
        this.sink = sink;
        this.formatter = formatter;
        this.minLevel = minLevel;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.worker = new Thread(this::drain);
        this.worker.start();
        this.accepting = true;
        this.lock = new ReentrantLock();
    }

    public void write(Log log) {
        if (log == null) {
            throw new IllegalArgumentException("log is null");
        }
        if (log.logLevel().severity() < minLevel.severity()) {
            return;
        }
        synchronized (lock) {
            if (!accepting) {
                return;
            }
            boolean accepted = queue.offer(log);
            if (!accepted) {
                System.err.println("Logging queue full");
            }
        }


    }

    public void drain() {
        while (accepting || !queue.isEmpty()) {

            try {
                Log log = queue.poll(100, TimeUnit.MILLISECONDS);
                String formatted = formatter.format(log);
                sink.write(formatted);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    @Override
    public void close() throws Exception {
        synchronized (lock) {
            accepting = false;
        }
        worker.join();
        if (sink instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }


}

interface Formatter {
    public String format(Log logRecord);
}

class JsonFormatter implements Formatter {
    @Override
    public String format(Log record) {
        return "{" + "\"timestamp\":\"" + escape(record.timestamp().toString()) + "\"," + "\"level\":\"" + record.logLevel() + "\"," + "\"thread\":\"" + escape(record.thread()) + "\"," + "\"message\":\"" + escape(record.message()) + "\"" + "}";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    result.append("\\\"");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    result.append(c);
            }
        }
        return result.toString();
    }
}

class PlainTextFormatter implements Formatter {
    @Override
    public String format(Log record) {
        return record.timestamp() + " [" + record.logLevel() + "] [" + record.thread() + "] " + record.message();
    }
}

interface Sink {
    public void write(String formatted) throws Exception;
}

class ConsoleSink implements Sink {
    @Override
    public void write(String formatted) {
        System.out.println(formatted);
    }
}

class FileSink implements Sink, AutoCloseable {
    private final BufferedWriter writer;

    public FileSink(String filePath) throws IOException {
        this.writer = Files.newBufferedWriter(Path.of(filePath), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Override
    synchronized public void write(String formatted) throws IOException {
        writer.write(formatted);
        writer.newLine();
        writer.flush();
    }

    @Override
    synchronized public void close() throws IOException {
        writer.close();
    }
}

record Log(String message, LogLevel logLevel, Instant timestamp, String thread) {
}


enum LogLevel {
    DEBUG(10), INFO(20), WARN(30), ERROR(40), FATAL(50);
    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }
}

//for testing
class MemorySink implements Sink {
    private final List<String> logs = new ArrayList<>();

    @Override
    public synchronized void write(String formatted) {
        logs.add(formatted);
    }

    public synchronized List<String> getLogs() {
        return new ArrayList<>(logs);
    }
}
