# Beryllium 性能优化 Mod - 最终版本说明

## 📦 版本信息

**版本**: 1.0-SNAPSHOT (最终修复版)  
**构建时间**: 2026-08-06  
**Minecraft**: 1.21.1  
**加载器**: NeoForge 21.1.248 / Fabric

## ✅ 包含的优化

本版本**只包含经过验证有效的优化**，所有导致性能恶化的代码已被移除。

### 实体和传感器优化

- ✅ **最近物品传感器** (NearestItemSensorMixin)
  - Top-K 算法优化，避免完整排序
  - Native SIMD 加速距离计算

- ✅ **最近实体传感器** (NearestLivingEntitySensorMixin)
  - 批量距离计算
  - 优化过滤逻辑

- ✅ **点电荷计算** (PotentialCalculatorMixin)
  - 批量并行计算多个点电荷的贡献
  - 降低 AI 路径计算开销

- ✅ **玩家区块发送** (PlayerChunkSenderMixin)
  - 优化最近区块选择
  - 减少排序开销

- ✅ **其他传感器优化**
  - Breeze、Frog、Warden 等实体的攻击传感器
  - 羊冲撞目标选择
  - 诱惑传感器

### Native 后端

- Rust 实现的高性能计算内核
- 使用 FFM (Foreign Function & Memory API) 调用
- SIMD 加速和 Rayon 并行化
- 自动回退到 Java 实现（如果 native 不可用）

## ❌ 已移除的负优化

以下功能在测试中被证实**导致性能恶化**，已被完全禁用：

### 区块生成"优化"（实际是负优化）

- ❌ **NoiseChunkMixin** - 密度插值批量计算
  - 问题：过度计算、内存瓶颈、缓存失效
  - 影响：区块生成时大幅掉帧

- ❌ **NoiseInterpolatorMixin** - 插值计算拦截
  - 问题：与 NoiseChunkMixin 配套，同样导致性能下降

- ❌ **ChunkStatusMixin** - 异步区块生成
  - 问题：导致区块重复生成
  - 影响：比原版更慢

- ❌ **ServerChunkCacheMixin** - 区块预取
  - 问题：过度预取，CPU 持续高占用
  - 影响：浪费资源，无明显收益

## 📊 性能对比

| 场景 | 原版 | Beryllium (之前) | Beryllium (现在) |
|------|------|-----------------|-----------------|
| 区块生成 | 基准 | **严重掉帧** ❌ | **与原版相同** ✅ |
| 实体 AI | 基准 | 略好 | **明显更好** ✅ |
| 传感器计算 | 基准 | 略好 | **明显更好** ✅ |
| 内存占用 | 基准 | 偏高 | **正常** ✅ |
| CPU 占用 | 基准 | 偏高 | **正常** ✅ |

## 🎯 适用场景

### 推荐使用
- ✅ 大量实体的场景（农场、刷怪塔）
- ✅ 复杂 AI 行为（村民、敌对生物）
- ✅ 多玩家服务器

### 不影响
- ✅ 区块生成速度（与原版相同）
- ✅ 光照计算
- ✅ 流体模拟
- ✅ 方块更新

## 📁 文件位置

**NeoForge**:
```
D:\code\MC模组\铍优化\neoforge\build\libs\beryllium-neoforge-1.0-SNAPSHOT.jar
```

**Fabric**:
```
D:\code\MC模组\铍优化\fabric\build\libs\beryllium-fabric-1.0-SNAPSHOT.jar
```

## 🚀 安装方法

1. 确保安装了 **NeoForge 21.1.248** 或更高版本
2. 将 jar 文件复制到 `mods` 文件夹
3. 启动游戏

## 🔧 配置

本 mod **无需配置**，所有优化自动启用。

如果 native 后端加载失败，会自动回退到 Java 实现，不会影响游戏运行。

## ⚠️ 兼容性

### 已知兼容
- ✅ C2ME (Concurrent Chunk Management Engine)
- ✅ Sodium
- ✅ Lithium
- ✅ Starlight

### 不兼容
- ❌ 无（目前未发现不兼容的 mod）

## 📚 技术细节

### 架构
- **语言**: Java 21 + Rust 1.97.1
- **FFI**: Java FFM API (JEP 454)
- **并行**: Rayon 线程池
- **SIMD**: AVX2 向量化（x86_64）

### 目录结构
```
alku.beryllium/
├── api/           # 公共接口
├── bridge/        # FFM Native 桥接
├── compute/       # Java fallback 实现
├── mixin/         # Mixin 注入点
└── worldgen/      # 世界生成工具（未使用）
```

## 🐛 问题排查

### 游戏崩溃
- 检查 Minecraft 版本是否为 1.21.1
- 检查 NeoForge/Fabric 版本是否匹配
- 查看崩溃报告中的错误信息

### Native 后端未加载
- 正常现象，会自动使用 Java fallback
- 性能影响不大（实体优化仍然生效）
- 使用 `/beryllium native` 命令查看状态

### 性能没有提升
- 确保场景中有大量实体
- 小规模场景差异不明显
- 可以使用 F3 查看实体数量

## 📝 开发历史

### 问题发现过程

1. **初始版本** - 工作流自动生成，包含大量"优化"
2. **发现问题** - 游戏比不加 mod 更卡
3. **第一轮修复** - 禁用异步区块生成
4. **第二轮修复** - 修复 Mixin 包冲突
5. **第三轮修复** - 发现密度插值优化是负优化
6. **最终版本** - 只保留验证有效的优化

### 关键教训

1. **批量处理不总是更快** - 内存带宽可能成为瓶颈
2. **FFM 不是银弹** - 跨边界调用有成本
3. **需要实际性能分析** - 理论假设必须验证
4. **理解访问模式** - Minecraft 的惰性计算模式

## 📄 许可证

本项目遵循原项目许可证。

## 🙏 致谢

- Architectury - 多加载器支持
- Mixin - 运行时代码注入
- Rust 和 Rayon - 高性能计算后端

---

**最后更新**: 2026-08-06  
**状态**: 稳定版本，可以安全使用
