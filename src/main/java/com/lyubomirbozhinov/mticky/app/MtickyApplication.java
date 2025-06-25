package com.lyubomirbozhinov.mticky.app;

import com.lyubomirbozhinov.mticky.config.ConfigManager;
import com.lyubomirbozhinov.mticky.tui.StockMonitorTui;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application entry point for the mticky stock monitor.
 * Handles command-line arguments, initialization, and graceful shutdown.
 */
public class MtickyApplication {
  private static final Logger logger = LoggerFactory.getLogger(MtickyApplication.class);

  private final ScheduledExecutorService executorService;
  private final ConfigManager configManager;
  private StockMonitorTui tui;

  /**
   * Creates a new MtickyApplication instance.
   */
  public MtickyApplication() {
    this.executorService = Executors.newScheduledThreadPool(2);
    this.configManager = new ConfigManager();
  }

  /**
   * Main entry point for the application.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    MtickyApplication app = new MtickyApplication();

    try {
      app.parseArguments(args);
      app.initialize();
      app.run();
    } catch (Exception e) {
      logger.error("Application failed to start: {}", e.getMessage(), e);
      System.err.println("Failed to start mticky: " + e.getMessage());
      System.exit(1);
    }
  }

  /**
   * Parses command-line arguments.
   *
   * @param args command-line arguments
   */
  private void parseArguments(String[] args) {
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];

      if ("--help".equals(arg) || "-h".equals(arg)) {
        printUsage();
        System.exit(0);
      } else {
        throw new IllegalArgumentException("Unknown argument: " + arg);
      }
    }
  }

  /**
   * Initializes the application components.
   */
  private void initialize() {
    logger.info("Starting mticky stock monitor...");

    // Initialize configuration
    configManager.initialize();

    // Initialize TUI
    tui = new StockMonitorTui(configManager, executorService);

    // Register shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
  }

  /**
   * Runs the main application loop.
   */
  private void run() {
    try {
      tui.start();
    } catch (Exception e) {
      logger.error("TUI execution failed", e);
      throw new RuntimeException("Failed to run TUI", e);
    }
  }

  /**
   * Performs graceful shutdown of the application.
   */
  private void shutdown() {
    logger.info("Shutting down mticky...");

    if (tui != null) {
      try {
        tui.stop();
      } catch (Exception e) {
        logger.warn("Error stopping TUI", e);
      }
    }

    // Shutdown executor service
    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
        executorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      executorService.shutdownNow();
      Thread.currentThread().interrupt();
    }

    // Save configuration
    try {
      configManager.save();
    } catch (Exception e) {
      logger.warn("Error saving configuration", e);
    }

    logger.info("mticky shutdown complete");
  }

  /**
   * Prints usage information.
   */
  private void printUsage() {
    System.out.println("mticky - A simple Java stock monitor for terminal dwellers");
    System.out.println();
    System.out.println("Usage: java -jar mticky.jar [OPTIONS]");
    System.out.println();
    System.out.println("Options:");
    System.out.println("  --help, -h      Show this help message");
    System.out.println();
    System.out.println();
    System.out.println("Controls:");
    System.out.println("  a               Add stock symbol to watchlist");
    System.out.println("  d               Delete stock symbol from watchlist");
    System.out.println("  t               Change application theme");
    System.out.println("  r               Change stock refresh interval");
    System.out.println("  q, Ctrl+C       Quit application");
  }
}
