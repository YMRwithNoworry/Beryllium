# 性能基准

该基准测最近物品传感器的距离阶段、直接最近实体、PotentialCalculator 点电荷阶段、方块距离内核，以及 PlayerChunkSender 最近 Top-K 阶段，不等同于整机 TPS 或帧率测试。

## 运行

```nu
$env.JAVA_HOME = 'D:/MC/jdk/graalvm-community-openjdk-21.0.2+13.1'
gradle --no-daemon :common:performanceBenchmark
```

默认配置为预热 `100` 次、测量 `300` 次；最近物品候选数量为 `256`、`1024`、`4096`、`8192`、`16384`，严格半径为 `32`；最近实体候选数量为 `32` 到 `8192`；PlayerChunkSender 候选数量为 `128`、`256`、`512`、`2048`、`4096`、`8192`、`16384`，批次配额为 `9` 和 `64`。该任务额外传入 `-Dberyllium.native.nearestItemTopKThreshold=1`，以便单独比较 Top-K 算法；正常运行仍使用默认阈值 `1024`。

每组使用相同的确定性坐标和 wanted/visible 谓词，输出中位耗时：

- `vanilla_java`：原版风格的 Java `List.sort` 加 Java 半径/谓词扫描。
- `legacy_native`：上一版 Beryllium 的 FFM 完整排序，再由 Java 重算距离和筛选半径。
- `fused_native`：一次 FFM 融合排序，返回完整索引顺序和严格半径前缀长度，再由 Java 执行谓词；FFM session 和 native buffer 在每个 Java 线程内复用。
- `allocating_top_k_native`：与当前 Top-K 语义相同的旧 Java 实现，每次重新分配 packed 坐标、Top-K 索引、完整排序索引和已评估标记，作为同进程分配对照。
- `top_k_native`：当前最近物品快路径。Rust 先在线性扫描中选择严格半径内最近 `16` 项，再由 Java 按完整排序会采用的顺序执行谓词；未命中才回退到 `fused_native` 的完整排序。
- `vanilla_chunk_send`：原版 `LongOpenHashSet.stream()`、`Long` 装箱和 Guava `Comparators.least` Top-K。
- `allocating_native_chunk_send`：旧生产路径，每次通过 primitive stream 分配候选快照与输出数组，再执行完整 FFM downcall 和 Rust Top-K。
- `reused_native_chunk_send`：当前生产路径，按 FastUtil stream 的桶顺序写入可重入线程 scratch，只将当次有效前缀传给 FFM；Java、FFM 和 Rust 三层 buffer 均按线程复用。

`speedup` 是中位耗时的比值，例如 `fused_speedup:2.00x` 表示当前路径耗时约为原版的一半。FFM、数组打包、排序和结果扫描均包含在测量区间内；世界实体查询、区块加载、其他 AI 传感器和 tick 调度不包含在内。

## 2026-08-01 JVM 实体路径与负优化清理

环境为 Windows x86_64、GraalVM Community JDK 21.0.2、Rust release native `OK`，预热 `100` 次、测量 `300` 次。最近实体原生索引在 `32` 到 `8192` 候选的本轮测量中仅为 Java 内核的 `0.29x` 到 `0.88x`；变量半径过滤为 `0.24x` 到 `0.82x`，AABB 过滤为 `0.14x` 到 `0.64x`。这些路径继续默认禁用 FFM。

旧 `EntityGetter` 覆盖会先构造过滤列表、打包 `double[]` 并分配命中索引；改成通用 JVM 单遍扫描后虽然相对 packed Java 快 `1.07x` 到 `3.59x`，部分规模仍慢于原版手写循环，因此该覆盖链已整体移除并恢复 Minecraft 原版查询。实体距离排序单独使用 `beryllium.native.entityDistanceSortThreshold=256`，因为同轮最近物品端到端排序在 `256` 候选已快于原版 `2.00x`，不再与无收益的半径过滤共用阈值。

