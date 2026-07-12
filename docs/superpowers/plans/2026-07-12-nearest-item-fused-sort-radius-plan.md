# 最近物品融合排序与半径前缀实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development for every production change. Execute the steps task-by-task and keep the Java fallback, FFM bridge, Rust kernel, and both verifier entry points synchronized.

**Goal:** 在保持最近物品搜索排序、半径边界、谓词时序和短路语义不变的前提下，用一次 Rust C ABI + Java FFM downcall 返回完整稳定距离排序和严格半径前缀长度，消除大批量路径的 Java 距离重算。

**Architecture:** `JavaComputeKernels` 先实现缓存平方距离的 Java 参考内核；`NativeBridge` 校验输入、通过 FFM 调用 C ABI 并对错误/越界计数回退到参考内核；Rust `kernel.rs` 在一次距离计算后排序并统计前缀，`ffi.rs` 只传递 primitive buffer 指针、长度和一个计数。`EntityDistanceSort` 只在大批量分支使用融合 API，继续在 Java 按完整排序调用前置谓词，并只对前缀内候选调用后置谓词。

**Tech Stack:** Java 21 FFM preview、Architectury Loom、Rust 2021 稳定 C ABI、Rayon、现有 JavaExec parity/native-runtime verifier。

---

## 文件职责

- `common/src/main/java/alku/beryllium/compute/JavaComputeKernels.java`：保存距离一次计算后的 Java 参考排序和严格前缀计数。
- `common/src/main/java/alku/beryllium/bridge/NativeBridge.java`：暴露融合 API、执行输入校验、处理 FFM 负数/越界计数并回退。
- `common/src/main/java/alku/beryllium/compute/EntityDistanceSort.java`：接入最近物品大批量路径，保持谓词调用与短路顺序。
- `common/src/test/java/alku/beryllium/compute/EntityDistanceSortVerifier.java`：验证调用方行为、前后置谓词和短路。
- `common/src/test/java/alku/beryllium/verify/BerylliumParityVerifier.java`：验证 Java kernel 与 NativeBridge 的公共契约。
- `common/src/test/java/alku/beryllium/verify/BerylliumNativeRuntimeVerifier.java`：在真实 DLL 加载时执行融合 FFM 路径。
- `native/crates/beryllium-native/src/kernel.rs`：实现 f64 融合内核、特殊浮点值和 Rayon 大批量测试。
- `native/crates/beryllium-native/src/ffi.rs`：实现稳定 C ABI 导出、指针切片和负计数错误编码。
- `native/crates/beryllium-native/src/lib.rs`：重新导出新增 C ABI 函数和 Rust kernel。

## Task 1: Java 参考契约 RED

**Files:**

- Modify: `common/src/test/java/alku/beryllium/verify/BerylliumParityVerifier.java`
- Modify: `common/src/test/java/alku/beryllium/compute/EntityDistanceSortVerifier.java`
- Modify: `common/src/test/java/alku/beryllium/compute/JavaComputeKernels.java` only after RED is observed

- [ ] **Step 1: Add the failing Java kernel contract test.**

Add a `verifyJavaDoubleFusedSortAndRadiusPrefix` verifier call and test method that passes positions `{2, 0, 0, 1, 0, 0, -1, 0, 0, 4, 0, 0}`, radius squared `4.0`, and a prefilled output buffer. Assert the complete stable order `{1, 2, 0, 3}` and prefix count `3`; assert the sentinel after the required output remains unchanged when the output has extra capacity.

Add separate assertions for an exact-boundary point, `Double.NaN` radius returning prefix `0`, `Double.POSITIVE_INFINITY` radius, and equal-distance order. Call the wished-for `JavaComputeKernels.sortByDistanceAndCountWithinRadiusExclusive` API so the failure identifies the missing method.

- [ ] **Step 2: Run the Java parity verifier and confirm the expected RED.**

Run:

```bash
export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.11.10-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
"/c/tmp/gradle-8121/gradle-8.12.1/bin/gradle.bat" --no-daemon :common:javaParityTest
```

Expected: compilation fails because `JavaComputeKernels.sortByDistanceAndCountWithinRadiusExclusive` does not exist. Do not add production code before this failure is observed.

