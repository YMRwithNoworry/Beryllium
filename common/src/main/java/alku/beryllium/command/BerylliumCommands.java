package alku.beryllium.command;

import alku.beryllium.bridge.NativeBridge;
import alku.beryllium.compute.NativeBatching;
import alku.beryllium.config.BerylliumConfig;
import alku.beryllium.monitor.ChunkGenPerformanceMonitor;
import alku.beryllium.worldgen.AsyncChunkGenerator;
import alku.beryllium.worldgen.ChunkGenerationCache;
import alku.beryllium.worldgen.ChunkGenerationTracker;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class BerylliumCommands {
    private static final int[] SAMPLE_POSITIONS = {
        0, 64, 0,
        3, 68, 4,
        -1, 63, -2,
        128, 70, -128
    };

    private BerylliumCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("beryllium")
            .then(Commands.literal("native")
                .executes(context -> showNativeStatus(context.getSource())))
            .then(Commands.literal("distance")
                .executes(context -> runDistanceKernel(context.getSource())))
            .then(Commands.literal("async")
                .executes(context -> showAsyncStatus(context.getSource()))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(context -> toggleAsync(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
            .then(Commands.literal("cache")
                .executes(context -> showCacheStats(context.getSource()))
                .then(Commands.literal("clear")
                    .executes(context -> clearCache(context.getSource()))))
            .then(Commands.literal("chunk")
                .executes(context -> showChunkStats(context.getSource()))
                .then(Commands.literal("reset")
                    .executes(context -> resetChunkStats(context.getSource()))))
            .then(Commands.literal("config")
                .executes(context -> showConfig(context.getSource()))
                .then(Commands.literal("syncRadius")
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 10))
                        .executes(context -> setSyncRadius(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("maxTasks")
                    .then(Commands.argument("tasks", IntegerArgumentType.integer(64, 1024))
                        .executes(context -> setMaxTasks(context.getSource(), IntegerArgumentType.getInteger(context, "tasks")))))
                .then(Commands.literal("adaptive")
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> toggleAdaptive(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("reload")
                    .executes(context -> reloadConfig(context.getSource())))
                .then(Commands.literal("save")
                    .executes(context -> saveConfig(context.getSource())))));
    }

    private static int showNativeStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
            "Beryllium native backend: "
                + NativeBridge.status()
                + ", entity batch threshold: "
                + NativeBatching.entityBatchThreshold()
                + ", entity distance sort threshold: "
                + NativeBatching.entityDistanceSortThreshold()
                + ", block distance batch threshold: "
                + NativeBatching.blockDistanceBatchThreshold()
                + ", potential batch threshold: "
                + NativeBatching.potentialBatchThreshold()
                + ", chunk send selection threshold: "
                + NativeBatching.chunkSendSelectionThreshold()
                + ", nearest-item Top-K threshold: "
                + NativeBatching.nearestItemTopKThreshold()
        ), false);
        return NativeBridge.isLoaded() ? 1 : 0;
    }

    private static int runDistanceKernel(CommandSourceStack source) {
        long[] distances = NativeBridge.computeSquaredDistances(0, 64, 0, SAMPLE_POSITIONS);
        source.sendSuccess(() -> Component.literal("Beryllium squared distances: " + format(distances)), false);
        return distances.length;
    }

    private static String format(long[] values) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(values[index]);
        }
        return builder.toString();
    }

    private static int showAsyncStatus(CommandSourceStack source) {
        AsyncChunkGenerator asyncGen = AsyncChunkGenerator.getInstance();
        boolean enabled = asyncGen.isEnabled();
        int pendingTasks = asyncGen.getPendingTaskCount();
        ChunkGenerationTracker.TrackerStats trackerStats = ChunkGenerationTracker.getInstance().getStats();

        source.sendSuccess(() -> Component.literal(
            "异步区块生成: " + (enabled ? "§a已启用" : "§c已禁用") +
            ", 待处理任务: §e" + pendingTasks +
            " §7| 正在生成: §e" + trackerStats.getGeneratingCount()
        ), false);

        return 1;
    }

    private static int toggleAsync(CommandSourceStack source, boolean enabled) {
        AsyncChunkGenerator.getInstance().setEnabled(enabled);
        BerylliumConfig.setAsyncChunkGenEnabled(enabled);

        source.sendSuccess(() -> Component.literal(
            "异步区块生成已" + (enabled ? "§a启用" : "§c禁用")
        ), true);

        return 1;
    }

    private static int showCacheStats(CommandSourceStack source) {
        ChunkGenerationCache.CacheStats stats = AsyncChunkGenerator.getInstance().getCacheStats();

        source.sendSuccess(() -> Component.literal("§6=== 区块生成缓存统计 ==="), false);
        source.sendSuccess(() -> Component.literal(
            "缓存大小: §e" + stats.getCurrentSize() + "§7/§e" + stats.getMaxSize()
        ), false);
        source.sendSuccess(() -> Component.literal(
            "缓存命中: §a" + stats.getHits() + " §7| 未命中: §c" + stats.getMisses()
        ), false);
        source.sendSuccess(() -> Component.literal(
            "命中率: §e" + String.format("%.2f%%", stats.getHitRate() * 100)
        ), false);

        return 1;
    }

    private static int clearCache(CommandSourceStack source) {
        AsyncChunkGenerator.getInstance().clearCache();
        source.sendSuccess(() -> Component.literal("§a区块生成缓存已清空"), true);
        return 1;
    }

    private static int showConfig(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6=== Beryllium 配置 ==="), false);
        source.sendSuccess(() -> Component.literal("异步生成: " +
            (BerylliumConfig.isAsyncChunkGenEnabled() ? "§a启用" : "§c禁用")), false);
        source.sendSuccess(() -> Component.literal("同步半径: §e" +
            BerylliumConfig.getSyncGenerationRadius() + " §7区块"), false);
        source.sendSuccess(() -> Component.literal("最大任务数: §e" +
            BerylliumConfig.getMaxPendingTasks()), false);
        source.sendSuccess(() -> Component.literal("工作线程: §e" +
            BerylliumConfig.getWorkerThreads()), false);
        source.sendSuccess(() -> Component.literal("自适应调整: " +
            (BerylliumConfig.isAdaptiveThresholdEnabled() ? "§a启用" : "§c禁用")), false);
        source.sendSuccess(() -> Component.literal("性能监控: " +
            (BerylliumConfig.isPerformanceMonitorEnabled() ? "§a启用" : "§c禁用")), false);

        return 1;
    }

    private static int setSyncRadius(CommandSourceStack source, int radius) {
        BerylliumConfig.setSyncGenerationRadius(radius);
        source.sendSuccess(() -> Component.literal(
            "同步生成半径已设置为: §e" + radius + " §7区块"
        ), true);
        return 1;
    }

    private static int setMaxTasks(CommandSourceStack source, int tasks) {
        BerylliumConfig.setMaxPendingTasks(tasks);
        source.sendSuccess(() -> Component.literal(
            "最大任务数已设置为: §e" + tasks
        ), true);
        return 1;
    }

    private static int toggleAdaptive(CommandSourceStack source, boolean enabled) {
        BerylliumConfig.setAdaptiveThresholdEnabled(enabled);
        source.sendSuccess(() -> Component.literal(
            "自适应阈值调整已" + (enabled ? "§a启用" : "§c禁用")
        ), true);
        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        BerylliumConfig.reload();
        source.sendSuccess(() -> Component.literal("§a配置已重新加载"), true);
        return 1;
    }

    private static int saveConfig(CommandSourceStack source) {
        BerylliumConfig.saveConfig();
        source.sendSuccess(() -> Component.literal("§a配置已保存"), true);
        return 1;
    }

    private static int showChunkStats(CommandSourceStack source) {
        ChunkGenPerformanceMonitor.PerformanceStats stats =
            ChunkGenPerformanceMonitor.getInstance().getStats();

        source.sendSuccess(() -> Component.literal("§6=== 区块生成统计 ==="), false);

        source.sendSuccess(() -> Component.literal(
            "总生成数: §e" + stats.getTotalGenerationCount() +
            " §7(同步: §e" + stats.getSyncGenerationCount() +
            " §7| 异步: §e" + stats.getAsyncGenerationCount() + "§7)"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "异步比例: §e" + String.format("%.1f%%", stats.getAsyncRatio() * 100)
        ), false);

        source.sendSuccess(() -> Component.literal(
            "平均耗时: §7同步 §e" + String.format("%.2f", stats.getAvgSyncGenerationMs()) + "ms" +
            " §7| 异步 §e" + String.format("%.2f", stats.getAvgAsyncGenerationMs()) + "ms"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "峰值耗时: §7同步 §e" + String.format("%.2f", stats.getMaxSyncGenerationMs()) + "ms" +
            " §7| 异步 §e" + String.format("%.2f", stats.getMaxAsyncGenerationMs()) + "ms"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "主线程阻塞: §c" + stats.getMainThreadBlockCount() + " §7次" +
            " §7| 总计 §c" + String.format("%.2f", stats.getTotalMainThreadBlockMs()) + "ms" +
            " §7| 平均 §c" + String.format("%.2f", stats.getAvgMainThreadBlockMs()) + "ms"
        ), false);

        if (stats.getMaxMainThreadBlockMs() > 0) {
            source.sendSuccess(() -> Component.literal(
                "最长阻塞: §c" + String.format("%.2f", stats.getMaxMainThreadBlockMs()) + "ms"
            ), false);
        }

        source.sendSuccess(() -> Component.literal(
            "生成速率: §e" + String.format("%.1f", stats.getGenerationRatePerSecond()) + " §7区块/秒"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "上次 tick: §7同步 §e" + stats.getLastTickSyncCount() +
            " §7| 异步 §e" + stats.getLastTickAsyncCount() +
            " §7| 阻塞 §c" + String.format("%.2f", stats.getLastTickMainThreadBlockMs()) + "ms"
        ), false);

        return 1;
    }

    private static int resetChunkStats(CommandSourceStack source) {
        ChunkGenPerformanceMonitor.getInstance().reset();
        source.sendSuccess(() -> Component.literal("§a区块生成统计已重置"), true);
        return 1;
    }
}
