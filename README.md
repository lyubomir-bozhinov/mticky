# mticky - TUI Stock Monitor

A simple Java TUI app that allows us terminal-dwellers to monitor stock prices in (near) real-time.

![Java](https://img.shields.io/badge/Java-17+-orange.svg)
![Maven](https://img.shields.io/badge/Maven-3.6+-blue.svg)
![License](https://img.shields.io/badge/License-MIT-green.svg)

## Features

- **Real-time Stock Monitoring**: Track multiple stocks with live price updates
- **Intuitive TUI Interface**: Clean, responsive terminal interface built with Lanterna
- **Persistent Watchlist**: Your stock selections are automatically saved and restored
- **Robust Error Handling**: Graceful handling of network errors, rate limits, and API issues
- **Production Ready**: Comprehensive logging, testing, and configuration management
- **Thread Safe**: Concurrent operations with proper synchronization
- **Configurable**: Customizable refresh intervals and settings
- **Customizable Themes**: Switch between built-in color themes or define your own

## Visuals
### tokyo-night (default)
![tokyo-night theme (default)](docs/screenshots/tokyo-night-2025-06-21-01.png)

### everforest
![everforest theme](docs/screenshots/everforest-2025-06-21-01.png)

## Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Finnhub API key (free at [finnhub.io](https://finnhub.io))

### Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/lyubomir-bozhinov/mticky.git
   cd mticky
   ```

2. **Build the application:**
   ```bash
   mvn clean package
   ```

3. **Run the application:**
   ```bash
   java -jar target/mticky.jar
   ```

4. **Set up your API key:**
The application will prompt you for your Finnhub API key on its first run. Once provided, the key will be saved in your local configuration for future use. The refresh interval is also configured directly within the TUI.

### Run from Binary (Alternative)
For a quicker start, you can download the pre-built binaries from the [releases page](https://github.com/lyubomir-bozhinov/mticky/releases).

1.  **Download the appropriate binary** for your operating system and architecture (e.g., `mticky-vX.Y.Z-linux-amd64.tar.gz` for Linux 64-bit, `mticky-vX.Y.Z-windows-amd64.zip` for Windows 64-bit).

2.  **Extract the archive:**
    * **Linux/macOS:**
        ```bash
        tar -xzf mticky-vX.Y.Z-PLATFORM-ARCH.tar.gz
        cd mticky-vX.Y.Z/bin
        ```
    * **Windows:** Extract the `.zip` file. Navigate into the extracted `mticky-vX.Y.Z\bin` directory.

3.  **Run the application:**
    * **Linux/macOS:**
        ```bash
        ./mticky
        ```
    * **Windows (in Command Prompt/PowerShell):**
        ```cmd
        .\mticky.exe
        ```

4. **Set up your API key:**
The application will prompt you for your Finnhub API key on its first run. Once provided, the key will be saved in your local configuration for future use. The refresh interval is also configured directly within the TUI.

## Usage

### Controls

- **`a`** - Add a new stock symbol to your watchlist
- **`d`** - Delete a stock symbol from your Watchlist
- **`t`** - Change application theme
- **`r`** - Change stock refresh interval (default: 15)
- **`q`** or **`Ctrl+C`** - Quit the application

### Command Line Options

```bash
java -jar mticky.jar [OPTIONS]

Options:
  --help, -h      Show help message
```

### Examples

```bash
# Run application 
java -jar mticky.jar

# Show help
java -jar mticky.jar --help
```

## Configuration

### Configuration Files

The application stores its configuration in `~/.mticky/`:

- `config.properties` - Watchlist and application settings
- `logs/app.log` - Application logs with daily rotation
- `themes` - Application themes (bundled and custom user .theme files) 

## Themes

Customize the TUI appearance using built-in or custom themes.

### Built-in Themes

- `tokyo-night` (default)
- `catppuccin`
- `everforest`
- `rose-pine`

### Custom Themes
Place your `.theme` files in `~/.mticky/themes/`.

Example `.theme` file format (tokyo-night colours):

```bash
# Main bg
theme[main_bg]=#1a1b26

# Main text color
theme[main_fg]=#cfc9c2

# Title color for boxes
theme[title]=#cfc9c2

# Highlight color for keyboard shortcuts
theme[hi_fg]=#7dcfff

# Background color of selected item in processes box
theme[selected_bg]=#414868

# Foreground color of selected item in processes box
theme[selected_fg]=#cfc9c2

# Color of inactive/disabled text
theme[inactive_fg]=#565f89

# Box divider line and small boxes line color
theme[div_line]=#565f89

# Stocks price change up
theme[positive_change_fg]=#9ece6a

# Stocks price change down
theme[negative_change_fg]=#f7768e

```

## Development

### Building from Source

```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Check code coverage 
mvn jacoco:report

# Check code style
mvn checkstyle:check

# Package with dependencies
mvn package
```

### Running Tests

```bash
# Unit tests only
mvn test

# Integration tests (requires network)
mvn integration-test

# All tests with coverage report
mvn clean test jacoco:report
```

### Architecture

The application follows clean architecture principles:

```
src/main/java/com/lyubomirbozhinov/mticky/
├── app/           # Application entry point and CLI handling
├── tui/           # Terminal UI components (Lanterna)
├── stock/         # Business logic and data models
├── api/           # External API integration (Finnhub)
└── config/        # Configuration and persistence management
```

Key components:

- **MtickyApplication**: Main entry point, argument parsing, lifecycle management
- **StockMonitorTui**: Terminal interface using Lanterna framework
- **FinnhubClient**: HTTP client with retry logic and rate limiting
- **StockService**: Business logic for formatting and calculations
- **ConfigManager**: Persistent storage for watchlist and settings

## API Integration

The application uses the [Finnhub API](https://finnhub.io/docs/api) for real-time stock data:

- **Endpoint**: `/api/v1/quote`
- **Rate Limits**: Handled with exponential backoff
- **Error Recovery**: Automatic retries with jitter
- **Response Caching**: Thread-safe concurrent data structures

### Getting a Finnhub API Key

1. Visit [finnhub.io](https://finnhub.io)
2. Sign up for a free account
3. Navigate to your dashboard
4. Copy your API key
5. Run the `mticky` application. It will prompt you for the API key directly within the terminal UI and save it.

Free tier includes:
- 60 API calls/minute
- Data for US stocks
- No credit card required

## Troubleshooting

### Common Issues

**"FINNHUB_API_KEY environment variable is required"**
- Ensure you've exported the environment variable with your API key
- Check the key is valid by testing it in your browser: `https://finnhub.io/api/v1/quote?symbol=AAPL&token=YOUR_KEY`

**"Rate limit exceeded"**
- The free Finnhub tier allows 60 calls/minute
- Reduce refresh frequency to 20 seconds or higher (press 'R' in the application)
- Consider upgrading your Finnhub plan for higher limits

**"No data for symbol XXX"**
- Verify the stock symbol exists (US markets only for free tier)
- Check if markets are open (data may be delayed when closed)
- Some symbols may not be available in the free tier

**Terminal display issues**
- Ensure your terminal supports UTF-8 and has sufficient size
- Try different terminal emulators if rendering is incorrect
- Minimum recommended size: 80x24 characters

### Logging

Application logs are stored in `~/.mticky/logs/app.log` with automatic rotation.

**Log Levels:**
- `ERROR`: Critical failures requiring attention
- `WARN`: Recoverable issues (rate limits, network timeouts)
- `INFO`: Normal application lifecycle events
- `DEBUG`: Detailed debugging information (API responses, calculations)

**Enable debug logging:**
```bash
# Temporarily enable debug mode
export JAVA_OPTS="-Dlogback.configurationFile=src/main/resources/logback-debug.xml"
java $JAVA_OPTS -jar mticky.jar
```

### Performance Tuning

**Memory usage:**
```bash
# Limit JVM heap size for resource-constrained environments
java -Xmx256m -jar mticky.jar
```

**Network timeouts:**
```bash
# Increase timeout for slow networks
java -Dapi.timeout.seconds=30 -jar mticky.jar
```

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

### Development Setup

1. **Fork and clone the repository**
2. **Install Java 17+ and Maven 3.6+**
3. **Set up your development environment:**
   ```bash
   # Install dependencies
   mvn clean install
   ```

4. **Run the development version:**
   ```bash
   export FINNHUB_API_KEY="your_api_key"
   mvn compile exec:java -Dexec.mainClass="com.lyubomirbozhinov.mticky.app.MtickyApplication"
   ```

### Code Standards

- **Checkstyle**: Google Java Style Guide enforced
- **Test Coverage**: Required for core modules
- **Documentation**: Javadoc required for all public APIs
- **Thread Safety**: All shared data structures must be thread-safe

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Finnhub](https://finnhub.io) for providing the stock data API
- [Lanterna](https://github.com/mabe02/lanterna) for the excellent TUI framework
- [Jackson](https://github.com/FasterXML/jackson) for JSON processing
- [SLF4J](http://www.slf4j.org/) and [Logback](http://logback.qos.ch/) for logging

## Support

- 🐛 **Issues**: [GitHub Issues](https://github.com/lyubomir-bozhinov/mticky/issues)
- 💬 **Discussions**: [GitHub Discussions](https://github.com/lyubomir-bozhinov/mticky/discussions)

---

**Happy Trading!** 📈
