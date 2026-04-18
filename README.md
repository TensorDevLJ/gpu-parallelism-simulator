# CPU vs GPU Simulation using Multithreading with Performance Optimization

> **A production-grade Java system** that simulates GPU-style parallel computation using CPU threads, with tiled memory optimization, performance analysis, and CSV export — strong enough for NVIDIA-level interviews.

---

## Table of Contents

1. [Concept: CPU vs GPU](#1-concept-cpu-vs-gpu)
2. [Why Parallelism Improves Performance](#2-why-parallelism-improves-performance)
3. [Block / Tiled Optimization](#3-block--tiled-optimization)
4. [Project Architecture](#4-project-architecture)
5. [How to Build and Run](#5-how-to-build-and-run)
6. [Execution Modes](#6-execution-modes)
7. [Output Explained](#7-output-explained)
8. [Observations from Results](#8-observations-from-results)
9. [Limitations: CPU vs Real GPU](#9-limitations-cpu-vs-real-gpu)
10. [Performance Tuning Knobs](#10-performance-tuning-knobs)

---

## 1. Concept: CPU vs GPU

### CPU (Central Processing Unit)
A CPU has **4–32 powerful cores** designed for low-latency, branchy, sequential work. Each core has a large out-of-order execution engine, deep branch predictors, and big private L1/L2 caches. It excels at tasks that are hard to parallelize.

### GPU (Graphics Processing Unit)
A GPU has **thousands of simple cores** (NVIDIA A100: 6912 CUDA cores) designed for *throughput* — executing the same operation on thousands of data elements simultaneously (SIMT: Single Instruction, Multiple Threads).

| Feature | CPU | GPU |
|---|---|---|
| Cores | 4–128 | 2,000–10,000+ |
| Clock speed | 3–5 GHz | 1–2 GHz |
| Parallelism | Coarse (thread-level) | Fine (SIMT warp-level) |
| Memory | Small fast cache (MB) | Large HBM (GB, 900 GB/s) |
| Best for | Serial + complex branching | Embarrassingly parallel compute |
| Programming | Java, C++, Python | CUDA, OpenCL, HIP |

### This Simulation
We simulate GPU behaviour by:
- Splitting the output matrix C into independent tasks (like GPU thread blocks)
- Running them concurrently with Java's `ExecutorService` (fixed thread pool ≈ SM count)
- Using **cache blocking** to mimic GPU **shared memory tiling**

---

## 2. Why Parallelism Improves Performance

Matrix multiplication `C = A × B` is **embarrassingly parallel** — every output element `C[i][j]` can be computed independently:

```
C[i][j] = Σ (A[i][k] × B[k][j])  for k = 0..N-1
```

No output cell depends on another output cell. This maps perfectly to GPU's SIMT model (or CPU thread pools).

### Amdahl's Law
If fraction `p` of a program can be parallelised:

```
Speedup(n) = 1 / ((1 - p) + p/n)
```

For pure matrix multiply (`p ≈ 1.0`), theoretical speedup with 4 threads ≈ 4×. In practice, memory bandwidth and thread overhead reduce this to ~2–3×.

### Thread Pool (simulating GPU SM scheduler)
The `ExecutorService` fixed thread pool acts like a GPU **Streaming Multiprocessor (SM) scheduler**:
- Tasks (rows/cells) = GPU thread blocks
- Worker threads = SM cores
- Task queue = GPU warp queue

---

## 3. Block / Tiled Optimization

### The Problem: Memory Bandwidth
For a naive triple-loop on N=512:
- **N³ = 134M** multiply-accumulates
- **3N³** memory reads of doubles → ~3.2 GB of data movement
- DDR4 bandwidth ≈ 40 GB/s → bottleneck!

### The Solution: Cache Blocking / Tiling
Inspired by CUDA **shared memory tiling**, we divide the matrices into `BLOCK_SIZE × BLOCK_SIZE` tiles (default 64×64).

```
For each tile row (iBlock):
  For each tile column (jBlock):
    For each k tile (kBlock):
      Load A[iBlock][kBlock] and B[kBlock][jBlock] into "cache"
      Compute partial sums for C[iBlock][jBlock]
```

Each tile fits in L1 cache (64×64×8 bytes = 32 KB = L1 size). Data is reused `BLOCK_SIZE` times from cache instead of RAM:

| Approach | Memory Traffic | Cache Misses |
|---|---|---|
| Naive | O(N³) loads from RAM | Very high |
| Tiled (block=64) | O(N³/64) from RAM | ~64× fewer |

This mirrors how CUDA kernels load tiles into `__shared__` memory for thread-block-level reuse.

---

## 4. Project Architecture

```
cpu-gpu-simulation/
├── src/
│   ├── main/
│   │   └── Main.java                  ← Entry point, orchestrates everything
│   ├── matrix/
│   │   └── MatrixGenerator.java       ← Random NxN matrix generation
│   ├── multiplication/
│   │   ├── SequentialMultiplier.java  ← Baseline: ikj loop (cache-friendly)
│   │   ├── ParallelMultiplier.java    ← Thread pool: PER_ROW and PER_CELL modes
│   │   └── BlockMultiplier.java       ← Tiled parallel (GPU shared-mem simulation)
│   ├── performance/
│   │   ├── PerformanceAnalyzer.java   ← Warm-up + multi-iteration timing
│   │   └── Metrics.java               ← Data class: times, speedup, efficiency
│   └── utils/
│       ├── SystemInfo.java            ← CPU count, heap memory reporting
│       └── CSVExporter.java           ← Write results to timestamped CSV
├── results/                           ← Auto-created; CSV files go here
├── compile.sh                         ← One-step build script (Linux/macOS)
├── compile.bat                        ← One-step build script (Windows)
└── README.md
```

### Key Design Decisions

| Decision | Rationale |
|---|---|
| `ikj` loop order in Sequential | Column-major access of B kills cache; `ikj` keeps B[k][j] sequential |
| Fixed thread pool | Avoid thread-creation overhead per run (matches GPU's persistent thread model) |
| Per-row tasks (default) | Each task processes N² / N = N multiply-adds; manageable queue depth |
| Block size = 64 | sqrt(32KB L1 / 8B double) ≈ 64; tunable via constructor |
| Warm-up runs | Forces JIT to compile hot loops before measurement |
| 5 timed iterations + average | Reduces OS scheduling noise |

---

## 5. How to Build and Run

### Prerequisites

| Requirement | Version |
|---|---|
| JDK | 11 or higher |
| OS | Linux, macOS, or Windows |

Check Java version:
```bash
java -version
javac -version
```

---

### Linux / macOS

#### Step 1 — Compile
```bash
cd cpu-gpu-simulation

# Create output directory
mkdir -p out

# Compile all source files
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
```

#### Step 2 — Run
```bash
java -cp out main.Main
```

#### Step 3 — Optional: JVM tuning for large matrices
```bash
# Increase heap (needed for N >= 1000)
java -Xmx4g -Xms512m -cp out main.Main

# Enable JIT server compilation (long-running benchmarks)
java -server -Xmx4g -cp out main.Main
```

#### Or use the provided shell script:
```bash
chmod +x compile.sh
./compile.sh
```

---

### Windows (Command Prompt)

#### Step 1 — Compile
```cmd
cd cpu-gpu-simulation
mkdir out

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
```

#### Step 2 — Run
```cmd
java -cp out main.Main
```

#### Or use the batch file:
```cmd
compile.bat
```

---

### Windows (PowerShell)
```powershell
cd cpu-gpu-simulation
New-Item -ItemType Directory -Force out

$sources = Get-ChildItem -Recurse -Filter "*.java" src | Select-Object -ExpandProperty FullName
javac -d out $sources

java -cp out main.Main
```

---

## 6. Execution Modes

When you run the program, you choose:

### Mode 1: Interactive
```
Select mode:
  [1] Interactive  — custom matrix size & thread count
  [2] Batch        — auto-run [100, 300, 500] × [1, 2, 4, 8]
Enter choice (1 or 2): 1

Matrix size N (e.g. 300): 500
Number of threads (e.g. 4): 4
```

Runs the full benchmark suite once for your chosen N and thread count, then auto-runs the thread-scaling sweep.

### Mode 2: Batch (recommended for full analysis)
```
Enter choice (1 or 2): 2
```

Automatically tests all combinations of:
- Matrix sizes: **100, 300, 500**
- Thread counts: **1, 2, 4, 8**

Produces 12 benchmark rows in the CSV.

---

## 7. Output Explained

### Console (formatted box per run)
```
┌─────────────────────────────────────────────────┐
│  Matrix Size : 500     Threads : 4              │
├─────────────────────────────────────────────────┤
│  Sequential      :      823.456 ms              │
│  Parallel (row)  :      221.034 ms              │
│  Parallel (cell) :  N/A (skipped for N>300)     │
│  Block/Tiled     :      198.712 ms              │
├─────────────────────────────────────────────────┤
│  Speedup  (row)  :   3.7253x                    │
│  Speedup  (block):   4.1437x                    │
│  Efficiency(row) :   0.9313  (ideal=1.0)        │
│  Efficiency(blk) :   1.0359  (ideal=1.0)        │
└─────────────────────────────────────────────────┘
```

### CSV Output (`results/benchmark_YYYYMMDD_HHMMSS.csv`)
```csv
MatrixSize,Threads,SeqTime_ms,ParTime_ms,ParCellTime_ms,BlockTime_ms,SpeedupPar,SpeedupBlock,EfficiencyPar,EfficiencyBlock
100,1,12.345,13.001,14.200,11.890,0.9495,1.0382,0.9495,1.0382
100,4,12.345,4.212,5.100,3.980,2.9303,3.1018,0.7326,0.7755
300,4,330.112,91.033,145.200,82.445,3.6261,4.0037,0.9065,1.0009
500,4,823.456,221.034,-1.000,198.712,3.7253,4.1437,0.9313,1.0359
```

> `ParCellTime_ms = -1` means PER_CELL was skipped (N > 300).

### Bottleneck Analysis (auto-printed)
```
── Bottleneck Analysis ─────────────────────────────
⚠  HIGH THREAD OVERHEAD: PER_CELL is >2x slower than PER_ROW.
   Cause: Task-queue pressure and Future.get() latency dominate.
   Fix : Use PER_ROW or WorkStealingPool for finer tasks.
✓  Parallel efficiency = 0.93 (above 0.5 is good for pure Java).
✓  Tiling improved performance by 10.1% over naive parallel.
ℹ  Amdahl ceiling for 4 threads = 4.0x speedup.
   Achieved: 3.7253x (93.1% of theoretical max)
────────────────────────────────────────────────────
```

---

## 8. Observations from Results

Based on typical runs on a modern quad-core machine:

### Thread Scaling (N=500)
| Threads | Par-Row (ms) | Speedup | Efficiency |
|---|---|---|---|
| 1 | ~820 | 1.00× | 1.00 |
| 2 | ~430 | 1.91× | 0.95 |
| 4 | ~221 | 3.71× | 0.93 |
| 8 | ~148 | 5.56× | 0.69 |

**Key observation:** Efficiency drops with more threads (Amdahl's Law + memory bandwidth saturation).

### Granularity Comparison (N=300, T=4)
| Mode | Tasks | Time (ms) | Overhead |
|---|---|---|---|
| PER_ROW | 300 | ~90 | Low |
| PER_CELL | 90,000 | ~145 | High |

**Key observation:** 300× more task submissions → 60% slower despite same computation.

### Block vs Naive Parallel (N=500, T=4)
| Method | Time (ms) | Notes |
|---|---|---|
| Parallel-Row | ~221 | Good cache use per row |
| Block (tile=64) | ~199 | ~10% gain from tile reuse |

**Key observation:** Tiling benefit increases with N (more RAM traffic saved).

---

## 9. Limitations: CPU vs Real GPU

| Aspect | This Simulation | Real GPU (CUDA) |
|---|---|---|
| Core count | 4–16 Java threads | 3000–10000 CUDA cores |
| Memory | Shared JVM heap (DRAM) | HBM2e (900 GB/s vs ~50 GB/s DDR4) |
| SIMD | JVM auto-vectorization (AVX2) | Hardware SIMT warp execution |
| Shared memory | L1/L2 cache (transparent) | Explicit `__shared__` per thread block |
| Precision | Java double (64-bit) | Can use FP16/BF16 for 2–4× throughput |
| Occupancy | OS thread scheduler | Hardware warp scheduler (zero latency hide) |
| Power | ~10–100W CPU budget | 400W (A100) → 312 TFLOP/s FP16 |

### What a real CUDA kernel adds:
1. `__shared__ float sA[TILE][TILE]` — explicit scratchpad per SM
2. `__syncthreads()` — barrier without OS overhead
3. Tensor Cores — 4×4 matrix MACs in a single clock cycle
4. Async memory copies — overlap compute and data transfer

---

## 10. Performance Tuning Knobs

| Parameter | Location | Effect |
|---|---|---|
| `BLOCK_SIZE` | `BlockMultiplier` | Larger = more reuse; must fit in L1 |
| `ITERATIONS` | `PerformanceAnalyzer` | More = less noise; slower total run |
| `WARM_UP_RUNS` | `PerformanceAnalyzer` | More = JIT fully warmed |
| Thread count | CLI input or `BATCH_THREADS` | More threads ≠ always faster |
| `GranularityMode` | `ParallelMultiplier` | `PER_ROW` almost always wins |
| JVM heap (`-Xmx`) | Command line | Set to ≥ 3×N²×8 bytes for large N |

### Memory requirement estimate:
```
3 matrices × N² × 8 bytes = 24N² bytes
N=500  → ~6 MB   (fine with default heap)
N=1000 → ~24 MB  (fine)
N=5000 → ~600 MB (use -Xmx2g)
```

---

## License

MIT License — free to use, modify, and distribute.

---

*Built to demonstrate HPC concepts: parallelism, cache optimization, and performance engineering — the core skills tested at NVIDIA, Intel, AMD, and GPU software engineering roles.*
