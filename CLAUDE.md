# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

Beryllium 是一个使用 Architectury 的 Minecraft 多加载器性能模组，目标平台是 Fabric 和 NeoForge。当前代码基线是 Minecraft 1.21.1、Java 21，核心功能是通过 Java 薄壳调用 Rust native 后端执行批量坐标距离计算，并在 native 不可用时回退到 Java 参考实现。

## 架构速览

- `common`：共享逻辑放这里。`alku.beryllium.Beryllium` 是共享初始化入口，Fabric 和 NeoForge 都会调用它。
- `common/src/main/java/alku/beryllium/command/BerylliumCommands.java`：注册 `/beryllium native` 和 `/beryllium distance` 命令。
- `common/src/main/java/alku/beryllium/compute/JavaComputeKernels.java`：Java 参考实现和 fallback。
- `common/src/main/java/alku/beryllium/bridge/NativeBridge.java`：native 后端的 Java 薄壳入口。
- `common/src/main/java/alku/beryllium/bridge/NativeLibraryLoader.java`：负责显式路径加载、jar 内 native 资源提取和 `System.loadLibrary` 回退。
- `native/`：Rust 原生工作区。`native/crates/beryllium-native/src/kernel.rs` 放纯计算 kernel，`ffi.rs` 暴露 JNI 入口。
- `fabric`：Fabric 专属入口和客户端入口都在这里。
  - `BerylliumFabric` 负责常规 mod 初始化，并转调 `Beryllium.init()`。
  - `BerylliumFabricClient` 负责 Fabric 的客户端专属逻辑。
- `neoforge`：NeoForge 专属入口在这里。
  - `BerylliumNeoForge` 的构造器负责转调 `Beryllium.init()`。
- `common/src/main/resources/beryllium.mixins.json`：共享 mixin 配置，已经同时被 Fabric 和 NeoForge 载入。
- `fabric/src/main/resources/fabric.mod.json` 与 `neoforge/src/main/resources/META-INF/neoforge.mods.toml`：分别是两个加载器的 mod 描述文件，负责声明入口、依赖和 mixin 配置。
- 根目录 `build.gradle` / `settings.gradle` / `gradle.properties`：统一管理版本、Minecraft/loader 依赖、Java 21 目标和多模块构建。

### 结构上的关键点

- 这是“共享核心 + 平台薄适配层”结构：业务逻辑、命令、通用工具、Java fallback 和共享 mixin 放进 `common`，Fabric/NeoForge API 相关代码限制在各自平台模块里。
- Rust 原生层保持“Java 负责状态与副作用、Rust 负责纯计算”的分层，跨 FFI 边界优先使用批处理快照，不要把游戏对象直接暴露给 native。
- Native 加载顺序：`beryllium.native.path` 显式路径 → jar 内 `assets/beryllium/native/<os>/<arch>/` → `System.loadLibrary("beryllium_native")` → Java fallback。
- 平台 jar 的打包是通过 Shadow + remap 流程完成的；不要在 `common` 里直接假设最终产物是单独的 jar。

## 常用命令

> 当前仓库没有提交 `gradlew` / `gradlew.bat`，下面默认使用系统安装的 `gradle`。如果以后补入 wrapper，把命令里的 `gradle` 换成 `./gradlew` 即可。构建需要 Java 21。

- 构建全部模块：`gradle :common:build :fabric:build :neoforge:build`
- 只构建 Fabric：`gradle :fabric:build`
- 只构建 NeoForge：`gradle :neoforge:build`
- 运行 Fabric 客户端：`gradle :fabric:runClient`
- 运行 NeoForge 客户端：`gradle :neoforge:runClient`
- 验证全部模块：`gradle :common:check :fabric:check :neoforge:check`
- 运行测试：`gradle :common:test :fabric:test :neoforge:test`
- 运行单个测试：`gradle :common:test --tests 'fully.qualified.TestClass'`
  - 如果测试放在 `fabric` 或 `neoforge`，把模块名换成对应子项目。
- 清理构建产物：`gradle clean`
- Rust 工作区测试：`cargo test --manifest-path native/Cargo.toml`
- Rust release 构建：`cargo build --manifest-path native/Cargo.toml --release`

### 备注

- 这个仓库没有单独的 lint 任务定义；`check` 是当前最接近统一验证入口的命令。
- 如果你在修改共享逻辑，优先检查 `common`，再分别在 Fabric 和 NeoForge 里确认入口和打包是否仍然一致。
- 版本号、平台版本和依赖版本都集中在 `gradle.properties`，升级时先看这里再改各个平台文件。
- 新增 native kernel 时先补 Java fallback，再补 Rust 实现和 parity 测试。
