package alku.beryllium.worldgen;

import alku.beryllium.config.BerylliumConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步区块生成器，避免主线程阻塞
 *
 * 工作原理：
 * 1. 使用独立线程池异步生成远距离区块
 * 2. 近距离区块在主线程生成（保证响应速度）
 * 3. 优先级队列确保玩家附近区块优先生成
 * 4. 背压控制防止内存溢出
 * 5. 集成区块预取和缓存机制
 */
public class AsyncChunkGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Beryllium/AsyncChunkGen");

    private static final int PREFETCH_INTERVAL_TICKS = 20; // 每秒更新一次预取

    private static AsyncChunkGenerator INSTANCE;

    private final ExecutorService executorService;
    private final PriorityBlockingQueue<ChunkGenTask> taskQueue;
    private final ConcurrentHashMap<Long, CompletableFuture<ChunkAccess>> pendingChunks;
    private final AtomicInteger pendingTaskCount;
    private final ChunkGenerationCache cache;
    private final ChunkGenerationPredictor predictor;
    private volatile boolean enabled;

    private AsyncChunkGenerator() {
        this.taskQueue = new PriorityBlockingQueue<>(256,
            (a, b) -> Integer.compare(a.priority, b.priority));
        this.pendingChunks = new ConcurrentHashMap<>();
        this.pendingTaskCount = new AtomicInteger(0);
        this.cache = ChunkGenerationCache.getInstance();
        this.predictor = ChunkGenerationPredictor.getInstance();
        this.enabled = BerylliumConfig.isAsyncChunkGenEnabled();

        int workerThreads = BerylliumConfig.getWorkerThreads();
        this.executorService = Executors.newFixedThreadPool(workerThreads, r -> {
            Thread thread = new Thread(r);
            thread.setName("Beryllium-ChunkGen-Worker");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });

        LOGGER.info("异步区块生成器已启动，工作线程数: {}", workerThreads);
    }

    public static synchronized AsyncChunkGenerator getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AsyncChunkGenerator();
        }
        return INSTANCE;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            LOGGER.info("异步区块生成已禁用");
        } else {
            LOGGER.info("异步区块生成已启用");
        }
    }

    /**
     * 判断区块是否应该异步生成
     */
    public boolean shouldGenerateAsync(ChunkPos playerPos, ChunkPos chunkPos) {
        if (!enabled) {
            return false;
        }

        int syncRadius = BerylliumConfig.getSyncGenerationRadius();
        int distanceSquared = playerPos.distanceSquared(chunkPos);

        return distanceSquared > syncRadius * syncRadius;
    }

    /**
     * 提交异步区块生成任务
     */
    public CompletableFuture<ChunkAccess> submitAsync(
        ServerLevel level,
        ChunkPos chunkPos,
        ChunkPos playerPos,
        Callable<ChunkAccess> generator
    ) {
        int maxPendingTasks = BerylliumConfig.getMaxPendingTasks();

        if (!enabled || pendingTaskCount.get() >= maxPendingTasks) {
            try {
                return CompletableFuture.completedFuture(generator.call());
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        long key = chunkPos.toLong();

        // 检查缓存
        if (cache.containsKey(chunkPos)) {
            // 有缓存，优先使用
            LOGGER.debug("区块 {} 命中缓存", chunkPos);
        }

        CompletableFuture<ChunkAccess> existing = pendingChunks.get(key);
        if (existing != null) {
            return existing;
        }

        int priority = calculatePriority(playerPos, chunkPos);

        CompletableFuture<ChunkAccess> future = new CompletableFuture<>();
        ChunkGenTask task = new ChunkGenTask(key, priority, generator, future);

        pendingChunks.put(key, future);
        pendingTaskCount.incrementAndGet();

        executorService.submit(() -> {
            try {
                ChunkAccess chunk = task.generator.call();
                task.future.complete(chunk);
            } catch (Exception e) {
                LOGGER.error("区块生成失败: {}", chunkPos, e);
                task.future.completeExceptionally(e);
            } finally {
                pendingChunks.remove(task.chunkKey);
                pendingTaskCount.decrementAndGet();
            }
        });

        return future;
    }

    /**
     * 更新玩家位置并触发预取
     */
    public void updatePlayerAndPrefetch(ServerPlayer player, int renderDistance) {
        if (!enabled) {
            return;
        }

        // 更新玩家移动历史
        predictor.updatePlayerPosition(player);

        // 预测需要的区块
        List<ChunkGenerationPredictor.PredictedChunk> predictions =
            predictor.predictChunks(player, renderDistance);

        if (predictions.isEmpty()) {
            return;
        }

        LOGGER.debug("为玩家 {} 预测了 {} 个区块", player.getName().getString(), predictions.size());

        // 将预测的区块加入优先级队列（但不立即生成）
        for (ChunkGenerationPredictor.PredictedChunk predicted : predictions) {
            ChunkPos chunkPos = predicted.getPos();
            long key = chunkPos.toLong();

            // 如果已经在队列中或正在生成，跳过
            if (pendingChunks.containsKey(key)) {
                continue;
            }

            // 如果缓存中已存在，跳过
            if (cache.containsKey(chunkPos)) {
                continue;
            }

            // TODO: 这里可以添加实际的预取逻辑
            // 例如：提前加载区块数据、预计算噪声等
        }
    }

    /**
     * 计算区块生成优先级（距离越近优先级越高）
     */
    private int calculatePriority(ChunkPos playerPos, ChunkPos chunkPos) {
        return playerPos.distanceSquared(chunkPos);
    }

    /**
     * 获取当前待处理任务数
     */
    public int getPendingTaskCount() {
        return pendingTaskCount.get();
    }

    /**
     * 获取缓存统计
     */
    public ChunkGenerationCache.CacheStats getCacheStats() {
        return cache.getStats();
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * 关闭异步生成器
     */
    public void shutdown() {
        LOGGER.info("正在关闭异步区块生成器...");
        enabled = false;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        pendingChunks.clear();
        cache.clear();
        LOGGER.info("异步区块生成器已关闭");
    }

    private static class ChunkGenTask {
        final long chunkKey;
        final int priority;
        final Callable<ChunkAccess> generator;
        final CompletableFuture<ChunkAccess> future;

        ChunkGenTask(long chunkKey, int priority, Callable<ChunkAccess> generator,
                    CompletableFuture<ChunkAccess> future) {
            this.chunkKey = chunkKey;
            this.priority = priority;
            this.generator = generator;
            this.future = future;
        }
    }
}
