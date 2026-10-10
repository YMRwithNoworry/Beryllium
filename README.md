# Beryllium

Beryllium 是一个基于 Architectury 的 Minecraft 多加载器性能模组，目标平台是 **Fabric** 和 **NeoForge**。

当前功能是提供一个 Rust 加速的批量计算后端，并只在有稳定端到端收益证据的距离排序、点电荷、区块 Top-K 和原版噪声批量采样场景接管原版路径。其余内核保留 Java 参考实现与显式调优入口，默认不跨 FFM。

Java 与 Rust 之间使用 Java 21 FFM downcall，不依赖 JNI、`jni` 或 `jni-sys`。

- Minecraft：`1.21.1`
- Java：`21`
- Rust：`1.97.1`（Rust 2024 edition）
- 构建系统：Gradle + Cargo
- 共享逻辑放在 `common`，平台入口分别放在 `fabric` 和 `neoforge`
- Rust 原生计算层放在 `native`

## 功能

- `/beryllium native`：显示 Rust native 后端加载状态。
- `/beryllium distance`：运行批量平方距离计算示例，优先使用 Rust native，native 不可用时自动回退到 Java 参考实现。
- Native distance sort：AI 实体距离排序达到实测阈值后使用 Rust；小列表继续执行 JVM 稳定排序，保持原版等距顺序。
- Native nearest-item Top-K：最近物品传感器在大候选集上先由 Rust 选择严格半径内稳定排序的最近小前缀；命中时避免完整排序，未命中时复用已打包坐标回退到完整排序。Java 侧以可重入的线程本地 scratch 复用坐标、排序索引和 Top-K 标记，保持原版的距离、等距和谓词执行顺序。
- JVM AI 热点：玩家、诱惑、Breeze 和 Warden 传感器以有序循环替代中间 Stream；实体分区迭代和青蛙攻击目标判断采用 Lithium 1.21.1 的已验证方案。
- FFM scratch session：每个 Java 线程复用 native buffer 和 arena slot，并只传输当次数组的有效前缀；已实测的默认热点预适配精确 MethodHandle 签名，避免每次 downcall 构造通用参数容器。buffer 扩容或类型切换时释放旧 arena，避免长期累积 native 内存。
- PotentialCalculator 批处理：点电荷贡献在 Rust 中按索引计算，再沿原版顺序累加；小批量直接调用原版点电荷方法，不经过通用数据打包。
- PlayerChunkSender 区块批次：远程连接的大型待发送集合会按 FastUtil stream 的哈希桶遇到顺序写入可重入 Java scratch，再通过 FFM 交给 Rust 选择最近 Top-K；保持原版 Guava 的 signed-int 回绕距离、Quickselect tie 行为、空区块过滤和移除副作用。低于实测阈值、native 不可用或集合实现被其他模组替换时执行原版分支。
- Minecraft NormalNoise 批处理：`DensityFunctions.Noise.fillArray` 在达到实测阈值后将坐标批量交给 Rust，复用已注册的原版 octave、origin 和 permutation 状态；结果与 Java 原版逐位一致。多 octave 默认阈值为 `128`，单 octave 为 `512`，小数组和 native 不可用时保留原版路径，避免 FFM 固定成本造成卡顿。
- Minecraft BlendedNoise 批处理：主世界、下界和末地的基础三维密度噪声一次注册 `40` 个原版 `ImprovedNoise` 状态，`fillArray` 只传批量方块坐标；Rust 保持原版 wrap、Y-smear、分支跳过和浮点顺序。默认从 `49` 个样本接管：Rust 侧按工作量（样本数 × octave 开销）触发 Rayon 并行，`49` 点切片实测约 `2x`，`128` 点以上约 `3x`；`33` 点及以下串行档不占优，保留原版路径。
- PalettedContainer 网络打包（默认禁用）：Rust 内核可逐字节复刻区块包中 `PalettedContainer.Data.write` 的 `bits 字节 + palette varint + 大端 long 数组` 格式，覆盖 SingleValue/Linear/HashMap/Global 四种 palette 并有逐字节 parity 校验；但原版 `writeLongArray` 经 JIT 后已按约 `2ns/long` 直写 netty 缓冲，FFM 路径多出输入/输出两次数组拷贝与一次 `writeBytes`，实测各形态均为 `0.51x-0.96x`，因此默认保持原版路径。

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

