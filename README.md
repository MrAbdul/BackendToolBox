# BackendToolBox

**BackendToolBox** is a versatile Java-based utility designed for backend developers to perform essential diagnostics, analysis, and connectivity checks directly from the terminal. It provides both a rich **Terminal User Interface (TUI)** for interactive workflows and a robust **Command Line Interface (CLI)** for automated or one-off tasks.

---

##  Key Features

### 1.  JDBC Resource Leak Detector
A static analysis tool that scans Java source code for potential JDBC resource leaks (e.g., unclosed `Connection`, `Statement`, or `ResultSet`).
- **Deep Static Analysis**: Uses `JavaParser` to traverse method logic and identify unclosed resources.
- **Context-Aware Filtering**: Can be configured to only scan specific classes (e.g., classes extending a particular DAO base class).
- **Flexible Reporting**: Produces detailed text-based reports on-screen or outputs structured JSON findings to a file.
- **CLI & TUI Integration**: Available in both interactive TUI (with file path auto-completion) and CLI modes.

### 2.  SSL/TLS Connectivity Checker
A diagnostic tool for verifying SSL/TLS certificate chains and server connectivity.
- **Certificate Validation**: Connects to remote hosts (e.g., `example.com:443`) and retrieves full certificate chains.
- **Detailed Insights**: Displays certificate subject, issuer, SHA-256 fingerprint, and expiration details.
- **Proxy Support**: Connects via HTTP proxies to test connectivity from restricted environments.
- **Custom Trust/Key Stores**: Allows specifying custom `.jks` or `.p12` truststores and keystores for testing mutual TLS (mTLS) or private certificates.

### 3.  DB Analyzer (SQL Diff)
A tool to diff SQL embedded in two codebases (base vs target) and report schema-relevant changes.
- **SQL Extraction**: Scans Java source files to find embedded SQL strings and constants.
- **Diffing Engine**: Compares SQL from a base branch (e.g., `master`) against a target branch (e.g., a feature or migration branch).
- **Schema Hints**: Reports added/removed/modified SQL and, for DML, highlights newly referenced columns per table via heuristics.
- **Flexible Options**: Filter by package, include/exclude dynamic SQL fragments, and export results to JSON.

### 4.  Hello Tool
A lightweight demonstration module used to showcase the extensibility of the TUI framework.

---

##  Architecture & Core Components

BackendToolBox is built on **Java 8** and **Spring Boot 2.7.x**, leveraging a modular architecture that makes it easy to add new tools.

### 1. **Framework Layers**
- **Spring Boot Core**: Handles dependency injection, component scanning, and application lifecycle.
- **Modular Tool System**:
    - `ToolModule` (TUI): Interface for tools that want to register a screen in the interactive TUI.
    - `CliCommand` (CLI): Interface for tools that want to provide a command-line command.
- **Shared Engines & Services**: Business logic (e.g., `JdbcDetectorEngine`, `SslCheckerService`) is kept separate from UI logic, allowing the same logic to be reused across TUI and CLI.

### 2. **Terminal User Interface (TUI)**
Powered by **Lanterna 3.1.2**, the TUI provides a graphical-like experience in a standard terminal.
- **`MainWindow`**: The main navigation hub, listing all registered `ToolModule` beans.
- **`ScreenRouter`**: Manages transitions between different tool screens.
- **`TaskRunner`**: Executes long-running tasks asynchronously to keep the UI responsive.
- **`StatusBar`**: Provides real-time feedback and status updates to the user.
- **Custom UI Components**: Includes `AutoCompleteTextBox` with file system support and `EscapableActionListBox`.

### 3. **Command Line Interface (CLI)**
A standard CLI mode for automation and CI/CD integration.
- **`CliRunner`**: Dispatches user commands to the appropriate `CliCommand` implementation.
- **`CliArgs`**: A specialized utility for parsing key-value pairs from command-line arguments (e.g., `--key value`).

---

##  Getting Started

### Prerequisites
- **Java 8** or higher.
- **Maven** (wrapper included).

### Build & Package
To compile and package the toolbox into an executable JAR:
```bash
# Windows
./mvnw.cmd clean package -DskipTests

# Unix-like
./mvnw clean package -DskipTests
```

### Running the App

#### Modes
- Default mode: CLI (no property required).
- Set `--toolbox.mode=tui` to start the interactive TUI.
- Set `--toolbox.mode=cli` to use explicit CLI mode.

#### Interactive TUI Mode
Launches the full Terminal UI.
```bash
java -jar target/BackendToolBox-1.0-SNAPSHOT.jar --toolbox.mode=tui
```

