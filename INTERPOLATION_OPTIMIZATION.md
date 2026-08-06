# 密度插值计算优化

## 优化内容

### 1. Rust 端优化 (kernel.rs)

#### 1.1 SIMD 加速
- 在 `interpolate_single_cell_simd` 中使用 AVX2 指令集
- 每次处理 4 个 f64 元素，利用 `_mm256_*` 系列指令
- Z 轴插值使用向量化计算，减少标量运算次数

#### 1.2 更激进的并行策略
- 降低并行阈值从无限制到 128 个 interpolators (`INTERPOLATION_PARALLEL_THRESHOLD`)
- 使用 Rayon 并行处理多个 cell
- 小批量也能受益于多核处理

#### 1.3 多层次优化路径
```rust
if interpolator_count >= 128 {
    // 大批量：Rayon 并行处理
    par_chunks_exact_mut().for_each(...)
} else if has_avx2() && interpolator_count >= 4 {
    // 中等批量：SIMD 优化
    interpolate_single_cell_simd(...)
} else {
    // 小批量：标量路径
    interpolate_single_cell(...)
}
```

### 2. Java 端优化 (NoiseChunkMixin.java)

#### 2.1 整个 Slab 批量处理
- **之前**：逐个 cell 调用 native 插值
- **现在**：一次性处理整个 slab 的所有 cells（通常 128-512 个）
- 减少 JNI 调用开销，提高数据局部性

#### 2.2 线程本地 Slab 缓存
- 新增 `SlabCache` 类缓存插值结果
- 使用 `cacheKey` 检测相同的 slab（基于 interpolationCounter 和位置）
- 避免重复计算相同的 cell corners
- 缓存验证包括：
  - cacheKey 匹配
  - interpolatorCount、cellCountY、cellCountXZ 一致
  - cellWidth、cellHeight 一致

#### 2.3 优化的缓存键计算
```java
long cacheKey = this.interpolationCounter ^ ((long) this.cellStartBlockX << 32);
```
- 混合时间和空间信息
- 快速哈希计算
- 检测缓存失效

## 性能提升

### 理论分析

#### SIMD 收益
- Z 轴插值：4x 理论加速（每次处理 4 个元素）
- 实际收益：考虑尾部处理和内存带宽，预计 2-3x

#### 并行收益
- 多核处理：N 核可达到 N-1x 加速（考虑线程开销）
- 典型场景（4-8 核）：3-6x 加速

#### 批量处理收益
- JNI 调用减少：从 128+ 次降到 1 次
- 数据局部性：连续内存访问，缓存友好
- 预计：1.5-2x 整体加速

#### 缓存收益
- 缓存命中时：跳过整个插值计算
- 适用场景：相同区块重复访问（地形预览、光照更新等）
- 命中率 10-30% 时：额外 10-40% 提升

### 综合预期
**总体提升：5-15x**
- 最佳情况（大批量 + 多核 + SIMD + 缓存命中）：~15x
- 典型情况（中等批量 + 4核 + SIMD）：~8x
- 最坏情况（小批量 + 单核）：~2x（仅 SIMD 和批量处理）

## 实现细节

### Rust SIMD 实现
```rust
// 使用 AVX2 一次处理 4 个 z 坐标
let v_z_indices = _mm256_set_pd(
    (z_base + 3) as f64,
    (z_base + 2) as f64,
    (z_base + 1) as f64,
    z_base as f64,
);
let v_delta_z = _mm256_div_pd(v_z_indices, v_width);
let v_diff = _mm256_sub_pd(v_z1, v_z0);
let v_scaled = _mm256_mul_pd(v_delta_z, v_diff);
let v_result = _mm256_add_pd(v_z0, v_scaled);
```

### Java Slab 缓存
```java
SlabCache cache = BERYLLIUM$SLAB_CACHE.get();
if (cache.isValid(cacheKey, ...)) {
    // 缓存命中，直接使用
    this.beryllium$nativeSlabReady = true;
    return;
}
// 缓存未命中，执行插值并更新缓存
NativeBridge.tryInterpolateDensityCells(...);
cache.update(cacheKey, ...);
```

## 兼容性

- **CPU 要求**：AVX2（2013+ Intel/AMD）
- **回退机制**：非 AVX2 CPU 自动使用标量路径
- **内存**：缓存增加约 128KB-2MB 线程本地内存
- **线程安全**：使用 ThreadLocal，无竞争

## 测试

### 单元测试
```bash
cd native
cargo test interpolate_density
```

### 基准测试
```bash
cd native
cargo bench --bench interpolation_bench
```

### 游戏内测试
1. 构建模组：`gradle :fabric:build` 或 `gradle :neoforge:build`
2. 进入创造模式，飞行浏览新区块
3. 使用 F3 观察 FPS 和世界生成时间
4. 预期：区块生成速度提升 30-50%

## 后续优化方向

1. **GPU 加速**：使用 CubeCL 在 GPU 上执行大批量插值
2. **预取优化**：提前计算下一个 slab 的 corners
3. **压缩缓存**：使用差分编码减少缓存内存占用
4. **自适应阈值**：根据 CPU 核心数动态调整并行阈值
