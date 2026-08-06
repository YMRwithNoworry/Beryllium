# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

Beryllium 是一个使用 Architectury 的 Minecraft 多加载器性能模组，目标平台是 Fabric 和 NeoForge。当前代码基线是 Minecraft 1.21.1、Java 21、Rust 1.97.1（Rust 2024 edition）。

核心功能包括：
- **Java 21 FFM downcall**：使用 Foreign Function & Memory API 调用 Rust native 后端，不依赖 JNI 或 `jni-sys`
- **异步区块生成系统**：通过 Rust 工作线程池执行噪声生成和生物群系混合，避免主线程阻塞
- **批量坐标距离计算**：AI 实体距离排序、最近物品传感器 Top-K、PlayerChunkSender 区块选择
- **点电荷计算加速**：PotentialCalculator 批处理，在 native 可用时并行计算点电荷贡献
- **线程本地 FFM scratch**：每个 Java 线程复用 native buffer 和 arena slot，避免重复分配
- **Java fallback**：native 不可用或未达阈值时自动回退到 Java 参考实现

## 架构速览

### 模块结构（共享核心 + 平台薄适配层）

- **`common`**：共享逻辑、命令、通用工具、Java fallback 和共享 mixin 配置
  - `alku.beryllium.Beryllium`：共享初始化入口，Fabric 和 NeoForge 都会调用
  - `alku.beryllium.command.BerylliumCommands`：注册 `/beryllium native` 和 `/beryllium distance` 命令
  - `alku.beryllium.compute.JavaComputeKernels`：Java 参考实现和 fallback
  - `alku.beryllium.bridge.NativeBridge`：native 后端的 Java 薄壳入口（FFM downcall）
  - `alku.beryllium.bridge.FfmNativeBridge`：线程本地 FFM session 和 buffer 复用层
  - `alku.beryllium.bridge.NativeLibraryLoader`：显式路径加载、jar 内 native 资源提取和 `System.loadLibrary` 回退
  - `common/src/main/resources/beryllium.mixins.json`：共享 mixin 配置，Fabric 与 NeoForge 都会加载

- **`fabric`**：Fabric 专属入口和客户端入口
  - `BerylliumFabric`：常规 mod 初始化，转调 `Beryllium.init()`
  - `BerylliumFabricClient`：Fabric 客户端专属逻辑
  - `fabric/src/main/resources/fabric.mod.json`：Fabric mod 描述文件

- **`neoforge`**：NeoForge 专属入口
  - `BerylliumNeoForge`：构造器转调 `Beryllium.init()`
  - `neoforge/src/main/resources/META-INF/neoforge.mods.toml`：NeoForge mod 描述文件

- **`native/`**：Rust 原生工作区（Cargo workspace）
  - `native/crates/beryllium-native/`：主 native 后端
    - `src/kernel.rs`：纯计算 kernel（距离计算、排序、Top-K）
    - `src/ffi.rs`：暴露稳定 C ABI 入口，供 Java FFM downcall 调用
    - `src/noise/`：噪声生成模块（Perlin、Simplex、OpenSimplex2）
    - `src/octave_noise.rs`：多倍频噪声批量采样
    - `src/biome.rs`：生物群系混合逻辑
    - `src/async_worker.rs`：异步工作线程池和优先级任务调度
  - `native/crates/beryllium-cubecl/`：CubeCL 预览后端（可选，默认不编译）

- **构建配置**
  - `build.gradle` / `settings.gradle` / `gradle.properties`：统一管理版本、Minecraft/loader 依赖、Java 21 目标和多模块构建
  - `rust-toolchain.toml`：Rust 稳定工具链配置（含 rustfmt 和 clippy）

### 关键架构原则

1. **分层边界**：Java 负责状态与副作用，Rust 负责纯计算。跨 FFI 边界优先使用批处理快照，不要把游戏对象直接暴露给 native。

2. **Native 加载顺序**：
   - `-Dberyllium.native.path=<绝对路径>` 显式指定的动态库
   - mod jar 中的 `assets/beryllium/native/<os>/<arch>/` 资源
   - `System.loadLibrary(“beryllium_native”)` 系统库路径
   - 全部失败时使用 Java fallback

