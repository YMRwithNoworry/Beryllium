# 最近物品融合距离排序与半径前缀设计

## 状态

- 日期：2026-07-10
- 状态：待书面审阅
- 目标调用方：`EntityDistanceSort.findFirstSortedByDistanceWithinExclusiveDistanceAfterPredicate`
- 直接受益功能：`NearestItemSensorMixin`

## 背景

最近物品传感器先按与生物的平方距离稳定排序候选物品，再按排序结果逐个执行：

1. `beforeDistancePredicate`，即 `Mob.wantsToPickUp`；
2. 严格半径判断 `distanceSquared < radiusSquared`；
3. `afterDistancePredicate`，即 `Mob.hasLineOfSight`；
4. 在第一个通过全部条件的候选处短路。

当前大批量路径先通过 Rust 返回完整距离顺序，随后 Java 为每个已执行前置谓词的候选重新计算打包坐标的平方距离。排序阶段已经计算过同一个距离，因此这次重算是可避免的。

现有 `sortWithinRadiusExclusive` 不能替代完整排序：它会丢弃半径外候选，进而跳过这些候选本应触发的 `beforeDistancePredicate`。返回一份排序后的距离数组虽然可以消除重算，但会增加一个 `double[N]` FFM buffer 和 Java 分配。

## 目标

新增一个融合内核，在一次成功的 FFM downcall 中：

- 每个候选的平方距离只计算一次；
- 向现有 `int[N]` 输出缓冲区写入全部候选的稳定排序索引；
- 返回排序结果中满足严格半径条件的连续前缀长度；
- 不返回额外距离数组；
- 保持原版候选顺序、谓词调用顺序、调用次数、边界、短路和副作用。

## 非目标

- 不改变 `NearestItemSensorMixin` 的搜索范围、谓词或记忆写入。
- 不改变小批量 Java 路径；本切片只优化已进入原生批处理的调用。
- 不修改或移除现有 `sortByDistance`、`sortWithinRadiusExclusive`，其他调用方继续使用原接口。
- 不把 Java 谓词移动到 Rust，也不跨 FFM 调用游戏对象。
- 不在本切片引入新的批处理阈值或性能配置。

## 接口设计

### Java 参考内核

在 `JavaComputeKernels` 增加：

```java
public static int sortByDistanceAndCountWithinRadiusExclusive(
    double originX,
    double originY,
    double originZ,
    double radiusSquared,
    double[] positions,
    int[] output
)
```

`output` 的前 `N = positions.length / 3` 项始终包含全部候选的排序索引，返回值 `prefixCount` 始终位于 `[0, N]`。该方法既是无原生库时的实现，也是 FFM 失败时的语义基准。参考内核同样先为每个索引缓存一次平方距离，再对缓存结果排序并统计前缀，不能在比较器或前缀扫描中重算距离。

### 原生桥接

在 `NativeBridge` 增加同签名的公开方法，并增加私有 FFM bridge 委托：

```java
private static native int sortByDistanceAndCountWithinRadiusExclusiveDoubleNative(
    double originX,
    double originY,
    double originZ,
    double radiusSquared,
    double[] positions,
    int[] output
);
```

公开桥接方法沿用现有校验规则：坐标必须是完整的 x/y/z 三元组，`output` 至少能容纳 `N` 个索引，负 `radiusSquared` 非法。未加载原生库时直接运行 Java 参考内核。

FFM 成功时写回全部 `N` 个排序索引并返回前缀长度。FFM 状态错误以负数返回。Java 还防御性检查 `prefixCount > N`；任何负数或越界计数都必须运行 Java 参考内核，并覆盖完整输出缓冲区。计数判定与回退集中在一个包可见辅助方法中，以便用合成的错误计数直接验证错误路径。

### Rust 内核

在 `beryllium-native` 增加一个返回 `Result<usize, NativeError>` 的 f64 内核。输入校验遵循现有距离排序内核；输出必须至少容纳 `N` 个索引。内核只写 `output[0..N]`，额外尾部容量保持不变。

内核构造 `Vec<(i32, f64)>`：每项只计算一次平方距离，并保留原始索引。候选数达到现有 `PARALLEL_THRESHOLD = 4096` 时，距离计算和排序继续使用 Rayon；否则使用串行路径。排序继续调用现有 `compare_distance_order_f64`，距离相等时按原始索引升序，确保并行与串行结果一致。

排序后，内核把全部索引写入 `output[0..N]`，再从已缓存距离的开头统计连续满足 `distance < radiusSquared` 的项数。该扫描不重新读取坐标或计算距离。

## 数据流

大批量最近物品搜索按以下顺序执行：

1. Java 按原列表顺序打包全部候选坐标，保持当前坐标 getter 的调用行为。
2. Java 分配一个 `int[N] order`。
3. `NativeBridge` 通过一次 FFM downcall 取得完整 `order` 和 `prefixCount`。
4. Java 从 `cursor = 0` 开始遍历完整 `order`。
5. Java 始终先对当前候选执行 `beforeDistancePredicate`。
6. 仅当该谓词为真且 `cursor < prefixCount` 时执行 `afterDistancePredicate`。
7. 第一个通过两个谓词的候选立即返回；没有候选通过时遍历完整顺序并返回空。

Java 消费逻辑等价于：