同轮也再次发现旧 ChunkMap 覆盖在多数规模慢于原版，POI 覆盖在 native 默认禁用时仍会把原版流收集为列表。两者已从 Mixin 配置移除并恢复 Minecraft 1.21.1 原版逻辑；后续 POI 优化只会采用经验证的 Lithium 分区索引方案，不再保留数组批处理覆盖。实体分区查询则采用 Lithium 1.21.1 `alloc.entity_iteration` 的做法，直接遍历 `ClassInstanceMultiMap.allInstances`，避免其公开迭代器的快照分配。

## 2026-07-29 最近实体索引阈值复测

环境为 Windows x86_64、GraalVM Community JDK 21.0.2 和 Rust release native `OK`。运行三个独立 Gradle/JVM 进程，每组预热 `100` 次、测量 `300` 次；packed `double[]` 预先生成，测量完整 Java 内核或 FFM 调用，不包含实体谓词与坐标打包。

`32`、`64`、`128` 和 `512` 候选的三轮 Native 均慢于 Java；`256`、`1024`、`4096`、`8192` 至少有一轮慢于 Java，且跨轮波动明显，不能确定安全的默认交叉点。因此生产 `EntityGetter` 覆盖已在 2026-08-01 移除；Rust AVX2 内核仅保留给基准和底层校验，不再接管原版最近实体查询。

因此结果用于比较本次距离查询热点的算法开销，不能直接换算成“整体 TPS 提升百分比”。实际收益取决于候选数量、谓词命中率、CPU、JVM、实体密度和 Native 是否成功加载。

## 2026-07-19 最近物品 Top-K 内核实测

环境为 Windows x86_64、JDK 21.0.11、Rust release 动态库和 Java 21 FFM。每组预热 `200` 次、测量 `500` 次，坐标数组预先生成；FFM 数组传输和 native 输出复制包含在内。该表只比较 Rust Top-K 选择与完整 native 距离排序，不包含实体打包、Java 谓词或 Top-K 未命中后的回退。

| 候选数 | Top-K FFM 中位数 | 完整排序 FFM 中位数 | 完整排序/Top-K |
| ---: | ---: | ---: | ---: |
| 256 | 28,100 ns | 14,400 ns | 0.51x |
| 1,024 | 16,400 ns | 36,200 ns | 2.21x |
| 4,096 | 15,600 ns | 279,500 ns | 17.92x |
| 8,192 | 39,300 ns | 347,100 ns | 8.83x |
| 32,768 | 87,600 ns | 971,100 ns | 11.09x |
| 65,536 | 182,200 ns | 2,087,300 ns | 11.46x |
| 262,144 | 1,209,600 ns | 9,605,300 ns | 7.94x |

`256` 项的 Top-K 仍受 FFM 与有界堆开销影响而较慢，因此生产默认阈值设为 `1024`。Top-K 命中时避免完整 `O(n log n)` 排序；未命中时必须回退完整排序，以保持原版的谓词与结果语义。

## 2026-07-12 实测

环境为 Windows x86_64、JDK 21、Rust native `OK`；预热 `100` 次，测量 `300` 次。以下是池化 FFM bridge 后连续三次独立 Gradle 进程的中位数再取中位数，speedup 使用同一组纳秒中位数计算：

### 最近物品距离阶段

| 候选数 | 原版 Java | 完整排序 FFM | 当前融合 FFM | 相对原版 | 融合/完整排序 |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 256 | 51,700 | 60,600 | 30,600 | 1.69x | 1.98x |
| 1,024 | 239,200 | 42,200 | 42,100 | 5.68x | 1.00x |
| 4,096 | 701,200 | 325,400 | 342,000 | 2.05x | 0.95x |
| 8,192 | 1,592,200 | 500,700 | 521,800 | 3.05x | 0.96x |

