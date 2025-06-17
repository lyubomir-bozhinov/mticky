package com.lyubomirbozhinov.mticky.tui;

import com.lyubomirbozhinov.mticky.api.FinnhubClient;
import com.lyubomirbozhinov.mticky.config.ConfigManager;
import com.lyubomirbozhinov.mticky.stock.StockQuote;
import com.lyubomirbozhinov.mticky.stock.StockService;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BasePane; // Import BasePane
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.DefaultWindowManager;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowListener;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Text User Interface for the stock monitor application.
 * Provides real-time stock data display and user interaction.
 */
public class StockMonitorTui {
    private static final Logger logger = LoggerFactory.getLogger(StockMonitorTui.class);

    private final ConfigManager configManager;
    private final ScheduledExecutorService executorService;
    private final int refreshInterval;
    private final FinnhubClient finnhubClient;
    private final StockService stockService;

    private Terminal terminal;
    private Screen screen;
    private MultiWindowTextGUI textGUI;
    private BasicWindow mainWindow;
    private Table<String> stockTable;
    private Label statusLabel;
    private Label commandLabel;

    private final Map<String, StockQuote> stockData = new ConcurrentHashMap<>();
    private volatile String currentStatus = "Starting..."; // Corrected variable name

    /**
     * Creates a new StockMonitorTui instance.
     *
     * @param configManager the configuration manager
     * @param executorService the executor service for background tasks
     * @param refreshInterval the refresh interval in seconds
     */
    public StockMonitorTui(ConfigManager configManager, ScheduledExecutorService executorService,
                           int refreshInterval) {
        this.configManager = configManager;
        this.executorService = executorService;
        this.refreshInterval = refreshInterval;
        this.finnhubClient = new FinnhubClient(System.getenv("FINNHUB_API_KEY"));
        this.stockService = new StockService();
    }

    /**
     * Starts the TUI application.
     */
    public void start() throws IOException {
        logger.info("Starting TUI...");

        initializeTerminal();
        initializeComponents();

        // Pre-load and display current data
        refreshAllStocks();
        updateTableDisplay(); // Ensure table appears immediately

        startDataRefresh();

        // Start Lanterna's own event loop. This blocks until the GUI is exited.
        // Using addWindowAndWait as per the Hello World example
        textGUI.addWindowAndWait(mainWindow);
    }

    /**
     * Stops the TUI application.
     */
    public void stop() throws IOException {
        logger.info("Stopping TUI...");

        // Gracefully shut down the screen and terminal
        if (screen != null) {
            try {
                screen.stopScreen();
            } catch (IOException e) {
                logger.warn("Failed to stop screen cleanly", e);
            }
        }

        if (terminal != null) {
            try {
                terminal.close();
            } catch (IOException e) {
                logger.warn("Failed to close terminal cleanly", e);
            }
        }

        executorService.shutdownNow();
        logger.info("Executor service shut down.");
    }

    /**
     * Initializes the terminal and screen.
     */
    private void initializeTerminal() throws IOException {
        logger.debug("Initializing terminal...");
        
        boolean isCI = "true".equalsIgnoreCase(System.getenv("CI"));
        if (isCI) {
          System.out.println("MTICKY: Detected CI environment, enabling headless mode.");
          System.setProperty("lanterna.terminal.headless", "true");
        }

        DefaultTerminalFactory terminalFactory = new DefaultTerminalFactory();

        if (isCI) {
          terminal = terminalFactory.createHeadlessTerminal();
        } else {
          terminal = terminalFactory.createTerminal();
        }

        screen = new TerminalScreen(terminal);
        screen.startScreen(); // Explicitly start the screen as in the Hello World example

        textGUI = new MultiWindowTextGUI(screen, new DefaultWindowManager(), new EmptySpace());
        logger.debug("Terminal initialized successfully");
    }