- [ ] **Step 3: Commit and push the RED test.**

```bash
git add common/src/test/java/alku/beryllium/verify/BerylliumParityVerifier.java common/src/test/java/alku/beryllium/compute/EntityDistanceSortVerifier.java
git commit -m '-（增加融合距离排序Java契约测试）'
git push origin master
```

## Task 2: Java reference kernel GREEN

**Files:**

- Modify: `common/src/main/java/alku/beryllium/compute/JavaComputeKernels.java`
- Test: `common/src/test/java/alku/beryllium/verify/BerylliumParityVerifier.java`

- [ ] **Step 1: Implement the minimal cached-distance reference kernel.**

Add the public output-buffer method with the exact signature:

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

Validate positions, reject only `radiusSquared < 0.0`, require output capacity for `N`, fill an `Integer[]`/distance pair array by computing each squared distance once, sort by `Double.compare(distance)` and original index, copy all `N` indices to `output`, then count the sorted prefix with strict `< radiusSquared`. Preserve Java NaN ordering and `-0.0` comparison behavior through `Double.compare` and Java `<`.

- [ ] **Step 2: Run Java parity and confirm GREEN for the new reference tests.**

Run the command from Task 1. Expected: the new fused Java contract passes and all existing Java parity checks remain green.

- [ ] **Step 3: Commit and push the Java reference slice.**

```bash
git add common/src/main/java/alku/beryllium/compute/JavaComputeKernels.java common/src/test/java/alku/beryllium/verify/BerylliumParityVerifier.java
git commit -m '-（实现融合距离排序Java回退内核）'
git push origin master
```

## Task 3: Native bridge contract RED/GREEN

**Files:**

- Modify: `common/src/main/java/alku/beryllium/bridge/NativeBridge.java`
- Modify: `common/src/test/java/alku/beryllium/verify/BerylliumParityVerifier.java`
- Modify: `common/src/test/java/alku/beryllium/verify/BerylliumNativeRuntimeVerifier.java`
- Modify: `native/crates/beryllium-native/src/ffi.rs` only after the Java bridge RED is observed
- Modify: `native/crates/beryllium-native/src/lib.rs` only with the C ABI implementation

- [ ] **Step 1: Add the bridge wishes and fallback assertions.**

Add a public `NativeBridge.sortByDistanceAndCountWithinRadiusExclusive` output-buffer method with the same arguments as the Java kernel. Add parity assertions for native-unavailable fallback, negative native count, and count greater than `N`; the latter two must prove the bridge overwrites the entire valid output prefix with Java reference results. Add native-runtime assertions for full output, strict boundary, tie order, and unchanged extra output capacity.

Use the existing `NativeBridge.isLoaded()` behavior and do not expose test-only mutable native state. A package-visible helper may be used for count validation if needed, but the public method must retain the normal loaded/unloaded fallback contract.

- [ ] **Step 2: Run the bridge verifier before adding FFM and confirm RED.**

Run:

```bash
export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.11.10-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
"/c/tmp/gradle-8121/gradle-8.12.1/bin/gradle.bat" --no-daemon :common:javaParityTest
```

Expected: compilation fails because the wished-for FFM bridge method is not present, or runtime reports the missing C ABI symbol once the Java bridge is present. This RED establishes that the bridge tests exercise a new contract.

- [ ] **Step 3: Implement Java bridge and FFM fallback handling.**

Add the private FFM delegate `sortByDistanceAndCountWithinRadiusExclusiveDoubleNative`. Validate input before FFM. If native is unavailable, call the Java reference method. If FFM returns `< 0` or `> N`, call the Java reference method; otherwise return the native count after the full output has been written.

- [ ] **Step 4: Keep bridge tests GREEN with a temporary Java-side error-path seam only if required.**

Run the parity verifier. If direct synthetic error counts cannot be injected without production-only state, test the shared count-validation/fallback helper with package-local verifier access and keep the FFM method free of test hooks. Remove any temporary seam that is not needed by runtime behavior.

- [ ] **Step 5: Commit and push the bridge slice.**

