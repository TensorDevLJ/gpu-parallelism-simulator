@echo off
REM ═══════════════════════════════════════════════════
REM  CPU vs GPU Simulation — Windows Build & Run
REM  Usage: Double-click or run from Command Prompt
REM ═══════════════════════════════════════════════════

echo ════════════════════════════════════════════
echo   CPU vs GPU Simulation — Build ^& Run
echo ════════════════════════════════════════════

REM Check javac
where javac >nul 2>&1
if errorlevel 1 (
    echo [ERROR] javac not found. Install JDK 11+ and add to PATH.
    echo Download: https://adoptium.net/
    pause
    exit /b 1
)

echo [Info] Java version:
java -version

REM Create dirs
if not exist out mkdir out
if not exist results mkdir results

echo.
echo [Step 1] Compiling sources...

javac -d out ^
  src\matrix\MatrixGenerator.java ^
  src\multiplication\SequentialMultiplier.java ^
  src\multiplication\ParallelMultiplier.java ^
  src\multiplication\BlockMultiplier.java ^
  src\performance\Metrics.java ^
  src\performance\PerformanceAnalyzer.java ^
  src\utils\SystemInfo.java ^
  src\utils\CSVExporter.java ^
  src\main\Main.java

if errorlevel 1 (
    echo [ERROR] Compilation failed.
    pause
    exit /b 1
)

echo [Step 1] Compilation successful!

echo.
echo [Step 2] Running simulation...
echo.

java -server -Xmx2g -cp out main.Main

echo.
echo [Done] Check the results\ folder for CSV output.
pause