    /**
     * Initializes UI components.
     */
    private void initializeComponents() {
        logger.debug("Initializing UI components...");

        mainWindow = new BasicWindow("mticky - Stock Monitor");
        mainWindow.setHints(java.util.Arrays.asList(Window.Hint.FULL_SCREEN));

        // Using a WindowListener to handle input events for the main window
        mainWindow.addWindowListener(new WindowListener() {
            @Override
            public void onResized(Window window, TerminalSize oldSize, TerminalSize newSize) {
                // Optional: handle window resizing
            }

            @Override
            public void onMoved(Window window, TerminalPosition oldPosition, TerminalPosition newPosition) {
                // Optional: handle window movement
            }

            // CORRECTED: This onInput method now correctly matches the WindowListener interface for Lanterna 3.x,
            // including the AtomicBoolean parameter. It overrides the BasePaneListener's method.
            @Override
            public void onInput(Window window, KeyStroke keyStroke, AtomicBoolean deliverEvent) {
                handleKeyPress(keyStroke);
                // If handleKeyPress consumes the event and we don't want it passed further,
                // we would set deliverEvent.set(false). By default, let it pass.
                deliverEvent.set(true);
            }

            // This onUnhandledInput is correct as it was
            @Override
            public void onUnhandledInput(Window window, KeyStroke keyStroke, AtomicBoolean isProcessed) {
                isProcessed.set(false);
            }
        });

        Panel mainPanel = new Panel(new BorderLayout());

        // Status bar
        statusLabel = new Label(currentStatus);
        mainPanel.addComponent(statusLabel, BorderLayout.Location.TOP);

        // Stock table
        stockTable = new Table<>("Symbol", "Price", "Δ$", "Δ%", "Last Updated");
        stockTable.setSelectAction(this::handleTableSelection);
        mainPanel.addComponent(stockTable, BorderLayout.Location.CENTER);

        // Command bar
        commandLabel = new Label("[A]dd [D]elete [Q]uit");
        mainPanel.addComponent(commandLabel, BorderLayout.Location.BOTTOM);

        mainWindow.setComponent(mainPanel);

        // Add window to GUI
        textGUI.addWindow(mainWindow);

        logger.debug("UI components initialized");
    }

    /**
     * Starts the background data refresh task.
     */
    private void startDataRefresh() {
        logger.debug("Starting data refresh...");

        // Initial load
        refreshAllStocks();

        // Schedule periodic refresh
        executorService.scheduleAtFixedRate(
                this::refreshAllStocks,
                refreshInterval,
                refreshInterval,
                TimeUnit.SECONDS
        );

        logger.info("Data refresh scheduled every {} seconds", refreshInterval);
    }

    /**
     * Refreshes stock data for all symbols in the watchlist.
     */
    private void refreshAllStocks() {
        try {
            var watchlist = configManager.getWatchlist();
            if (watchlist.isEmpty()) {
                updateStatus("No stocks in watchlist. Press 'a' to add some.");
                return;
            }

            updateStatus("Refreshing " + watchlist.size() + " stocks...");

            for (String symbol : watchlist) {
                finnhubClient.getStockQuote(symbol).thenAccept(optionalQuote -> {
                    if (optionalQuote.isPresent()) {
                        stockData.put(symbol, optionalQuote.get());
                        logger.debug("Updated quote for {}: {}", symbol, optionalQuote.get());
                    } else {
                        logger.warn("No quote available for symbol: {}", symbol);
                        updateStatus("Warning: No data for " + symbol);
                    }
                    updateTableDisplay();
                }).exceptionally(throwable -> {
                    logger.error("Failed to fetch quote for {}", symbol, throwable);
                    updateStatus("Error fetching " + symbol + ": " + throwable.getMessage());
                    return null;
                });
            }

        } catch (Exception e) {
            logger.error("Error during stock refresh", e);
            updateStatus("Refresh error: " + e.getMessage());
        }
    }

    /**
     * Updates the stock table display.
     */
    private void updateTableDisplay() {
        textGUI.getGUIThread().invokeLater(() -> {
            stockTable.getTableModel().clear();

            for (Map.Entry<String, StockQuote> entry : stockData.entrySet()) {
                StockQuote quote = entry.getValue();

                String[] row = {
                        quote.getSymbol(),
                        stockService.formatPrice(quote.getCurrentPrice()),
                        stockService.formatChange(quote.getChange()),
                        stockService.formatPercentChange(quote.getPercentChange()),
                        stockService.formatTimestamp(quote.getLastUpdated())
                };

                stockTable.getTableModel().addRow(row);
            }

            if (stockData.isEmpty()) {
                updateStatus("Ready - Press 'a' to add stocks to watchlist");
            } else {
                updateStatus(String.format("Updated %d stocks at %s",
                        stockData.size(),
                        stockService.formatTimestamp(LocalDateTime.now())));
            }
        });
    }