池化后，融合路径在四个候选规模的三轮 speedup 中位数均高于 `1.0x`，`1,024` 候选达到 `5.68x`，`8,192` 候选达到 `3.05x`。这只反映排序、半径过滤、FFM 编组和 Java 结果扫描阶段；连续启动之间仍存在 JIT、GC 与系统调度波动，不能直接换算成整体 TPS。

### PotentialCalculator 点电荷阶段

同一轮运行额外测量 `8,192` 个点电荷的 Java 顺序参考与 Native 路径：

| 点电荷数 | Java 参考 | Native | 相对原版 |
| ---: | ---: | ---: | ---: |
| 8,192 | 132,400 ns | 167,500 ns | 0.79x |

Native 路径只并行计算每个点的独立贡献，最后按原始索引顺序累加，因此 benchmark 结果与 Java 参考保持相同的浮点求和顺序。三轮 speedup 为 `0.87x`、`0.98x`、`0.79x`，当前仍不能宣称点电荷稳定加速；这只代表单机局部计算阶段，不等同于整体 TPS。

### ChunkMap 刷怪水平距离阶段

| 玩家数 | 三轮 speedup 范围 | speedup 中位数 |
| ---: | ---: | ---: |
| 32 | 0.64x-0.92x | 0.64x |
| 128 | 0.61x-0.83x | 0.63x |
| 512 | 0.67x-1.13x | 0.73x |
| 2,048 | 0.61x-4.26x | 0.78x |
| 4,096 | 1.04x-1.11x | 1.06x |
| 8,192 | 0.90x-1.11x | 1.06x |

ChunkMap 默认路径刻意保留在 JVM 内执行：玩家对象需要先经过 spectator 资格判断，随后只做两次坐标差平方；把这些对象打包后跨 FFM 没有稳定收益。因此该路径不会强制跨 FFM，表中的波动也不能据此宣称 ChunkMap 或整体 TPS 提升。

## 2026-07-12 当前实现复测

本次为单个 Gradle 进程的一次复测，环境仍为 Windows x86_64、JDK 21、Rust native `OK`，预热 `100` 次、测量 `300` 次。该结果用于确认本次小批量 PotentialCalculator 路径改动后的当前基线，不替代上面的连续三次中位数记录。

### 最近物品距离阶段

| 候选数 | 原版 Java | 完整排序 FFM | 当前融合 FFM | 相对原版 | 融合/完整排序 |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 256 | 37,000 | 48,600 | 30,400 | 1.22x | 1.60x |
| 1,024 | 205,600 | 37,400 | 39,100 | 5.26x | 0.96x |
| 4,096 | 624,900 | 273,300 | 287,100 | 2.18x | 0.95x |
| 8,192 | 1,473,100 | 433,000 | 473,800 | 3.11x | 0.91x |

### PotentialCalculator 点电荷阶段

本次实现默认使用 `beryllium.native.potentialBatchThreshold=32`：小于 `32` 个点电荷直接按原版顺序在 Java 中计算，达到 `32` 个且 native 可用时才跨 FFM。`8,192` 个点电荷的单次中位数如下：

| 点电荷数 | Java 顺序参考 | Native FFM | 相对原版 |
| ---: | ---: | ---: | ---: |
| 8,192 | 118,100 ns | 124,200 ns | 0.95x |

因此点电荷阶段当前仍没有稳定加速证据；本次优化的主要收益是避免小批量的 primitive array 编组和 FFM 开销，同时保持原版提取顺序、零乘数短路和顺序浮点累加。

### ChunkMap 刷怪水平距离阶段

| 玩家数 | 原版 Java | Beryllium JVM 路径 | 相对原版 |
| ---: | ---: | ---: | ---: |
| 32 | 3,600 | 5,500 | 0.65x |
| 128 | 2,900 | 3,700 | 0.78x |
| 512 | 9,900 | 19,100 | 0.52x |
| 2,048 | 69,300 | 76,600 | 0.90x |
| 4,096 | 14,800 | 26,900 | 0.55x |
| 8,192 | 29,500 | 29,000 | 1.02x |