3. **FFM 资源复用**：每个 Java 线程复用 FFM session、arena 和 native buffer；scratch 池按谓词重入深度分配独立项，避免嵌套查询覆盖外层数据。只传输当次数组的有效前缀，buffer 扩容或类型切换时释放旧 arena。

4. **性能阈值策略**：所有 native 路径都设置了基于实测的最小候选数阈值（见 Native 调优参数章节）。低于阈值或 native 不可用时自动回退到 Java/原版分支，保证小规模场景不承担 FFM 编组开销。

5. **打包与产物**：平台 jar 通过 Shadow + remap 流程完成；不要在 `common` 里直接假设最终产物是单独的 jar。Fabric 和 NeoForge 的 `build` 任务会额外验证最终 jar 中包含 native 动态库、native bridge 类和 mixin 配置。

## 常用命令

> **注意**：当前仓库没有提交 `gradlew` / `gradlew.bat`，下面默认使用系统安装的 `gradle`。如果以后补入 wrapper，把命令里的 `gradle` 换成 `./gradlew` 即可。构建需要 Java 21（推荐使用 GraalVM Community JDK 21）。

> **重要**：根据全局 CLAUDE.md 配置，所有命令应使用 **nushell** 执行。

### Gradle 构建命令

```bash
# 构建全部模块
gradle :common:build :fabric:build :neoforge:build

# 只构建特定平台
gradle :fabric:build
gradle :neoforge:build

# 运行客户端
gradle :fabric:runClient
gradle :neoforge:runClient

# 清理构建产物
gradle clean
```

### 验证与测试

```bash
# 运行完整验证（包含自定义 JavaExec 验证器）
gradle :common:check :fabric:check :neoforge:check

# 标准单元测试（当前已禁用，验证通过 JavaExec 任务完成）
gradle :common:test :fabric:test :neoforge:test

# 运行单个测试类
gradle :common:test --tests 'fully.qualified.TestClass'

# 性能基准测试（需要 native 后端）
gradle :common:performanceBenchmark
```

### Common 模块的专用验证任务

`:common:check` 会自动运行以下 JavaExec 验证器：

- **`javaParityTest`**：验证 Java fallback 与 native 后端的语义一致性
- **`nativeRuntimeTest`**：要求打包进 classpath 的 native 后端真实加载并执行所有 FFM 入口
- **`ffmReuseTest`**：验证线程本地 FFM buffer 复用和并发隔离
- **`inputLatencyStateTest`**：验证客户端输入刷新保持原版每 tick 动作语义

可以单独运行：
```bash
gradle :common:javaParityTest
gradle :common:nativeRuntimeTest
gradle :common:ffmReuseTest
gradle :common:inputLatencyStateTest
```

### Rust 原生库

```bash
# Rust 单元测试
cargo test --manifest-path native/Cargo.toml

# Release 构建
cargo build --manifest-path native/Cargo.toml --release

# 带 CubeCL 预览后端的构建（可选，默认不启用）
cargo build --manifest-path native/Cargo.toml --release --package beryllium-native --package beryllium-cubecl --features beryllium-native/cubecl-preview

# 性能基准测试（Criterion）
cargo bench --manifest-path native/Cargo.toml

# 快速性能测试工具
cargo run --manifest-path native/Cargo.toml --release --example quick_perf_test
```

### CubeCL 预览模式（可选）

CubeCL CPU/MLIR 后端固定为预览版 `0.11.0-pre.1`，LLVM sidecar 约 83 MB，默认不编译和打包。

启用预览模式：
```bash
# Rust 测试（包含 CubeCL）
cargo test --manifest-path native/Cargo.toml --workspace
cargo test --manifest-path native/Cargo.toml --package beryllium-native --features cubecl-preview

# Gradle 构建（包含 CubeCL sidecar）
gradle -PberylliumCubeclPreview=true :common:build :fabric:build :neoforge:build
```

**注意**：CubeCL/LLVM 预览 DLL 与 GraalVM 21 的加载初始化不兼容，但可由 OpenJDK 21 加载。Sidecar 加载失败只会禁用 CubeCL，主 native 后端继续正常工作。

## Native 调优参数

以下系统属性控制 native 路径的启用阈值，低于阈值时自动回退到 Java/原版分支：

