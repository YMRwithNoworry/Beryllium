# Beryllium

Beryllium 是一个基于 Architectury 的 Minecraft 多加载器性能模组，目标平台是 **Fabric** 和 **NeoForge**。

当前功能是提供一个 Rust 加速的批量计算后端，并只在有稳定端到端收益证据的距离排序、点电荷和区块 Top-K 场景接管原版路径。其余内核保留 Java 参考实现与显式调优入口，默认不跨 FFM。

Java 与 Rust 之间使用 Java 21 FFM downcall，不依赖 JNI、`jni` 或 `jni-sys`。

- Minecraft：`1.21.1`
- Java：`21`
- 构建系统：Gradle + Cargo
- 共享逻辑放在 `common`，平台入口分别放在 `fabric` 和 `neoforge`
- Rust 原生计算层放在 `native`

## 功能

- `/beryllium native`：显示 Rust native 后端加载状态。
- `/beryllium distance`：运行批量平方距离计算示例，优先使用 Rust native，native 不可用时自动回退到 Java 参考实现。
- Native distance sort：AI 实体距离排序达到实测阈值后使用 Rust；小列表继续执行 JVM 稳定排序，保持原版等距顺序。
- Native nearest-item Top-K：最近物品传感器在大候选集上先由 Rust 选择严格半径内稳定排序的最近小前缀；命中时避免完整排序，未命中时复用已打包坐标回退到完整排序，保持原版的距离、等距和谓词执行顺序。
- JVM AI 热点：玩家、诱惑、Breeze 和 Warden 传感器以有序循环替代中间 Stream；实体分区迭代和青蛙攻击目标判断采用 Lithium 1.21.1 的已验证方案。
- FFM scratch session：每个 Java 线程复用 native buffer 和 arena slot，降低重复 FFM 调用的分配成本；buffer 扩容或类型切换时释放旧 arena，避免长期累积 native 内存。
- PotentialCalculator 批处理：点电荷贡献在 Rust 中按索引计算，再沿原版顺序累加；小批量直接调用原版点电荷方法，不经过通用数据打包。
- PlayerChunkSender 区块批次：远程连接的大型待发送集合会把 packed chunk long 通过 FFM 交给 Rust 选择最近 Top-K；保留原版 Guava 的 signed-int 回绕距离、Quickselect tie 行为、FastUtil stream 顺序、空区块过滤和移除副作用。低于实测阈值或 native 不可用时执行原版分支。

## 目录结构

- `common`：共享初始化、命令注册、Java fallback、native bridge 和共享 mixin 配置
- `fabric`：Fabric 的 mod 入口与客户端入口
- `neoforge`：NeoForge 的 mod 入口与打包配置
- `native`：Rust 原生库工作区，包含稳定 C ABI 导出和批量计算 kernel

## 运行与构建

> 推荐使用 Java 21。当前仓库没有提交 `gradlew` / `gradlew.bat`，所以下面的命令默认使用系统安装的 `gradle`。

### 构建

```bash
gradle :common:build :fabric:build :neoforge:build
```

Fabric 和 NeoForge 的 `build` 会额外验证最终平台 jar 中包含 native 动态库、native bridge 类和 mixin 配置，避免产物意外退回 Java fallback。

### 运行 Fabric 客户端

```bash
gradle :fabric:runClient
```

### 运行 NeoForge 客户端

```bash
gradle :neoforge:runClient
```

### 测试

```bash
gradle :common:test :fabric:test :neoforge:test
```

当前 `:common:check` 会运行三个 JavaExec 验证器：`javaParityTest` 覆盖 Java fallback/native 语义一致性，`nativeRuntimeTest` 要求打包进 classpath 的 native 后端真实加载并执行所有 FFM 入口，`ffmReuseTest` 验证线程本地 FFM buffer 复用和并发隔离。

性能对比可运行 `gradle :common:performanceBenchmark`，测试说明和实测记录见 [`docs/performance-benchmark.md`](docs/performance-benchmark.md)。结果覆盖最近物品距离、最近实体、点电荷、方块距离内核和 PlayerChunkSender Top-K 阶段，不能直接换算成整体 TPS 提升。

### 运行单个测试

```bash
gradle :common:test --tests 'fully.qualified.TestClass'
```

如果测试在 `fabric` 或 `neoforge` 模块中，把模块名替换为对应子项目即可。

### Rust 原生库