所有数字都是局部计算阶段的中位耗时，不能直接换算成整体 TPS、帧率或服务器 tick 提升百分比。

## 2026-07-13 FFM 干净构建复测

清除 Rust 构建缓存并确认依赖树不含 `jni`/`jni-sys` 后，使用同一环境重新构建，通过 Java parity、真实 FFM runtime、FFM 并发复用及双平台打包校验，再运行三个独立 Gradle/JVM 进程。下表先分别取三轮耗时中位数，再以同组中位数计算加速比。

| 最近物品候选数 | 原版 Java | 当前融合 FFM | 相对原版 | 耗时降低 |
| ---: | ---: | ---: | ---: | ---: |
| 256 | 39,200 ns | 37,100 ns | 1.06x | 5.4% |
| 1,024 | 243,700 ns | 42,100 ns | 5.79x | 82.7% |
| 4,096 | 749,100 ns | 358,300 ns | 2.09x | 52.2% |
| 8,192 | 1,608,700 ns | 550,700 ns | 2.92x | 65.8% |

`8,192` 个点电荷的三轮耗时中位数为 Java `136,600 ns`、FFM `134,300 ns`，即 `1.02x`。该差异很小，仍不足以证明 PotentialCalculator 获得稳定加速。ChunkMap 各规模结果跨轮波动明显，且部分规模慢于原版，也不作为加速结论。当前可重复的收益仅限于上表所测的大批量最近物品距离热点，不能据此推导整体 TPS 或 FPS 提升。

## 2026-07-13 PlayerChunkSender Top-K

本节使用最终真实分支形态：两条路径都从同一个 FastUtil `LongOpenHashSet` 开始。原版测量包含 boxed `stream` 和 Guava Top-K；Beryllium 测量包含与原版同顺序的 primitive stream 快照、输出数组、完整 FFM 调用和 Rust 选择。共同的 `ChunkMap.getChunkToSend`、null 过滤、发包和集合移除不在测量区间内。

在 Windows x86_64、JDK 21、Rust native `OK` 上运行三个独立 Gradle/JVM 进程，每组预热 `100` 次、测量 `300` 次。该轮保守地把 `4096` 候选作为默认阈值：

| 候选数 | 配额 | 原版三轮中位数 | FFM 三轮中位数 | 相对原版 | 耗时降低 | 三轮 speedup 范围 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 4,096 | 9 | 113,300 ns | 42,500 ns | 2.67x | 62.5% | 2.26x-4.11x |
| 4,096 | 64 | 105,000 ns | 34,500 ns | 3.04x | 67.1% | 1.50x-3.17x |

`2048` 候选、配额 `9` 的独立轮次曾出现 `0.93x`，所以当时默认阈值未设为 `2048`。该策略只声明局部选择阶段加速，不推导整体 TPS、区块发送吞吐或网络延迟提升。

## 2026-07-25 GraalVM PlayerChunkSender 阈值复测

环境为 Windows x86_64、GraalVM Community JDK 21.0.2、Rust release native `OK`。运行三个独立 Gradle/JVM 进程，每组预热 `100` 次、测量 `300` 次；测量继续包含 FastUtil primitive 快照、输出数组、FFM downcall 和 Rust Top-K，不包含共同的区块查找、空值过滤、发送与集合移除。

| 候选数 | 配额 | 原版三轮中位数 | Native 三轮中位数 | 相对原版 | 三轮 speedup 范围 |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 512 | 9 | 16,000 ns | 8,100 ns | 1.98x | 1.27x-2.31x |
| 512 | 64 | 22,300 ns | 9,400 ns | 2.37x | 1.89x-2.44x |
| 2,048 | 9 | 33,400 ns | 19,300 ns | 1.73x | 0.85x-1.81x |
| 2,048 | 64 | 54,800 ns | 30,300 ns | 1.81x | 1.11x-2.18x |