```bash
git add common/src/main/java/alku/beryllium/bridge/NativeBridge.java common/src/test/java/alku/beryllium/verify/BerylliumParityVerifier.java common/src/test/java/alku/beryllium/verify/BerylliumNativeRuntimeVerifier.java
git commit -m '-（增加融合距离排序Native桥接契约）'
git push origin master
```

## Task 4: Rust kernel and FFM GREEN

**Files:**

- Modify: `native/crates/beryllium-native/src/kernel.rs`
- Modify: `native/crates/beryllium-native/src/ffi.rs`
- Modify: `native/crates/beryllium-native/src/lib.rs`
- Modify: `common/src/test/java/alku/beryllium/verify/BerylliumNativeRuntimeVerifier.java`

- [ ] **Step 1: Add Rust unit tests before implementation.**

Add tests for complete output plus prefix, exact boundary exclusion, empty/no-match/partial/all-match, equal-distance index order, NaN radius, infinity and NaN coordinates, signed zero, output capacity errors, and a `5000`-position Rayon-path reference comparison. Call the wished-for kernel function so `cargo test` fails to compile.

- [ ] **Step 2: Run `cargo test` and confirm RED.**

```bash
cargo test --manifest-path native/Cargo.toml
```

Expected: compile failure naming the missing fused kernel function.

- [ ] **Step 3: Implement the Rust kernel.**

Add `sort_by_distance_and_count_within_radius_f64_exclusive` returning `Result<usize, NativeError>`. Validate non-negative radius, triple-aligned positions, and output capacity. Build indexed `(i32, f64)` distances once using the existing `PARALLEL_THRESHOLD`; sort with `compare_distance_order_f64`, write all indices, and count the initial entries whose cached distance is `< radius_squared`. Never recompute distance during sorting or prefix counting.

- [ ] **Step 4: Implement the C ABI entry point and exports.**

Add `beryllium_sort_by_distance_and_count_within_radius_exclusive_double`, accept primitive buffer pointers and lengths, call the Rust kernel, write `output[..position_count]`, return the prefix count, and encode all errors with the FFM count contract. Re-export the C ABI function and kernel from `lib.rs`.

- [ ] **Step 5: Run Rust tests and the native runtime verifier.**

```bash
cargo fmt --manifest-path native/Cargo.toml --check
cargo test --manifest-path native/Cargo.toml
export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.11.10-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
"/c/tmp/gradle-8121/gradle-8.12.1/bin/gradle.bat" --no-daemon :common:nativeRuntimeTest
```

Expected: all Rust tests and Java native-runtime assertions pass, including the complete output and prefix contract.

- [ ] **Step 6: Commit and push the Rust/FFM slice.**

```bash
git add native/crates/beryllium-native/src/kernel.rs native/crates/beryllium-native/src/ffi.rs native/crates/beryllium-native/src/lib.rs common/src/test/java/alku/beryllium/verify/BerylliumNativeRuntimeVerifier.java
git commit -m '-（实现融合距离排序Rust内核与FFM）'
git push origin master
```

## Task 5: Integrate nearest-item behavior RED/GREEN

**Files:**

- Modify: `common/src/main/java/alku/beryllium/compute/EntityDistanceSort.java`
- Modify: `common/src/test/java/alku/beryllium/compute/EntityDistanceSortVerifier.java`
- Modify: `common/src/test/java/alku/beryllium/verify/BerylliumParityVerifier.java`
- Modify: `common/src/test/java/alku/beryllium/verify/BerylliumNativeRuntimeVerifier.java`

- [ ] **Step 1: Add failing behavior tests.**

Add a package-local verifier call for this exact wished-for helper signature:

```java
static <T> Optional<T> findFirstBySortedOrderWithinPrefix(
    List<? extends T> values,
    int[] order,
    int prefixCount,
    Predicate<? super T> beforeDistancePredicate,
    Predicate<? super T> afterDistancePredicate
)
```

Exercise it with an order containing inside-radius, exact-boundary, and outside-radius candidates. Assert that the before predicate receives every sorted candidate, the after predicate receives only prefix entries after before-predicate acceptance, equal-distance ties remain in the supplied order, and a successful after predicate stops both logs at the same candidate as the existing implementation. Also add a large-list integration case so the native branch is selected when the native runtime is available.

- [ ] **Step 2: Run the verifier and confirm RED for the new fused-path expectation.**

