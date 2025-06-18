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
  private static final int DEFAULT_REFRESH_INTERVAL_SECONDS = 15;
  
  private final ScheduledExecutorService executorService;
  private final ConfigManager configManager;
  private StockMonitorTui tui;
  private int refreshInterval;
  
  /**
   * Creates a new MtickyApplication instance.
   */
  public MtickyApplication() {
    this.executorService = Executors.newScheduledThreadPool(2);
    this.configManager = new ConfigManager();
    this.refreshInterval = DEFAULT_REFRESH_INTERVAL_SECONDS;
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
      app.validateEnvironment();
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
      
      if (arg.startsWith("--refresh=")) {
        try {
          this.refreshInterval = Integer.parseInt(arg.substring("--refresh=".length()));
          if (this.refreshInterval < 1) {
            throw new IllegalArgumentException("Refresh interval must be at least 1 second");
          }
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("Invalid refresh interval: " + arg);
        }
      } else if ("--help".equals(arg) || "-h".equals(arg)) {
        printUsage();
        System.exit(0);
      } else {
        throw new IllegalArgumentException("Unknown argument: " + arg);
      }
    }
  }
  
  /**
   * Validates that required environment variables are set.
   */
  private void validateEnvironment() {
    String apiKey = System.getenv("FINNHUB_API_KEY");
    if (apiKey == null || apiKey.trim().isEmpty()) {
      throw new IllegalStateException(
          "FINNHUB_API_KEY environment variable is required. "
          + "Get your API key from https://finnhub.io and set it as an environment variable.");
    }
  }
  
  /**
   * Initializes the application components.
   */
  private void initialize() {
    logger.info("Starting mticky stock monitor (refresh interval: {}s)", refreshInterval);
    
    // Initialize configuration
    configManager.initialize();
    
    // Initialize TUI
    tui = new StockMonitorTui(configManager, executorService, refreshInterval);
    
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
    System.out.println("mticky - TUI Stock Monitor");
    System.out.println();
    System.out.println("Usage: java -jar mticky.jar [OPTIONS]");
    System.out.println();
    System.out.println("Options:");
    System.out.println("  --refresh=N     Set refresh interval in seconds (default: 5)");
    System.out.println("  --help, -h      Show this help message");
    System.out.println();
    System.out.println("Environment Variables:");
    System.out.println("  FINNHUB_API_KEY Required API key from https://finnhub.io");
    System.out.println();
    System.out.println("Controls:");
    System.out.println("  a               Add stock symbol to watchlist");
    System.out.println("  d               Delete stock symbol from watchlist");
    System.out.println("  q, Ctrl+C       Quit application");
  }
}