`512` 候选在两种配额的六组独立测量中均快于原版，因此默认 `beryllium.native.chunkSendSelectionThreshold` 从 `4096` 下调到 `512`。`128` 候选虽然也快于原版，但相对 primitive Java 路径存在波动，暂不继续下探；native 不可用或低于 `512` 时仍执行原版 Guava 分支。

同轮方块最近项端到端复测覆盖 `256` 到 `65,536` 候选，紧凑 `BlockPos.asLong()` native 路径的三轮中位数均未稳定快于原版，因此方块距离 native 默认保持禁用。PotentialCalculator 的缓存 native 路径从 `512` 个点电荷起三轮均快于 Java 参考，继续保留默认阈值 `512`。

## 2026-07-25 Rust ChunkSend scratch 复用

Rust ChunkSend Top-K 改为在每个调用线程内复用距离数组和选择 buffer，避免每次 FFM 调用重新分配完整 `Vec<i32>` 与候选 `Vec<usize>`。纯内核仍保留无状态入口；稳定 C ABI 使用线程本地 scratch，并通过 8 个并发线程各 50 次调用验证隔离。

在相同 Windows x86_64、GraalVM Community JDK 21.0.2、Rust release native `OK` 环境下，运行三个独立 Gradle/JVM 进程，每组预热 `100` 次、测量 `300` 次。下表列出阈值附近所有候选规模：

| 候选数 | 配额 | 原版三轮中位数 | Native 三轮中位数 | 相对原版 | 三轮 speedup 范围 |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 128 | 9 | 19,500 ns | 7,600 ns | 2.57x | 2.57x-2.84x |
| 128 | 64 | 24,600 ns | 6,100 ns | 4.03x | 2.66x-6.71x |
| 256 | 9 | 11,500 ns | 8,500 ns | 1.35x | 1.16x-2.22x |
| 256 | 64 | 40,500 ns | 10,200 ns | 3.97x | 3.56x-6.11x |
| 512 | 9 | 10,900 ns | 7,100 ns | 1.54x | 1.47x-3.94x |
| 512 | 64 | 23,400 ns | 11,500 ns | 2.03x | 1.39x-4.06x |

`128`、`256`、`512` 候选在两种配额的 18 组独立测量中全部快于原版，因此默认 `beryllium.native.chunkSendSelectionThreshold` 从 `512` 下调到 `128`。低于 `128` 或 native 不可用时继续执行原版 Guava 分支。

另行测试过让 Rust 直接回传 packed chunk long，以省去 Java 的索引间接访问；该方案在 `512` 候选、配额 `64` 下相对索引回传仅为 `0.61x-0.72x`，原因是 FFM 输出复制量翻倍，因此未进入生产实现。

## 2026-08-01 默认路径负优化审计

环境仍为 Windows x86_64、GraalVM Community JDK 21.0.2、Rust release native `OK`，每个 JVM 预热 `100` 次并测量 `300` 次。最近物品完整融合排序在 `256` 候选的复测中为原版 `2.87x` 和 `2.96x`，因此实体距离排序阈值继续保持 `256`。PotentialCalculator 缓存路径在 `512` 个点电荷为 `1.07x` 和 `1.33x`，在 `2048` 个点电荷为 `1.23x` 和 `1.36x`，继续保留默认阈值 `512`。

PlayerChunkSender 在本轮某些中等规模组合出现回退：`256` 候选、配额 `9` 为 `0.93x`，`4096` 候选、配额 `64` 为 `0.87x`。虽然其他独立 JVM 中这些组合快于原版，但默认覆盖不能建立在易波动的结果上，因此生产阈值从 `128` 收紧到 `8192`；本轮 `8192` 候选的四组配额复测均未慢于原版，范围为 `1.11x-1.68x`。