- **`-Dberyllium.native.entityBatchThreshold=<正整数>`**  
  实体半径过滤和批量距离计算跨 FFM 的最小候选数，**默认禁用**（`2147483647`）。  
  默认路径按 Lithium 风格直接在 JVM 中单遍筛选并保留短路，避免 FFM 复制导致负优化。

- **`-Dberyllium.native.entityDistanceSortThreshold=<正整数>`**  
  实体距离排序跨 FFM 的最小候选数，**默认 256**。  
  完整排序在该规模已有端到端收益；小列表直接使用 JVM 稳定排序。

- **`-Dberyllium.native.blockDistanceBatchThreshold=<正整数>`**  
  方块距离排序跨 FFM 的最小候选数，**默认禁用**（`2147483647`）。  
  紧凑 `BlockPos` FFM 路径尚未表现出稳定的端到端收益。

- **`-Dberyllium.native.potentialBatchThreshold=<正整数>`**  
  PotentialCalculator 点电荷计算跨 FFM 的最小点电荷数，**默认 512**。  
  低于阈值时直接按原版顺序在 Java 中计算，避免小批量数组编组开销。

- **`-Dberyllium.native.chunkSendSelectionThreshold=<正整数>`**  
  PlayerChunkSender 最近 Top-K 跨 FFM 的最小待发送区块数，**默认 8192**。  
  Java 在每个线程内复用候选快照与输出索引，Rust 复用距离和选择 scratch buffer。

- **`-Dberyllium.native.nearestItemTopKThreshold=<正整数>`**  
  最近物品传感器先走 Rust Top-K 快路径的最小候选数，**默认 1024**。  
  快路径仅检查按原版距离/等距规则排列的最近 16 个严格半径内候选；未命中时继续完整排序。

### Rust 内部并行阈值

以下阈值硬编码在 Rust 代码中，避免小规模场景的调度开销：

- 通用批处理内核：从 **2048** 个候选开始并行
- 半径与 AABB 筛选：从 **16384** 个候选开始并行
- 最近方块查询：从 **65536** 个候选开始并行
- 最近物品 Top-K：从 **1048576** 个候选开始并行
- PlayerChunkSender 距离预计算：从 **32768** 个候选开始并行

使用 `/beryllium native` 命令可在游戏内查看当前阈值配置和 native 后端加载状态。

## 开发工作流建议

### 新增 Native Kernel

1. **先补 Java fallback**：在 `JavaComputeKernels.java` 中实现参考逻辑
2. **补 Rust 实现**：在 `kernel.rs` 中实现纯计算 kernel
3. **暴露 C ABI**：在 `ffi.rs` 中添加 `#[no_mangle] pub extern "C"` 函数
4. **添加 FFM 绑定**：在 `NativeBridge.java` 和 `FfmNativeBridge.java` 中添加 downcall
5. **补 parity 测试**：在 `BerylliumParityVerifier` 中验证 Java 与 native 结果一致性
6. **设置合理阈值**：基于性能基准测试结果设置默认阈值

### 修改共享逻辑

- 优先检查 `common` 模块
- 分别在 Fabric 和 NeoForge 里确认入口和打包是否仍然一致
- 运行完整验证：`gradle :common:check :fabric:check :neoforge:check`

### 版本升级

- 版本号、平台版本和依赖版本都集中在 `gradle.properties`
- 升级时先看这里再改各个平台文件
- Minecraft/Fabric/NeoForge 版本更新时需要同步更新 mixin target

### 性能测试

- 详细的性能基准说明和实测记录见 [`docs/performance-benchmark.md`](docs/performance-benchmark.md)
- 运行基准测试：`gradle :common:performanceBenchmark`
- 结果覆盖最近物品距离、最近实体、点电荷、方块距离内核和 PlayerChunkSender Top-K 阶段
- **注意**：基准测试结果是局部计算阶段的耗时对比，不能直接换算成整体 TPS 提升

## 重要提醒

- 这个仓库没有单独的 lint 任务定义；`check` 是当前统一验证入口
- 所有 FFM downcall 需要 JVM 参数 `--enable-native-access=ALL-UNNAMED`
- Native 库的构建由 Gradle 任务 `cargoBuildNative` 自动触发，无需手动运行 Cargo
- CubeCL 预览后端仅作为可选特性，默认不编译、不打包、不加载