Run both `:common:javaParityTest` and `:common:nativeRuntimeTest`. The new helper test must fail at test compilation because the package-local helper does not exist; existing semantic tests may remain green. This RED is specific to the new Java consumption boundary, while the direct bridge contract tests cover the native/fallback result boundary.

- [ ] **Step 3: Replace only the large-batch distance/order section.**

Add the package-local helper with the signature from Step 1. It must validate `prefixCount` is within `0..order.length`, iterate the supplied order exactly once, call the before predicate before checking the prefix, call the after predicate only for accepted prefix entries, and short-circuit on the first match. In `findFirstSortedByDistanceWithinExclusiveDistanceAfterPredicate`, pack all original candidates once, allocate `int[] order`, call the new bridge method once, then delegate to this helper. Keep the existing small-batch path unchanged.

- [ ] **Step 4: Run both verifier entry points and inspect predicate logs.**

```bash
export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.11.10-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
"/c/tmp/gradle-8121/gradle-8.12.1/bin/gradle.bat" --no-daemon :common:javaParityTest :common:nativeRuntimeTest
```

Expected: all old and new predicate-order, boundary, tie, and short-circuit assertions pass through Java fallback and real FFM paths.

- [ ] **Step 5: Commit and push the integration slice.**

```bash
git add common/src/main/java/alku/beryllium/compute/EntityDistanceSort.java common/src/test/java/alku/beryllium/compute/EntityDistanceSortVerifier.java common/src/test/java/alku/beryllium/verify/BerylliumParityVerifier.java common/src/test/java/alku/beryllium/verify/BerylliumNativeRuntimeVerifier.java
git commit -m '-（接入最近物品融合排序半径前缀）'
git push origin master
```

## Task 6: Full verification and platform artifacts

**Files:**

- Modify only when required by failures: the files from Tasks 1-5
- Preserve: `CLAUDE.md` user changes and `modrinth-description.md` untracked state

- [ ] **Step 1: Run format, Rust tests, Java checks, and both platform builds.**

```bash
cargo fmt --manifest-path native/Cargo.toml --check
cargo test --manifest-path native/Cargo.toml
export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.11.10-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
"/c/tmp/gradle-8121/gradle-8.12.1/bin/gradle.bat" --no-daemon :common:check :fabric:check :neoforge:check :fabric:build :neoforge:build
git diff --check
```

Expected: every command exits `0`; both remapped jars pass `verifyNativePackage` and contain the new bridge class/native DLL/mixin resources.

- [ ] **Step 2: Audit the diff and behavior against the design.**

Check that the output is a full permutation, prefix uses strict `<`, no Java distance array was added, only one FFM downcall is used by the large path, predicates remain Java-side, and fallback handles negative/out-of-range native counts. Search for `TODO`, `TBD`, accidental debug output, and changes outside the listed files.

- [ ] **Step 3: Commit and push any final fixes.**

```bash
git add common/src/main/java/alku/beryllium/compute/JavaComputeKernels.java common/src/main/java/alku/beryllium/bridge/NativeBridge.java common/src/main/java/alku/beryllium/compute/EntityDistanceSort.java common/src/test/java/alku/beryllium/compute/EntityDistanceSortVerifier.java common/src/test/java/alku/beryllium/verify/BerylliumParityVerifier.java common/src/test/java/alku/beryllium/verify/BerylliumNativeRuntimeVerifier.java common/src/test/java/alku/beryllium/bridge/NativeBridgeVerifier.java native/crates/beryllium-native/src/kernel.rs native/crates/beryllium-native/src/ffi.rs native/crates/beryllium-native/src/lib.rs
git commit -m '-（完成最近物品融合内核全量验证）'
git push origin master
```

- [ ] **Step 4: Verify remote synchronization and artifacts.**

```bash
git fetch origin master
git rev-list --left-right --count origin/master...master
find fabric/build/libs neoforge/build/libs -maxdepth 1 -type f -name '*.jar' -print
```

Expected: divergence is `0 0`, both platform jars exist, and the working tree contains only the pre-existing user-owned `CLAUDE.md` modification and `modrinth-description.md` if no final feature changes remain unstaged.
