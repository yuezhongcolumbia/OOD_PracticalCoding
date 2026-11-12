/**
Prompt;
"Design a logging service. Or call it a logger, whichever you prefer."
 **/

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.LogRecord;

/**
Requirements:
1. each log has different levels, severity in ascending order:
    DEBUG, INFO, WARN, ERROR, FATAL;
2. each logs are send to different destinations, and each destination has different filter level;
3.  logger can written in different file format, JSON, txt, file....;
4. support concurrent logging
5. configs are loaded at startup

flow: each destination has independent filter level and logging format. we write the logs, we send the logs, concurrently;

Entity:
1. Logger;
2. LogRecord:
    - message;
    - format;
    - thread;
    - logLevel;
3. destination:
    - formatter;
    - sink;
    = minLevel
4. Enum LogLevel:
**/

public class LoggingSystemOOD {

}

class Logger{
    private final List<Destination> destinations;

    public Logger(List<Destination> destinations) {
        this.destinations = new ArrayList<>(destinations);
    }

    public void log(String message, LogLevel logLevel){
        Log logRecord = new Log(message, logLevel, Instant.now(), Thread.currentThread().getName());
        for(Destination destination : destinations){
            destination.write(logRecord);
        }
    }
}
class Destination{
    private final Sink sink;
    private final Formatter formatter;
    private final LogLevel minLevel;

    public Destination(Sink sink, Formatter formatter, LogLevel minLevel){
        this.sink = sink;
        this.formatter = formatter;
        this.minLevel = minLevel;
    }

    public void write(Log log){
        if (log == null){
            throw new IllegalArgumentException("log is null");
        }
        if (log.logLevel().severity() < minLevel.severity()){
            return;
        }
        String formatted = formatter.format(log);

        try{
            sink.write(formatted);
        } catch(Exception e){
            System.out.println("exception occured during sink writing: " + e.getMessage());
        }

    }


}

interface Formatter{
    public String format(Log logRecord);
}
class JsonFormatter implements Formatter{
    @Override
    public String format(Log record) {
        return "{"
                + "\"timestamp\":\"" + escape(record.timestamp().toString()) + "\","
                + "\"level\":\"" + record.logLevel() + "\","
                + "\"thread\":\"" + escape(record.thread()) + "\","
                + "\"message\":\"" + escape(record.message()) + "\""
                + "}";
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
        return record.timestamp() + " [" + record.logLevel() + "] ["
                + record.thread() + "] " + record.message();
    }
}
interface Sink{
    public void write(String formatted) throws Exception;
}
class ConsoleSink implements Sink{
    @Override
    public void write(String formatted) {
        System.out.println(formatted);
    }
}
class FileSink implements Sink, AutoCloseable{
    private final BufferedWriter writer;

    public FileSink(String filePath) throws IOException {
        this.writer = Files.newBufferedWriter(
                Path.of(filePath),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    @Override
    synchronized public  void write(String formatted) throws IOException {
        writer.write(formatted);
        writer.newLine();
        writer.flush();
    }

    @Override
    synchronized public void close() throws IOException {
        writer.close();
    }
}

record Log(
        String message,
        LogLevel logLevel,
        Instant timestamp,
        String thread
){}


enum LogLevel{
    DEBUG(10),
    INFO(20),
    WARN(30),
    ERROR(40),
    FATAL(50);
    private final int severity;
    LogLevel(int severity){
        this.severity = severity;
    }
    public int severity(){
        return severity;
    }
}
