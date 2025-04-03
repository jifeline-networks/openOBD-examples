package nl.factorit.openobd.functionlauncher;

import java.util.logging.*;

import java.time.Duration;

/**
 * Manages the Function Launcher thread, main entry to the application
 */
public class Server {
    public static final int MAX_ITERATIONS = 10;
    public static final Duration SLEEP_TIME_IN_SECONDS = Duration.ofSeconds(1);

    private static final Logger logger = Logger.getLogger("Server");

    /**
     * Start the Function Launcher and register a Shutdown hook to gracefully stop it when shutting down the application
     */
    public static void main(String[] args) throws InterruptedException, LauncherCouldNotBeStartedException {
        // Set the base loggers log level to what we want
        FunctionLauncher launcher = createFunctionLauncher();

        // Give the Function Launcher a bit of time to gracefully shut down, but force it should it take too long
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down, waiting for max %s second(s)...".formatted(MAX_ITERATIONS * SLEEP_TIME_IN_SECONDS.toSeconds()));
            launcher.requestShutdown();

            try {
                for (int iteration = 0; iteration < MAX_ITERATIONS && launcher.isRunning(); iteration++) {
                    Thread.sleep(SLEEP_TIME_IN_SECONDS.toMillis());
                    logger.debug("Function Launcher is still running, waiting %s more time(s)...".formatted((MAX_ITERATIONS - 1) - iteration));
                }

                if (launcher.isRunning()) {
                    logger.error("Max wait time hit, forcing shutdown...");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));

        launcher.run();

        logger.info("Done...");
    }

    /**
     * Tries to create the Function Launcher, retrying should it fail
     *
     * @return Fully initialized Function Launcher
     */
    private static FunctionLauncher createFunctionLauncher() throws InterruptedException, LauncherCouldNotBeStartedException {
        Exception latestError = null;

        for (int currentTry = 0; currentTry < MAX_ITERATIONS; currentTry++) {
            try {
                return new FunctionLauncher();
            } catch (Exception e) {
                logger.error("Could not create Function Launcher (%s), trying %s more time(s)".formatted(e.getClass(), (MAX_ITERATIONS - currentTry)));

                Thread.sleep((currentTry + 1) * SLEEP_TIME_IN_SECONDS.toMillis()); // Incremental backoff

                latestError = e;
            }
        }

        throw new LauncherCouldNotBeStartedException(latestError);
    }

    public static class LauncherCouldNotBeStartedException extends Throwable {
        public LauncherCouldNotBeStartedException(Throwable cause) {
            super("Function Launcher could not be started due to a %s: %s".formatted(cause.getClass(), cause.getMessage()), cause);
        }
    }
}
