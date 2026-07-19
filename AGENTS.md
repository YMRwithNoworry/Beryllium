# Repository Guidelines

## Project Structure & Module Organization

Beryllium is an Architectury Minecraft 1.21.1 performance mod targeting Fabric and NeoForge. Shared Java logic, mixins, the Java fallback, and FFM bridge live in `common/src/main/java/alku/beryllium`; shared resources are in `common/src/main/resources`. Keep loader-specific entry points and metadata in `fabric/` and `neoforge/`. The Rust workspace is `native/`; pure kernels belong in `native/crates/beryllium-native/src/kernel.rs`, while the stable C ABI belongs in `ffi.rs`.

Keep game state and side effects in Java. Pass packed, batched data to Rust rather than Minecraft objects. Every native path must preserve the Java fallback's ordering, boundary, tie-breaking, and failure behavior.

## Build, Test, and Development Commands

Use Java 21 and a system-installed Gradle; this repository does not include a Gradle wrapper.

```bash
gradle :common:build :fabric:build :neoforge:build  # build remapped platform jars
gradle :common:check :fabric:check :neoforge:check  # parity, native-runtime, and FFM-reuse checks
gradle :fabric:runClient                             # launch a Fabric development client
gradle :neoforge:runClient                           # launch a NeoForge development client
cargo test --manifest-path native/Cargo.toml         # test the Rust workspace
cargo build --manifest-path native/Cargo.toml --release
```

Run `gradle :common:performanceBenchmark` only for measured performance work; do not treat it as a functional test.

## Coding Style & Naming Conventions

Use four-space indentation in Java and follow the existing brace and import layout. Java packages remain lowercase under `alku.beryllium`; classes use `PascalCase`, methods and fields use `camelCase`, and mixins end in `Mixin` or `Accessor`. Use Rust 2021 conventions: `snake_case` functions/modules and `CamelCase` types. Prefer small, allocation-aware batch helpers over object-heavy FFI calls.

## Testing Guidelines

Place Java tests in `common/src/test/java` beside their package. Name JUnit classes `*Test`; use `*Verifier` for executable parity/runtime checks. Add edge cases for packed-array validation, vanilla boundary semantics, equal-distance ordering, and native-unavailable fallback. New kernels require a Java reference implementation, Rust implementation, and parity coverage.

## Commit & Pull Request Guidelines

Recent commits use concise Chinese parenthesized subjects, such as `（优化FFM线程本地内存复用并加入并发校验）`; retain that style or use an equally brief imperative subject focused on one change. Keep commits narrowly scoped. PRs should describe affected modules, semantic/parity risks, verification commands run, linked issues, and screenshots or benchmark deltas when behavior or performance changes.