同轮最近实体、变量半径、AABB 和方块最近项 FFM 仍存在低于 `1.0x` 的规模，继续默认禁用。旧 EntityGetter、TargetingConditions、ChunkMap、POI、支撑方块和批量 EntitySection 覆盖均已移除；实体分区只保留 Lithium 1.21.1 的直接列表迭代器重定向，青蛙传感器只保留 Lithium 的廉价食物类型检查前置。小批量 PotentialCalculator 直接调用原版点电荷方法，玩家与诱惑传感器使用 JVM 循环消除多余 Stream/候选列表。

## 2026-08-01 最近物品 Java/FFM scratch 复用

最近物品 native 路径改为在每个 Java 调用线程内复用 packed 坐标、完整排序索引、Top-K 索引和已评估标记。scratch 池按谓词重入深度分配独立项，避免嵌套实体查询覆盖外层数据；Top-K 未命中时只清理最多 `16` 个实际标记，不再线性清零整个候选数组。FFM session 保留数组容量以便后续复用，但只将当次有效前缀传给 Rust 和复制回 Java。native 不可用或小于已实测阈值时仍走原有 JVM 分支。

在 Windows x86_64、GraalVM Community JDK 21.0.2、Rust release native `OK` 上运行三个独立 Gradle/JVM 进程，每组预热 `100` 次、测量 `300` 次。`allocating_top_k_native` 在同一进程内重现旧的每次分配路径，两条路径共享相同 Rust 内核和 FFM session，因此表格隔离的是 Java scratch 与有效前缀复用收益：

| 候选数 | 旧分配路径三轮中位数 | scratch 三轮中位数 | 相对旧路径 | 三轮 speedup 范围 |
| ---: | ---: | ---: | ---: | ---: |
| 256 | 21,000 ns | 17,700 ns | 1.19x | 1.18x-1.51x |
| 1,024 | 38,700 ns | 38,300 ns | 1.01x | 1.01x-1.21x |
| 4,096 | 68,400 ns | 37,500 ns | 1.82x | 1.49x-2.19x |
| 8,192 | 64,700 ns | 56,800 ns | 1.14x | 1.13x-1.30x |
| 16,384 | 143,200 ns | 123,600 ns | 1.16x | 1.06x-1.16x |

五个规模的三轮同进程对照均未低于 `1.0x`，因此复用逻辑进入默认 native 路径。`1,024` 候选的收益最小，故 Top-K 生产阈值不再下调；小规模和 native 不可用场景不承担 ThreadLocal/FFM 路径成本。

## 2026-08-01 PlayerChunkSender Java/FFM 有效前缀复用

PlayerChunkSender 的大批量 native 分支原本每次通过 `LongSet.longStream().toArray()` 分配 packed chunk 快照，并另外分配输出索引。新路径按调用线程复用两个 Java buffer，FFM 只复制当次有效前缀，Rust 继续复用已有的距离与选择 scratch。scratch 池按重入深度分配独立项，不会覆盖尚在执行的外层批次。

FastUtil 8.5.12 sources JAR 显示 `LongOpenHashSet.SetSpliterator` 会先输出 null key，再从桶 `0` 正序扫描到 `n - 1`。生产快照使用 `remap=false` shadow mixin 读取 `key` 与 `containsNull`，按相同规则直接写入 scratch，因此保留 boxed stream 的 encounter order 与等距 tie 行为。如果其他模组把 `pendingChunks` 替换为其他 `LongSet` 实现，`instanceof` 守卫会使该次调用回到原版 Guava 分支。

在 Windows x86_64、GraalVM Community JDK 21.0.2、Rust release native `OK` 上运行三个独立 Gradle/JVM 进程，每组预热 `100` 次、测量 `300` 次。`allocating_native_chunk_send` 与 `reused_native_chunk_send` 在同一进程中使用同一个确定性 `LongOpenHashSet`、FFM session 和 Rust 内核：

