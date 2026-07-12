# FFM Thread-Local Scratch Arena Design

**Date:** 2026-07-12
**Status:** Implementation target

## Goal

Reduce the fixed Java 21 FFM overhead on existing Rust kernels without changing Minecraft-visible calculations, ordering, boundary comparisons, predicate evaluation, or Java fallback behavior.

## Evidence

The current Windows x86_64/JDK 21 benchmark uses the bundled native library and 100 warmup plus 300 measurement iterations. The latest run measured fused nearest-item distance at `0.57x`, `2.34x`, `1.96x`, and `3.18x` of vanilla Java for 256, 1,024, 4,096, and 8,192 candidates. The 8,192-charge potential calculation was `0.77x`; small ChunkMap scans were also slower. The existing `FfmNativeBridge` opens a shared arena, allocates native buffers, creates a session list, copies arrays, and closes the arena for every downcall.

The next slice therefore targets bridge allocation and session setup. It does not claim that every Minecraft hotspot should cross FFM.

## Architecture

Each Java thread receives a private reusable `Session` through `ThreadLocal`. A session owns reusable buffer slots, and each slot owns one shared FFM arena. Buffers from two threads can never overlap. Replacing a slot's kind or capacity creates a replacement arena and closes the old one after the native call has ended, so gradual input growth does not retain every historical allocation.

Each buffer records its element kind, allocated capacity, current Java array, and native segment. A slot is reallocated only when the requested kind changes or the requested length exceeds its capacity. The logical array length, rather than the retained capacity, is still passed to every C ABI function.

Before every call, input and output arrays are copied into their logical native ranges. Output arrays are initialized from their Java values before the call, preserving the native kernels' output-tail contract when a kernel writes only a prefix. Successful calls copy the full logical output array back before the session is reused.

The native kernels remain synchronous and receive only primitive buffers. No Minecraft object, Java collection, or mutable game state crosses FFM. Any reflection failure, invalid native status, or unexpected downcall exception continues to return the existing failure sentinel so `NativeBridge` uses the Java reference implementation.

## Scope

In scope:

- Reuse the shared arena and per-thread session/buffer slots.
- Preserve all existing FFM symbols, C ABI lengths, status codes, output tails, and fallback paths.
- Add runtime coverage for repeated calls, output-tail preservation, and concurrent calls from multiple threads.
- Re-run the existing parity, native runtime, Rust, platform packaging, and performance checks.

Out of scope for this slice:

- Enabling Java FFM preview types in the compile API.
- Moving Minecraft predicates or entity state into Rust.
- Adding new mixins without a measured hotspot.
- Changing kernel arithmetic, tie ordering, strict/inclusive radius rules, or floating-point accumulation order.

## Failure and lifecycle rules

- A failed initialization leaves `FfmNativeBridge` unavailable exactly as before.
- A session is reset before each call, so stale output registrations and buffer references cannot leak between calls.
- A downcall never runs concurrently on the same session because sessions are thread-local.
- Each live buffer slot retains only its current arena allocation; a capacity/type replacement closes the previous arena.
- If a pooled operation cannot allocate or copy, the current bridge returns its normal failure sentinel and the Java caller falls back.

## Verification

1. Existing Java parity verifier covers every public bridge operation and reference semantics.
2. Native runtime verifier proves the bundled FFM path executes every symbol and preserves output prefixes/tails.
3. A concurrency verifier invokes distance, filtering, sorting, and potential kernels from multiple Java threads with independent expected results.
4. The benchmark is run before and after the change with the same JVM arguments and reports each candidate size, potential calculation, and ChunkMap comparison.
5. `cargo fmt --check`, `cargo test`, `:common:check`, `:fabric:build`, `:neoforge:build`, and both native-package verifiers must pass.

Success means the pooled bridge is semantically identical to the current Java reference and improves or does not regress the measured large-batch paths. Small paths remain eligible for Java fallback through the existing batching policy.
