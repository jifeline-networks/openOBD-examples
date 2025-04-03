package nl.factorit.openobd.functionlauncher;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class Logger {
    private static final Level DEFAULT_LOG_LEVEL = Level.INFO;

    private static Level CURRENT_LOG_LEVEL;
    private final String context;

    public static Logger getLogger(String context) {
        if (null == CURRENT_LOG_LEVEL) {
            String logLevel = "";
            try {
                logLevel = System.getenv("LOG_OUTPUT_LEVEL").toUpperCase();

                CURRENT_LOG_LEVEL = Level.valueOf(logLevel);

                print("INIT", "Logger", "Setting log level to %s".formatted(CURRENT_LOG_LEVEL));
            } catch (Exception e) {
                print("INIT", "Logger", "Reverting to default log level (%s) due to invalid config (%s)".formatted(DEFAULT_LOG_LEVEL, logLevel));
                CURRENT_LOG_LEVEL = DEFAULT_LOG_LEVEL;
            }
        }

        return new Logger(context, CURRENT_LOG_LEVEL);
    }

    private Logger(String context, Level level) {
        this.context = context;
        Logger.CURRENT_LOG_LEVEL = level;
    }

    public void error(String text) {
        this.error(text, null);
    }

    public void error(String text, Throwable cause) {
        if (Logger.CURRENT_LOG_LEVEL.ordinal() < Level.ERROR.ordinal()) {
            return;
        }

        String errorLabel = Level.ERROR.label;

        if (null != cause) {
            String textWithCause = "%s, cause: %s".formatted(text, cause);
            String stackTrace = Arrays.toString(cause.getStackTrace());

            print(errorLabel, this.context, textWithCause);
            print(errorLabel, this.context, stackTrace);
        } else {
            print(errorLabel, this.context, text);
        }
    }

    public void info(String text) {
        if (Logger.CURRENT_LOG_LEVEL.ordinal() < Level.INFO.ordinal()) {
            return;
        }

        print(Level.INFO.label, this.context, text);
    }

    public void debug(String text) {
        if (Logger.CURRENT_LOG_LEVEL.ordinal() < Level.DEBUG.ordinal()) {
            return;
        }

        print(Level.DEBUG.label, this.context, text);
    }

    private static void print(String level, String context, String text) {
        System.out.printf("%s | [%s] [%s]: %s%n", getTime(), level, context, text);
    }

    private static String getTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
    }

    public enum Level {
        ERROR("ERROR"),
        INFO("INFO"),
        DEBUG("DEBUG");

        public final String label;

        Level(String label) {
            this.label = label;
        }
    }
}