| 候选数 | 配额 | 原版三轮中位数 | 旧分配 native 中位数 | 复用 native 中位数 | 相对旧 native | 相对原版 | 三轮复用 speedup 范围 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 8,192 | 9 | 125,700 ns | 89,600 ns | 60,800 ns | 1.47x | 2.07x | 1.47x-1.54x |
| 8,192 | 64 | 157,900 ns | 100,000 ns | 66,800 ns | 1.50x | 2.36x | 1.31x-1.50x |
| 16,384 | 9 | 284,200 ns | 189,100 ns | 136,600 ns | 1.38x | 2.08x | 1.38x-1.48x |
| 16,384 | 64 | 280,000 ns | 196,100 ns | 146,300 ns | 1.34x | 1.91x | 1.34x-1.40x |

默认覆盖的四个组合在全部独立 JVM 中均快于旧分配 native 和原版。但 `256` 候选、配额 `9` 仍有独立轮次只达原版 `0.86x` 和 `0.91x`，所以生产 `beryllium.native.chunkSendSelectionThreshold` 严格保持 `8192`，不因大批量复用收益下调。

## 2026-08-02 默认热点 FFM 精确调用

旧 FFM 调度层在每次 downcall 时通过 varargs、`ArrayList` 和 `MethodHandle.invokeWithArguments` 展开参数。新路径在 native 初始化时只为已实测且默认启用的区块 Top-K、Potential 缓存、实体距离排序、最近物品融合排序与 Top-K 预适配 `Object` 地址载体和 primitive 参数签名，调用时直接使用 `invokeExact`。默认禁用或尚无稳定收益的实体过滤、AABB 和方块距离 ABI 继续使用旧通用调用，不承担该改动的风险。

在 Windows x86_64、GraalVM Community JDK 21.0.2、Rust release native `OK` 上，分别运行三个旧调度层 JVM 和三个精确调用 JVM；每组预热 `100` 次、测量 `300` 次。下表先取各自三轮中位数，再计算精确调用相对旧调度层的收益；最后一列是精确调用在三个 JVM 中相对原版 Java/Guava 的完整路径范围：

| 路径 | 规模 | 旧通用调用中位数 | 精确调用中位数 | 相对旧调用 | 精确调用相对原版范围 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Potential 缓存 | 512 | 10,700 ns | 8,600 ns | 1.24x | 1.27x-1.54x |
| Potential 缓存 | 2,048 | 34,500 ns | 30,700 ns | 1.12x | 1.35x-1.44x |
| Potential 缓存 | 8,192 | 128,600 ns | 110,700 ns | 1.16x | 1.50x-1.54x |
| ChunkSend Top-K | 8,192 / 配额 9 | 111,200 ns | 87,600 ns | 1.27x | 1.73x-2.06x |
| ChunkSend Top-K | 8,192 / 配额 64 | 115,400 ns | 97,400 ns | 1.18x | 1.73x-2.09x |
| ChunkSend Top-K | 16,384 / 配额 9 | 217,500 ns | 176,100 ns | 1.24x | 1.72x-2.08x |
| ChunkSend Top-K | 16,384 / 配额 64 | 232,600 ns | 188,200 ns | 1.24x | 1.70x-1.98x |

最近物品默认覆盖的 `1,024`、`4,096`、`8,192`、`16,384` 候选在精确调用三轮中相对原版分别为 `18.15x-22.77x`、`20.43x-23.05x`、`19.74x-25.20x`、`21.33x-25.24x`。`256` 候选仍有一轮 Top-K 仅为融合排序的 `0.86x`，Potential `32/128` 也没有稳定快于 Java，因此生产阈值继续严格保持最近物品 `1024`、Potential `512`、区块发送 `8192`，不根据调用层固定成本下降而下调。

## 2026-08-02 CubeCL 预览后端