```java
for (int cursor = 0; cursor < order.length; cursor++) {
    T value = values.get(order[cursor]);
    if (beforeDistancePredicate.test(value)
        && cursor < prefixCount
        && afterDistancePredicate.test(value)) {
        return Optional.of(value);
    }
}
```

`cursor < prefixCount` 取代 Java 的距离重算，但不能用于提前结束循环，因为半径外候选仍必须执行前置谓词。

## 语义不变量

### 排序与边界

- `output[0..N]` 是 `0..N` 的完整排列，半径外索引也必须存在。
- 排序键为平方距离，距离相同时按原始索引升序，等价于稳定保留输入顺序。
- 半径判断严格使用 `<`，距离恰好等于 `radiusSquared` 的候选不属于前缀。
- `prefixCount` 只描述排序游标范围，不改变完整排序内容。

### 谓词与短路

- `beforeDistancePredicate` 仍按完整距离顺序调用，包括半径外候选。
- 当前候选的前置谓词返回 `false` 时，不调用其后置谓词。
- `afterDistancePredicate` 只可能对前缀内且前置谓词返回 `true` 的候选调用。
- 搜索在与当前实现相同的第一个候选处短路；没有结果时，前置谓词仍覆盖完整排序。
- Rust 和 FFM bridge 不持有或调用 Minecraft 对象，不改变任何 Java 侧副作用位置。

### 浮点值

- 负 `radius` 继续由 `EntityDistanceSort` 拒绝；桥接层也拒绝负 `radiusSquared`。
- `NaN` 半径不属于负值，所有 `< NaN` 比较均为假，因此 `prefixCount = 0`。
- `+Infinity` 半径包含所有有限距离，但不包含等于 `+Infinity` 的距离或 `NaN` 距离。
- 坐标产生的 `NaN` 和无穷距离沿用现有 Java/Rust 排序规则；`NaN` 排在非 `NaN` 后，最终以原始索引打破平局。
- `+0.0` 与 `-0.0` 坐标产生相等的零平方距离，并按原始索引保持稳定顺序；`-0.0` 半径平方不匹配任何零距离。

## 错误处理

- Java 参数错误在进入 FFM 前抛出 `IllegalArgumentException`，与现有桥接方法一致。
- Rust 对三元组长度和输出容量错误返回 `NativeError`，C ABI 将其编码为负计数。
- FFM bridge 只在内核成功后写回完整排序输出；native buffer 错误返回负计数。
- Java 对任何负计数或大于 `N` 的计数执行参考内核。参考内核会覆盖 `output[0..N]`，因此调用方不会消费部分或不可信的原生结果。
- 合法的 `0` 是“没有候选位于严格半径内”，不是错误。

## 测试设计

实现按测试先行顺序增加以下覆盖。

### Rust 单元测试

- 输出包含半径外候选，且仍是完整稳定排序。
- 空输入、前缀为零、部分前缀、全部前缀。
- 精确边界被排除。
- 相等距离按原始索引排序。
- `NaN` 半径返回零前缀。
- 有限值、`Infinity`、`NaN` 坐标距离的排序和前缀。
- `+0.0`/`-0.0` 坐标保持稳定顺序。
- 至少一个超过 4096 项的用例验证 Rayon 路径与串行参考顺序、前缀一致。
- 非三元组输入和不足输出容量返回对应错误。

### Java 参考与桥接测试

- Java 参考内核返回完整排序与正确前缀，覆盖空、零、部分、全部和边界。
- Java 参考内核覆盖相等距离、`NaN`、无穷和有符号零。
- 未加载原生库时，`NativeBridge` 结果与 Java 参考内核一致。
- 原生运行时对同一输入返回与 Java 参考相同的完整顺序和前缀。
- 包可见回退辅助方法分别接收负计数和 `N + 1`，验证它重新执行 Java 内核、返回合法计数并覆盖完整输出。

### 调用方行为测试

- 半径外候选仍触发 `beforeDistancePredicate`，顺序和次数不变。
- `afterDistancePredicate` 只对前缀内且通过前置谓词的候选调用。
- 精确边界候选不触发后置谓词。
- 相等距离时谓词调用保持输入顺序。
- 命中时在与旧实现相同的候选处短路；未命中时前置谓词遍历全部候选。
- 大批量用例强制走融合原生路径，并由 Java-only 与 native-runtime 两个验证入口共同执行。

## 验证命令

实现完成后运行：

```text
cargo fmt --manifest-path native/Cargo.toml --check
cargo test --manifest-path native/Cargo.toml
JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot' '/c/tmp/gradle-8121/gradle-8.12.1/bin/gradle.bat' :common:check :fabric:check :neoforge:check :fabric:build :neoforge:build
git diff --check
git rev-list --left-right --count origin/master...master
```

最终远端同步结果必须为 `0 0`。

## 实施范围

后续实现计划预计只触及：

- `common/src/main/java/alku/beryllium/compute/JavaComputeKernels.java`
- `common/src/main/java/alku/beryllium/bridge/NativeBridge.java`
- `common/src/main/java/alku/beryllium/compute/EntityDistanceSort.java`
- Java parity/native-runtime 验证文件及必要的桥接包测试辅助文件
- `native/crates/beryllium-native/src/kernel.rs`
- `native/crates/beryllium-native/src/ffi.rs`
- `native/crates/beryllium-native/src/lib.rs`

不需要修改 Fabric 或 NeoForge 平台专用业务逻辑。