当前 `:common:check` 会运行 JavaExec 验证器：`javaParityTest` 覆盖 Java fallback/native 语义一致性，`nativeRuntimeTest` 要求打包进 classpath 的 native 后端真实加载并执行所有 FFM 入口，`ffmReuseTest` 验证线程本地 FFM buffer 复用和并发隔离，`inputLatencyStateTest` 验证输入时序和重复处理门控，`mixinCompatibilityTest` 验证 VulkanMod、Lithium、Quark 检测及全部八种组合的 Mixin 加载策略。

安装 VulkanMod 时，Beryllium 会在 Mixin 应用前自动跳过整组客户端输入延迟优化，避免干扰 VulkanMod 对 `Minecraft.runTick` 的注入和局部变量捕获。键鼠输入由原版及 VulkanMod 处理，世界生成、实体计算和 Rust 原生优化继续启用；未安装 VulkanMod 时保留 Beryllium 输入优化。启动日志会输出兼容模式提示。

NeoForge 移植版 VulkanMod 首次启动若停在 `VulkanTransformationService is Loading...`，检查是否有要求重启的提示窗口。该版本会将 `config/fml.toml` 的 `earlyWindowControl` 改为 `false` 后等待确认并退出；关闭提示并重新启动实例即可绕过该分支。这发生在模组发现阶段，不能靠 Beryllium 的 Mixin 兼容开关解决。

安装 Lithium 时，Beryllium 自动跳过重复的 `FrogAttackablesSensorMixin`，避免双方重定向同一个调用而导致启动失败。该青蛙目标筛选优化由 Lithium 提供。VulkanMod 应与 Sodium、Sodium Extra 和 Ixeris 分开使用；在 NeoVulkanMod 0.5.5-dev+3.1 的实例中已确认 Sodium Extra 的垂直同步注入冲突及 Ixeris 的模块重复导出错误。

安装 Quark 时，Beryllium 自动跳过 `TemptingSensorMixin`，保留原版诱惑传感器中 Quark 喂食槽所需的 `Stream.collect` 注入点，避免覆盖该方法导致注入失败或喂食槽失效。其他优化继续启用；未安装 Quark 时保留诱惑传感器优化。启动日志会输出 Quark 兼容模式提示。

区块发送批处理通过 FastUtil 的公开 primitive spliterator 复制到复用缓冲，保持原版流的遇见顺序（包括零键和等距离目标），不再向上层类加载器中的 `LongOpenHashSet` 注入模组接口，避免 NeoForge 启动时的 `LongOpenHashSetAccess` 类加载失败。

同一 NeoVulkanMod 实例若在窗口创建时直接退出且 `latest.log` 没有异常，应检查实例目录的 `hs_err_pid*.log`。ImmediatelyFast 1.6.14 的 `windowInit()` 调用 OpenGL `glGetString` 已确认会在该 Vulkan 窗口下触发 `lwjgl_opengl.dll` 原生崩溃，应禁用 ImmediatelyFast。

Photon 2.2.7 要求 OpenGL 3.2，会在此 Vulkan 实例中触发 NeoForge feature bounds 验证失败；Accelerated Rendering 1.0.14 则在 `AvailabilityUtils.isAvailable()` 调用 `GL.createCapabilities()` 时因没有 OpenGL 上下文而失败。使用该 NeoVulkanMod 版本时也应禁用这两个模组。

LDLib2 2.2.41 的 `LDLibShaders.init()` 在客户端初始化阶段直接调用 OpenGL `glCreateShader`，在该实例中已触发原生崩溃。SPD 和 KilaGraph 强制依赖 LDLib2，因此保留这组模组时应使用原版/Sodium 渲染；继续使用此 NeoVulkanMod 版本则需要同时禁用 LDLib2 及其依赖方。上述问题来自其他模组的 OpenGL 初始化，Beryllium 的兼容模式无法替它们提供 OpenGL 上下文。

性能对比可运行 `gradle :common:performanceBenchmark`；NormalNoise 和 BlendedNoise 专项分别可运行 `gradle :common:normalNoisePerformanceBenchmark`、`gradle :common:blendedNoisePerformanceBenchmark`。测试说明和实测记录见 [`docs/performance-benchmark.md`](docs/performance-benchmark.md)。结果仅表示对应局部计算路径，不能直接换算成整体 TPS 提升。

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

