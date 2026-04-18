#!/bin/bash
# ═══════════════════════════════════════════════════
#  CPU vs GPU Simulation — Build & Run Script
#  Usage: chmod +x compile.sh && ./compile.sh
# ═══════════════════════════════════════════════════

set -e  # exit on first error

echo "════════════════════════════════════════════"
echo "  CPU vs GPU Simulation — Build & Run"
echo "════════════════════════════════════════════"

# ── Ensure javac is available ──────────────────
if ! command -v javac &>/dev/null; then
    echo "[ERROR] javac not found. Install JDK 11+."
    echo "  Ubuntu : sudo apt install openjdk-17-jdk"
    echo "  macOS  : brew install openjdk@17"
    exit 1
fi

JAVA_VER=$(javac -version 2>&1 | awk '{print $2}' | cut -d. -f1)
echo "[Info] Java version: $(java -version 2>&1 | head -1)"

# ── Create output dir ──────────────────────────
mkdir -p out results

# ── Compile ────────────────────────────────────
echo "[Step 1] Compiling sources..."

javac -d out \
  src/matrix/MatrixGenerator.java \
  src/multiplication/SequentialMultiplier.java \
  src/multiplication/ParallelMultiplier.java \
  src/multiplication/BlockMultiplier.java \
  src/performance/Metrics.java \
  src/performance/PerformanceAnalyzer.java \
  src/utils/SystemInfo.java \
  src/utils/CSVExporter.java \
  src/main/Main.java

echo "[Step 1] Compilation successful ✓"

# ── Run ────────────────────────────────────────
echo "[Step 2] Running simulation..."
echo ""

# -server: use server JIT (C2 compiler) for better optimisation
# -Xmx2g : 2 GB heap — adjust if running N > 1000
java -server -Xmx2g -cp out main.Main

echo ""
echo "[Done] Check the 'results/' folder for CSV output."
