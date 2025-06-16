# Contributing to mticky

Thank you for your interest in contributing to mticky! This document provides guidelines and information for contributors.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Pull Request Process](#pull-request-process)
- [Issue Reporting](#issue-reporting)

## Code of Conduct

This project adheres to a code of conduct that we expect all contributors to follow:

- **Be Respectful**: Treat everyone with respect and professionalism
- **Be Inclusive**: Welcome newcomers and help them learn
- **Be Constructive**: Provide helpful feedback and suggestions
- **Be Patient**: Remember that everyone has different skill levels and backgrounds

## Development Setup

### Prerequisites

- **Java 17+**: Required for building and running the application
- **Maven 3.6+**: For dependency management and building
- **Git**: For version control
- **IDE**: IntelliJ IDEA or Eclipse recommended
- **Finnhub API Key**: For testing (free at [finnhub.io](https://finnhub.io))

### Environment Setup

1. **Fork the repository** on GitHub

2. **Clone your fork:**
   ```bash
   git clone https://github.com/yourusername/mticky.git
   cd mticky
   ```

3. **Add upstream remote:**
   ```bash
   git remote add upstream https://github.com/example/mticky.git
   ```

4. **Install dependencies:**
   ```bash
   mvn clean install
   ```

5. **Set up environment variables:**
   ```bash
   export FINNHUB_API_KEY="your_test_api_key"
   ```

6. **Run tests to verify setup:**
   ```bash
   mvn test
   ```

### IDE Configuration

#### IntelliJ IDEA

1. **Import project**: File → Open → Select `pom.xml`
2. **Configure code style**: 
   - File → Settings → Editor → Code Style → Java
   - Import Google Java Style Guide: https://github.com/google/styleguide/blob/gh-pages/intellij-java-google-style.xml
3. **Configure Checkstyle**:
   - Install Checkstyle-IDEA plugin
   - Configure to use Google checks
4. **Enable annotation processing** for any future Lombok usage

#### Eclipse

1. **Import project**: File → Import → Existing Maven Projects
2. **Install Google Java Format plugin**
3. **Configure Checkstyle**: Help → Eclipse Marketplace → Search "Checkstyle"

## How to Contribute

### Types of Contributions

We welcome several types of contributions:

- **Bug Fixes**: Fix issues in existing functionality
- **New Features**: Add new capabilities to the application
- **Documentation**: Improve README, Javadoc, or other documentation
- **Tests**: Add test coverage or improve existing tests
- **Performance**: Optimize existing code for better performance
- **Code Quality**: Refactoring and cleanup

### Getting Started

1. **Check existing issues** for something to work on
2. **Create a new issue** if you have an idea not already covered
3. **Comment on the issue** to let others know you're working on it
4. **Create a feature branch** from `main`
5. **Make your changes** following our coding standards
6. **Test thoroughly** including edge cases
7. **Submit a pull request** with a clear description

## Coding Standards

### Java Code Style

We follow the **Google Java Style Guide** with these additions:

#### General Principles

- **Clarity over cleverness**: Write code that's easy to understand
- **Consistency**: Follow existing patterns in the codebase
- **Documentation**: Public APIs must have Javadoc
- **Error handling**: Handle all exceptions appropriately
- **Thread safety**: Document thread safety guarantees

#### File Organization

```java
// 1. License header (if applicable)
// 2. Package declaration
package com.example.mticky.stock;

// 3. Imports (grouped and sorted)
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.example.mticky.api.FinnhubClient;
import org.slf4j.Logger;

// 4. Class documentation
/**
 * Service class for stock-related operations.
 * 
 * <p>This class provides thread-safe operations for managing stock data
 * and calculations. All public methods are safe for concurrent access.
 * 
 * @author Your Name
 * @since 1.0.0
 */
public class StockService {
    // Class implementation
}
```

#### Naming Conventions

- **Classes**: PascalCase (`StockService`, `FinnhubClient`)
- **Methods**: camelCase (`getStockQuote`, `calculatePercentChange`)
- **Variables**: camelCase (`stockPrice`, `lastUpdated`)
- **Constants**: SCREAMING_SNAKE_CASE (`MAX_RETRIES`, `DEFAULT_TIMEOUT`)
- **Packages**: lowercase with dots (`com.example.mticky.stock`)

#### Method Documentation

```java
/**
 * Fetches a stock quote for the given symbol.
 * 
 * <p>This method makes an asynchronous HTTP request to the Finnhub API
 * and returns a CompletableFuture containing the stock quote data.
 * 
 * @param symbol the stock symbol (e.g., "AAPL", "GOOGL")
 * @return a CompletableFuture containing the stock quote, or empty if not found
 * @throws IllegalArgumentException if symbol is null or invalid
 * @throws RuntimeException if API key is not configured
 * @since 1.0.0
 */
public CompletableFuture<Optional<StockQuote>> getStockQuote(String symbol) {
    // Implementation
}
```

#### Error Handling

```java
// Good: Specific exception handling
try {
    StockQuote quote = fetchQuoteFromApi(symbol);
    return Optional.of(quote);
} catch (IOException e) {
    logger.warn("Network error fetching quote for {}: {}", symbol, e.getMessage());
    throw new StockServiceException("Failed to fetch quote due to network error", e);
} catch (JsonProcessingException e) {
    logger.error("Invalid JSON response for symbol {}", symbol, e);
    return Optional.empty();
}

// Avoid: Generic exception catching
try {
    // ... operations
} catch (Exception e) {  // Too broad
    logger.error("Something went wrong", e);
    throw e;
}
```

### Architecture Guidelines

#### Package Structure

Follow the existing package organization:

```
com.example.mticky/
├── app/           # Application entry point, CLI parsing
├── tui/           # User interface components
├── stock/         # Business logic and data models
├── api/           # External API integration
└── config/        # Configuration management
```

#### Dependency Rules

- **No circular dependencies** between packages
- **API package** should not depend on TUI
- **Stock package** should be framework-agnostic
- **Use dependency injection** for testability

#### Thread Safety

- **Document thread safety** in class Javadoc
- **Use concurrent collections** for shared data
- **Minimize synchronization** scope
- **Prefer immutable objects** when possible

```java
/**
 * Thread-safe service for stock operations.
 * 
 * <p>All public methods in this class are safe for concurrent access
 * by multiple threads. Internal state is protected using concurrent
 * data structures and atomic operations.
 */
@ThreadSafe
public class StockService {
    private final ConcurrentHashMap<String, StockQuote> cache = new ConcurrentHashMap<>();
    // ...
}
```

## Testing Guidelines

### Test Structure

We maintain high test coverage (minimum 80%) with these test categories:

#### Unit Tests
- **Location**: `src/test/java/`
- **Purpose**: Test individual classes in isolation
- **Naming**: `ClassNameTest.java`
- **Mock dependencies** using Mockito

```java
@ExtendWith(MockitoExtension.class)
class StockServiceTest {
    
    @Mock
    private FinnhubClient mockClient;
    
    @InjectMocks
    private StockService stockService;
    
    @Test
    void shouldFormatPriceCorrectly() {
        // Given
        BigDecimal price = new BigDecimal("1234.56");
        
        // When
        String formatted = stockService.formatPrice(price);
        
        // Then
        assertEquals("1,234.56", formatted);
    }
}
```

#### Integration Tests
- **Location**: `src/test/java/`
- **Purpose**: Test component interactions
- **Use WireMock** for API testing
- **Test real file I/O** with temporary directories

```java
class FinnhubClientIntegrationTest {
    
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().port(8089))
        .build();
    
    @Test
    void shouldHandleRateLimitGracefully() {
        // Given
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/quote"))
            .willReturn(aResponse().withStatus(429)));
        
        // When & Then
        assertDoesNotThrow(() -> {
            client.getStockQuote("AAPL").get(5, SECONDS);
        });
    }
}
```

### Test Best Practices

#### Test Naming
- Use descriptive method names: `shouldReturnEmptyWhenSymbolNotFound()`
- Follow Given-When-Then structure in test body
- Group related tests in nested classes

#### Test Data
- **Use constants** for test data
- **Create builder patterns** for complex objects
- **Avoid magic numbers** and strings

```java
class StockQuoteTestData {
    public static final String VALID_SYMBOL = "AAPL";
    public static final BigDecimal SAMPLE_PRICE = new BigDecimal("150.75");
    
    public static StockQuote.Builder aValidStockQuote() {
        return StockQuote.builder()
            .symbol(VALID_SYMBOL)
            .currentPrice(SAMPLE_PRICE)
            .change(new BigDecimal("2.25"))
            .percentChange(new BigDecimal("1.52"))
            .lastUpdated(LocalDateTime.now());
    }
}
```

#### Assertions
- **Use specific assertions**: `assertThat(list).hasSize(3)` vs `assertEquals(3, list.size())`
- **Provide meaningful messages**: `assertThat(result).as("Stock price should be positive").isPositive()`
- **Test edge cases**: null inputs, empty collections, boundary values

### Running Tests

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=StockServiceTest

# Integration tests only
mvn test -Dtest="*IntegrationTest"

# With coverage report
mvn clean test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

## Pull Request Process

### Before Submitting

1. **Sync with upstream:**
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run full test suite:**
   ```bash
   mvn clean test
   ```

3. **Check code style:**
   ```bash
   mvn checkstyle:check
   ```

4. **Verify coverage:**
   ```bash
   mvn jacoco:report
   # Check that coverage hasn't decreased
   ```

### PR Requirements

#### Title and Description
- **Clear title**: Summarize the change in 50 characters or less
- **Detailed description**: Explain what, why, and how
- **Link issues**: Use "Fixes #123" or "Relates to #456"

#### Example PR Description
```markdown
## Summary
Add support for cryptocurrency symbols in stock watchlist

## Changes
- Extended StockService to validate crypto symbols (BTC, ETH, etc.)
- Updated FinnhubClient to use cryptocurrency endpoint
- Added new test cases for crypto symbol validation
- Updated documentation with crypto examples

## Testing
- Added unit tests for crypto symbol validation
- Updated integration tests to cover crypto API endpoints
- Manually tested with BTC, ETH, ADA symbols

Fixes #142
```

#### Code Changes
- **Small, focused commits**: Each commit should represent a logical change
- **Clear commit messages**: Follow conventional commit format
- **No merge commits**: Rebase instead of merging
- **Clean history**: Squash fixup commits before submitting

#### Review Checklist
- [ ] Code follows style guidelines
- [ ] All tests pass
- [ ] New functionality has tests
- [ ] Documentation is updated
- [ ] No breaking changes (or properly documented)
- [ ] Performance impact is considered

### Review Process

1. **Automated checks** must pass (CI, tests, style)
2. **Code review** by at least one maintainer
3. **Address feedback** promptly and professionally  
4. **Final approval** from maintainer
5. **Maintainer merges** (no self-merging)

### After Merge

1. **Delete feature branch:**
   ```bash
   git branch -d feature-branch-name
   git push origin --delete feature-branch-name
   ```

2. **Update local main:**
   ```bash
   git checkout main
   git pull upstream main
   ```

## Issue Reporting

### Bug Reports

Use the bug report template and include:

- **Environment details** (Java version, OS, terminal)
- **Steps to reproduce** the issue
- **Expected vs actual behavior**
- **Error messages or logs**
- **Screenshots** if applicable

### Feature Requests

Use the feature request template and include:

- **Problem description**: What limitation are you facing?
- **Proposed solution**: How should it work?
- **Alternatives considered**: Other approaches you've thought of
- **Additional context**: Use cases, examples, mockups

### Performance Issues

Include:

- **Profiling data** if available
- **System specifications**
- **Dataset size** (number of stocks, frequency)
- **Memory usage** observations

## Communication

### Getting Help

- **GitHub Discussions**: For questions and general discussion
- **GitHub Issues**: For bug reports and feature requests  
- **Code Reviews**: For implementation feedback
- **Documentation**: Check README and Javadocs first

### Best Practices

- **Search existing issues** before creating new ones
- **Use appropriate labels** when creating issues
- **Be patient and respectful** in all interactions
- **Help others** when you can contribute knowledge

---

Thank you for contributing to mticky! 🚀