```bash
cargo test --manifest-path native/Cargo.toml
cargo build --manifest-path native/Cargo.toml --release
```

## 构建与入口概览

- `common/src/main/java/alku/beryllium/Beryllium.java` 是共享初始化入口，Fabric 和 NeoForge 都会调用它。
- `common/src/main/java/alku/beryllium/command/BerylliumCommands.java` 注册 `/beryllium` 命令。
- `common/src/main/java/alku/beryllium/compute/JavaComputeKernels.java` 是 Java 参考实现和 fallback。
- `common/src/main/java/alku/beryllium/bridge/NativeBridge.java` 是 native 后端的 Java 薄壳入口。
- `common/src/main/java/alku/beryllium/bridge/NativeLibraryLoader.java` 负责 native 资源提取、显式路径加载和回退。
- `native/crates/beryllium-native/src/kernel.rs` 实现 Rust 批量计算 kernel。
- `native/crates/beryllium-native/src/ffi.rs` 暴露稳定 C ABI 入口，Java 侧通过 FFM downcall 调用。
- `fabric/src/main/java/alku/beryllium/fabric/BerylliumFabric.java` 是 Fabric 主入口。
- `fabric/src/main/java/alku/beryllium/fabric/client/BerylliumFabricClient.java` 是 Fabric 客户端入口。
- `neoforge/src/main/java/alku/beryllium/neoforge/BerylliumNeoForge.java` 是 NeoForge 主入口。
- `common/src/main/resources/beryllium.mixins.json` 是共享 mixin 配置，Fabric 与 NeoForge 都会加载它。

## Native 加载策略

`NativeLibraryLoader` 按以下顺序加载 Rust 动态库：

1. `-Dberyllium.native.path=<绝对路径>` 指定的动态库
2. mod jar 中的 `assets/beryllium/native/<os>/<arch>/` 资源
3. `System.loadLibrary("beryllium_native")`
4. 全部失败时使用 Java fallback

## Native 调优参数

- `-Dberyllium.native.entityBatchThreshold=<正整数>`：控制实体半径过滤和批量距离计算跨 FFM 的最小候选数，默认禁用（`2147483647`）。默认路径按 Lithium 风格直接在 JVM 中单遍筛选并保留短路，避免坐标、索引数组和 FFM 复制导致负优化；仅应在目标服务器实测后显式调低。
- `-Dberyllium.native.entityDistanceSortThreshold=<正整数>`：控制实体距离排序跨 FFM 的最小候选数，默认 `256`。排序仍保留 Rust 路径，因为完整排序在该规模已有端到端收益；较小的常见 AI 列表直接使用 JVM 稳定排序。
- `-Dberyllium.native.blockDistanceBatchThreshold=<正整数>`：控制方块距离排序跨 FFM 的最小候选数。默认禁用（`2147483647`），因为紧凑 `BlockPos` FFM 路径尚未表现出稳定的端到端收益；仅应在目标服务器实测后显式调低。`/beryllium native` 会显示当前阈值。
- `-Dberyllium.native.potentialBatchThreshold=<正整数>`：控制 PotentialCalculator 点电荷计算跨 FFM 的最小点电荷数，默认 `512`。低于阈值时直接按原版顺序在 Java 中计算，避免小批量数组编组开销；`/beryllium native` 会显示当前阈值。
- `-Dberyllium.native.chunkSendSelectionThreshold=<正整数>`：控制 PlayerChunkSender 最近 Top-K 跨 FFM 的最小待发送区块数，默认 `8192`。Rust 在每个调用线程内复用距离和选择 scratch buffer；低于阈值或 native 不可用时保留原版 Guava 路径。该保守阈值避开了复测中有波动的中等候选规模。
- `-Dberyllium.native.nearestItemTopKThreshold=<正整数>`：控制最近物品传感器先走 Rust Top-K 快路径的最小候选数，默认 `1024`。快路径仅检查按原版距离/等距规则排列的最近 `16` 个严格半径内候选；未命中时继续完整排序，避免改变谓词与结果语义。
- Rust Rayon 通用批处理内核从 `2048` 个候选开始并行，半径与 AABB 筛选从 `16384` 个候选开始，最近方块查询从 `65536` 个候选开始；最近物品 Top-K 从 `1048576` 个候选开始，PlayerChunkSender 的轻量距离预计算从 `32768` 个候选开始，避免调度开销抵消收益。
