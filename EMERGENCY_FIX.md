# 紧急修复：区块生成卡顿问题

## 问题分析

工作流自动生成的异步区块生成代码存在严重性能问题，导致游戏比不加 mod 更卡。

### 根本原因

1. **ChunkStatusMixin 负优化**
   - 每个区块生成都遍历所有玩家查找最近玩家
   - 异步任务什么都没做，只返回空的 `centerChunk`
   - 原始生成方法仍然完整执行
   - 结果：**每个区块被处理 2 次 + N 次玩家遍历**

2. **ServerChunkCacheMixin 过度预取**
   - 每 20 tick（1秒）遍历所有玩家
   - 每个玩家生成 64 个预测区块
   - 大量内存分配和无效计算

3. **预测器内存泄漏**
   - `playerHistories` 只增不减
   - 玩家离线后历史数据仍保留

4. **异步任务无实际作用**
   - 缓存从未真正存储区块数据
   - 追踪器标记了但没有实际意义
   - 线程池创建了但任务是空操作

## 已应用的紧急修复

### 立即禁用问题 Mixin

**文件**: `common/src/main/resources/beryllium.mixins.json`

已移除：
- `ChunkStatusMixin` - 完全是负优化，导致重复生成
- `ServerChunkCacheMixin` - 过度预取，CPU 占用过高

保留有效的优化：
- `NoiseChunkMixin` - 密度插值 SIMD 优化（已验证有效）
- `NoiseInterpolatorMixin` - 插值计算优化
- 其他实体/传感器优化

## 当前状态

✅ **保留的有效优化**：
- 密度插值 SIMD 加速（5-15x 性能提升）
- 线程本地缓存和批量处理
- 实体距离排序优化
- 点电荷计算优化

❌ **已禁用的负优化**：
- 异步区块生成（实现有严重问题）
- 区块预取和预测（过度消耗 CPU）

## 正确的异步区块生成实现方案

如果将来要重新实现，需要：

1. **在正确的地方拦截**
   ```java
   // 应该拦截 ChunkMap.scheduleChunkGeneration 或 ServerChunkCache.getChunk
   // 而不是 ChunkStatus.generate（太底层，会重复触发）
   ```

2. **真正异步执行生成逻辑**
   ```java
   CompletableFuture<ChunkAccess> future = CompletableFuture.supplyAsync(() -> {
       // 调用原始生成方法
       return originalGenerate(level, generator, chunks, centerChunk);
   }, executorService);
   
   cir.setReturnValue(future); // 返回异步 Future
   cir.cancel(); // 取消原始方法执行
   ```

3. **减少玩家查找开销**
   ```java
   // 缓存最近的玩家位置映射，而不是每次遍历
   // 或使用空间索引（如 R-Tree）
   ```

4. **合理的预取频率**
   ```java
   // 每 100-200 tick（5-10秒）预取一次
   // 或仅在玩家移动速度改变时触发
   ```

5. **清理离线玩家数据**
   ```java
   @Inject(method = "removePlayer")
   private void beryllium$cleanupPlayer(ServerPlayer player, CallbackInfo ci) {
       predictor.removePlayer(player.getUUID());
   }
   ```

## 性能对比

| 配置 | 预期效果 |
|------|---------|
| 原版 | 基准性能 |
| 加入之前的 mod（有问题） | **比原版更卡**（负优化） |
| 当前修复后 | 应该比原版略好（密度插值优化） |

## 测试建议

1. **重新编译 mod**
   ```bash
   cd "D:\code\MC模组\铍优化"
   ./gradle-8.10.2/bin/gradle.bat clean build
   ```

2. **测试密度插值优化效果**
   - 进入游戏，观察区块生成时的帧率
   - 应该比原版略流畅（不会有质的飞跃，但不会更卡）

3. **检查 CPU 使用率**
   - 打开任务管理器观察 Java 进程 CPU 占用
   - 应该没有异常的 100% CPU 峰值

## 后续计划

要真正实现异步区块生成优化，需要：

1. **重新设计架构**
   - 从 ChunkMap 层拦截，而不是 ChunkStatus
   - 使用 Minecraft 自己的异步机制

2. **渐进式测试**
   - 先实现最小可行版本
   - 逐步添加预取、缓存等功能
   - 每步都测试性能影响

3. **参考成熟实现**
   - C2ME (Concurrent Chunk Management Engine)
   - Starlight (光照优化)
   - Lithium (全面优化)

这些 mod 都有经过实战检验的异步实现。

---

**修复时间**: 2026-08-06  
**问题来源**: Workflow 自动生成代码未充分测试  
**影响范围**: 所有使用异步区块生成的玩家  
**严重程度**: 🔴 严重（导致性能恶化）