In the TUI, select the entry named "DB Analyzer (SQL Diff)", then:
- Fill "Base codebase root" (e.g., a checked‑out master/main).
- Fill "Target codebase root" (e.g., your feature/migration branch).
- Optionally set package filter CSV, toggle dynamic SQL inclusion, and provide a JSON output path.
- Press "Run SQL Diff" to execute. Results appear in the Output pane.

#### **CLI Mode**
Run a specific command directly from the command line.
```bash
# General usage
java -jar target/BackendToolBox-1.0-SNAPSHOT.jar --toolbox.mode=cli <command> [options]

# Example: JDBC Leak Detection (scan project sources)
java -jar target/BackendToolBox-1.0-SNAPSHOT.jar --toolbox.mode=cli jdbcdetector --sourceRoot ./src/main/java --includeWarnings true --includeParseErrors true

# Example: SSL Check (using a JKS, with hostname verification)
java -jar target/BackendToolBox-1.0-SNAPSHOT.jar --toolbox.mode=cli ssl-check --jks ./truststore.jks --pass changeit --target google.com:443 --hostnameVerification true

# Example: DB Analyzer (Compare two codebase versions)
java -jar target/BackendToolBox-1.0-SNAPSHOT.jar --toolbox.mode=cli dbanalyzer --baseRoot ./repo-master --targetRoot ./repo-migration --includePackages com.example.dao --jsonOut ./report.json
```

---

##  Command Reference

### jdbcdetector
Run the JDBC leak detector over a source tree.

Usage:
```bash
java -jar BackendToolBox.jar --toolbox.mode=cli jdbcdetector --sourceRoot <path> [options]
```
Required:
- `--sourceRoot <path>`: Root directory to scan (walks `*.java`).

Optional:
- `--daoBaseTypes <A,B,com.x.Dao>`: Only analyze classes extending any of these base types.
- `--jsonOut <path>`: Write findings as JSON to this path.
- `--includeWarnings <true|false>`: Default `true`.
- `--includeParseErrors <true|false>`: Default `true`.

Exit codes:
- `0` OK (0 issues)
- `1` Issues found (or scan completed with issues)
- `2` Invalid usage / missing args

### ssl-check
Run an SSL/TLS handshake check using a JKS (optionally via proxy).

Usage:
```bash
java -jar BackendToolBox.jar --toolbox.mode=cli ssl-check --jks <path> --pass <password> --target <https://host:port|host:port>
```
Required:
- `--jks <path>`: Path to JKS file.
- `--target <target>`: `https://host:port` or `host:port` (port 443 may be inferred by service).

Optional:
- `--pass <password>`: JKS password (can be empty). Default: empty string.
- `--proxy <host:port>`: HTTP CONNECT proxy.
- `--trustSame <true|false>`: Use same JKS as TrustStore. Default `true`.
- `--hostnameVerification <true|false>`: Enable HTTPS hostname verification. Default `true`.

Exit codes:
- `0` OK
- `1` Check failed (handshake/validation failed)
- `2` Invalid usage / missing args

### dbanalyzer
Diff SQL embedded in two codebases (base vs target) and report schema-relevant changes.

Usage:
```bash
java -jar BackendToolBox.jar --toolbox.mode=cli dbanalyzer --baseRoot <path> --targetRoot <path> [options]
```
Required:
- `--baseRoot <path>`: Base codebase root (e.g., master).
- `--targetRoot <path>`: Target codebase root (e.g., migration branch).

Optional:
- `--includePackages <csv>`: Comma-separated package prefixes filter (values are trimmed), e.g., `com.bbyn.dao,com.bbyn.repo`.
- `--includeDynamic <true|false>`: Include dynamic SQL fragments assembled via builders. Default: `false`.
- `--jsonOut <path>`: Write JSON report to this path (directories auto-created).

Help:
- `--help` or `-h` prints usage and exits.

Exit codes:
- `0` No schema-relevant SQL changes detected.
- `1` SQL changes detected.
- `2` Invalid usage / missing args

