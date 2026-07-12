# Beryllium

Beryllium 是一个基于 Architectury 的 Minecraft 多加载器性能模组，目标平台是 **Fabric** 和 **NeoForge**。

当前功能是提供一个 Rust 加速的批量坐标、包围盒和点电荷计算后端，并把 Minecraft 的实体查询、最近玩家/最近实体查询和 PotentialCalculator 接到批处理计算上，通过游戏内命令暴露验证入口。

- Minecraft：`1.21.1`
- Java：`21`
- 构建系统：Gradle + Cargo
- 共享逻辑放在 `common`，平台入口分别放在 `fabric` 和 `neoforge`
- Rust 原生计算层放在 `native`

## 功能

- `/beryllium native`：显示 Rust native 后端加载状态。
- `/beryllium distance`：运行批量平方距离计算示例，优先使用 Rust native，native 不可用时自动回退到 Java 参考实现。
- 近邻查询优化：`EntityGetter` 的最近玩家/最近实体默认逻辑会在候选集足够大时走 Rust batch 距离计算，并保留原版判定条件。
- `EntityGetter.getPlayerByUUID`：服务端会优先走 `PlayerList` 的 UUID map，再回退到原版列表扫描。
- Native filter/sort kernel：半径过滤和按距离排序在大批量输入下使用 Rayon 并行路径，并通过 Java fallback/parity verifier 覆盖原版语义。
- Native nearest-index kernel：最近玩家、存活玩家检测、无源 TargetingConditions 查询，以及不需要逐实体可见度距离修正的 TargetingConditions 最近实体查询可直接在 Rust 中返回最近候选索引，减少 Java 侧整批距离数组扫描。
- TargetingConditions 批量过滤：不需要隐身可见度修正的固定范围查询会直接走 native double 半径过滤，保留需要逐实体可见度计算的原逻辑。
- Native AABB filter：附近玩家查询会把玩家坐标批量传入 Rust 执行 AABB contains 过滤，空源和有源 TargetingConditions 路径共用批量 AABB helper，并保持原版 `min <= value < max` 边界语义。
- Native entity-section intersection：底层 `EntitySection` 的实体包围盒相交查询会在候选数足够大时批量传入 Rust 过滤，再按原顺序调用原版 consumer，保留 abort 行为和 `AABB.intersects` 边界语义。
- FFM scratch session：每个 Java 线程复用 native buffer 和 arena slot，降低重复 FFM 调用的分配成本；buffer 扩容或类型切换时释放旧 arena，避免长期累积 native 内存。
- PotentialCalculator 批处理：点电荷贡献在 Rust 中按索引并行计算，再沿原版顺序累加，避免并行归约改变浮点结果；小批量继续使用 Java 参考路径。
- ChunkMap 刷怪距离：保留 `DistanceManager.hasPlayersNearby`、spectator 资格判断、严格水平半径和玩家迭代顺序；默认直接在 JVM 内短路扫描，避免这个小型标量热点承担 FFM 编组开销。

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

当前 `:common:check` 会运行两个 JavaExec 验证器：`javaParityTest` 覆盖 Java fallback/native 语义一致性，`nativeRuntimeTest` 要求打包进 classpath 的 native 后端真实加载并执行所有 FFM 入口。

性能对比可运行 `gradle :common:performanceBenchmark`，测试说明和一次实测记录见 [`docs/performance-benchmark.md`](docs/performance-benchmark.md)。结果分别覆盖最近物品距离、点电荷和 ChunkMap 水平距离阶段，不能直接换算成整体 TPS 提升。

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

- `-Dberyllium.native.entityBatchThreshold=<正整数>`：控制实体批处理跨 FFM 的最小候选数，默认 `32`。数值越低越激进，数值越高越保守。`/beryllium native` 会显示当前阈值。
- `-Dberyllium.native.potentialBatchThreshold=<正整数>`：控制 PotentialCalculator 点电荷计算跨 FFM 的最小点电荷数，默认 `32`。低于阈值时直接按原版顺序在 Java 中计算，避免小批量数组编组开销；`/beryllium native` 会显示当前阈值。
- Rust Rayon 并行内核默认从 `4096` 个坐标/包围盒/charge 开始使用；该阈值固定在 native kernel 内，用于抵消并行调度与 FFM 编组开销。
