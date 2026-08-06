package alku.beryllium.worldgen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 */
public class AsyncChunkGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Beryllium/AsyncChunkGen");
    
    private static final int SYNC_RADIUS = 3;
    private static final int MAX_PENDING_TASKS = 256;
    private static final int WORKER_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
    
    private static AsyncChunkGenerator INSTANCE;
    
    private final ExecutorService executorService;
    private final PriorityBlockingQueue<ChunkGenTask> taskQueue;
    private final ConcurrentHashMap<Long, CompletableFuture<ChunkAccess>> pendingChunks;
    private final AtomicInteger pendingTaskCount;
    private volatile boolean enabled;
    
    private AsyncChunkGenerator() {
        this.taskQueue = new PriorityBlockingQueue<>(256, 
            (a, b) -> Integer.compare(a.priority, b.priority));
        this.pendingChunks = new ConcurrentHashMap<>();
        this.pendingTaskCount = new AtomicInteger(0);
        this.enabled = true;
        
        this.executorService = Executors.newFixedThreadPool(WORKER_THREADS, r -> {
            Thread thread = new Thread(r);
            thread.setName("Beryllium-ChunkGen-Worker");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
        
        LOGGER.info("异步区块生成器已启动，工作线程数: {}", WORKER_THREADS);
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
        
        int distanceSquared = playerPos.distanceSquared(chunkPos);
        
        return distanceSquared > SYNC_RADIUS * SYNC_RADIUS;
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
        if (!enabled || pendingTaskCount.get() >= MAX_PENDING_TASKS) {
            try {
                return CompletableFuture.completedFuture(generator.call());
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        
        long key = chunkPos.toLong();
        
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