JSON output (example):
```json
{
  "tool" : "dbanalyzer",
  "version" : "0.1",
  "baseRoot": "./repo-master",
  "targetRoot": "./repo-migration",
  "includeDynamic" : true,
  "includePackages" : [ ],
  "baseSqlCount" : 2,
  "targetSqlCount" : 2,
  "modifiedCount" : 2,
  "addedCount" : 0,
  "removedCount" : 0,
  "changes" : [ {
    "kind" : "MODIFIED",
    "key" : "src/main/java/com/example/dao/AccountDao.java:5 (AccountDao findAccounts:sql)",
    "base" : {
      "idKey" : "src/main/java/com/example/dao/AccountDao.java#AccountDao#findAccounts:sql",
      "file" : "src/main/java/com/example/dao/AccountDao.java",
      "className" : "AccountDao",
      "owner" : "findAccounts:sql",
      "line" : 5,
      "dynamic" : false,
      "normalizedSql" : "SELECT A.ID, A.BALANCE FROM ACCOUNT A WHERE A.CUST_ID = ?",
      "type" : "SELECT",
      "tables" : [ "A" ],
      "columnsByTable" : {
        "A" : [ "BALANCE", "ID" ]
      },
      "parsedFully" : true
    },
    "target" : {
      "idKey" : "src/main/java/com/example/dao/AccountDao.java#AccountDao#findAccounts:sql",
      "file" : "src/main/java/com/example/dao/AccountDao.java",
      "className" : "AccountDao",
      "owner" : "findAccounts:sql",
      "line" : 5,
      "dynamic" : false,
      "normalizedSql" : "SELECT A.ID, A.BALANCE, A.CURRENCY FROM ACCOUNT A WHERE A.CUST_ID = ?",
      "type" : "SELECT",
      "tables" : [ "A" ],
      "columnsByTable" : {
        "A" : [ "BALANCE", "CURRENCY", "ID" ]
      },
      "parsedFully" : true
    },
    "newColumnsByTable" : {
      "A" : [ "CURRENCY" ]
    }
  }
  ]
}
```

---

##  Extending BackendToolBox

To add a new tool, simply create a new `@Component` that implements either:
- **`ToolModule`**: To add the tool to the TUI navigation.
- **`CliCommand`**: To add the tool as a CLI command.

The application automatically discovers new tools via Spring's component scanning.

---

##  Core Technologies
- **Spring Boot 2.7.18**: Core framework.
- **Lanterna 3.1.2**: Terminal UI library.
- **JavaParser 3.25.10**: Static analysis of Java source code.
- **Jackson**: JSON serialization/deserialization.
- **JNA**: Native access for terminal enhancements.


---

##  Configuration
- Application properties live in `src/main/resources/application.properties`.
- Defaults provided:
  - `spring.main.web-application-type=none` (non-web app)
  - `spring.main.banner-mode=off`
  - `logging.level.root=WARN`
- Mode selection is controlled at runtime by property `toolbox.mode`:
  - `--toolbox.mode=cli` (default if missing)
  - `--toolbox.mode=tui`

##  Project Structure
- `com.mrabdul`
  - `BackendToolBox` — Spring Boot entry point.
- `com.mrabdul.cli`
  - `CliRunner` — CLI dispatcher; `matchIfMissing=true` makes CLI the default mode.
  - `CliArgs` — minimal key/value argument parser.
- `com.mrabdul.tui`
  - `TuiRunner` — Lanterna bootstrapper; active when `toolbox.mode=tui`.
  - `MainWindow`, `ScreenRouter`, `StatusBar`, `TaskRunner`, `UiDialogs`, `AutoCompleteTextBox`, `EscapableActionListBox`, `FilePathAutoCompleter` — TUI framework and widgets.
- `com.mrabdul.tools`
  - `ToolModule`, `ToolScreen`, `CliCommand` — extension points for implementing tools.
- `com.mrabdul.tools.hello`
  - `HelloToolModule`, `HelloScreen` — sample module demonstrating TUI integration.
- `com.mrabdul.tools.dbanalyzer`
  - `DbAnalyzerService`, `DbAnalyzerScreen`, `DbAnalyzerToolModule`, `DbAnalyzerCliCommand`, `DbAnalyzerRequest`, `DbAnalyzerResult`, `SqlExtractor`, `SqlDiffEngine`, `SqlHeuristicParser`, `SqlArtifact`, `SqlMeta`, `SqlNormalizer`, `DbAnalyzerJsonReport` — extraction and diffing of embedded SQL across two source roots.
- `com.mrabdul.tools.jdbcdetector`
  - `JdbcDetectorEngine`, `JdbcDetectorService`, `JdbcDetectorScreen`, `JdbcDetectorToolModule`, `JdbcDetectorCliCommand`, `JdbcDetectorRequest`, `JdbcDetectorResult`, `Finding`, `DaoFilterParser` — static analysis for JDBC resource management.
- `com.mrabdul.tools.ssl`
  - `SslCheckerService`, `SslCheckerScreen`, `SslCheckerToolModule`, `SslCheckCliCommand`, `SslCheckRequest`, `SslCheckResult`, `ProxyConfig`, `CapturingTrustManager`, `CapturingKeyManager` — SSL/TLS diagnostics with optional proxy and custom stores.

##  Testing
- Framework: JUnit 5 (Jupiter) via Spring Boot starter.
- Run all tests:
```bash
./mvnw.cmd -q test
```