使用 CubeCL `0.11.0-pre.1` 的 CPU/MLIR JIT 后端测试缓存 PotentialCalculator。环境为 Windows x86_64、Intel i5-1135G7（4 核 8 线程）、Rust/Cargo `1.97.1` release；kernel 使用 `4 x f64` 向量，输出回主机后严格按原索引顺序累加，因此与 AVX2 fallback 保持位级一致。

`32,768` 个点电荷的三次独立进程中，一轮在校准阶段被 `2x` 安全门拒绝；另外两轮校准返回后，持续测量的 CubeCL 中位数分别为 `669,100 ns`、`658,400 ns`，AVX2 为 `312,400 ns`、`313,300 ns`，CubeCL 仅为 `0.467x`、`0.476x`。该规模不能接管。

`65,536` 个点电荷的三次独立进程均通过安全门，校准耗时为 `189-202 ms`，校准后 41 次测量如下：

| 轮次 | CubeCL | AVX2 | 加速比 |
|---:|---:|---:|---:|
| 1 | 289,800 ns | 701,900 ns | 2.422x |
| 2 | 267,600 ns | 769,300 ns | 2.875x |
| 3 | 306,100 ns | 702,200 ns | 2.294x |

完整 Java 21 FFM 联调进一步发现，`65,536` 在 OpenJDK 进程内被安全门拒绝，而 `262,144` 能完成校准并进入 CubeCL 状态 `2`，接管后的结果与主 AVX2 参考逐位一致。因此最终最低校准规模提高到 `262,144`；更小缓存完全不启动 JIT，避免后台校准本身成为负优化。

CubeCL/LLVM 静态链接产物约 `83.3 MB`，并且在 GraalVM Community 21 中会发生 DLL 初始化失败，但同一产物可由 Microsoft OpenJDK 21 加载。最终实现将其拆成独立 `beryllium_cubecl` sidecar：默认 Windows `beryllium_native.dll` 仍为 `644,608` 字节，显式预览主库约 `718 KB`；GraalVM 下 sidecar 加载失败只会禁用 CubeCL，真实 FFM 复用、缓存 Potential 和主 AVX2 后端继续通过。OpenJDK 21 的预览 Gradle 联调则验证 sidecar 状态达到 `2` 并执行了接管后的逐位对照。

因此 CubeCL 只作为显式 `cubecl-preview` Cargo/Gradle 特性提供，默认构建不编译、不打包也不加载 sidecar。预览模式前台在异步加载和后台校准期间继续使用 AVX2；最终以 41 次交替采样、`2x` 中位数门槛和 CubeCL `P90 <= AVX2 P10` 尾延迟门槛共同决定是否接管，任何取消、panic、错误、位级不一致或速度不足都会保守禁用。

### 最终默认路径回归

切回不带 CubeCL sidecar 的默认 JAR 后，使用 GraalVM Community JDK 21.0.2 运行三个独立 Gradle/JVM 进程，每组预热 `100` 次、测量 `300` 次。只提取生产阈值及以上的路径，三轮相对原版范围如下：

| 默认生产路径 | 规模 | 三轮 speedup 范围 |
|---|---:|---:|
| 最近物品 Top-K | 1,024 | 4.40x-7.69x |
| 最近物品 Top-K | 4,096 | 25.13x-26.40x |
| 最近物品 Top-K | 8,192 | 23.22x-25.83x |
| 缓存 Potential | 512 | 1.65x-1.68x |
| 缓存 Potential | 2,048 | 1.44x-1.49x |
| 缓存 Potential | 8,192 | 1.48x-1.54x |
| 区块发送 Top-K，配额 9 | 8,192 | 1.59x-2.41x |
| 区块发送 Top-K，配额 64 | 8,192 | 1.21x-2.15x |

所有默认接管组合在三次独立 JVM 中均快于原版。基准仍会展示默认禁用的最近实体、实体过滤、AABB 和方块距离实验入口，其中低于 `1.0x` 的结果不代表生产负优化；这些路径不会被默认调用。