    /**
     * Updates the status display in a thread-safe manner.
     */
    private void updateStatus(String status) {
        currentStatus = status; // Corrected: use currentStatus
        textGUI.getGUIThread().invokeLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(currentStatus); // Corrected: use currentStatus
            }
        });
    }

    /**
     * Handles keyboard input.
     */
    private void handleKeyPress(KeyStroke keyStroke) {
        if (keyStroke.getKeyType() == KeyType.Character) {
            char ch = Character.toLowerCase(keyStroke.getCharacter());

            switch (ch) {
                case 'a':
                    handleAddStock();
                    break;
                case 'd':
                    handleDeleteStock();
                    break;
                case 'q':
                    try {
                        stop();
                    } catch (IOException e) {
                        logger.error("Error stopping TUI on 'q' key press", e);
                    }
                    System.exit(0); // Force JVM shutdown
                    break;
            }
        } else if (keyStroke.getKeyType() == KeyType.EOF ||
                (keyStroke.isCtrlDown() && keyStroke.getKeyType() == KeyType.Character &&
                        keyStroke.getCharacter() == 'c')) {
            try {
                stop();
            } catch (IOException e) {
                logger.error("Error stopping TUI on Ctrl+C", e);
            }
            System.exit(0); // Force JVM shutdown on Ctrl+C
        }
    }

    /**
     * Handles adding a new stock to the watchlist.
     */
    private void handleAddStock() {
        try {
            String symbol = TextInputDialog.showDialog(textGUI, "Add Stock", "Enter stock symbol:", "");
            if (symbol != null && !symbol.trim().isEmpty()) {
                if (stockService.isValidStockSymbol(symbol)) {
                    String normalized = stockService.normalizeStockSymbol(symbol);

                    if (configManager.addToWatchlist(normalized)) {
                        updateStatus("Added " + normalized + " to watchlist");
                        configManager.save();

                        // Fetch quote and update table
                        finnhubClient.getStockQuote(normalized).thenAccept(optionalQuote -> {
                            if (optionalQuote.isPresent()) {
                                stockData.put(normalized, optionalQuote.get());
                                textGUI.getGUIThread().invokeLater(this::updateTableDisplay);
                            } else {
                                textGUI.getGUIThread().invokeLater(() ->
                                        MessageDialog.showMessageDialog(textGUI, "No Data",
                                                "No stock data available for: " + normalized));
                            }
                        }).exceptionally(ex -> {
                            logger.error("Failed to fetch stock data for {}", normalized, ex);
                            textGUI.getGUIThread().invokeLater(() ->
                                    MessageDialog.showMessageDialog(textGUI, "Error",
                                            "Error fetching stock data: " + ex.getMessage()));
                            return null;
                        });

                    } else {
                        updateStatus(normalized + " is already in watchlist");
                    }
                } else {
                    textGUI.getGUIThread().invokeLater(() ->
                            MessageDialog.showMessageDialog(textGUI, "Invalid Symbol",
                                    "The symbol '" + symbol + "' is not valid."));
                }
            }

        } catch (Exception e) {
            logger.error("Error adding stock", e);
            textGUI.getGUIThread().invokeLater(() ->
                    MessageDialog.showMessageDialog(textGUI, "Error",
                            "An error occurred while adding the stock:\n" + e.getMessage()));
        }
    }

    /**
     * Handles removing a stock from the watchlist.
     */
    private void handleDeleteStock() {
        try {
            String symbol = TextInputDialog.showDialog(textGUI, "Delete Stock",
                    "Enter stock symbol to remove:", "");
            if (symbol != null && !symbol.trim().isEmpty()) {
                String normalized = symbol.trim().toUpperCase();

                if (configManager.removeFromWatchlist(normalized)) {
                    stockData.remove(normalized);
                    configManager.save();
                    updateStatus("Removed " + normalized + " from watchlist");

                    textGUI.getGUIThread().invokeLater(this::updateTableDisplay);
                } else {
                    textGUI.getGUIThread().invokeLater(() ->
                            MessageDialog.showMessageDialog(textGUI, "Not Found",
                                    normalized + " is not in the watchlist."));
                }
            }

        } catch (Exception e) {
            logger.error("Error deleting stock", e);
            textGUI.getGUIThread().invokeLater(() ->
                    MessageDialog.showMessageDialog(textGUI, "Error",
                            "An error occurred while deleting the stock:\n" + e.getMessage()));
        }
    }

    /**
     * Handles table selection events.
     */
    private void handleTableSelection() {
        // Future extension: show detailed stock information
    }
}