根目录 `rust-toolchain.toml` 使用最小化 `stable` 工具链，并安装 `rustfmt` 与 `clippy`；当前验证版本为 Rust/Cargo `1.97.1`，native crate 使用 Rust 2024 edition。执行 `rustup update stable` 后，进入仓库会自动使用最新稳定版。

CubeCL CPU/MLIR 后端固定为预览版 `0.11.0-pre.1`。LLVM sidecar 约 `83 MB`，且中等批量存在明显性能波动，因此不会进入默认产物；主 `beryllium_native` 始终保持轻量，sidecar 加载失败也不会禁用 AVX2。只有明确接受预览成本时才启用：

```bash
cargo test --manifest-path native/Cargo.toml --workspace
cargo test --manifest-path native/Cargo.toml --package beryllium-native --features cubecl-preview
cargo build --manifest-path native/Cargo.toml --release --package beryllium-native --package beryllium-cubecl --features beryllium-native/cubecl-preview
gradle -PberylliumCubeclPreview=true :common:build :fabric:build :neoforge:build
```

预览后端也不会直接接管计算：仅对至少 `262,144` 个已缓存点电荷异步加载 sidecar 并启动单一后台校准线程，逐次验证结果位级一致。41 次交替测量不仅要求中位数至少快 `2x`，还要求 CubeCL `P90` 不慢于 AVX2 `P10`。校准中、取消、异常、结果不一致或未达到安全门时始终使用原 AVX2 路径；一次性 Potential 调用从不承担 CubeCL 上传和 JIT 成本。当前 CubeCL/LLVM 预览 DLL 与 GraalVM 21 的加载初始化不兼容，该情况会被隔离为 sidecar 不可用，主 native 后端继续正常工作；OpenJDK 21 已验证可加载并接管。

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
- `-Dberyllium.native.chunkSendSelectionThreshold=<正整数>`：控制 PlayerChunkSender 最近 Top-K 跨 FFM 的最小待发送区块数，默认 `8192`。Java 在每个调用线程内复用候选快照与输出索引，Rust 复用距离和选择 scratch buffer；低于阈值或 native 不可用时保留原版 Guava 路径。该保守阈值避开了复测中有波动的中等候选规模。
- `-Dberyllium.native.nearestItemTopKThreshold=<正整数>`：控制最近物品传感器先走 Rust Top-K 快路径的最小候选数，默认 `1024`。快路径仅检查按原版距离/等距规则排列的最近 `16` 个严格半径内候选；未命中时继续完整排序，避免改变谓词与结果语义。
- `-Dberyllium.native.normalNoiseBatchThreshold=<正整数>`：控制包含多个有效 octave 的原版 NormalNoise 批量采样跨 FFM 的最小样本数，默认 `128`。
- `-Dberyllium.native.singleOctaveNormalNoiseBatchThreshold=<正整数>`：控制只有一个有效 octave 的 NormalNoise 批量采样跨 FFM 的最小样本数，默认 `512`。单 octave 每样本执行两遍 Perlin，`512` 样本即达到 Rust 侧工作量并行档（HotSpot 实测约 `1.8x`）；更小批次保留 Java 路径；零有效 octave 永不进入 native。
- `-Dberyllium.native.blendedNoiseBatchThreshold=<正整数>`：控制原版 BlendedNoise 跨 FFM 的最小样本数，默认 `49`。Rust 内核按工作量（样本数 × 每 octave 成本，BlendedNoise 每样本约 `24`）从 `1024` 工作单位起 Rayon 并行：`49` 点切片实测约 `2x`、`128/512` 点约 `3x`；`33` 点以下串行档在 HotSpot 上约 `0.87x`，保留原版路径。
- `-Dberyllium.native.palettedContainerPackThreshold=<正整数>`：控制区块包 PalettedContainer 序列化跨 FFM 的最小 packed long 数，默认 `Integer.MAX_VALUE`（禁用）。该路径保持逐字节 parity 校验可用，但实测 `0.51x-0.96x` 无收益，仅在未来 JVM 或 FFM 语义变化时作为实验开关。
- Rust Rayon 通用批处理内核从 `2048` 个候选开始并行，半径与 AABB 筛选从 `16384` 个候选开始，最近方块查询从 `65536` 个候选开始；最近物品 Top-K 从 `1048576` 个候选开始，PlayerChunkSender 的轻量距离预计算从 `32768` 个候选开始，避免调度开销抵消收益。
